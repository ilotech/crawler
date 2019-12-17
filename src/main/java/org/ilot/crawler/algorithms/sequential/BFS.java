package org.ilot.crawler.algorithms.sequential;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class BFS<E> extends AbstractGraphSearch<E> {
    private BFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction) {
        super(workDequeue, visited, workDequeue::addLast, getNeighboursFunction);
    }

    private BFS(Deque<E> workDequeue, Set<E> visited, Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
        super(workDequeue, visited, workDequeue::addLast, getNeighboursFunction, searchPredicate);
    }

    public static <E> BFS createTraversing(Function<E, Set<E>> getNeighboursFunction) {
        return new BFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction);
    }

    public static <E> BFS createSearching(Function<E, Set<E>> getNeighboursFunction, Predicate<E> searchPredicate) {
        return new BFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction, searchPredicate);
    }
}
