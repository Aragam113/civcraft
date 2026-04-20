"""Assemble a local Maven repo out of:
  - jars the user downloaded manually into civcraft-deps/
  - poms already present in the Gradle cache (from earlier failed builds)

The resulting directory matches standard Maven2 layout:
  <repo>/<group_slash>/<artifact>/<version>/<artifact>-<version>.{jar,pom}

For the fabric-loom 1.10-SNAPSHOT artifact we also write a timestamped jar/pom
pair and a maven-metadata.xml so Gradle's snapshot resolver can locate it.
"""
import shutil
from pathlib import Path

CACHE = Path(r"C:\Users\fajar\.gradle\caches\modules-2\files-2.1")
DOWNLOADS_DIRS = [
    Path(r"C:\Users\fajar\Downloads\civcraft-deps"),
    Path(r"C:\Users\fajar\Downloads"),
]
REPO = Path(r"C:\Users\fajar\dev\mc-mods\civcraft\local-repo")

LOOM_TIMESTAMPED = "1.10-20250323.164030-5"

# filename_in_downloads -> (group, artifact, version)
MAPPING = {
    # --- Loom 1.10 baseline (kept so older gradle configs still resolve) ---
    "jsr305-3.0.2.jar": ("com.google.code.findbugs", "jsr305", "3.0.2"),
    "gson-2.10.1.jar": ("com.google.code.gson", "gson", "2.10.1"),
    "error_prone_annotations-2.23.0.jar": ("com.google.errorprone", "error_prone_annotations", "2.23.0"),
    "failureaccess-1.0.2.jar": ("com.google.guava", "failureaccess", "1.0.2"),
    "guava-33.0.0-jre.jar": ("com.google.guava", "guava", "33.0.0-jre"),
    "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar":
        ("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
    "commons-io-2.15.1.jar": ("commons-io", "commons-io", "2.15.1"),
    "access-widener-2.1.0.jar": ("net.fabricmc", "access-widener", "2.1.0"),
    "fabric-loom-1.10-20250323.164030-5.jar": ("net.fabricmc", "fabric-loom", "1.10-SNAPSHOT"),
    "fabric-loom-native-0.2.0.jar": ("net.fabricmc", "fabric-loom-native", "0.2.0"),
    "mapping-io-0.7.1.jar": ("net.fabricmc", "mapping-io", "0.7.1"),
    "mercury-0.4.2.jar": ("net.fabricmc", "mercury", "0.4.2"),
    "stitch-0.6.2.jar": ("net.fabricmc", "stitch", "0.6.2"),
    "tiny-mappings-parser-0.3.0+build.17.jar": ("net.fabricmc", "tiny-mappings-parser", "0.3.0+build.17"),
    "tiny-remapper-0.11.1.jar": ("net.fabricmc", "tiny-remapper", "0.11.1"),
    "at-0.1.0-rc1.jar": ("org.cadixdev", "at", "0.1.0-rc1"),
    "bombe-0.3.4.jar": ("org.cadixdev", "bombe", "0.3.4"),
    "lorenz-0.5.7.jar": ("org.cadixdev", "lorenz", "0.5.7"),
    "checker-qual-3.41.0.jar": ("org.checkerframework", "checker-qual", "3.41.0"),
    "kotlin-metadata-jvm-2.0.21.jar": ("org.jetbrains.kotlin", "kotlin-metadata-jvm", "2.0.21"),
    "asm-9.7.1.jar": ("org.ow2.asm", "asm", "9.7.1"),
    "asm-analysis-9.7.1.jar": ("org.ow2.asm", "asm-analysis", "9.7.1"),
    "asm-commons-9.7.1.jar": ("org.ow2.asm", "asm-commons", "9.7.1"),
    "asm-tree-9.7.1.jar": ("org.ow2.asm", "asm-tree", "9.7.1"),
    "asm-util-9.7.1.jar": ("org.ow2.asm", "asm-util", "9.7.1"),

    # --- Loom 1.13.3 upgrade (required by fabric-api 0.141.3+1.21.11) ---
    "fabric-loom-1.13.3.jar": ("net.fabricmc", "fabric-loom", "1.13.3"),
    "tiny-remapper-0.12.0.jar": ("net.fabricmc", "tiny-remapper", "0.12.0"),
    "class-tweaker-0.1.1.jar": ("net.fabricmc", "class-tweaker", "0.1.1"),
    "mapping-io-0.8.0.jar": ("net.fabricmc", "mapping-io", "0.8.0"),
    "mercury-0.4.3.jar": ("net.fabricmc", "mercury", "0.4.3"),
    "mercurymixin-0.2.2.jar": ("net.fabricmc", "mercurymixin", "0.2.2"),
    "unpick-3.0.0-beta.13.jar": ("net.fabricmc.unpick", "unpick", "3.0.0-beta.13"),
    "unpick-format-utils-3.0.0-beta.13.jar": ("net.fabricmc.unpick", "unpick-format-utils", "3.0.0-beta.13"),
    "asm-9.9.jar": ("org.ow2.asm", "asm", "9.9"),
    "asm-analysis-9.9.jar": ("org.ow2.asm", "asm-analysis", "9.9"),
    "asm-commons-9.9.jar": ("org.ow2.asm", "asm-commons", "9.9"),
    "asm-tree-9.9.jar": ("org.ow2.asm", "asm-tree", "9.9"),
    "asm-util-9.9.jar": ("org.ow2.asm", "asm-util", "9.9"),
}


def find_download(fname):
    for d in DOWNLOADS_DIRS:
        p = d / fname
        if p.exists():
            return p
    return None


def find_pom_in_cache(group: str, artifact: str, version: str) -> Path | None:
    base = CACHE / group / artifact / version
    if not base.exists():
        return None
    for sha_dir in base.iterdir():
        if not sha_dir.is_dir():
            continue
        pom = sha_dir / f"{artifact}-{version}.pom"
        if pom.exists():
            return pom
    return None


def place(group: str, artifact: str, version: str, jar_path: Path):
    target_dir = REPO / group.replace(".", "/") / artifact / version
    target_dir.mkdir(parents=True, exist_ok=True)

    # Copy jar
    shutil.copy2(jar_path, target_dir / f"{artifact}-{version}.jar")

    # Copy pom from Gradle cache if present
    pom = find_pom_in_cache(group, artifact, version)
    if pom is not None:
        shutil.copy2(pom, target_dir / f"{artifact}-{version}.pom")
    else:
        # Minimal synthetic POM (enough for Maven resolvers to accept)
        synth = (
            f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
            f'  <modelVersion>4.0.0</modelVersion>\n'
            f'  <groupId>{group}</groupId>\n'
            f'  <artifactId>{artifact}</artifactId>\n'
            f'  <version>{version}</version>\n'
            f'</project>\n'
        )
        (target_dir / f"{artifact}-{version}.pom").write_text(synth, encoding="utf-8")

    # Special: fabric-loom SNAPSHOT requires timestamped names + maven-metadata.xml
    if group == "net.fabricmc" and artifact == "fabric-loom" and version.endswith("-SNAPSHOT"):
        ts_jar = target_dir / f"{artifact}-{LOOM_TIMESTAMPED}.jar"
        ts_pom = target_dir / f"{artifact}-{LOOM_TIMESTAMPED}.pom"
        shutil.copy2(jar_path, ts_jar)
        shutil.copy2(target_dir / f"{artifact}-{version}.pom", ts_pom)

        metadata = f"""<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <versioning>
    <snapshot>
      <timestamp>20250323.164030</timestamp>
      <buildNumber>5</buildNumber>
    </snapshot>
    <lastUpdated>20250323164030</lastUpdated>
    <snapshotVersions>
      <snapshotVersion>
        <extension>jar</extension>
        <value>{LOOM_TIMESTAMPED}</value>
        <updated>20250323164030</updated>
      </snapshotVersion>
      <snapshotVersion>
        <extension>pom</extension>
        <value>{LOOM_TIMESTAMPED}</value>
        <updated>20250323164030</updated>
      </snapshotVersion>
    </snapshotVersions>
  </versioning>
</metadata>
"""
        (target_dir / "maven-metadata.xml").write_text(metadata, encoding="utf-8")


def main():
    if REPO.exists():
        shutil.rmtree(REPO)
    REPO.mkdir(parents=True)

    missing_downloads = []
    placed = 0
    for fname, (g, a, v) in MAPPING.items():
        src = find_download(fname)
        if src is None or src.stat().st_size == 0:
            missing_downloads.append(fname)
            continue
        place(g, a, v, src)
        placed += 1

    print(f"placed {placed} artifacts into {REPO}")
    if missing_downloads:
        print("MISSING DOWNLOADS:")
        for m in missing_downloads:
            print(f"  {m}")


if __name__ == "__main__":
    main()
