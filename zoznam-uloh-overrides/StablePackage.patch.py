from pathlib import Path

gradle = Path("ZOZNAM_ULOH/app/build.gradle")
s = gradle.read_text()
s = s.replace("applicationId 'sk.zoznamuloh.app'", "applicationId 'sk.zoznamuloh.personal'")
s = s.replace("versionCode 1", "versionCode 2")
s = s.replace("versionName '1.0'", "versionName '5.0'")
gradle.write_text(s)
