package org.ilot.crawler;

import org.ilot.crawler.algorithms.GraphAlgorithm;

import java.util.Optional;

public abstract class CrawlerImpl<E> implements Crawler<E> {
    private final GraphAlgorithm<E> graphAlgorithm;

    public CrawlerImpl(GraphAlgorithm<E> graphAlgorithm) {
        this.graphAlgorithm = graphAlgorithm;
    }

    @Override
    public void crawl(E rootElement) {
        graphAlgorithm.traverse(rootElement);
    }

    @Override
    public Optional<E> crawlAndFind(E rootElement) {
        return graphAlgorithm.search(rootElement);
    }
}