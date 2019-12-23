package org.ilot.crawler.algorithms.concurrent;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BFS<E> extends AbstractGraphAlgorithm<E> {

    public BFS(ExecutorService executorService,
               BiFunction<E, Long, Set<E>> getNeighbours,
               Function<E, E> transformElement,
               Predicate<Node<E>> searchPredicate,
               long isEmptyTimeout,
               long getNeighboursTimeout) {
        super(executorService, getNeighbours, transformElement, searchPredicate, isEmptyTimeout, getNeighboursTimeout);
    }

    protected void internalSearch() {
        Phaser phaser = new Phaser(1);
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDeque.poll();
            if (node == null) continue;
            phaser.register();
            executorService.execute(new Worker<>(this, node, phaser));
            if (node.getLevel() != phaser.getPhase()) phaser.arriveAndAwaitAdvance();
        }
        // TODO implement shutdown policy
        executorService.shutdownNow();
    }

    private static class Worker<E> extends AbstractWorker<E> {
        private final Phaser phaser;

        private Worker(BFS<E> bfs, Node<E> node, Phaser phaser) {
            super(bfs, node);
            this.phaser = phaser;
        }

        @Override
        public void run() {
            try {
                if (isResult(node)) return;
                if (ga.visited.contains(ga.transformElement.apply(node.getElement()))) return;
                if (Thread.currentThread().isInterrupted()) return;
                ga.getNeighbours.apply(node.getElement(), ga.getNeighboursTimeout)
                        .stream()
                        .map(ga.transformElement)
                        .filter(e -> !ga.visited.contains(e))
                        .map(e -> Node.of(e, node.getLevel() + 1))
                        .collect(Collectors.toSet())
                        .forEach(ga.addNode);

                ga.visited.add(node.getElement());
                Util.signalNotEmpty(ga.lock, ga.isEmpty);
            } catch (Exception e) {
                // TODO rethrow
            } finally {
                phaser.arriveAndDeregister();
            }
        }
    }
}