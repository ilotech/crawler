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
    private final Algorithms.UnorderedSearch<String> breadthFirstSearch;

    public WebCrawlerImpl() {
        Function<Node<String>, Set<Node<String>>> getNeighboursFunction = URL -> {
            //2. Fetch the HTML code
            Document document;
            try {
                document = Jsoup.connect(URL.getElement()).get();

                //3. Parse the HTML to extract links to other URLs
                Elements linksOnPage = document.select("a[href]");

                Set<String> elements = linksOnPage.stream()
                        .map(page -> page.attr("abs:href"))
                        .collect(Collectors.toSet());

                return elements.stream().map(el -> new Node<>(el, URL.getLevel() + 1)).collect(Collectors.toSet());

            } catch (IOException e) {
                e.printStackTrace();
            }
            //noinspection unchecked
            return Collections.EMPTY_SET;
        };

        //noinspection unchecked
        this.breadthFirstSearch = Algorithms.UnorderedSearch.createConcurrentUnorderedSearch(getNeighboursFunction);
    }

    public void crawl(String URL) {
            breadthFirstSearch.traverse(new Node<>(URL, 0));
    }

    public static void main(String[] args) {
        new WebCrawlerImpl().crawl("http://www.mkyong.com/");
    }
}