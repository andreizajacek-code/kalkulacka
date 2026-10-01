package sk.zbierkapohladnic.zberatel;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchEngine {
    private static final Pattern RESULT_LINK = Pattern.compile("<a[^>]+class=\\\"result__a\\\"[^>]+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern YEAR = Pattern.compile("\\b(18\\d{2}|19\\d{2}|20[0-2]\\d)\\b");

    private SearchEngine() {}

    public static List<SearchResult> search(Context c, Progress progress) throws Exception {
        String topicsRaw = AppPrefs.topics(c);
        List<String> topics = splitCsv(topicsRaw);
        if (topics.isEmpty()) topics.add("Tatry");

        LinkedHashSet<String> domains = new LinkedHashSet<>(AppPrefs.sources(c));
        for (String d : splitCsv(AppPrefs.customSources(c))) {
            d = normalizeDomain(d);
            if (!d.isEmpty()) domains.add(d);
        }

        List<String> queries = new ArrayList<>();
        for (String topic : topics) {
            String contentTerms = contentTerms(c);
            queries.add("\"" + topic + "\" " + contentTerms);
            for (String domain : domains) {
                queries.add("site:" + domain + " \"" + topic + "\" " + contentTerms);
            }
        }

        // Prevent a runaway number of requests on large watchlists.
        if (queries.size() > 28) queries = queries.subList(0, 28);
        Map<String, SearchResult> unique = new LinkedHashMap<>();
        int done = 0;
        for (String query : queries) {
            done++;
            if (progress != null) progress.onProgress(done, queries.size(), query);
            List<SearchResult> partial = queryDuckDuckGo(c, query, topics);
            for (SearchResult r : partial) {
                if (!AppPrefs.ignoredUrls(c).contains(r.url)) {
                    SearchResult old = unique.get(r.url);
                    if (old == null || r.score > old.score) unique.put(r.url, r);
                }
            }
        }

        List<SearchResult> out = new ArrayList<>(unique.values());
        Collections.sort(out, Comparator.comparingInt((SearchResult r) -> r.score).reversed());
        if (out.size() > 120) return new ArrayList<>(out.subList(0, 120));
        return out;
    }

    private static List<SearchResult> queryDuckDuckGo(Context c, String q, List<String> topics) throws Exception {
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
        URL url = new URL("https://html.duckduckgo.com/html/?q=" + encoded);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        con.setRequestProperty("Accept-Language", "sk-SK,sk;q=0.9,en;q=0.6");
        int code = con.getResponseCode();
        if (code < 200 || code >= 400) throw new Exception("Vyhľadávač vrátil HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder html = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && html.length() < 2_500_000) html.append(line).append('\n');
        br.close();
        con.disconnect();

        List<SearchResult> out = new ArrayList<>();
        Matcher m = RESULT_LINK.matcher(html.toString());
        while (m.find() && out.size() < 25) {
            String rawUrl = htmlDecode(m.group(1));
            String title = clean(m.group(2));
            String realUrl = unwrapDuck(rawUrl);
            if (!realUrl.startsWith("http")) continue;
            int nextStart = m.end();
            int nextEnd = Math.min(html.length(), nextStart + 1400);
            String near = clean(html.substring(nextStart, nextEnd));
            String snippet = near.length() > 340 ? near.substring(0, 340) : near;
            String source = host(realUrl);
            int score = score(c, title + " " + snippet + " " + source, topics);
            String type = detectType(title + " " + snippet);
            String year = detectYear(title + " " + snippet);
            out.add(new SearchResult(title, realUrl, snippet, source, type, year, score));
        }
        return out;
    }

    private static int score(Context c, String text, List<String> topics) {
        String t = fold(text);
        int score = 12;
        for (String topic : topics) {
            String f = fold(topic);
            if (f.length() > 2 && t.contains(f)) score += 35;
            for (String token : f.split("\\s+")) if (token.length() > 3 && t.contains(token)) score += 6;
        }
        if (containsAny(t, "pohladnic", "postcard", "ansichtskarte", "carte postale", "doplnicova karta")) score += 30;
        if (AppPrefs.includePhotos(c) && containsAny(t, "fotograf", "photo", "foto", "negativ", "diapozitiv")) score += 20;
        if (AppPrefs.includeDocs(c) && containsAny(t, "dokument", "prospekt", "mapa", "letak", "brozura", "archiv")) score += 16;
        if (containsAny(t, "aukro", "ebay", "delcampe", "bazos", "antikvariat", "auction", "aukcia", "predaj")) score += 14;

        if (AppPrefs.excludeArticles(c) && containsAny(t, "clanok", "blog", "spravy", "wikipedia", "magazin")) score -= 34;
        if (AppPrefs.excludeAccommodation(c) && containsAny(t, "ubytovanie", "rezervacia", "booking", "hotel booking", "pobyt")) score -= 35;
        if (AppPrefs.excludeSocial(c) && containsAny(t, "facebook", "instagram", "tiktok", "diskusia", "forum")) score -= 30;
        if (!AppPrefs.includePostcards(c) && containsAny(t, "pohladnic", "postcard", "ansichtskarte")) score -= 35;
        if (!AppPrefs.includePhotos(c) && containsAny(t, "fotograf", "photo", "foto")) score -= 30;
        if (!AppPrefs.includeDocs(c) && containsAny(t, "dokument", "prospekt", "mapa", "brozura")) score -= 28;
        return Math.max(5, Math.min(99, score));
    }

    private static String contentTerms(Context c) {
        List<String> x = new ArrayList<>();
        if (AppPrefs.includePostcards(c)) x.add("pohľadnica OR postcard OR Ansichtskarte");
        if (AppPrefs.includePhotos(c)) x.add("historická fotografia OR old photo");
        if (AppPrefs.includeDocs(c)) x.add("historický dokument OR prospekt OR mapa");
        return "(" + String.join(" OR ", x) + ")";
    }

    private static String detectType(String text) {
        String t = fold(text);
        if (containsAny(t, "pohladnic", "postcard", "ansichtskarte", "carte postale")) return "Pohľadnica";
        if (containsAny(t, "fotograf", "photo", "foto", "negativ", "diapozitiv")) return "Fotografia";
        if (containsAny(t, "mapa", "prospekt", "dokument", "brozura", "letak")) return "Dokument";
        return "Možný nález";
    }

    private static String detectYear(String text) {
        Matcher m = YEAR.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String unwrapDuck(String u) {
        try {
            if (u.startsWith("//")) u = "https:" + u;
            Uri uri = Uri.parse(u);
            String uddg = uri.getQueryParameter("uddg");
            if (uddg != null && !uddg.isEmpty()) return URLDecoder.decode(uddg, StandardCharsets.UTF_8.toString());
        } catch (Exception ignored) {}
        return u;
    }

    private static String host(String u) {
        try {
            String h = Uri.parse(u).getHost();
            if (h == null) return "web";
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) { return "web"; }
    }

    private static String clean(String s) {
        return htmlDecode(s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
    }

    private static String htmlDecode(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private static String fold(String s) {
        if (s == null) return "";
        String x = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD);
        return x.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) if (text.contains(fold(n))) return true;
        return false;
    }

    private static List<String> splitCsv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String x : raw.split("[,;\\n]+")) {
            x = x.trim();
            if (!x.isEmpty()) out.add(x);
        }
        return out;
    }

    private static String normalizeDomain(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^https?://", "");
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.startsWith("www.")) s = s.substring(4);
        return s.replaceAll("[^a-z0-9._-]", "");
    }

    public interface Progress { void onProgress(int done, int total, String query); }
}
