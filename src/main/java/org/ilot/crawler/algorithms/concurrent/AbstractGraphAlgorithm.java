package org.ilot.crawler.algorithms.concurrent;

import org.ilot.crawler.algorithms.GraphAlgorithm;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public abstract class AbstractGraphAlgorithm<E> implements GraphAlgorithm<E> {
    protected final ExecutorService executorService;
    protected final Deque<Node<E>> workDeque;
    protected final Set<E> visited;

    protected final Consumer<Node<E>> addNode;
    protected final BiFunction<E, Long, Set<E>> getNeighbours;
    protected final Function<E, E> transformElement;
    protected final Predicate<Node<E>> searchPredicate;

    protected final long isEmptyTimeout;
    protected final long getNeighboursTimeout;

    protected final Lock lock = new ReentrantLock(true);
    protected final Condition isEmpty = lock.newCondition();

    protected volatile E searchResult;
    protected volatile boolean resultFound;

    AbstractGraphAlgorithm(ExecutorService executorService,
                           BiFunction<E, Long, Set<E>> getNeighbours,
                           Function<E, E> transformElement,
                           Predicate<Node<E>> searchPredicate,
                           long isEmptyTimeout,
                           long getNeighboursTimeout) {
        this.executorService = executorService;
        this.workDeque = new ConcurrentLinkedDeque<>();
        this.visited = ConcurrentHashMap.newKeySet();
        this.addNode = workDeque::addLast;
        this.getNeighbours = getNeighbours;
        this.transformElement = transformElement;
        this.isEmptyTimeout = isEmptyTimeout;
        this.getNeighboursTimeout = getNeighboursTimeout;
        this.searchPredicate = searchPredicate;
    }

    protected abstract void internalSearch();

    @Override
    public void traverse(E rootElement) {
        workDeque.add(Node.of(rootElement, 0));
        internalSearch();
    }

    @Override
    public Optional<E> search(E rootElement) {
        workDeque.add(Node.of(rootElement));
        internalSearch();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
    }

    @Override
    public void continueTraversingFrom(List<E> nodes) {
        workDeque.addAll(nodes.parallelStream().map(Node::of).collect(Collectors.toList()));
        internalSearch();
    }

    @Override
    public Optional<E> continueSearchingFrom(List<E> nodes) {
        workDeque.addAll(nodes.parallelStream().map(Node::of).collect(Collectors.toList()));
        internalSearch();
        return resultFound ? Optional.of(searchResult) : Optional.empty();
    }

    protected boolean awaitNotEmpty() {
        return Util.awaitNotEmpty(workDeque, lock, isEmpty, isEmptyTimeout);
    }

    protected abstract static class AbstractWorker<E> implements Runnable {
        protected final AbstractGraphAlgorithm<E> ga;
        protected final Node<E> node;

        protected AbstractWorker(AbstractGraphAlgorithm<E> ga, Node<E> node) {
            this.ga = ga;
            this.node = node;
        }

        protected boolean isResult(Node<E> node) {
            if (!ga.searchPredicate.test(node)) {
                return false;
            }
            ga.resultFound = true;
            ga.searchResult = node.getElement();
            return true;
        }
    }
}