package org.ilot.crawler;

public interface Crawler<E> {
    void crawl(E rootElement);
}
