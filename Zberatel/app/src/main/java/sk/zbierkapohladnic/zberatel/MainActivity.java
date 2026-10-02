package sk.zbierkapohladnic.zberatel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(18, 56, 75);
    private static final int TEAL = Color.rgb(14, 129, 152);
    private static final int TEAL_LIGHT = Color.rgb(221, 245, 248);
    private static final int BG = Color.rgb(246, 250, 252);
    private static final int MUTED = Color.rgb(91, 111, 125);
    private static final int PAPER = Color.rgb(246, 239, 226);
    private static final int GREEN = Color.rgb(31, 142, 111);
    private static final int ORANGE = Color.rgb(198, 121, 39);

    private FrameLayout root;
    private LinearLayout contentHost;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int activeTab = 0;
    private TextView[] navLabels = new TextView[4];
    private TextView searchProgress;
    private Button searchButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        requestNotificationPermission();
        NotificationHelper.ensureChannel(this);
        SearchScheduler.schedule(this);
        buildShell();
        boolean openResults = getIntent().getBooleanExtra("open_results", false);
        if (openResults) showResults(); else showSearch();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("open_results", false)) showResults();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(0, 0, 0, 0);
        root.addView(shell, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        shell.addView(contentHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(bottomNav(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        setContentView(root);
    }

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        String[] icons = {"⌕", "▤", "◷", "⚙"};
        String[] names = {"Hľadať", "Nálezy", "História", "Nastavenia"};
        for (int i=0; i<4; i++) {
            final int ix=i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView icon = tv(icons[i], 25, NAVY, Typeface.BOLD);
            TextView label = tv(names[i], 11, MUTED, Typeface.BOLD);
            navLabels[i] = label;
            item.addView(icon);
            item.addView(label);
            item.setOnClickListener(v -> {
                if (ix==0) showSearch(); else if (ix==1) showResults(); else if (ix==2) showHistory(); else showSettings();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return nav;
    }

    private void selectTab(int tab) {
        activeTab = tab;
        for (int i=0;i<navLabels.length;i++) navLabels[i].setTextColor(i==tab ? TEAL : MUTED);
    }

    private ScrollView page() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setClipToPadding(false);
        s.setPadding(dp(15), 0, dp(15), dp(18));
        return s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, 0, 0, dp(24));
        return l;
    }

    private void clearPage() { contentHost.removeAllViews(); }

    private void showSearch() {
        selectTab(0); clearPage();
        ScrollView scroll = page(); LinearLayout col=column(); scroll.addView(col);
        col.addView(header("ZBERATEĽ", "Inteligentné vyhľadávanie historických pohľadníc, fotografií a dokumentov"));

        LinearLayout hero = card();
        TextView eyebrow = tv("OBJAVUJ • HĽADAJ • NEPREMEŠKAJ", 11, TEAL, Typeface.BOLD); hero.addView(eyebrow);
        TextView h = tv("Hľadaj s pomocou inteligentného filtra", 23, NAVY, Typeface.BOLD); h.setPadding(0,dp(5),0,dp(5)); hero.addView(h);
        hero.addView(tv("Zadáš témy, aplikácia prehľadá web a ukáže len výsledky, ktoré vyzerajú ako pohľadnice, staré fotografie alebo historické dokumenty.", 14, MUTED, Typeface.NORMAL));
        col.addView(hero);

        LinearLayout topicsCard = card();
        topicsCard.addView(sectionTitle("Sledované témy"));
        EditText topics = edit(AppPrefs.topics(this), "Tatry, Malá Fatra, horské chaty…");
        topics.setMinLines(2); topics.setGravity(Gravity.TOP);
        topicsCard.addView(topics);
        topicsCard.addView(helper("Oddeľ témy čiarkou. Pri manuálnom aj automatickom hľadaní sa kontroluje každá z nich."));
        col.addView(topicsCard);

        LinearLayout types = card();
        types.addView(sectionTitle("Čo zahrnúť"));
        Switch post = sw("Pohľadnice a korešpondenčné karty", AppPrefs.includePostcards(this));
        Switch photos = sw("Staré fotografie, negatívy a diapozitívy", AppPrefs.includePhotos(this));
        Switch docs = sw("Dokumenty, mapy, prospekty a tlačoviny", AppPrefs.includeDocs(this));
        types.addView(post); types.addView(photos); types.addView(docs);
        col.addView(types);

        LinearLayout exclude = card();
        exclude.addView(sectionTitle("Čo potlačiť"));
        Switch articles = sw("Články, blogy a správy", AppPrefs.excludeArticles(this));
        Switch acc = sw("Ubytovanie a rezervačné portály", AppPrefs.excludeAccommodation(this));
        Switch social = sw("Sociálne siete, diskusie a fóra", AppPrefs.excludeSocial(this));
        exclude.addView(articles); exclude.addView(acc); exclude.addView(social);
        col.addView(exclude);

        int srcCount = AppPrefs.sources(this).size() + splitCsv(AppPrefs.customSources(this)).size();
        LinearLayout sourceBrief = card();
        LinearLayout row = row();
        LinearLayout txt = columnNoPad();
        txt.addView(tv("Zdroje vyhľadávania", 16, NAVY, Typeface.BOLD));
        txt.addView(tv(srcCount + " zdrojových domén + všeobecné webové výsledky", 13, MUTED, Typeface.NORMAL));
        row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button editSources = smallButton("Nastaviť"); editSources.setOnClickListener(v->showSettings()); row.addView(editSources);
        sourceBrief.addView(row); col.addView(sourceBrief);

        searchProgress = tv("", 13, MUTED, Typeface.NORMAL); searchProgress.setPadding(dp(4),dp(4),dp(4),dp(8)); col.addView(searchProgress);
        searchButton = primaryButton("⌕  VYHĽADAŤ AKTUÁLNE NÁLEZY");
        searchButton.setOnClickListener(v -> {
            AppPrefs.topics(this, topics.getText().toString().trim());
            AppPrefs.p(this).edit()
                    .putBoolean("inc_postcards", post.isChecked()).putBoolean("inc_photos", photos.isChecked()).putBoolean("inc_docs", docs.isChecked())
                    .putBoolean("exc_articles", articles.isChecked()).putBoolean("exc_accommodation", acc.isChecked()).putBoolean("exc_social", social.isChecked()).apply();
            runManualSearch();
        });
        col.addView(searchButton);
        Button current = outlineButton("Zobraziť posledné výsledky"); current.setOnClickListener(v->showResults()); col.addView(current);
        contentHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void runManualSearch() {
        if (searchButton != null) searchButton.setEnabled(false);
        if (searchProgress != null) searchProgress.setText("Pripravujem vyhľadávanie…");
        executor.execute(() -> {
            try {
                List<SearchResult> results = SearchEngine.search(this, (done,total,q) -> main.post(() -> {
                    if (searchProgress != null) searchProgress.setText("Hľadám " + done + "/" + total + " • " + shorten(q, 60));
                }), true);
                DataStore.saveResults(this, results);
                Set<String> known = AppPrefs.knownUrls(this);
                for (SearchResult r: results) known.add(r.url);
                AppPrefs.knownUrls(this, known);
                DataStore.addHistory(this, results.size(), "Manuálne vyhľadávanie");
                main.post(() -> {
                    Toast.makeText(this, "Hotovo: " + results.size() + " nálezov", Toast.LENGTH_SHORT).show();
                    showResults();
                });
            } catch (Exception e) {
                DataStore.addHistory(this, 0, "Manuálne vyhľadávanie zlyhalo: " + e.getMessage());
                main.post(() -> {
                    if (searchButton != null) searchButton.setEnabled(true);
                    if (searchProgress != null) searchProgress.setText("Nepodarilo sa dokončiť vyhľadávanie: " + e.getMessage());
                    Toast.makeText(this, "Vyhľadávanie zlyhalo", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showResults() {
        selectTab(1); clearPage();
        ScrollView scroll=page(); LinearLayout col=column(); scroll.addView(col);
        List<SearchResult> results=DataStore.loadResults(this);
        col.addView(header("Nálezy", results.isEmpty()?"Zatiaľ bez uložených výsledkov":results.size()+" výsledkov z posledného hľadania"));

        LinearLayout filter = card();
        LinearLayout fr = row();
        TextView status = tv(results.isEmpty()?"Spusti vyhľadávanie":"Najvyššia zhoda ako prvá", 14, NAVY, Typeface.BOLD);
        fr.addView(status, new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button searchNow=smallButton("Vyhľadať teraz"); searchNow.setOnClickListener(v->{showSearch(); main.postDelayed(()->{ if(searchButton!=null) searchButton.performClick();},100);}); fr.addView(searchNow);
        filter.addView(fr); col.addView(filter);

        if (results.isEmpty()) {
            LinearLayout empty=card(); empty.addView(tv("Žiadne výsledky",22,NAVY,Typeface.BOLD));
            empty.addView(tv("Na karte Hľadať zadaj témy a stlač Vyhľadať aktuálne nálezy.",14,MUTED,Typeface.NORMAL)); col.addView(empty);
        } else {
            int shown=0;
            for (SearchResult r:results) {
                if (shown++>100) break;
                col.addView(resultCard(r));
            }
        }
        contentHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View resultCard(SearchResult r) {
        LinearLayout c=card();
        LinearLayout top=row();
        TextView badge = pill(r.score + "% zhoda", r.score>=85?GREEN:(r.score>=65?TEAL:ORANGE), Color.WHITE);
        top.addView(badge);
        Space sp=new Space(this); top.addView(sp,new LinearLayout.LayoutParams(0,1,1));
        TextView source=tv(r.source,12,MUTED,Typeface.BOLD); top.addView(source);
        c.addView(top);
        TextView title=tv(r.title,18,NAVY,Typeface.BOLD); title.setPadding(0,dp(10),0,dp(5)); c.addView(title);

        if (r.imageUrl!=null && !r.imageUrl.isEmpty()) {
            ImageView preview=new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setAdjustViewBounds(false);
            preview.setBackground(round(Color.rgb(239,245,247),14));
            preview.setClipToOutline(true);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(190));
            ip.setMargins(0,dp(7),0,dp(9));
            preview.setLayoutParams(ip);
            c.addView(preview);
            ImageLoader.load(preview,r.imageUrl);
        }

        if (r.price!=null && !r.price.isEmpty()) {
            TextView price=tv(r.price,19,GREEN,Typeface.BOLD);
            price.setPadding(0,dp(2),0,dp(7));
            c.addView(price);
        }

        LinearLayout tags=row();
        tags.addView(pill(r.type, TEAL_LIGHT, NAVY));
        if (!r.year.isEmpty()) tags.addView(pill(r.year, PAPER, NAVY));
        c.addView(tags);
        if (r.snippet!=null && !r.snippet.isEmpty()) {
            TextView sn=tv(shorten(r.snippet,280),13,MUTED,Typeface.NORMAL); sn.setPadding(0,dp(8),0,dp(8)); c.addView(sn);
        }
        LinearLayout actions=row();
        Button open=smallButton("Otvoriť zdroj"); open.setOnClickListener(v->openUrl(r.url));
        Button ignore=smallButton("Ignorovať"); ignore.setOnClickListener(v->{
            Set<String> ignored=AppPrefs.ignoredUrls(this); ignored.add(r.url); AppPrefs.ignoredUrls(this,ignored);
            List<SearchResult> list=DataStore.loadResults(this); list.removeIf(x->x.url.equals(r.url)); DataStore.saveResults(this,list); showResults();
        });
        actions.addView(open,new LinearLayout.LayoutParams(0,dp(42),1)); actions.addView(space(dp(8)));
        actions.addView(ignore,new LinearLayout.LayoutParams(0,dp(42),1)); c.addView(actions);
        c.setOnClickListener(v->openUrl(r.url));
        return c;
    }

    private void showHistory() {
        selectTab(2); clearPage();
        ScrollView scroll=page(); LinearLayout col=column(); scroll.addView(col);
        col.addView(header("História", "Kontroly a manuálne vyhľadávania"));
        JSONArray a=DataStore.history(this);
        if (a.length()==0) {
            LinearLayout e=card(); e.addView(tv("Zatiaľ nič",21,NAVY,Typeface.BOLD)); e.addView(tv("Po prvom vyhľadávaní tu uvidíš čas kontroly a počet nálezov.",14,MUTED,Typeface.NORMAL)); col.addView(e);
        }
        SimpleDateFormat f=new SimpleDateFormat("d. M. yyyy • HH:mm", Locale.getDefault());
        for (int i=0;i<a.length();i++) {
            try {
                JSONObject o=a.getJSONObject(i); LinearLayout c=card();
                LinearLayout top=row(); TextView n=tv(o.optString("note"),15,NAVY,Typeface.BOLD); top.addView(n,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
                TextView count=tv(String.valueOf(o.optInt("count")),22,TEAL,Typeface.BOLD); top.addView(count); c.addView(top);
                c.addView(tv(f.format(new Date(o.optLong("time"))),12,MUTED,Typeface.NORMAL));
                col.addView(c);
            } catch(Exception ignored) {}
        }
        contentHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showSettings() {
        selectTab(3); clearPage();
        ScrollView scroll=page(); LinearLayout col=column(); scroll.addView(col);
        col.addView(header("Nastavenia", "Automatické kontroly, upozornenia a zdroje"));

        LinearLayout schedule=card(); schedule.addView(sectionTitle("Automatické vyhľadávanie"));
        schedule.addView(helper("Android môže pri šetrení batérie inexact interval mierne posunúť. Najkratšia voľba je 15 minút."));
        String[] intervals={"15 minút","30 minút","1 hodina","3 hodiny","6 hodín","12 hodín","24 hodín"};
        long[] mins={15,30,60,180,360,720,1440};
        Spinner intervalSpinner=spinner(intervals); int si=0; for(int i=0;i<mins.length;i++) if(mins[i]==AppPrefs.intervalMinutes(this)) si=i; intervalSpinner.setSelection(si); schedule.addView(intervalSpinner);
        col.addView(schedule);

        LinearLayout notify=card(); notify.addView(sectionTitle("Upozornenia"));
        Switch notifications=sw("Upozorniť, keď sa nájde niečo nové",AppPrefs.notifications(this)); notify.addView(notifications);
        Switch vibrate=sw("Vibrovať",AppPrefs.vibration(this)); notify.addView(vibrate);
        notify.addView(label("Zvuk")); Spinner sound=spinner(new String[]{"Jemné","Výrazné","Tiché"}); select(sound,AppPrefs.sound(this)); notify.addView(sound);
        notify.addView(label("Spôsob upozornenia")); Spinner mode=spinner(new String[]{"Súhrn","Každý nález"}); select(mode,AppPrefs.notificationMode(this)); notify.addView(mode);
        notify.addView(label("Minimálna zhoda pre upozornenie")); Spinner threshold=spinner(new String[]{"50 %","60 %","70 %","80 %","90 %"}); select(threshold,AppPrefs.threshold(this)+" %"); notify.addView(threshold);
        Button test=outlineButton("Otestovať upozornenie"); test.setOnClickListener(v->{saveNotificationTemp(sound,vibrate,notifications,mode,threshold); requestNotificationPermission(); NotificationHelper.test(this);}); notify.addView(test);
        Button sys=outlineButton("Systémové nastavenia upozornení"); sys.setOnClickListener(v->openNotificationSettings()); notify.addView(sys);
        col.addView(notify);

        LinearLayout sources=card(); sources.addView(sectionTitle("Zdrojové portály")); sources.addView(helper("Zapnuté portály sa používajú ako cielené site: vyhľadávania. Okrem nich sa robí aj všeobecné webové hľadanie."));
        Set<String> enabled=AppPrefs.sources(this); List<CheckBox> sourceChecks=new ArrayList<>();
        for(String d:AppPrefs.DEFAULT_SOURCES){ CheckBox cb=check(d,enabled.contains(d)); sourceChecks.add(cb); sources.addView(cb); }
        sources.addView(label("Vlastné domény")); EditText custom=edit(AppPrefs.customSources(this),"napr. antikvariat.example, burza.example"); custom.setMinLines(2); sources.addView(custom); col.addView(sources);

        LinearLayout privacy=card(); privacy.addView(sectionTitle("Ako vyhľadávanie funguje"));
        privacy.addView(tv("Aplikácia neposiela tvoje témy na náš vlastný server. Pri hľadaní vytvára webové dotazy na vyhľadávač a výsledky hodnotí priamo v telefóne. Prvá verzia analyzuje text a zdroj; nerozpoznáva ešte obraz samotnej pohľadnice.",13,MUTED,Typeface.NORMAL));
        col.addView(privacy);

        Button save=primaryButton("✓  ULOŽIŤ NASTAVENIA");
        save.setOnClickListener(v->{
            Set<String> src=new LinkedHashSet<>(); for(CheckBox cb:sourceChecks) if(cb.isChecked()) src.add(cb.getText().toString());
            int t=Integer.parseInt(threshold.getSelectedItem().toString().replace(" %",""));
            AppPrefs.p(this).edit()
                    .putLong("interval_min",mins[intervalSpinner.getSelectedItemPosition()])
                    .putBoolean("notifications",notifications.isChecked()).putBoolean("vibration",vibrate.isChecked())
                    .putString("sound",sound.getSelectedItem().toString()).putString("notification_mode",mode.getSelectedItem().toString())
                    .putInt("threshold",t).putStringSet("sources",src).putString("custom_sources",custom.getText().toString().trim()).apply();
            NotificationHelper.ensureChannel(this); SearchScheduler.schedule(this);
            Toast.makeText(this,"Nastavenia uložené",Toast.LENGTH_SHORT).show();
            showSettings();
        }); col.addView(save);
        contentHost.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void saveNotificationTemp(Spinner sound, Switch vib, Switch notifications, Spinner mode, Spinner threshold) {
        int t=Integer.parseInt(threshold.getSelectedItem().toString().replace(" %",""));
        AppPrefs.p(this).edit().putBoolean("notifications",notifications.isChecked()).putBoolean("vibration",vib.isChecked())
                .putString("sound",sound.getSelectedItem().toString()).putString("notification_mode",mode.getSelectedItem().toString()).putInt("threshold",t).apply();
        NotificationHelper.ensureChannel(this);
    }

    private View header(String title, String subtitle) {
        LinearLayout h=new LinearLayout(this); h.setOrientation(LinearLayout.VERTICAL); h.setPadding(dp(5),dp(20),dp(5),dp(14));
        LinearLayout brand=row();
        TextView logo=tv("▲▱",24,TEAL,Typeface.BOLD); brand.addView(logo);
        TextView t=tv(title,28,NAVY,Typeface.BOLD); t.setPadding(dp(8),0,0,0); brand.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        h.addView(brand); TextView s=tv(subtitle,13,MUTED,Typeface.NORMAL); s.setPadding(0,dp(4),0,0); h.addView(s); return h;
    }

    private LinearLayout card() {
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(15),dp(16),dp(15));
        c.setBackground(round(Color.WHITE,18)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(11)); c.setLayoutParams(lp); c.setElevation(dp(1)); return c;
    }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout columnNoPad(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView sectionTitle(String s){ TextView t=tv(s,17,NAVY,Typeface.BOLD); t.setPadding(0,0,0,dp(8)); return t; }
    private TextView label(String s){ TextView t=tv(s,12,MUTED,Typeface.BOLD); t.setPadding(0,dp(12),0,dp(5)); return t; }
    private TextView helper(String s){ TextView t=tv(s,12,MUTED,Typeface.NORMAL); t.setPadding(0,dp(6),0,dp(4)); return t; }

    private TextView tv(String text,int sp,int color,int style){ TextView t=new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.create("sans",style)); t.setLineSpacing(0,1.08f); return t; }
    private TextView pill(String text,int bg,int fg){ TextView t=tv(text,11,fg,Typeface.BOLD); t.setPadding(dp(9),dp(5),dp(9),dp(5)); t.setBackground(round(bg,20)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,dp(6),0); t.setLayoutParams(lp); return t; }

    private EditText edit(String value,String hint){ EditText e=new EditText(this); e.setText(value); e.setHint(hint); e.setTextSize(14); e.setTextColor(NAVY); e.setHintTextColor(Color.rgb(145,160,170)); e.setPadding(dp(13),dp(10),dp(13),dp(10)); e.setBackground(round(Color.rgb(248,251,252),13)); e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); return e; }
    private Switch sw(String text,boolean checked){ Switch s=new Switch(this); s.setText(text); s.setTextSize(14); s.setTextColor(NAVY); s.setChecked(checked); s.setPadding(0,dp(5),0,dp(5)); return s; }
    private CheckBox check(String text,boolean checked){ CheckBox c=new CheckBox(this); c.setText(text); c.setTextSize(14); c.setTextColor(NAVY); c.setChecked(checked); c.setPadding(0,dp(3),0,dp(3)); return c; }

    private Button primaryButton(String text){ Button b=new Button(this); b.setText(text); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(round(TEAL,14)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)); lp.setMargins(0,dp(5),0,dp(8)); b.setLayoutParams(lp); return b; }
    private Button outlineButton(String text){ Button b=new Button(this); b.setText(text); b.setTextSize(13); b.setTextColor(NAVY); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); GradientDrawable g=round(Color.WHITE,13); g.setStroke(dp(1),Color.rgb(205,218,225)); b.setBackground(g); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)); lp.setMargins(0,dp(5),0,dp(5)); b.setLayoutParams(lp); return b; }
    private Button smallButton(String text){ Button b=new Button(this); b.setText(text); b.setTextSize(11); b.setTextColor(TEAL); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setAllCaps(false); b.setBackground(round(TEAL_LIGHT,12)); b.setPadding(dp(10),0,dp(10),0); return b; }

    private Spinner spinner(String[] values){ Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){ @Override public View getView(int p, View cv, ViewGroup parent){ TextView v=(TextView)super.getView(p,cv,parent); v.setTextColor(NAVY); v.setTextSize(14); v.setPadding(dp(12),dp(9),dp(12),dp(9)); return v; } }; a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); s.setBackground(round(Color.rgb(248,251,252),12)); return s; }
    private void select(Spinner s,String value){ for(int i=0;i<s.getCount();i++) if(s.getItemAtPosition(i).toString().equals(value)){s.setSelection(i);return;} }

    private GradientDrawable round(int color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private View space(int w){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(w,1)); return s; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void openUrl(String u){ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }catch(Exception e){ Toast.makeText(this,"Odkaz sa nepodarilo otvoriť",Toast.LENGTH_SHORT).show(); } }
    private void openNotificationSettings(){
        try{
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS); i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()); startActivity(i);
        }catch(Exception e){ startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
    }

    private void requestNotificationPermission(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44); }
    private static String shorten(String s,int max){ if(s==null)return ""; return s.length()<=max?s:s.substring(0,max-1)+"…"; }
    private static List<String> splitCsv(String raw){ List<String> l=new ArrayList<>(); if(raw==null)return l; for(String s:raw.split("[,;\\n]+")){s=s.trim(); if(!s.isEmpty())l.add(s);} return l; }
}
