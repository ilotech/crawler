package org.ilot.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashSet;


public class CrawlerImpl implements Crawler {
    private HashSet<String> links;

    public CrawlerImpl() {
        links = new HashSet<>();
    }

    public void getPageLinks(String URL) {
        if (links.contains(URL)) {
            return;
        }
        try {
            crawl(URL);
        } catch (IOException e) {
            System.err.println("For '" + URL + "': " + e.getMessage());
        }
    }

    private void crawl(String URL) throws IOException {
        //1. Check if you have already crawled the URLs
        //1. (i) If not add it to the index
        if (links.add(URL)) {
            System.out.println(URL);
        }
        //2. Fetch the HTML code
        Document document = Jsoup.connect(URL).get();
        //3. Parse the HTML to extract links to other URLs
        Elements linksOnPage = document.select("a[href]");

        //5. For each extracted URL... go back to Step 4.
        linksOnPage.stream()
                .map(page -> page.attr("abs:href"))
                .forEach(this::getPageLinks);
    }

    public static void main(String[] args) {
        //1. Pick a URL from the frontier
        new CrawlerImpl().getPageLinks("http://www.mkyong.com/");
    }
}