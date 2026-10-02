package sk.zbierkapohladnic.zberatel;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class DataStore {
    private DataStore() {}

    public static void saveResults(Context c, List<SearchResult> results) {
        JSONArray a = new JSONArray();
        try {
            for (SearchResult r : results) {
                JSONObject o = new JSONObject();
                o.put("title", r.title); o.put("url", r.url); o.put("snippet", r.snippet);
                o.put("source", r.source); o.put("type", r.type); o.put("year", r.year); o.put("score", r.score);
                o.put("imageUrl", r.imageUrl); o.put("price", r.price);
                a.put(o);
            }
        } catch (Exception ignored) {}
        AppPrefs.p(c).edit().putString("last_results", a.toString()).apply();
    }

    public static List<SearchResult> loadResults(Context c) {
        List<SearchResult> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(AppPrefs.p(c).getString("last_results", "[]"));
            for (int i=0; i<a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new SearchResult(o.optString("title"), o.optString("url"), o.optString("snippet"),
                        o.optString("source"), o.optString("type"), o.optString("year"), o.optInt("score", 0),
                        o.optString("imageUrl"), o.optString("price")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void addHistory(Context c, int count, String note) {
        JSONArray old;
        try { old = new JSONArray(AppPrefs.p(c).getString("history", "[]")); }
        catch (Exception e) { old = new JSONArray(); }
        JSONArray next = new JSONArray();
        try {
            JSONObject n = new JSONObject();
            n.put("time", System.currentTimeMillis()); n.put("count", count); n.put("note", note);
            next.put(n);
            for (int i=0; i<Math.min(old.length(), 24); i++) next.put(old.get(i));
        } catch (Exception ignored) {}
        AppPrefs.p(c).edit().putString("history", next.toString()).apply();
    }

    public static JSONArray history(Context c) {
        try { return new JSONArray(AppPrefs.p(c).getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
