package org.ilot.crawler.algorithms.concurrent;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UnorderedSearch<E> extends AbstractGraphAlgorithm<E> {

    public UnorderedSearch(ExecutorService executorService,
                           BiFunction<E, Long, Set<E>> getNeighbours,
                           Function<E,E> transformElement,
                           Predicate<Node<E>> searchPredicate,
                           long isEmptyTimeout,
                           long getNeighboursTimeout) {
        super(executorService, getNeighbours, transformElement, searchPredicate, isEmptyTimeout, getNeighboursTimeout);
    }

    protected void internalSearch() {
        while (awaitNotEmpty() && !resultFound) {
            Node<E> node = workDeque.poll();
            if (node == null) continue;
            executorService.execute(new Worker<>(this, node));
            sleep(); // for testing until saturation policy is implemented
        }
        executorService.shutdownNow();
    }

    // TODO implement proper saturation policy, hack for now
    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class Worker<E> extends AbstractWorker<E> {
        private Worker(UnorderedSearch<E> us, Node<E> node) {
            super(us, node);
        }

        @Override
        public void run() {
            if (ga.visited.contains(node.getElement())) return;
            if (Thread.currentThread().isInterrupted()) return;
            Set<Node<E>> nodes = ga.getNeighbours.apply(node.getElement(), ga.getNeighboursTimeout)
                    .stream()
                    .map(ga.transformElement)
                    .filter(e -> !ga.visited.contains(e))
                    .map(e -> Node.of(e, node.getLevel() + 1))
                    .collect(Collectors.toSet());

            if (nodes.stream().anyMatch(this::isResult)) return;
            nodes.forEach(ga.addNode);
            ga.visited.add(node.getElement());
            Util.signalNotEmpty(ga.lock, ga.isEmpty);
        }
    }
}