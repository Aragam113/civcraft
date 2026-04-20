"""Walk the Gradle cache, find every artifact whose POM is present but JAR is
missing, and print a browser-friendly download list: one direct URL per file
(Maven Central or Fabric Maven, chosen by group id).

Also emits a small shell script that the user can drop into a Downloads folder
so downloaded jars can be installed into the Gradle cache at the right SHA1
path Gradle expects (we reuse the SHA1 directory that already exists alongside
the POM; Gradle validates jar SHA1 against the checksum cache, so if SHA does
not match Gradle will redownload — that is OK, the downloaded jar just acts
as a local mirror source).
"""
import os
import re
from pathlib import Path

CACHE = Path(r"C:\Users\fajar\.gradle\caches\modules-2\files-2.1")

# Fabric-hosted groups. Everything else defaults to Maven Central.
FABRIC_GROUPS = {
    "net.fabricmc",
    "org.cadixdev",
}

MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
FABRIC_MAVEN = "https://maven.fabricmc.net"

# fabric-loom 1.10-SNAPSHOT uses a unique timestamped file inside the
# 1.10-SNAPSHOT directory. Hardcoded from previous resolution:
LOOM_TIMESTAMPED = "1.10-20250323.164030-5"


def coords_from_pom_path(p: Path):
    """cache path is: modules-2/files-2.1/<group>/<artifact>/<version>/<sha1>/file
    Strip trailing 2 dirs (sha1/file) and version dir."""
    rel = p.relative_to(CACHE)
    parts = rel.parts
    # parts: [group, artifact, version, sha1, filename]
    if len(parts) < 5:
        return None
    group, artifact, version = parts[0], parts[1], parts[2]
    return group, artifact, version


def build_url(group, artifact, version):
    group_slash = group.replace(".", "/")
    base = FABRIC_MAVEN if group in FABRIC_GROUPS else MAVEN_CENTRAL
    filename = f"{artifact}-{version}.jar"
    # Special case: fabric-loom SNAPSHOT has timestamped artifact name.
    if group == "net.fabricmc" and artifact == "fabric-loom" and version.endswith("-SNAPSHOT"):
        filename = f"{artifact}-{LOOM_TIMESTAMPED}.jar"
    return f"{base}/{group_slash}/{artifact}/{version}/{filename}"


def jar_sibling_exists(pom: Path):
    version_dir = pom.parent.parent
    artifact_dir = version_dir
    art = pom.stem  # e.g. gson-2.10.1
    for hash_dir in version_dir.iterdir():
        if not hash_dir.is_dir():
            continue
        if (hash_dir / f"{art}.jar").exists():
            return True
    return False


def main():
    missing = []
    for pom in CACHE.rglob("*.pom"):
        coords = coords_from_pom_path(pom)
        if coords is None:
            continue
        group, artifact, version = coords
        # Skip BOMs and parent POMs (have packaging=pom, no jar).
        art_lower = artifact.lower()
        if art_lower.endswith("-bom") or art_lower.endswith("-parent") \
           or artifact in ("oss-parent", "apache", "ow2", "commons-parent",
                            "guava-parent", "error_prone_parent", "junit-bom",
                            "gson-parent"):
            continue
        if jar_sibling_exists(pom):
            continue
        url = build_url(group, artifact, version)
        missing.append((group, artifact, version, url))

    missing.sort()
    print(f"# {len(missing)} jar(s) to download\n")
    for g, a, v, u in missing:
        print(u)

    # Also print a machine-readable table
    print("\n# Paste this mapping into tools/install_downloaded.sh after downloading:")
    for g, a, v, u in missing:
        filename = u.rsplit("/", 1)[1]
        print(f"#   {filename}  ->  {g}:{a}:{v}")


if __name__ == "__main__":
    main()
