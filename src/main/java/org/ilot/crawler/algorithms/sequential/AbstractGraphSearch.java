package org.ilot.crawler.algorithms.sequential;

import org.ilot.crawler.algorithms.GraphAlgorithm;
import org.springframework.util.Assert;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractGraphSearch<E> implements GraphAlgorithm<E> {
    private final Deque<E> workDequeue;
    private final Set<E> visited;
    private final Consumer<E> addElement;
    private final Function<E, Set<E>> getNeighbours;
    private Predicate<E> searchPredicate;

    AbstractGraphSearch(Deque<E> workDequeue,
                        Set<E> visited,
                        Consumer<E> addElement,
                        Function<E, Set<E>> getNeighbours) {
        this.workDequeue = workDequeue;
        this.visited = visited;
        this.addElement = addElement;
        this.getNeighbours = getNeighbours;
    }

    AbstractGraphSearch(Deque<E> workDequeue,
                        Set<E> visited,
                        Consumer<E> addElement,
                        Function<E, Set<E>> getNeighbours,
                        Predicate<E> searchPredicate) {
        this(workDequeue, visited, addElement, getNeighbours);
        this.searchPredicate = searchPredicate;
    }

    @Override
    public void traverse(E rootElement) {
        Assert.notNull(rootElement, "Root element must not be null!");
        workDequeue.add(rootElement);

        while (!workDequeue.isEmpty()) {
            E element = workDequeue.poll();
            if (element == null || visited.contains(element)) continue;
            // TODO remove
            System.out.println("Current element -> " + element);

            getNeighbours.apply(element)
                    .stream()
                    .filter(e -> !visited.contains(e))
                    .collect(Collectors.toSet())
                    .forEach(addElement);

            visited.add(element);
        }
    }

    // TODO revisit method
    @Override
    public Optional<E> search(E rootElement) {
        Assert.notNull(rootElement, "Root element must not be null!");
        Assert.state(searchPredicate != null, "Search predicate must be defined when using search function.");
        workDequeue.add(rootElement);

        while (!workDequeue.isEmpty()) {
            E element = workDequeue.poll();
            if (element == null) continue;
            if (searchPredicate.test(element)) return Optional.of(element);
            if (visited.contains(element)) continue;

            getNeighbours.apply(element)
                    .stream()
                    .filter(e -> !visited.contains(e))
                    .collect(Collectors.toSet())
                    .forEach(addElement);

            visited.add(element);
        }
        return Optional.empty();
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
}