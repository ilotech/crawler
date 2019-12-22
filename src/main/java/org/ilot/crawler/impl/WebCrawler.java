package org.ilot.crawler.impl;

import org.ilot.crawler.AbstractCrawler;
import org.ilot.crawler.algorithms.concurrent.BFS;
import org.ilot.crawler.algorithms.concurrent.ExecutorServiceFactory;
import org.ilot.crawler.algorithms.concurrent.ExecutorServiceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


public class WebCrawler extends AbstractCrawler<String> {
    private static final BiFunction<String, Long, Set<String>> getNeighboursFunction;

    static {
        getNeighboursFunction = (url, timeout) -> {
            //2. Fetch the HTML code
            Document document;
            try {
                document = Jsoup.connect(url).timeout(timeout.intValue()).get();

                //3. Parse the HTML to extract links to other URLs
                Elements linksOnPage = document.select("a[href]");

                return linksOnPage.stream()
                        .map(page -> page.attr("abs:href"))
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                // TODO handle timeouts
            }
            //noinspection unchecked
            return Collections.EMPTY_SET;
        };
    }

    public WebCrawler() {
        super(new BFS<>(ExecutorServiceFactory.createCustomExecutorService(
                ExecutorServiceType.FORK_JOIN_POOL, 0.98d), getNeighboursFunction, 5000L, 3000L)
        );
    }

    public static void main(String[] args) {
        new WebCrawler().crawl("http://www.mkyong.com/");
    }
}