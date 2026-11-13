#!/usr/bin/env python3
"""Validate Mixin targets against a Minecraft jar without launching the game.

Compiling a Mixin class never checks its string targets ("method = ...",
"target = ..."), so a compile-green build can still crash on startup. This
script reads every Mixin listed in a mixins.json, resolves the target class
with javap and verifies that each referenced method / field exists with the
given descriptor. It exits 1 if any target is missing.

usage:
  check_mixins.py --src fabric/src/main/java \
                  --mixins-json fabric/src/main/resources/mmdskin-fabric.mixins.json \
                  --classpath minecraft-merged.jar[:other.jar...] [--summary FILE]

Only the Python standard library and a JDK (javap) are required.
"""
import argparse
import json
import os
import re
import subprocess
import sys
from functools import lru_cache

INJECTORS = {"Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable",
             "ModifyConstant", "WrapOperation", "WrapWithCondition",
             "ModifyExpressionValue", "ModifyReturnValue", "WrapMethod"}
MEMBER_ANN = {"Accessor", "Invoker", "Shadow"}
PRIMS = {"B": "byte", "C": "char", "D": "double", "F": "float", "I": "int",
         "J": "long", "S": "short", "Z": "boolean", "V": "void"}


# --------------------------------------------------------------------------- javap
class Javap:
    def __init__(self, classpath):
        self.cp = classpath

    @lru_cache(maxsize=None)
    def members(self, binary_name):
        """Return (methods, fields) or None when the class cannot be found.
        methods: list of (name, descriptor); fields: list of (name, descriptor)."""
        cmd = ["javap", "-p", "-s"]
        if self.cp:
            cmd += ["-cp", self.cp]
        cmd.append(binary_name)
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0 or not res.stdout.strip():
            return None
        out = res.stdout
        simple = binary_name.rsplit(".", 1)[-1].rsplit("$", 1)[-1]
        methods, fields = [], []
        pending = None
        for line in out.splitlines():
            m = re.match(r"^\s+descriptor:\s*(\S+)\s*$", line)
            if m and pending is not None:
                desc = m.group(1)
                if "(" in pending:
                    nm = re.search(r"([\w$<>]+)\s*\(", pending)
                    name = nm.group(1) if nm else "?"
                    if name == simple and desc.endswith(")V"):
                        name = "<init>"
                    methods.append((name, desc))
                else:
                    nm = re.search(r"([\w$]+)\s*;\s*$", pending)
                    fields.append((nm.group(1) if nm else "?", desc))
                pending = None
                continue
            if re.match(r"^  \S", line) and line.rstrip().endswith(";") and "descriptor" not in line:
                pending = line.strip()
            elif re.match(r"^  static \{\};", line):
                pending = None
        return methods, fields


# --------------------------------------------------------------------------- source parsing
def read_balanced(src, i):
    """src[i] == '('. Return (inner_text, index_after_closing_paren)."""
    depth, j, in_str = 0, i, False
    while j < len(src):
        c = src[j]
        if in_str:
            if c == "\\":
                j += 1
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return src[i + 1:j], j + 1
        j += 1
    return src[i + 1:], len(src)


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), src, flags=re.S)
    return re.sub(r"(?<!:)//[^\n]*", "", src)


def parse_imports(src):
    imp = {}
    for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)\s*;", src, re.M):
        fq = m.group(1)
        imp[fq.rsplit(".", 1)[-1]] = fq
    return imp


def resolve_class(name, imports, pkg):
    """'Outer.Inner' / 'Camera' / 'a.b.C' -> binary name with $ for nested."""
    parts = name.split(".")
    if parts[0] in imports:
        base = imports[parts[0]]
        return base + "".join("$" + p for p in parts[1:])
    if len(parts) > 1 and parts[0][:1].islower():
        # fully qualified: assume last capitalised part starts nesting
        for k, p in enumerate(parts):
            if p[:1].isupper():
                return ".".join(parts[:k + 1]) + "".join("$" + q for q in parts[k + 1:])
    return pkg + "." + name.replace(".", "$")


def split_top(text, sep=","):
    out, depth, cur = [], 0, ""
    for c in text:
        if c in "<([{":
            depth += 1
        elif c in ">)]}":
            depth -= 1
        if c == sep and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += c
    if cur.strip():
        out.append(cur)
    return out


def simple_type(t):
    t = re.sub(r"@\w+(\([^)]*\))?", "", t).replace("final ", "").strip()
    arr = t.count("[]")
    t = re.sub(r"<.*>", "", t.replace("[]", "")).strip().rsplit(".", 1)[-1]
    return t + "[]" * arr


def desc_params(desc):
    """'(Lnet/x/Camera;F[I)V' -> ['Camera','float','int[]']"""
    inner = desc[1:desc.index(")")]
    out, i = [], 0
    while i < len(inner):
        arr = 0
        while inner[i] == "[":
            arr += 1
            i += 1
        if inner[i] == "L":
            j = inner.index(";", i)
            t = inner[i + 1:j].rsplit("/", 1)[-1].rsplit("$", 1)[-1]
            i = j + 1
        else:
            t = PRIMS[inner[i]]
            i += 1
        out.append(t + "[]" * arr)
    return out


def next_member(src, i):
    """After an annotation ending at i: skip further annotations, return the member
    declaration text up to '{' or ';' (top-level)."""
    n = len(src)
    while True:
        while i < n and src[i].isspace():
            i += 1
        if i < n and src[i] == "@":
            m = re.match(r"@[\w.]+", src[i:])
            i += m.end()
            while i < n and src[i].isspace():
                i += 1
            if i < n and src[i] == "(":
                _, i = read_balanced(src, i)
            continue
        break
    depth, j = 0, i
    while j < n:
        c = src[j]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        elif c in "{;=" and depth == 0:
            break
        j += 1
    return src[i:j].strip()


def handler_signature(decl):
    m = re.search(r"([\w$]+)\s*\((.*)\)\s*(?:throws[\w\s,.]+)?$", decl, re.S)
    if not m:
        return None, []
    params = []
    for p in split_top(m.group(2)):
        p = p.strip()
        if not p:
            continue
        toks = re.sub(r"@\w+(\([^)]*\))?", "", p).strip().rsplit(None, 1)
        params.append(simple_type(toks[0] if len(toks) == 2 else p))
    return m.group(1), params


def strings_of(arg_text, key):
    """Values of  key = "x"  or  key = {"x","y"}  (also value= / bare first string)."""
    out = []
    m = re.search(r"\b" + key + r"\s*=\s*(\{[^}]*\}|\"(?:\\.|[^\"\\])*\")", arg_text)
    if m:
        out = re.findall(r"\"((?:\\.|[^\"\\])*)\"", m.group(1))
    return out


def parse_method_ref(s):
    """'name' | 'name(desc)ret' | 'Lowner;name(desc)ret' | 'Lowner;name:Ltype;'"""
    m = re.match(r"^(?:L([^;]+);)?([\w$<>]+)(\(.*\).*|:.*)?$", s)
    if not m:
        return None
    return m.group(1), m.group(2), m.group(3)


# --------------------------------------------------------------------------- checking
class Report:
    def __init__(self):
        self.rows = []
        self.fail = 0
        self.warn = 0

    def add(self, level, mixin, what, detail=""):
        self.rows.append((level, mixin, what, detail))
        if level == "FAIL":
            self.fail += 1
        elif level == "WARN":
            self.warn += 1


def check_mixin(path, javap, rep):
    raw = open(path, encoding="utf-8").read()
    src = strip_comments(raw)
    pkg = re.search(r"^package\s+([\w.]+)\s*;", src, re.M).group(1)
    mixin_name = os.path.basename(path)[:-5]
    imports = parse_imports(src)
    pseudo = bool(re.search(r"@Pseudo\b", src))

    # target classes
    targets = []
    for m in re.finditer(r"@Mixin\s*\(", src):
        args, _ = read_balanced(src, m.end() - 1)
        targets += [resolve_class(c, imports, pkg) for c in re.findall(r"([\w.]+)\.class", args)]
        targets += [t.replace("/", ".") for t in strings_of(args, "targets")]
    if not targets:
        rep.add("WARN", mixin_name, "no @Mixin target found")
        return

    tinfo = {}
    for t in targets:
        info = javap.members(t)
        if info is None:
            rep.add("WARN" if pseudo else "FAIL", mixin_name, f"target class {t}",
                    "not found (@Pseudo: skipped)" if pseudo else "class does not exist in this Minecraft version")
        else:
            tinfo[t] = info
            rep.add("OK", mixin_name, f"target class {t}")
    if not tinfo:
        return

    def methods_named(name):
        return [(t, d) for t, (ms, _) in tinfo.items() for n, d in ms if n == name]

    for m in re.finditer(r"@(\w+)\b", src):
        ann = m.group(1)
        if ann not in INJECTORS and ann not in MEMBER_ANN:
            continue
        j = m.end()
        args = ""
        k = j
        while k < len(src) and src[k] in " \t\r\n":
            k += 1
        if k < len(src) and src[k] == "(":
            args, end = read_balanced(src, k)
        else:
            end = j
        decl = next_member(src, end)
        hname, hparams = handler_signature(decl)

        if ann in INJECTORS:
            refs = strings_of(args, "method")
            if not refs:
                m2 = re.match(r"\s*\"((?:\\.|[^\"\\])*)\"", args)
                refs = [m2.group(1)] if m2 else []
            matched_here = []
            for ref in refs:
                pr = parse_method_ref(ref)
                if not pr:
                    rep.add("WARN", mixin_name, f"@{ann} {hname}: cannot parse method '{ref}'")
                    continue
                _, name, desc = pr
                cands = methods_named(name)
                if desc:
                    cands = [(t, d) for t, d in cands if d == desc]
                if not cands:
                    avail = sorted({d for _, d in methods_named(name)})
                    rep.add("FAIL", mixin_name, f"@{ann} {hname}: method '{ref}' not found",
                            ("same name exists with: " + "; ".join(avail)) if avail else "no method with that name")
                else:
                    rep.add("OK", mixin_name, f"@{ann} {hname}: method '{ref}'")
                    matched_here += cands

            # @At(... target = "...")
            for am in re.finditer(r"@At\s*\(", args):
                at_args, _ = read_balanced(args, am.end() - 1)
                value = (strings_of(at_args, "value") or [""])[0]
                tgt = (strings_of(at_args, "target") or [""])[0]
                if not tgt:
                    continue
                pr = parse_method_ref(tgt)
                if not pr or not pr[0]:
                    rep.add("WARN", mixin_name, f"@At {value} target '{tgt}' not checkable (no owner)")
                    continue
                owner, name, desc = pr
                oinfo = javap.members(owner.replace("/", "."))
                if oinfo is None:
                    rep.add("FAIL", mixin_name, f"@At {value} target '{tgt}'", f"owner class {owner} not found")
                    continue
                ms, fs = oinfo
                if desc and desc.startswith(":"):
                    ok = any(n == name and d == desc[1:] for n, d in fs)
                else:
                    ok = any(n == name and (not desc or d == desc) for n, d in ms)
                if ok:
                    rep.add("OK", mixin_name, f"@At {value} target {owner.rsplit('/', 1)[-1]}.{name}")
                else:
                    avail = sorted({d for n, d in ms if n == name})
                    rep.add("FAIL", mixin_name, f"@At {value} target '{tgt}' not found",
                            ("same name exists with: " + "; ".join(avail)) if avail else "no member with that name")

            # handler parameter sanity check for simple injection points
            if ann == "Inject" and len(matched_here) == 1 and hparams:
                _, d = matched_here[0]
                want = desc_params(d)
                got = [p for p in hparams if not p.startswith("CallbackInfo")]
                if got[:len(want)] != want:
                    rep.add("WARN", mixin_name, f"@Inject {hname}: parameters differ from target",
                            f"target({', '.join(want)}) vs handler({', '.join(got)})")

        elif ann in ("Accessor", "Invoker"):
            m3 = re.match(r"\s*(?:value\s*=\s*)?\"([\w$]+)\"", args)
            if m3:
                name = m3.group(1)
            else:
                name = re.sub(r"^(get|set|is|call|invoke)", "", hname or "")
                name = name[:1].lower() + name[1:]
            if ann == "Accessor":
                ok = any(n == name for _, (_, fs) in tinfo.items() for n, _ in fs)
            else:
                ok = bool(methods_named(name))
            rep.add("OK" if ok else "FAIL", mixin_name, f"@{ann} {hname}: '{name}'",
                    "" if ok else "no such member in target class")

        elif ann == "Shadow":
            if "prefix" in args:
                continue
            if "(" in decl:
                nm, _ = handler_signature(decl)
                ok = bool(methods_named(nm))
            else:
                nm = (re.findall(r"([\w$]+)\s*$", decl) or ["?"])[0]
                ok = any(n == nm for _, (_, fs) in tinfo.items() for n, _ in fs)
            rep.add("OK" if ok else "FAIL", mixin_name, f"@Shadow {nm}",
                    "" if ok else "no such member in target class")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="java source root containing the mixin package")
    ap.add_argument("--mixins-json", required=True)
    ap.add_argument("--classpath", default="", help="javap classpath (Minecraft jar first)")
    ap.add_argument("--summary", default="", help="write a Markdown report to this file")
    ap.add_argument("--quiet-ok", action="store_true", help="hide OK rows")
    a = ap.parse_args()

    cfg = json.load(open(a.mixins_json, encoding="utf-8"))
    pkg = cfg["package"]
    names = cfg.get("mixins", []) + cfg.get("client", []) + cfg.get("server", [])
    javap = Javap(a.classpath)
    rep = Report()
    for n in names:
        path = os.path.join(a.src, pkg.replace(".", "/"), n.replace(".", "/") + ".java")
        if not os.path.isfile(path):
            rep.add("FAIL", n, "mixin source file missing", path)
            continue
        check_mixin(path, javap, rep)

    for level, mixin, what, detail in rep.rows:
        if level == "OK" and a.quiet_ok:
            continue
        print(f"[{level:4}] {mixin}: {what}" + (f"\n         -> {detail}" if detail else ""))
    print(f"\n{len(names)} mixins checked: {rep.fail} FAIL, {rep.warn} WARN, "
          f"{sum(1 for r in rep.rows if r[0] == 'OK')} OK")

    if a.summary:
        with open(a.summary, "a", encoding="utf-8") as f:
            f.write("## Mixin target check\n\n")
            f.write(f"**{rep.fail} FAIL, {rep.warn} WARN**\n\n")
            bad = [r for r in rep.rows if r[0] != "OK"]
            if bad:
                f.write("| Level | Mixin | Problem | Detail |\n|---|---|---|---|\n")
                for lv, mx, what, det in bad:
                    esc = lambda s: s.replace("|", "\\|")
                    f.write(f"| {lv} | {mx} | {esc(what)} | {esc(det)} |\n")
            else:
                f.write("All Mixin targets resolve.\n")
    sys.exit(1 if rep.fail else 0)


if __name__ == "__main__":
    main()
