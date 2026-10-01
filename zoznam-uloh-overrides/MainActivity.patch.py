from pathlib import Path
p=Path("ZOZNAM_ULOH/app/src/main/java/sk/zoznamuloh/app/MainActivity.java")
s=p.read_text()

s=s.replace("""    private boolean shoppingMode = false;
""","""    private boolean shoppingMode = false;
    private int appliedTheme = -1;
""")

s=s.replace("""        getWindow().setStatusBarColor(Ui.NAVY_DARK);
        getWindow().setNavigationBarColor(Ui.NAVY_DARK);
""","""        Ui.applySystemBars(this);
        appliedTheme = Ui.getTheme(this);
""",1)

s=s.replace("top.setBackgroundColor(Ui.ACCENT);","top.setBackgroundColor(Ui.NAVY_DARK);",1)

s=s.replace("""        settings.setOnClickListener(v -> showPermissionDialog());
""","""        settings.setOnClickListener(v -> openSettings());
""",1)

s=s.replace("""    protected void onResume() {
        super.onResume();
        refreshCurrent();
    }
""","""    protected void onResume() {
        super.onResume();
        int currentTheme = Ui.getTheme(this);
        if (appliedTheme != -1 && currentTheme != appliedTheme) {
            recreate();
            return;
        }
        refreshCurrent();
    }
""")

start=s.index("    private void addPermissionBanner() {")
end=s.index("    private void addTaskGroup(", start)
banner='''    private void addPermissionBanner() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        box.setBackground(Ui.roundedStroke(Ui.CARD_SOFT, Ui.ACCENT, 1, 20, this));

        TextView h = Ui.text(this, "Dokonči nastavenie alarmov", 16, Ui.TEXT);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(h);

        TextView p = Ui.text(this, "Povoľ notifikácie, panel nad aplikáciami a presné alarmy.", 13, Ui.MUTED);
        p.setPadding(0, Ui.dp(this, 5), 0, 0);
        box.addView(p);

        TextView setup = Ui.button(this, "OTVORIŤ NASTAVENIA", Ui.ACCENT, Ui.TEXT);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        Ui.margin(buttonLp, this, 0, 12, 0, 0);
        box.addView(setup, buttonLp);
        setup.setOnClickListener(v -> openSettings());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        Ui.margin(lp, this, 0, 0, 0, 12);
        taskContainer.addView(box, lp);
    }

'''
s=s[:start]+banner+s[end:]

start=s.index("    private void showPermissionDialog() {")
end=s.index("    private void addPermissionRow(", start)
replacement='''    private void showPermissionDialog() {
        openSettings();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

'''
s=s[:start]+replacement+s[end:]

p.write_text(s)
