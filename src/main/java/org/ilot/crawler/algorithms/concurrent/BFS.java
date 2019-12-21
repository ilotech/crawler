package org.ilot.crawler.algorithms.concurrent;

import org.ilot.crawler.algorithms.GraphAlgorithm;
import org.springframework.util.Assert;

import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BFS<E> implements GraphAlgorithm<E> {
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

    public BFS(ExecutorService executorService,
               Function<E, Set<E>> getNeighbours,
               long timeout) {
        this.executorService = executorService;
        this.workDequeue = new ConcurrentLinkedDeque<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.addElement = workDequeue::addLast;
        this.getNeighbours = getNeighbours;
        this.timeout = timeout;
    }

    public BFS(ExecutorService executorService,
               Function<E, Set<E>> getNeighbours,
               long timeout,
               Predicate<Node<E>> searchPredicate) {
        this(executorService, getNeighbours, timeout);
        this.searchPredicate = searchPredicate;
    }


    @Override
    public void traverse(E rootElement) {
        workDequeue.add(Node.of(rootElement, 0));
        Phaser phaser = new Phaser(1);
        while (awaitNotEmpty()) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new TraversalTask(node, phaser));
            if (node.getLevel() != phaser.getPhase()) phaser.arriveAndAwaitAdvance();
        }
        // TODO implement shutdown policy
        executorService.shutdownNow();
    }

    // TODO revisit
    @Override
    public Optional<E> search(E rootElement) {
        Assert.state(searchPredicate != null, "Search predicate must be defined when using search function.");
        workDequeue.add(Node.of(rootElement, 0));
        Phaser phaser = new Phaser(1);
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            executorService.execute(new SearchTask(node, phaser));
            if (node.getLevel() != phaser.getPhase()) phaser.arriveAndAwaitAdvance();
        }
        executorService.shutdownNow();
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

    private class TraversalTask implements Runnable {
        private final Node<E> node;
        private final Phaser phaser;

        private TraversalTask(Node<E> node, Phaser phaser) {
            this.node = node;
            this.phaser = phaser;
        }

        @Override
        public void run() {
            if (visited.contains(node)) return;
            if (Thread.currentThread().isInterrupted()) return;
            // TODO how to timeout if #getNeighbours is taking too long? future.get? #getNeighbours should support timeouts
            phaser.register();
            try {
                getNeighbours.apply(node.getElement())
                        .stream()
                        .filter(e -> !visited.contains(Node.of(e)))
                        .map(e -> Node.of(e, node.getLevel() + 1))
                        .forEach(addElement);

                visited.add(node);
                signalWaitingThread();
                System.out.println("Phaser arriving");
            } catch (Exception e) {
                // TODO rethrow
            }
            finally {
                phaser.arriveAndDeregister();
            }
        }
    }

    private class SearchTask implements Runnable {
        private final Node<E> node;
        private final Phaser phaser;

        private SearchTask(Node<E> node, Phaser phaser) {
            this.node = node;
            this.phaser = phaser;
        }

        @Override
        public void run() {
            if (searchPredicate.test(node)) {
                resultFound = true;
                searchResult = node.getElement();
                return;
            }
            if (visited.contains(node)) return;
            if (Thread.currentThread().isInterrupted()) return;
            phaser.register();
            try {
                getNeighbours.apply(node.getElement())
                        .stream()
                        .filter(e -> !visited.contains(Node.of(e)))
                        .map(e -> Node.of(e, node.getLevel() + 1))
                        .collect(Collectors.toSet())
                        .forEach(addElement);

                visited.add(node);
                signalWaitingThread();
            } catch (Exception e) {
                // TODO rethrow
            } finally {
                phaser.arriveAndDeregister();
            }
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