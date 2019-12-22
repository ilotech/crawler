package org.ilot.crawler.algorithms.concurrent;

import org.ilot.crawler.algorithms.GraphAlgorithm;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BFS<E> implements GraphAlgorithm<E> {
    private final ExecutorService executorService;
    private final Deque<Node<E>> workDequeue;
    private final Set<Node<E>> visited;

    private final Consumer<Node<E>> addElement;
    private final BiFunction<E, Long, Set<E>> getNeighbours;

    private final long isEmptyTimeout;
    private final long getNeighboursTimeout;


    private final Lock lock = new ReentrantLock(true);
    private final Condition isEmpty = lock.newCondition();

    private volatile E searchResult;
    private volatile boolean resultFound;

    private Predicate<Node<E>> searchPredicate;

    public BFS(ExecutorService executorService,
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
        this.searchPredicate = e -> false;
    }

    public BFS(ExecutorService executorService,
               BiFunction<E, Long, Set<E>> getNeighbours,
               long isEmptyTimeout,
               long getNeighboursTimeout,
               Predicate<Node<E>> searchPredicate) {
        this(executorService, getNeighbours, isEmptyTimeout, getNeighboursTimeout);
        this.searchPredicate = searchPredicate;
    }

    @Override
    public void traverse(E rootElement) {
        workDequeue.add(Node.of(rootElement));
        internalSearch();
    }

    // TODO revisit
    @Override
    public Optional<E> search(E rootElement) {
        workDequeue.add(Node.of(rootElement));
        internalSearch();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
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

    private void internalSearch() {
        Phaser phaser = new Phaser(1);
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDequeue.poll();
            if (node == null) continue;
            phaser.register();
            executorService.execute(new Worker(node, phaser));
            if (node.getLevel() != phaser.getPhase()) phaser.arriveAndAwaitAdvance();
        }
        // TODO implement shutdown policy
        executorService.shutdownNow();
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

    private class Worker implements Runnable {
        private final Node<E> node;
        private final Phaser phaser;

        private Worker(Node<E> node, Phaser phaser) {
            this.node = node;
            this.phaser = phaser;
        }

        @Override
        public void run() {
            try {
                if (isResult(node)) return;
                if (visited.contains(node)) return;
                if (Thread.currentThread().isInterrupted()) return;
                getNeighbours.apply(node.getElement(), getNeighboursTimeout)
                        .stream()
                        .filter(e -> !visited.contains(Node.of(e)))
                        .map(e -> Node.of(e, node.getLevel() + 1))
                        .collect(Collectors.toSet())
                        .forEach(addElement);

                visited.add(node);
                signalNotEmpty();
            } catch (Exception e) {
                // TODO rethrow
            } finally {
                phaser.arriveAndDeregister();
            }
        }

        private void signalNotEmpty() {
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

        private boolean isResult(Node<E> node) {
            if (!searchPredicate.test(node)) {
                return false;
            }
            resultFound = true;
            searchResult = node.getElement();
            return true;
        }
    }
}