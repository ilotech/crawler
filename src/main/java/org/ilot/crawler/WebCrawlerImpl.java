package org.ilot.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class WebCrawlerImpl implements Crawler<String> {
    private final Algorithms.BFS<String> breadthFirstSearch;

    public WebCrawlerImpl() {
        Function<String, Set<String>> getNeighboursFunction = URL -> {
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

        //noinspection unchecked
        this.breadthFirstSearch = Algorithms.BFS.createSingleThreadingBFS(getNeighboursFunction);
    }

    public void crawl(String URL) {
            breadthFirstSearch.traverse(URL);
    }

    public static void main(String[] args) {
        new WebCrawlerImpl().crawl("http://www.mkyong.com/");
    }
}