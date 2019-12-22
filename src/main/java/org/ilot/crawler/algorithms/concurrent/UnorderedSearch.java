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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UnorderedSearch<E> implements GraphAlgorithm<E> {
    private final ExecutorService executorService;
    private final Deque<Node<E>> workDequeue;
    private final Set<Node<E>> visited;

    private final Consumer<Node<E>> addElement;
    private final BiFunction<E, Long, Set<E>> getNeighbours;
    private Predicate<Node<E>> searchPredicate;

    private final long isEmptyTimeout;
    private final long getNeighboursTimeout;

    private final Lock lock = new ReentrantLock(true);
    private final Condition isEmpty = lock.newCondition();

    private volatile E searchResult;
    private volatile boolean resultFound;

    public UnorderedSearch(ExecutorService executorService,
                           BiFunction<E, Long, Set<E>> getNeighbours,
                           long isEmptyTimeout,
                           long getNeighboursTimeout) {
        this.executorService = executorService;
        this.workDequeue = new ConcurrentLinkedDeque<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.addElement = workDequeue::addLast;
        this.getNeighbours = getNeighbours;
        this.isEmptyTimeout = isEmptyTimeout;
        this.getNeighboursTimeout = getNeighboursTimeout;
        this.searchPredicate = n -> true;
    }

    public UnorderedSearch(ExecutorService executorService,
                           BiFunction<E, Long, Set<E>> getNeighbours,
                           long isEmptyTimeout,
                           long getNeighboursTimeout,
                           Predicate<Node<E>> searchPredicate) {
        this(executorService, getNeighbours, isEmptyTimeout, getNeighboursTimeout);
        this.searchPredicate = searchPredicate;
    }

    @Override
    public void traverse(E rootElement) {
        workDequeue.add(Node.of(rootElement, 0));
        internalSearch();
        // TODO implement shutdown policy

    }

    // TODO revisit
    @Override
    public Optional<E> search(E rootElement) {
        Node<E> rootNode = Node.of(rootElement, 0);
        if (searchPredicate.test(rootNode)) return Optional.of(rootElement);
        internalSearch();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
    }

    private void internalSearch() {
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new Worker<>(this, node));
            sleep(); // for testing until saturation policy is implemented
        }
        executorService.shutdownNow();
    }

    @Override
    public void continueTraversingFrom(List<E> elements) {
        // TODO make sure list is sorted
        workDequeue.addAll(elements.parallelStream().map(Node::of).collect(Collectors.toList()));
        internalSearch();
    }

    @Override
    public Optional<E> continueSearchingFrom(List<E> elements) {
        // TODO make sure list is sorted
        workDequeue.addAll(elements.parallelStream().map(Node::of).collect(Collectors.toList()));
        internalSearch();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
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
                        if (!isEmpty.await(isEmptyTimeout, TimeUnit.MILLISECONDS)) return false;
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

    private static class Worker<E> implements Runnable {
        private final UnorderedSearch<E> us;
        private final Node<E> node;

        private Worker(UnorderedSearch<E> us, Node<E> node) {
            this.us = us;
            this.node = node;
        }

        @Override
        public void run() {
            if (us.visited.contains(node)) return;
            if (Thread.currentThread().isInterrupted()) return;
            Set<Node<E>> nodes = us.getNeighbours.apply(node.getElement(), us.getNeighboursTimeout)
                    .stream()
                    .filter(e -> !us.visited.contains(Node.of(e)))
                    .map(e -> Node.of(e, node.getLevel() + 1))
                    .collect(Collectors.toSet());

            if (nodes.stream().anyMatch(this::isResult)) return;
            nodes.forEach(us.addElement);
            us.visited.add(node);
            signalWaitingThread();
        }

        private void signalWaitingThread() {
            try {
                us.lock.lock();
                us.isEmpty.signal();
            } catch (IllegalMonitorStateException e) {
                // shouldn't be thrown
                // TODO rethrow
            } finally {
                us.lock.unlock();
            }
        }

        private boolean isResult(Node<E> node) {
            if (!us.searchPredicate.test(node)) {
                return false;
            }
            us.resultFound = true;
            us.searchResult = node.getElement();
            return true;
        }
    }
}