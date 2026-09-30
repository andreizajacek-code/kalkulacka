from pathlib import Path
p=Path("ZOZNAM_ULOH/app/src/main/java/sk/zoznamuloh/app/TaskEditorActivity.java")
s=p.read_text()
s=s.replace("top.setBackgroundColor(Ui.ACCENT);","top.setBackgroundColor(Ui.NAVY_DARK);",1)
s=s.replace("""        TextView back = Ui.button(this, "‹", Ui.ACCENT, Ui.TEXT);
        back.setTextSize(32);
        back.setBackgroundColor(Color.TRANSPARENT);
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        back.setOnClickListener(v -> finish());
""","""        TextView back = Ui.button(this, "←", Ui.CARD_SOFT, Ui.TEXT);
        back.setTextSize(23);
        back.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        back.setOnClickListener(v -> finish());
""")
s=s.replace("top.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));","top.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));")
p.write_text(s)
