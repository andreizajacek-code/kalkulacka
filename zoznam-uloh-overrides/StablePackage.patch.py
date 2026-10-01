from pathlib import Path

gradle = Path("ZOZNAM_ULOH/app/build.gradle")
s = gradle.read_text()
s = s.replace("applicationId 'sk.zoznamuloh.app'", "applicationId 'sk.zoznamuloh.stable'")
s = s.replace("versionCode 1", "versionCode 3")
s = s.replace("versionName '1.0'", "versionName '3.0'")
gradle.write_text(s)

strings = Path("ZOZNAM_ULOH/app/src/main/res/values/strings.xml")
s = strings.read_text()
s = s.replace("<string name=\"app_name\">ZOZNAM ULOH</string>", "<string name=\"app_name\">ZOZNAM ULOH</string>")
strings.write_text(s)
