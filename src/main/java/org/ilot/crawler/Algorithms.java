package org.ilot.crawler;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"WeakerAccess", "unused"})
public class Algorithms {
    public static class BFS<E> extends GraphSearch<E> {
        private BFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction) {
            super(workDequeue, visited, Deque::poll, getNeighboursFunction);
        }

        private BFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
            super(workDequeue, visited, Deque::poll, getNeighboursFunction, searchPredicate);
        }

        public static <E> BFS createSingleThreadingBFS(Function<E, Set<E>> getNeighboursFunction) {
            return new BFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction);
        }
    }

    public static class DFS<E> extends GraphSearch<E> {
        private DFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction);
        }

        private DFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction, searchPredicate);
        }

        public static <E> DFS createSingleThreadingDFS(Function<E, Set<E>> getNeighboursFunction) {
            return new DFS<>(new ArrayDeque<>(), new HashSet<>(), getNeighboursFunction);
        }
    }

    private static abstract class GraphSearch<E> {
        private final Deque<E> workDequeue;
        private final Set<E> visited;
        private final Function<Deque<E>, E> getElementFunction;
        private final Function<E, Set<E>> getNeighboursFunction;
        private Predicate<E> searchPredicate;

        private GraphSearch(Deque<E> workDequeue,
                           Set<E> visited,
                           Function<Deque<E>, E> getElementFunction,
                           Function<E, Set<E>> getNeighboursFunction) {
            this.workDequeue = workDequeue;
            this.visited = visited;
            this.getElementFunction = getElementFunction;
            this.getNeighboursFunction = getNeighboursFunction;
        }

        private GraphSearch(Deque<E> workDequeue,
                           Set<E> visited,
                           Function<Deque<E>, E> getElementFunction,
                           Function<E, Set<E>> getNeighboursFunction,
                           Predicate<E> searchPredicate) {
            this(workDequeue, visited, getElementFunction, getNeighboursFunction);
            this.searchPredicate = searchPredicate;
        }

        public void traverse(E rootElement) {
            workDequeue.add(rootElement);
            while (!workDequeue.isEmpty()) {
                E element = getElementFunction.apply(workDequeue);
                if (visited.contains(element)) {
                    continue;
                }
                visited.add(element);
                System.out.println("Current element -> " + element);

                Set<E> neighbours = getNeighboursFunction.apply(element)
                        .stream()
                        .filter(e -> !visited.contains(e))
                        .collect(Collectors.toSet());

                workDequeue.addAll(neighbours);
            }
        }

        // TODO revisit method
        public E search(E rootElement) {
            if (searchPredicate == null) {
                // TODO revisit message
                throw new IllegalStateException("Search predicate must be defined whn using search function.");
            }
            workDequeue.add(rootElement);
            while (!workDequeue.isEmpty()) {
                E element = getElementFunction.apply(workDequeue);
                if (searchPredicate.test(element)) {
                    return element;
                }
                if (visited.contains(element)) {
                    continue;
                }
                visited.add(element);
                System.out.println("Current element -> " + element);

                Set<E> neighbours = getNeighboursFunction.apply(element)
                        .stream()
                        .filter(e -> !visited.contains(e))
                        .collect(Collectors.toSet());

                workDequeue.addAll(neighbours);
            }
            // TODO revisit
            throw new NoSuchElementException("Search element not found.");
        }
    }
}