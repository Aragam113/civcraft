"""Extract nested Fabric API submodule jars from the bundled distribution and
lay them out inside local-repo as proper Maven artifacts so Gradle+Loom can
resolve each one as net.fabricmc.fabric-api:<name>:<version>.
"""
import re
import shutil
import sys
import zipfile
from pathlib import Path

BUNDLE = Path(r"C:\Users\fajar\Downloads\civcraft-deps\fabric-api-0.141.3+1.21.11.jar")
REPO = Path(r"C:\Users\fajar\dev\mc-mods\civcraft\local-repo")
PARENT_VERSION = "0.141.3+1.21.11"


def write_pom(group, artifact, version, deps=()):
    out_dir = REPO / group.replace(".", "/") / artifact / version
    out_dir.mkdir(parents=True, exist_ok=True)
    pom = out_dir / f"{artifact}-{version}.pom"
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<project xmlns="http://maven.apache.org/POM/4.0.0">',
        '  <modelVersion>4.0.0</modelVersion>',
        f'  <groupId>{group}</groupId>',
        f'  <artifactId>{artifact}</artifactId>',
        f'  <version>{version}</version>',
    ]
    if deps:
        lines.append("  <dependencies>")
        for g, a, v in deps:
            lines += [
                "    <dependency>",
                f"      <groupId>{g}</groupId>",
                f"      <artifactId>{a}</artifactId>",
                f"      <version>{v}</version>",
                "    </dependency>",
            ]
        lines.append("  </dependencies>")
    lines.append("</project>")
    pom.write_text("\n".join(lines), encoding="utf-8")
    return pom


def main():
    # filename like fabric-api-base-1.0.5+4ebb5c083e.jar
    # Split into: artifact = everything before the last "-<version>.jar"
    pat = re.compile(r"^(?P<artifact>.+?)-(?P<version>[0-9][\w\.\+]*)\.jar$")

    submodules = []  # (artifact, version)
    with zipfile.ZipFile(BUNDLE) as z:
        for name in z.namelist():
            if not name.startswith("META-INF/jars/") or not name.endswith(".jar"):
                continue
            basename = Path(name).name
            m = pat.match(basename)
            if not m:
                print(f"skip (no match): {basename}")
                continue
            artifact = m.group("artifact")
            version = m.group("version")
            group = "net.fabricmc.fabric-api"
            out_dir = REPO / group.replace(".", "/") / artifact / version
            out_dir.mkdir(parents=True, exist_ok=True)
            dest = out_dir / f"{artifact}-{version}.jar"
            with z.open(name) as src, open(dest, "wb") as dst:
                shutil.copyfileobj(src, dst)
            write_pom(group, artifact, version)
            submodules.append((artifact, version))
            print(f"extracted {artifact}:{version}")

    # Put the bundled jar itself at the parent coordinate so build.gradle can
    # request fabric-api:0.141.3+1.21.11, with a POM that pulls in every submodule.
    parent_dir = REPO / "net/fabricmc/fabric-api/fabric-api" / PARENT_VERSION
    parent_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(BUNDLE, parent_dir / f"fabric-api-{PARENT_VERSION}.jar")
    deps = [("net.fabricmc.fabric-api", a, v) for a, v in submodules]
    write_pom("net.fabricmc.fabric-api", "fabric-api", PARENT_VERSION, deps)
    print(f"\nwrote parent pom with {len(deps)} submodule deps")


if __name__ == "__main__":
    main()
