"""Download a single HTTP(S) URL in chunks of roughly 15 KB using Range
requests, writing the concatenated result to an output path.

DPI filtering observed here truncates large responses at ~20 KB per TCP
connection, but Range requests under that threshold arrive whole. Splitting
every download into sub-20 KB windows consistently bypasses the filter.
"""
import os
import sys
import urllib.request
import urllib.error

CHUNK = 15000  # bytes; stays under the DPI cut-off


def head_content_length(url):
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req, timeout=15) as r:
        cl = r.headers.get("Content-Length")
        return int(cl) if cl else None


def fetch_range(url, start, end, attempts=5):
    last_err = None
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url)
            req.add_header("Range", f"bytes={start}-{end}")
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except Exception as e:
            last_err = e
    raise last_err


def download(url, out_path):
    total = head_content_length(url)
    if total is None:
        raise RuntimeError(f"No Content-Length for {url}")
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    written = 0
    with open(out_path, "wb") as f:
        while written < total:
            end = min(written + CHUNK - 1, total - 1)
            data = fetch_range(url, written, end)
            if not data:
                raise RuntimeError(f"empty chunk at offset {written}")
            f.write(data)
            written += len(data)
    if written != total:
        raise RuntimeError(f"size mismatch: got {written}, expected {total}")
    return total


def main():
    if len(sys.argv) < 3:
        print("usage: chunked_download.py <url> <out_path>", file=sys.stderr)
        sys.exit(2)
    url, out_path = sys.argv[1], sys.argv[2]
    try:
        size = download(url, out_path)
        print(f"ok {size}B  {url} -> {out_path}")
    except Exception as e:
        print(f"FAILED {url}: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
