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

    public SearchResult(String title, String url, String snippet, String source, String type, String year, int score) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.source = source;
        this.type = type;
        this.year = year;
        this.score = score;
    }
}
