package org.ilot.crawler.algorithms.sequential;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class DFS<E> extends AbstractGraphSearch<E> {
    private DFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction) {
        super(workDequeue, visited, workDequeue::addFirst, getNeighboursFunction);
    }

    private DFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
        super(workDequeue, visited, workDequeue::addFirst, getNeighboursFunction, searchPredicate);
    }

    public static <E> DFS createTraversing(Function<E, Set<E>> getNeighboursFunction) {
        return new DFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction);
    }

    public static <E> DFS createSearching(Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
        return new DFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction, searchPredicate);
    }
}