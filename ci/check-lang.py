#!/usr/bin/env python3
"""Fails when a language file is not strict JSON.

Minecraft parses language files strictly: one stray trailing comma makes the whole file fail to
load, and every text of the mod then shows up as its raw translation key.
"""
import json
import pathlib
import sys

LANG_DIR = pathlib.Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/mmdskin/lang"


def main() -> int:
    failed = False
    keys = {}
    for path in sorted(LANG_DIR.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, UnicodeDecodeError) as error:
            print(f"FAIL {path.name}: {error}")
            failed = True
            continue
        if not isinstance(data, dict) or not all(isinstance(v, str) for v in data.values()):
            print(f"FAIL {path.name}: expected an object of string values")
            failed = True
            continue
        keys[path.stem] = set(data)
        print(f"OK   {path.name}: {len(data)} keys")

    reference = keys.get("en_us")
    if reference is None:
        print("FAIL en_us.json is missing or invalid; it is the fallback for every other language")
        return 1
    for lang, lang_keys in keys.items():
        missing = sorted(reference - lang_keys)
        if missing:
            print(f"NOTE {lang}: {len(missing)} keys fall back to English, e.g. {missing[0]}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
