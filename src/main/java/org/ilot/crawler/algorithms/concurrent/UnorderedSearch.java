package org.ilot.crawler.algorithms.concurrent;

import org.ilot.crawler.algorithms.GraphAlgorithm;
import org.springframework.util.Assert;

import java.util.Deque;
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
    private final Function<Node<E>, Set<Node<E>>> getNeighbours;

    // TODO use executor service from the configuration
    private ExecutorService executorService;

    private final Lock lock = new ReentrantLock(true);
    private final Condition isEmpty = lock.newCondition();

    private volatile E searchResult;
    private volatile boolean resultFound;

    private Predicate<Node<E>> searchPredicate;
    private long timeout;

    private UnorderedSearch(ExecutorService executorService,
                            Function<Node<E>, Set<Node<E>>> getNeighbours) {
        this.executorService = executorService;
        this.workDequeue = new ConcurrentLinkedDeque<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.addElement = workDequeue::addLast;
        this.getNeighbours = getNeighbours;
    }

    private UnorderedSearch(ExecutorService executorService,
                            Function<Node<E>, Set<Node<E>>> getNeighbours,
                            Predicate<Node<E>> searchPredicate) {
        this(executorService, getNeighbours);
        this.searchPredicate = searchPredicate;
    }

    @Override
    public void traverse(E rootElement) {
        workDequeue.add(Node.of(rootElement));
        while (awaitNotEmpty()) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new UnorderedTraversalTask(node));
            sleep();
        }
        // TODO implement shutdown policy
        executorService.shutdown();
    }

    // TODO revisit
    @Override
    public Optional<E> search(E rootElement) {
        Assert.state(searchPredicate != null, "Search predicate must be defined when using search function.");
        workDequeue.add(Node.of(rootElement));
        while (awaitNotEmpty()) {
            if (resultFound) break;
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new UnorderedSearchTask(node));
            sleep();
        }
        executorService.shutdown();
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

    private class UnorderedTraversalTask implements Runnable {
        private final Node<E> node;

        private UnorderedTraversalTask(Node<E> node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (visited.contains(node)) return;
            getNeighbours.apply(node)
                    .stream()
                    .filter(e -> !visited.contains(e))
                    .collect(Collectors.toSet())
                    .forEach(addElement);

            visited.add(node);
            signalWaitingThread();
        }

        private void signalWaitingThread() {
            try {
                lock.lock();
                isEmpty.signalAll();
            } catch (IllegalMonitorStateException e) {
                // TODO log
            } finally {
                lock.unlock();
            }
        }
    }

    private class UnorderedSearchTask implements Runnable {
        private final Node<E> node;

        private UnorderedSearchTask(Node<E> node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (searchPredicate.test(node)) {
                resultFound = true;
                searchResult = node.getElement();
                return;
            }
            if (visited.contains(node)) return;
            getNeighbours.apply(node)
                    .stream()
                    .filter(e -> !visited.contains(e))
                    .collect(Collectors.toSet())
                    .forEach(addElement);

            visited.add(node);
            signalWaitingThread();
        }

        private void signalWaitingThread() {
            try {
                lock.lock();
                isEmpty.signalAll();
            } catch (IllegalMonitorStateException e) {
                // TODO log
            } finally {
                lock.unlock();
            }
        }
    }
}