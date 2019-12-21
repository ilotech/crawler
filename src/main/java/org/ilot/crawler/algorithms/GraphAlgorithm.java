package org.ilot.crawler.algorithms;

import java.util.Optional;

public interface GraphAlgorithm<E> {
    void traverse(E rootElement);
    Optional<E> search(E rootElement);
    // TODO implement
//    void stop();
//    void continueTraversingFrom(List<E> nodes);
//    void continueSearchingFrom(List<E> nodes);
}