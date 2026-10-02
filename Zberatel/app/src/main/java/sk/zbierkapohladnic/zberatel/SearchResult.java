package sk.zbierkapohladnic.zberatel;

import java.io.Serializable;

public class SearchResult implements Serializable {
    public String title;
    public String url;
    public String snippet;
    public String source;
    public String type;
    public String year;
    public int score;
    public String imageUrl;
    public String price;

    public SearchResult(String title, String url, String snippet, String source, String type, String year, int score) {
        this(title, url, snippet, source, type, year, score, "", "");
    }

    public SearchResult(String title, String url, String snippet, String source, String type, String year, int score, String imageUrl, String price) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.source = source;
        this.type = type;
        this.year = year;
        this.score = score;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.price = price == null ? "" : price;
    }
}
