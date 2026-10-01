from pathlib import Path
p=Path("ZOZNAM_ULOH/app/src/main/AndroidManifest.xml")
s=p.read_text()
needle='''        <activity
            android:name=".SnoozeActivity"
            android:exported="false"
            android:theme="@style/SnoozeTheme" />
'''
replacement='''        <activity
            android:name=".SettingsActivity"
            android:exported="false" />

        <activity
            android:name=".SnoozeActivity"
            android:exported="false"
            android:theme="@style/SnoozeTheme" />
'''
s=s.replace(needle,replacement)
p.write_text(s)
