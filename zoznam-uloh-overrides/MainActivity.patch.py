from pathlib import Path
p=Path("ZOZNAM_ULOH/app/src/main/java/sk/zoznamuloh/app/MainActivity.java")
s=p.read_text()
s=s.replace("top.setBackgroundColor(Ui.ACCENT);","top.setBackgroundColor(Ui.NAVY_DARK);",1)
p.write_text(s)
