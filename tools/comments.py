"""Checks Java comments: each comment is one sentence and says what the code does now, not what it was.

Usage: python3 tools/comments.py FILE...  (tools/lint.sh passes all tracked Java files)
A Javadoc tag (@param, @return, ...) is its own sentence. Inactive Stonecutter code in comments is skipped.
"""
import re
import sys

HISTORY = re.compile(r"\b(previously|formerly|used to (be|do|call|reload|throw)|was replaced|replaces the old|"
                     r"the old (code|version|way|implementation|flag|sentinel)|before this change|"
                     r"no longer (does|uses|needs|calls|reloads|throws|returns|has)|"
                     r"now (returns|uses|takes|reloads|throws))\b", re.I)
# Dots that do not end a sentence: abbreviations, versions, file names, ellipses.
NOT_AN_END = re.compile(r"\b(e\.g|i\.e|etc|vs|approx)\.|\.\.\.|\d\.\d|\w\.\w")
SENTENCE_END = re.compile(r"[.!?][)\"']?\s+[A-Z\"(]")


def comments(text):
    """Yields (line, text) for each comment: a block comment, or a run of // lines."""
    i, n, line = 0, len(text), 1
    run, run_line = [], 0
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            body = text[i + 2:end]
            if body.startswith("?"):  # a Stonecutter marker
                i = end
                continue
            if run and run_line + len(run) == line:
                run.append(body.strip())
            else:
                if run:
                    yield run_line, " ".join(run)
                run, run_line = [body.strip()], line
            i = end
            continue
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end
            body = text[i + 2:end]
            stonecutter = text.startswith("*///?", end) or body.startswith("?")
            if not stonecutter:
                yield line, " ".join(l.strip().lstrip("*").strip() for l in body.lstrip("*").splitlines())
            line += body.count("\n")
            i = end + 2
            continue
        if c in "\"'":
            j = i + 1
            while j < n and text[j] != c and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        i += 1
    if run:
        yield run_line, " ".join(run)


def parts(comment):
    """The main text and each tag's text, each of which must be one sentence."""
    comment = re.sub(r"\{@\w+\s*([^}]*)\}", r"\1", comment)
    chunks = re.split(r"(?:^|\s)@(?:param|return|throws|exception|see|deprecated|since)\b", comment)
    return [c.strip() for c in chunks if c.strip()]


def check(path):
    bad = 0
    text = open(path, encoding="utf-8").read()
    for line, comment in comments(text):
        if HISTORY.search(comment):
            print("%s:%d: error: a comment says what the code does, not what it was" % (path, line))
            bad = 1
        for part in parts(comment):
            if len(SENTENCE_END.findall(NOT_AN_END.sub("", part))) > 0:
                print("%s:%d: error: a comment has one sentence: %s" % (path, line, part[:90]))
                bad = 1
                break
    return bad


def main(paths):
    return max([check(p) for p in paths] + [0])


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
