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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class SearchEngine {
    private static final Pattern DDG_RESULT = Pattern.compile("<a[^>]+class=[\\\"']result__a[\\\"'][^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DDG_LITE_RESULT = Pattern.compile("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*class=[\\\"']result-link[\\\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_LINK = Pattern.compile("<link>(.*?)</link>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RSS_DESC = Pattern.compile("<description>(.*?)</description>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern YEAR = Pattern.compile("\\b(18\\d{2}|19\\d{2}|20[0-2]\\d)\\b");
    private static final Pattern JSON_PRICE = Pattern.compile("\\\"price\\\"\\s*:\\s*\\\"?([0-9]+(?:[.,][0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_CURRENCY = Pattern.compile("\\\"priceCurrency\\\"\\s*:\\s*\\\"([A-Z]{3})\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUKRO_OFFER_LINK = Pattern.compile("href=[\\\"'](/[^\\\"'?#]+-\\d{9,})[\\\"']", Pattern.CASE_INSENSITIVE);

    private SearchEngine() {}

    public static List<SearchResult> search(Context c, Progress progress) throws Exception {
        return search(c, progress, false);
    }

    public static List<SearchResult> search(Context c, Progress progress, boolean deepManualSearch) throws Exception {
        List<String> topics = splitCsv(AppPrefs.topics(c));
        if (topics.isEmpty()) topics.add("Tatry");

        LinkedHashSet<String> domains = new LinkedHashSet<>(AppPrefs.sources(c));
        for (String d : splitCsv(AppPrefs.customSources(c))) {
            d = normalizeDomain(d);
            if (!d.isEmpty()) domains.add(d);
        }

        List<String> queries = new ArrayList<>();
        for (String topic : topics) {
            String terms = contentTerms(c);
            queries.add("\"" + topic + "\" " + terms);
            queries.add(topic + " " + terms);

            List<String> domainList = new ArrayList<>(domains);
            for (int i = 0; i < domainList.size(); i += 4) {
                int end = Math.min(domainList.size(), i + 4);
                StringBuilder sites = new StringBuilder("(");
                for (int j = i; j < end; j++) {
                    if (j > i) sites.append(" OR ");
                    sites.append("site:").append(domainList.get(j));
                }
                sites.append(")");
                queries.add(sites + " \"" + topic + "\" " + terms);
            }
        }

        if (queries.size() > 24) queries = new ArrayList<>(queries.subList(0, 24));

        Map<String, SearchResult> unique = new LinkedHashMap<>();
        Set<String> ignored = AppPrefs.ignoredUrls(c);

        // Aukro is queried directly. Search engines are only a supplementary source.
        if (AppPrefs.sources(c).contains("aukro.cz") || AppPrefs.sources(c).contains("aukro.sk")) {
            int aukroPages = deepManualSearch ? 4 : 1;
            int topicNo = 0;
            for (String topic : topics) {
                topicNo++;
                if (progress != null) {
                    progress.onProgress(topicNo, topics.size(), "Aukro priamo • " + topic);
                }
                try {
                    List<SearchResult> direct = queryAukroDirect(c, topic, topics, aukroPages);
                    for (SearchResult r : direct) {
                        if (ignored.contains(r.url)) continue;
                        SearchResult old = unique.get(r.url);
                        if (old == null || r.score > old.score) unique.put(r.url, r);
                    }
                } catch (Exception ignoredAukro) {
                    // General web search below remains available as fallback.
                }
            }
        }

        int done = 0;
        int successfulQueries = unique.isEmpty() ? 0 : 1;
        String lastError = "";

        for (String query : queries) {
            done++;
            if (progress != null) progress.onProgress(done, queries.size(), query);

            try {
                List<SearchResult> partial = queryAcrossProviders(c, query, topics);
                successfulQueries++;
                for (SearchResult r : partial) {
                    if (ignored.contains(r.url)) continue;
                    SearchResult old = unique.get(r.url);
                    if (old == null || r.score > old.score) unique.put(r.url, r);
                }
            } catch (Exception e) {
                lastError = e.getMessage() == null ? "neznáma chyba" : e.getMessage();
            }

            // A short pause prevents search services from interpreting one tap as a burst of bot traffic.
            if (done < queries.size()) {
                try { Thread.sleep(220L); } catch (InterruptedException ignoredSleep) { Thread.currentThread().interrupt(); }
            }
        }

        if (successfulQueries == 0) {
            throw new Exception("Webové vyhľadávanie je dočasne nedostupné" + (lastError.isEmpty() ? "" : " • " + lastError));
        }

        List<SearchResult> out = new ArrayList<>(unique.values());
        Collections.sort(out, Comparator.comparingInt((SearchResult r) -> r.score).reversed());
        if (out.size() > 240) out = new ArrayList<>(out.subList(0, 240));

        if (progress != null && !out.isEmpty()) {
            progress.onProgress(queries.size(), queries.size(), "Načítavam náhľady a ceny…");
        }
        enrichTopResults(out);
        return out;
    }

    private static List<SearchResult> queryAukroDirect(Context c, String topic, List<String> allTopics, int pages) throws Exception {
        Map<String, SearchResult> found = new LinkedHashMap<>();
        String q = "pohlednice " + topic;
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());

        for (int page = 1; page <= pages; page++) {
            String address = "https://aukro.cz/vysledky-vyhledavani?text=" + encoded
                    + "&size=180&searchRedirectDisabled=true&page=" + page;
            String html;
            try {
                html = download(address, "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            } catch (Exception e) {
                if (page == 1) throw e;
                break;
            }

            Matcher m = AUKRO_OFFER_LINK.matcher(html);
            int pageAdded = 0;
            while (m.find()) {
                String path = htmlDecode(m.group(1));
                String url = "https://aukro.cz" + path;
                if (found.containsKey(url)) continue;

                String title = aukroTitleFromPath(path);
                if (!topicMatchesTitle(topic, title)) continue;

                String synthetic = "Pohlednice " + title + " Aukro";
                int sc = score(c, synthetic, allTopics);
                SearchResult r = new SearchResult(title, url,
                        "Priamy výsledok z kategórie pohľadníc na Aukre.", "aukro.cz",
                        "Pohľadnica", detectYear(title), sc);
                found.put(url, r);
                pageAdded++;
            }

            // No matching offers on this page usually means we are already outside useful results.
            if (page > 1 && pageAdded == 0) break;
        }

        return new ArrayList<>(found.values());
    }

    private static String aukroTitleFromPath(String path) {
        String s = path == null ? "" : path;
        if (s.startsWith("/")) s = s.substring(1);
        s = s.replaceFirst("-\\d{9,}$", "");
        s = s.replace('-', ' ');
        try { s = URLDecoder.decode(s, StandardCharsets.UTF_8.toString()); } catch (Exception ignored) {}
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "Pohľadnica na Aukre";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static boolean topicMatchesTitle(String topic, String title) {
        String t = fold(title);
        String q = fold(topic);
        if (q.length() > 2 && t.contains(q)) return true;

        int meaningful = 0;
        int hits = 0;
        for (String token : q.split("\\s+")) {
            if (token.length() <= 3) continue;
            meaningful++;
            String stem = token.substring(0, Math.min(5, token.length()));
            if (t.contains(token) || (stem.length() >= 4 && t.contains(stem))) hits++;
        }
        if (meaningful == 0) return false;
        if (meaningful == 1) return hits == 1;
        return hits >= Math.min(2, meaningful);
    }

    private static List<SearchResult> queryAcrossProviders(Context c, String q, List<String> topics) throws Exception {
        Map<String, SearchResult> merged = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int providersOk = 0;

        try {
            List<SearchResult> x = queryBingRss(c, q, topics);
            providersOk++;
            mergeResults(merged, x);
        } catch (Exception e) {
            errors.add("Bing " + shortError(e));
        }

        try {
            List<SearchResult> x = queryDuckLite(c, q, topics);
            providersOk++;
            mergeResults(merged, x);
        } catch (Exception e) {
            errors.add("DuckDuckGo Lite " + shortError(e));
        }

        try {
            List<SearchResult> x = queryDuckHtml(c, q, topics);
            providersOk++;
            mergeResults(merged, x);
        } catch (Exception e) {
            errors.add("DuckDuckGo " + shortError(e));
        }

        if (providersOk == 0) throw new Exception(String.join(" / ", errors));
        return new ArrayList<>(merged.values());
    }

    private static void mergeResults(Map<String, SearchResult> target, List<SearchResult> incoming) {
        for (SearchResult r : incoming) {
            SearchResult old = target.get(r.url);
            if (old == null || r.score > old.score) target.put(r.url, r);
        }
    }

    private static List<SearchResult> queryBingRss(Context c, String q, List<String> topics) throws Exception {
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
        String xml = download("https://www.bing.com/search?format=rss&cc=sk&setlang=sk-SK&q=" + encoded,
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");

        List<SearchResult> out = new ArrayList<>();
        Matcher items = RSS_ITEM.matcher(xml);
        while (items.find() && out.size() < 30) {
            String item = items.group(1);
            String title = matchText(RSS_TITLE, item);
            String link = htmlDecode(matchText(RSS_LINK, item));
            String snippet = clean(matchText(RSS_DESC, item));
            if (!link.startsWith("http") || title.isEmpty()) continue;
            addResult(c, topics, out, clean(title), link, snippet);
        }
        return out;
    }

    private static List<SearchResult> queryDuckLite(Context c, String q, List<String> topics) throws Exception {
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
        String html = download("https://lite.duckduckgo.com/lite/?q=" + encoded,
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");

        List<SearchResult> out = new ArrayList<>();
        Matcher m = DDG_LITE_RESULT.matcher(html);
        while (m.find() && out.size() < 25) {
            String link = unwrapDuck(htmlDecode(m.group(1)));
            String title = clean(m.group(2));
            if (!link.startsWith("http")) continue;
            int start = m.end();
            int end = Math.min(html.length(), start + 900);
            addResult(c, topics, out, title, link, clean(html.substring(start, end)));
        }
        return out;
    }

    private static List<SearchResult> queryDuckHtml(Context c, String q, List<String> topics) throws Exception {
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString());
        String html = download("https://html.duckduckgo.com/html/?q=" + encoded,
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");

        List<SearchResult> out = new ArrayList<>();
        Matcher m = DDG_RESULT.matcher(html);
        while (m.find() && out.size() < 25) {
            String link = unwrapDuck(htmlDecode(m.group(1)));
            String title = clean(m.group(2));
            if (!link.startsWith("http")) continue;
            int start = m.end();
            int end = Math.min(html.length(), start + 1200);
            addResult(c, topics, out, title, link, clean(html.substring(start, end)));
        }
        return out;
    }

    private static String download(String address, String userAgent) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(15000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", userAgent);
        con.setRequestProperty("Accept-Language", "sk-SK,sk;q=0.9,cs;q=0.7,en;q=0.5");
        con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        int code = con.getResponseCode();
        if (code < 200 || code >= 400) {
            con.disconnect();
            throw new Exception("HTTP " + code);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && data.length() < 2_500_000) data.append(line).append('\n');
        br.close();
        con.disconnect();
        return data.toString();
    }

    private static void addResult(Context c, List<String> topics, List<SearchResult> out, String title, String link, String snippet) {
        String source = host(link);
        String text = title + " " + snippet + " " + source;

        // Precision first: a place-name match alone is NOT enough.
        // The result must also look like a collectible/historical material.
        if (!isRelevantCandidate(c, text)) return;

        int score = score(c, text, topics);
        String type = detectType(text);
        String year = detectYear(text);
        out.add(new SearchResult(title, link, snippet.length() > 340 ? snippet.substring(0, 340) : snippet, source, type, year, score));
    }

    private static boolean isRelevantCandidate(Context c, String text) {
        String t = fold(text);

        boolean postcard = AppPrefs.includePostcards(c) && containsAny(t,
                "pohladnic", "pohlednic", "postcard", "ansichtskarte", "carte postale",
                "korespondencna karta", "korespondenční lístek");

        boolean oldPhoto = AppPrefs.includePhotos(c)
                && containsAny(t, "fotograf", "photo", "foto", "negativ", "diapozitiv", "albumin")
                && containsAny(t, "stara", "stary", "stare", "historick", "dobov", "archiv", "vintage", "antik");

        boolean historicalDoc = AppPrefs.includeDocs(c)
                && containsAny(t, "dokument", "prospekt", "brozura", "letak", "mapa", "plan", "tlacovina", "tiskovina")
                && containsAny(t, "stary", "stara", "stare", "historick", "dobov", "archiv", "vintage", "antik");

        boolean explicitCollectible = postcard || oldPhoto || historicalDoc;

        boolean obviousNoise = containsAny(t,
                "nehnutelnost", "reality", "realit", "stavebny pozemok", "pozemok",
                "rodinny dom", "byt ", "apartman", "prenajom", "developersk",
                "hypoteka", "novostavba", "ubytovanie", "rezervacia", "booking",
                "hotel ", "penzion", "wellness", "lyziarsky pobyt", "dovolenka",
                "automobil", "vozidlo", "pneumatik", "autodiel", "motocykel",
                "pracovna ponuka", "zamestnanie");

        if (obviousNoise && !explicitCollectible) return false;
        return explicitCollectible;
    }

    private static void enrichTopResults(List<SearchResult> results) {
        int limit = Math.min(results.size(), 70);
        if (limit <= 0) return;

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            SearchResult r = results.get(i);
            futures.add(pool.submit(() -> enrichOne(r)));
        }

        pool.shutdown();
        try {
            pool.awaitTermination(22, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }

    private static void enrichOne(SearchResult r) {
        HttpURLConnection con = null;
        try {
            URL pageUrl = new URL(r.url);
            con = (HttpURLConnection) pageUrl.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(6500);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            con.setRequestProperty("Accept-Language", "sk-SK,sk;q=0.9,cs;q=0.7,en;q=0.5");
            con.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.7");

            int code = con.getResponseCode();
            if (code < 200 || code >= 400) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null && html.length() < 1_500_000) html.append(line).append('\n');
            br.close();

            String page = html.toString();

            String image = firstMeta(page, "og:image", "twitter:image", "twitter:image:src", "image");
            if (!image.isEmpty()) {
                image = htmlDecode(image.trim());
                try { image = new URL(pageUrl, image).toString(); } catch (Exception ignored) {}
                if (image.startsWith("http://") || image.startsWith("https://")) r.imageUrl = image;
            }

            String amount = firstMeta(page, "product:price:amount", "og:price:amount", "price", "product:price");
            String currency = firstMeta(page, "product:price:currency", "og:price:currency", "priceCurrency");

            if (amount.isEmpty()) {
                Matcher pm = JSON_PRICE.matcher(page);
                if (pm.find()) amount = pm.group(1);
            }
            if (currency.isEmpty()) {
                Matcher cm = JSON_CURRENCY.matcher(page);
                if (cm.find()) currency = cm.group(1);
            }

            if (amount.isEmpty()) {
                Pattern visible = Pattern.compile("(?i)(?:cena|price|preis)[^0-9]{0,25}([0-9][0-9\\s.,]{0,12})\\s*(€|EUR|Kč|CZK|USD|\\$|GBP|£)");
                Matcher vm = visible.matcher(clean(page));
                if (vm.find()) {
                    amount = vm.group(1).trim().replaceAll("\\s+", " ");
                    currency = vm.group(2);
                }
            }

            if (!amount.isEmpty()) r.price = formatPrice(amount, currency);
        } catch (Exception ignored) {
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String firstMeta(String html, String... keys) {
        for (String key : keys) {
            String q = Pattern.quote(key);
            Pattern p1 = Pattern.compile("(?is)<meta[^>]+(?:property|name|itemprop)=[\\\"']" + q + "[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]*>");
            Matcher m1 = p1.matcher(html);
            if (m1.find()) return m1.group(1);

            Pattern p2 = Pattern.compile("(?is)<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+(?:property|name|itemprop)=[\\\"']" + q + "[\\\"'][^>]*>");
            Matcher m2 = p2.matcher(html);
            if (m2.find()) return m2.group(1);
        }
        return "";
    }

    private static String formatPrice(String amount, String currency) {
        String a = amount == null ? "" : amount.trim();
        String c = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (a.isEmpty()) return "";
        if (c.equals("EUR") || c.equals("€")) return a + " €";
        if (c.equals("CZK") || c.equals("KČ")) return a + " Kč";
        if (c.equals("USD") || c.equals("$")) return "$" + a;
        if (c.equals("GBP") || c.equals("£")) return "£" + a;
        return c.isEmpty() ? a : a + " " + c;
    }

    private static int score(Context c, String text, List<String> topics) {
        String t = fold(text);
        int score = 12;

        for (String topic : topics) {
            String f = fold(topic);
            if (f.length() > 2 && t.contains(f)) score += 35;
            for (String token : f.split("\\s+")) {
                if (token.length() > 3 && t.contains(token)) score += 6;
            }
        }

        if (containsAny(t, "pohladnic", "pohlednic", "postcard", "ansichtskarte", "carte postale", "doplnicova karta")) score += 38;
        if (AppPrefs.includePhotos(c) && containsAny(t, "fotograf", "photo", "foto", "negativ", "diapozitiv")
                && containsAny(t, "stara", "stare", "historick", "dobov", "archiv", "vintage", "antik")) score += 30;
        if (AppPrefs.includeDocs(c) && containsAny(t, "dokument", "prospekt", "mapa", "letak", "brozura", "archiv")
                && containsAny(t, "stary", "stara", "stare", "historick", "dobov", "archiv", "vintage", "antik")) score += 26;
        if (containsAny(t, "aukro", "ebay", "delcampe", "bazos", "antikvariat", "auction", "aukcia", "predaj")) score += 4;

        if (AppPrefs.excludeArticles(c) && containsAny(t, "clanok", "blog", "spravy", "wikipedia", "magazin")) score -= 34;
        if (AppPrefs.excludeAccommodation(c) && containsAny(t, "ubytovanie", "rezervacia", "booking", "hotel booking", "pobyt")) score -= 60;
        if (containsAny(t, "nehnutelnost", "reality", "realit", "stavebny pozemok", "pozemok", "rodinny dom", "novostavba", "hypoteka")) score -= 80;
        if (AppPrefs.excludeSocial(c) && containsAny(t, "facebook", "instagram", "tiktok", "diskusia", "forum")) score -= 30;
        if (!AppPrefs.includePostcards(c) && containsAny(t, "pohladnic", "postcard", "ansichtskarte")) score -= 35;
        if (!AppPrefs.includePhotos(c) && containsAny(t, "fotograf", "photo", "foto")) score -= 30;
        if (!AppPrefs.includeDocs(c) && containsAny(t, "dokument", "prospekt", "mapa", "brozura")) score -= 28;

        return Math.max(5, Math.min(99, score));
    }

    private static String contentTerms(Context c) {
        List<String> x = new ArrayList<>();
        if (AppPrefs.includePostcards(c)) x.add("(pohľadnica OR pohlednice OR postcard OR Ansichtskarte)");
        if (AppPrefs.includePhotos(c)) x.add("(historická fotografia OR stará fotografia OR old photo)");
        if (AppPrefs.includeDocs(c)) x.add("(historický dokument OR prospekt OR mapa OR brožúra)");
        if (x.isEmpty()) return "pohľadnica";
        return "(" + String.join(" OR ", x) + ")";
    }

    private static String detectType(String text) {
        String t = fold(text);
        if (containsAny(t, "pohladnic", "pohlednic", "postcard", "ansichtskarte", "carte postale")) return "Pohľadnica";
        if (containsAny(t, "fotograf", "photo", "foto", "negativ", "diapozitiv")) return "Fotografia";
        if (containsAny(t, "mapa", "prospekt", "dokument", "brozura", "letak")) return "Dokument";
        return "Možný nález";
    }

    private static String detectYear(String text) {
        Matcher m = YEAR.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String matchText(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? stripCdata(m.group(1)).trim() : "";
    }

    private static String stripCdata(String s) {
        if (s == null) return "";
        return s.replace("<![CDATA[", "").replace("]]>", "");
    }

    private static String shortError(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "zlyhalo" : m;
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
        } catch (Exception e) {
            return "web";
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return htmlDecode(s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
    }

    private static String htmlDecode(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
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

    public interface Progress {
        void onProgress(int done, int total, String query);
    }
}
