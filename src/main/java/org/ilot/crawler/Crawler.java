package org.ilot.crawler;

import java.util.Optional;

public interface Crawler<E> {
    void crawl(E rootElement);
    Optional<E> crawlAndFind(E rootElement);
}