package org.ilot.crawler.algorithms;

import java.util.List;
import java.util.Optional;

public interface GraphAlgorithm<E> {
    void traverse(E rootElement);
    Optional<E> search(E rootElement);
    // TODO implement
//    void stop();
//    void abort();
    void continueTraversingFrom(List<E> nodes);
    Optional<E> continueSearchingFrom(List<E> nodes);
}