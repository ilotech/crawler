package org.ilot.crawler.algorithms.concurrent;

import org.ilot.crawler.algorithms.GraphAlgorithm;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UnorderedSearch<E> implements GraphAlgorithm<E> {
    private final Deque<Node<E>> workDequeue;
    private final Set<Node<E>> visited;
    private final Consumer<Node<E>> addElement;
    private final Function<E, Set<E>> getNeighbours;

    private ExecutorService executorService;

    private final Lock lock = new ReentrantLock(true);
    private final Condition isEmpty = lock.newCondition();

    private volatile E searchResult;
    private volatile boolean resultFound;

    private Predicate<Node<E>> searchPredicate;
    private long timeout;

    public UnorderedSearch(ExecutorService executorService,
                           Function<E, Set<E>> getNeighbours,
                           long timeout) {
        this.executorService = executorService;
        this.workDequeue = new ConcurrentLinkedDeque<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.addElement = workDequeue::addLast;
        this.getNeighbours = getNeighbours;
        this.timeout = timeout;
    }

    public UnorderedSearch(ExecutorService executorService,
                           Function<E, Set<E>> getNeighbours,
                           long timeout,
                           Predicate<Node<E>> searchPredicate) {
        this(executorService, getNeighbours, timeout);
        this.searchPredicate = searchPredicate;
    }

    @Override
    public void traverse(E rootElement) {
        workDequeue.add(Node.of(rootElement, 0));
        while (awaitNotEmpty()) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new TraversalTask(node));
            sleep(); // for testing until saturation policy is implemented
        }
        // TODO implement shutdown policy
        executorService.shutdownNow();
    }

    // TODO revisit
    @Override
    public Optional<E> search(E rootElement) {
        Node<E> rootNode = Node.of(rootElement, 0);
        if (searchPredicate.test(rootNode)) return Optional.of(rootElement);
        workDequeue.add(Node.of(rootElement, 0));
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new SearchTask(node));
            sleep(); // for testing until saturation policy is implemented
        }
        executorService.shutdownNow();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
    }

    @Override
    public void continueTraversingFrom(List<E> nodes) {
        // TODO implement
    }

    @Override
    public Optional<E> continueSearchingFrom(List<E> nodes) {
        // TODO implement
        return Optional.empty();
    }

    private boolean awaitNotEmpty() {
        if (!workDequeue.isEmpty()) {
            return true;
        }
        try {
            lock.lock();
            {
                while (workDequeue.isEmpty()) {
                    try {
                        if (!isEmpty.await(timeout, TimeUnit.MILLISECONDS)) return false;
                    } catch (InterruptedException ignored) {
                        // nobody should interrupt the main thread
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            // shouldn't be thrown
            return false;
        } finally {
            lock.unlock();
        }
    }

    // TODO implement proper saturation policy, hack for now
    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class TraversalTask implements Runnable {
        private final Node<E> node;

        private TraversalTask(Node<E> node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (visited.contains(node)) return;
            if (Thread.currentThread().isInterrupted()) return;
            // TODO how to timeout if #getNeighbours is taking too long? future.get? #getNeighbours should support timeouts
            getNeighbours.apply(node.getElement())
                    .stream()
                    .filter(e -> !visited.contains(Node.of(e)))
                    .map(e -> Node.of(e, node.getLevel() + 1))
                    .collect(Collectors.toSet())
                    .forEach(addElement);

            visited.add(node);
            signalWaitingThread();
        }
    }

    private class SearchTask implements Runnable {
        private final Node<E> node;

        private SearchTask(Node<E> node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (visited.contains(node)) return;
            if (Thread.currentThread().isInterrupted()) return;
            getNeighbours.apply(node.getElement())
                    .stream()
                    .filter(e -> !visited.contains(Node.of(e)))
                    .map(e -> Node.of(e, node.getLevel() + 1))
                    .collect(Collectors.toSet())
                    .forEach(neighbour -> {
                        if (searchPredicate.test(node)) {
                            resultFound = true;
                            searchResult = node.getElement();
                            return;
                        }
                        addElement.accept(neighbour);
                    });

            visited.add(node);
            signalWaitingThread();
        }
    }

    private void signalWaitingThread() {
        try {
            lock.lock();
            isEmpty.signal();
        } catch (IllegalMonitorStateException e) {
            // shouldn't be thrown
            // TODO rethrow
        } finally {
            lock.unlock();
        }
    }
}