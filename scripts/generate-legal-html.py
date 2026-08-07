#!/usr/bin/env python3
"""
Generates the public and bundled HTML legal documents from the Markdown sources.

The Markdown files in `legal/` are the single source of truth. The public pages and the offline
copies bundled in the APK are generated from them, so the two can never drift apart — a drift the
release gate would otherwise have to detect after the fact.

Outputs:
    legal/public/privacy-policy.html          public page, permanent URL
    legal/public/terms-of-service.html        public page, permanent URL
    app/src/main/assets/legal/privacy-policy-v<v>.html   offline copy shipped in the APK
    app/src/main/assets/legal/terms-v<v>.html            offline copy shipped in the APK
    app/src/main/assets/legal/metadata.json              versions and effective dates

No external CSS, no fonts, no JavaScript: the pages must render with no network access, and the
bundled copies must contain nothing that could execute.

Usage:
    python3 scripts/generate-legal-html.py
"""

from __future__ import annotations

import html
import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
LEGAL = REPO / "legal"
PUBLIC = LEGAL / "public"
ASSETS = REPO / "app" / "src" / "main" / "assets" / "legal"

PAGE_CSS = """
:root { color-scheme: light dark; --fg:#1a1a1a; --bg:#ffffff; --muted:#5b5b5b;
        --rule:#e2e2e2; --accent:#2b5c8a; --code:#f4f4f5; }
@media (prefers-color-scheme: dark) {
  :root { --fg:#e8e8e8; --bg:#121212; --muted:#a0a0a0; --rule:#2e2e2e;
          --accent:#7fb3e0; --code:#1e1e1e; }
}
* { box-sizing: border-box; }
body { margin:0; padding:2rem 1.25rem 5rem; background:var(--bg); color:var(--fg);
       font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
       max-width:44rem; margin-inline:auto; overflow-wrap:break-word; }
h1 { font-size:1.85rem; line-height:1.25; margin:0 0 .5rem; }
h2 { font-size:1.3rem; margin:2.5rem 0 .75rem; padding-top:.75rem;
     border-top:1px solid var(--rule); }
h3 { font-size:1.05rem; margin:1.75rem 0 .5rem; }
p, li { color:var(--fg); }
ul, ol { padding-left:1.35rem; }
li { margin:.3rem 0; }
a { color:var(--accent); }
hr { border:0; border-top:1px solid var(--rule); margin:2rem 0; }
blockquote { margin:1.25rem 0; padding:.75rem 1rem; border-left:3px solid var(--rule);
             color:var(--muted); background:var(--code); border-radius:0 6px 6px 0; }
code { background:var(--code); padding:.1rem .35rem; border-radius:4px; font-size:.9em; }
.meta { color:var(--muted); font-size:.95rem; margin:0 0 2rem; }
.table-scroll { overflow-x:auto; margin:1.25rem 0; }
table { border-collapse:collapse; width:100%; min-width:22rem; font-size:.95rem; }
th, td { border:1px solid var(--rule); padding:.5rem .65rem; text-align:left; vertical-align:top; }
th { background:var(--code); font-weight:600; }
strong { font-weight:650; }
@media print { body { max-width:none; padding:0; } h2 { break-after:avoid; } }
""".strip()


def inline(text: str) -> str:
    """Escapes, then re-applies the small Markdown subset the documents actually use."""
    out = html.escape(text, quote=False)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    # Links: only http(s) and relative .md/.html targets. Anything else stays literal text, so a
    # javascript: or data: URI can never survive into a rendered page.
    def link(m: re.Match) -> str:
        label, target = m.group(1), m.group(2)
        if re.match(r"^(https?://|[\w./-]+\.(md|html))", target):
            safe = target[:-3] + ".html" if target.endswith(".md") else target
            return f'<a href="{html.escape(safe, quote=True)}">{label}</a>'
        return f"{label} ({html.escape(target)})"

    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, out)


def render_table(rows: list[str]) -> str:
    cells = [[c.strip() for c in r.strip().strip("|").split("|")] for r in rows]
    if len(cells) >= 2 and all(set(c) <= set("-: ") for c in cells[1]):
        head, body = cells[0], cells[2:]
    else:
        head, body = None, cells
    parts = ['<div class="table-scroll"><table>']
    if head:
        parts.append("<tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr>")
    for row in body:
        parts.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
    parts.append("</table></div>")
    return "".join(parts)


def to_html(md: str) -> str:
    lines = md.split("\n")
    out: list[str] = []
    i = 0
    list_open: str | None = None

    def close_list() -> None:
        nonlocal list_open
        if list_open:
            out.append(f"</{list_open}>")
            list_open = None

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            close_list()
            i += 1
            continue

        if stripped.startswith("|"):
            close_list()
            block = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                block.append(lines[i])
                i += 1
            out.append(render_table(block))
            continue

        if stripped.startswith("#"):
            close_list()
            level = len(stripped) - len(stripped.lstrip("#"))
            out.append(f"<h{level}>{inline(stripped[level:].strip())}</h{level}>")
            i += 1
            continue

        if stripped.startswith(">"):
            close_list()
            block = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                block.append(lines[i].strip().lstrip(">").strip())
                i += 1
            out.append(f"<blockquote><p>{inline(' '.join(block))}</p></blockquote>")
            continue

        if stripped in ("---", "***", "___"):
            close_list()
            out.append("<hr>")
            i += 1
            continue

        bullet = re.match(r"^[-*]\s+(.*)$", stripped)
        numbered = re.match(r"^\d+\.\s+(.*)$", stripped)
        if bullet or numbered:
            want = "ul" if bullet else "ol"
            if list_open != want:
                close_list()
                out.append(f"<{want}>")
                list_open = want
            out.append(f"<li>{inline((bullet or numbered).group(1))}</li>")
            i += 1
            continue

        close_list()
        para = [stripped]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(
            r"^\s*([-*]\s|\d+\.\s|#|\||>|---)", lines[i]
        ):
            para.append(lines[i].strip())
            i += 1
        out.append(f"<p>{inline(' '.join(para))}</p>")

    close_list()
    return "\n".join(out)


def page(title: str, body: str) -> str:
    return (
        "<!doctype html>\n"
        '<html lang="en">\n<head>\n'
        '<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<meta name="robots" content="index, follow">\n'
        '<link rel="icon" href="/favicon.svg" type="image/svg+xml">\n'
        f"<title>{html.escape(title)} — TrueVault</title>\n"
        f"<style>\n{PAGE_CSS}\n</style>\n"
        "</head>\n<body>\n"
        f"{body}\n"
        "</body>\n</html>\n"
    )


def read_version(md: str) -> tuple[str, str]:
    version = re.search(r"^\*\*Version:\*\*\s*(.+)$", md, re.M)
    effective = re.search(r"^\*\*Effective date:\*\*\s*(.+)$", md, re.M)
    return (
        version.group(1).strip() if version else "0.0",
        effective.group(1).strip() if effective else "",
    )


def main() -> int:
    sources = {
        "privacy-policy": ("Privacy Policy", LEGAL / "privacy-policy.md"),
        "terms-of-service": ("Terms of Service", LEGAL / "terms-of-service.md"),
    }

    missing = [str(p) for _, p in sources.values() if not p.exists()]
    if missing:
        print("FATAL: missing source documents: " + ", ".join(missing), file=sys.stderr)
        return 2

    PUBLIC.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)

    metadata: dict[str, object] = {}

    for slug, (title, path) in sources.items():
        md = path.read_text(encoding="utf-8")
        version, effective = read_version(md)
        rendered = page(title, to_html(md))

        (PUBLIC / f"{slug}.html").write_text(rendered, encoding="utf-8")

        asset_name = "terms" if slug == "terms-of-service" else slug
        (ASSETS / f"{asset_name}-v{version}.html").write_text(rendered, encoding="utf-8")

        # The Markdown goes into the APK too, and it is what the in-app reader actually renders.
        # The app parses this into structured Compose text; the HTML copy above exists for the
        # export/print path and for anyone opening the file outside the app. Rendering the HTML in a
        # WebView instead would put a browser engine in front of a document the user is being asked
        # to rely on, for no gain.
        (ASSETS / f"{asset_name}-v{version}.md").write_text(md, encoding="utf-8")

        key = "terms" if slug == "terms-of-service" else "privacy"
        metadata[f"{key}Version"] = version
        metadata[f"{key}EffectiveDate"] = effective

        print(f"generated {slug} v{version}")

    # requiresReacceptance is a human decision recorded in legal-config.json, never inferred here.
    config = json.loads((LEGAL / "legal-config.json").read_text(encoding="utf-8"))
    metadata["requiresReacceptance"] = config["documents"]["requiresReacceptance"]
    metadata["acceptanceFlowVersion"] = config["documents"]["acceptanceFlowVersion"]

    (ASSETS / "metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(f"wrote {ASSETS / 'metadata.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
