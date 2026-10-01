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
""","""        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
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
p.write_text(s)
