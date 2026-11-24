"""Fail CI if runtime network permissions or cloud SDKs return."""
from pathlib import Path
import re
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
blocked = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"}
assert not {node.get(ANDROID + "name") for node in manifest.findall("uses-permission")} & blocked
assert manifest.find("application").get(ANDROID + "allowBackup") == "false"
catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
for dependency in catalog["libraries"].values():
    group = dependency.get("group", "")
    assert not re.search(r"firebase|okhttp|retrofit|googleid|credentials|work-runtime", str(dependency)), dependency
    assert group != "com.google.android.gms", dependency
for path in (ROOT / "app/src/main").rglob("*"):
    if path.is_file() and path.suffix in {".kt", ".xml", ".json"}:
        text = path.read_text()
        assert not re.search(r"AIza[\w-]{30,}|import com\.google\.firebase|import okhttp3|import retrofit2", text), path
for name in ("backup_rules.xml", "data_extraction_rules.xml"):
    root = ET.parse(ROOT / "app/src/main/res/xml" / name).getroot()
    sections = list(root) if name == "data_extraction_rules.xml" else [root]
    for section in sections:
        assert {n.get("domain") for n in section.findall("exclude")} >= {"root", "file", "database", "sharedpref", "external"}
print("Offline policy checks passed")