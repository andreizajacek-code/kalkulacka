package sk.zbierkapohladnic.zberatel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class SearchReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<SearchResult> results = SearchEngine.search(context, null);
                DataStore.saveResults(context, results);
                Set<String> known = AppPrefs.knownUrls(context);
                List<SearchResult> fresh = new ArrayList<>();
                int threshold = AppPrefs.threshold(context);
                for (SearchResult r : results) {
                    if (r.score >= threshold && !known.contains(r.url)) fresh.add(r);
                    known.add(r.url);
                }
                AppPrefs.knownUrls(context, known);
                DataStore.addHistory(context, results.size(), "Automatická kontrola");
                NotificationHelper.notifyResults(context, fresh);
            } catch (Exception e) {
                DataStore.addHistory(context, 0, "Automatická kontrola zlyhala: " + e.getMessage());
            } finally { pending.finish(); }
        });
    }
}
