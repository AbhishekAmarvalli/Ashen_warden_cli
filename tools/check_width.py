"""Width audit for ASHEN WARDEN console output.

Usage:  python tools/check_width.py <width> < captured-output.txt

Strips ANSI escape sequences and CR, then prints every line whose visible
length exceeds <width>, plus a summary. Exit code 1 when any line overflows.
"""
import re
import sys

# print box-drawing characters even on a Windows cp1252 console
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
PROMPT = re.compile(r"^(?:> |❯ )")


def main() -> int:
    if len(sys.argv) != 2 or not sys.argv[1].isdigit():
        print(__doc__)
        return 2
    limit = int(sys.argv[1])
    data = sys.stdin.buffer.read().decode("utf-8", errors="replace")
    lines = data.replace("\r\n", "\n").split("\n")
    # With piped input the prompt is printed but the typed command never is, so
    # the next output line starts on the prompt's own line; drop that artifact.
    stripped = [ANSI.sub("", ln) for ln in lines]
    bad = [(i + 1, PROMPT.sub("", ln, count=1)) for i, ln in enumerate(stripped)]
    bad = [(n, ln) for n, ln in bad if len(ln) > limit]
    for n, ln in bad[:40]:
        print(f"{n}: ({len(ln)}) {ln}")
    print(f"--- {len(bad)} line(s) wider than {limit} columns "
          f"out of {len(lines)}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
