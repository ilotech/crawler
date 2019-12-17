package org.ilot.crawler.impl;

import org.ilot.crawler.CrawlerImpl;
import org.ilot.crawler.algorithms.sequential.BFS;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class WebCrawlerImplImpl extends CrawlerImpl<String> {
    private static final Function<String, Set<String>> getNeighboursFunction;

    static {
        getNeighboursFunction = URL -> {
            //2. Fetch the HTML code
            Document document;
            try {
                document = Jsoup.connect(URL).get();

                //3. Parse the HTML to extract links to other URLs
                Elements linksOnPage = document.select("a[href]");

                return linksOnPage.stream()
                        .map(page -> page.attr("abs:href"))
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                e.printStackTrace();
            }
            //noinspection unchecked
            return Collections.EMPTY_SET;
        };
    }

    public WebCrawlerImplImpl() {
        super(BFS.createTraversing(getNeighboursFunction));
    }

    public static void main(String[] args) {
        new WebCrawlerImplImpl().crawl("http://www.mkyong.com/");
    }
}