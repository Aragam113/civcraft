"""Resolve Mojang-named Minecraft classes/methods/fields to Fabric intermediary
(`class_NNNN`, `method_NNNNN`, `field_NNNNN`) so we can use them via reflection
without needing a deobfuscated classpath at compile time.

Inputs:
  - Mojang ProGuard mappings (client.txt + server.txt) that pair
    `net.minecraft.foo.Bar` names with obfuscated short names.
  - Intermediary tiny-v1 mappings that pair obfuscated names with
    Fabric's stable `class_/method_/field_` runtime names.

Output: a JSON blob keyed by Mojang name with resolved intermediary values.
"""
import re
import json
import sys
from pathlib import Path

MAPPING_DIR = Path(r"C:\Users\fajar\AppData\Local\Temp\mj")
INTERMEDIARY_FILE = Path(r"C:\Users\fajar\AppData\Local\Temp\im\mappings\mappings.tiny")


def parse_proguard(path):
    """Return dict[named_class] = {obf_class, fields{}, methods[]}.

    Methods list because same name can have multiple overloads.
    """
    classes = {}
    current = None
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip()
            if not line or line.startswith("#"):
                continue
            if not line.startswith("    "):
                m = re.match(r"([\w.$]+) -> ([\w.$]+):", line)
                if m:
                    named, obf = m.group(1), m.group(2)
                    current = {"obf": obf, "fields": {}, "methods": []}
                    classes[named] = current
                continue
            body = line.strip()
            if "(" in body:
                # Method line: "[lineA:lineB:]RetType fqn.method(Params) -> obf"
                m = re.match(r"(?:\d+:\d+:)?([\w.$\[\]]+)\s+([\w.$<>]+)\(([^)]*)\)\s*->\s*(\S+)$", body)
                if m and current is not None:
                    ret, name, params, obf = m.group(1), m.group(2), m.group(3), m.group(4)
                    current["methods"].append({
                        "name": name, "obf": obf, "ret": ret, "params": params,
                    })
            else:
                # Field line: "Type name -> obf"
                m = re.match(r"([\w.$\[\]]+)\s+(\w+)\s*->\s*(\w+)$", body)
                if m and current is not None:
                    ftype, name, obf = m.group(1), m.group(2), m.group(3)
                    current["fields"][name] = {"obf": obf, "type": ftype}
    return classes


def parse_intermediary(path):
    cls = {}       # obf_slash -> intermediary
    fields = {}    # (obf_cls_slash, obf_field_name) -> intermediary
    methods = {}   # (obf_cls_slash, obf_method_name, obf_method_desc) -> intermediary
    with open(path, encoding="utf-8") as f:
        # Skip header
        next(f)
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if not parts:
                continue
            tag = parts[0]
            if tag == "CLASS" and len(parts) >= 3:
                cls[parts[1]] = parts[2]
            elif tag == "FIELD" and len(parts) >= 5:
                fields[(parts[1], parts[3])] = parts[4]
            elif tag == "METHOD" and len(parts) >= 5:
                methods[(parts[1], parts[3])] = parts[4]
                # Note: intermediary v1 skips descriptor disambiguation at lookup,
                # we rely on (owner_class, method_name) uniqueness in practice.
    return cls, fields, methods


def main():
    # Targets: what we need at runtime.
    # Fields: list of (named_class, field_name).
    # Methods: list of (named_class, method_name).
    targets_cls = [
        "net.minecraft.client.Minecraft",
        "net.minecraft.server.MinecraftServer",
        "net.minecraft.server.level.ServerLevel",
        "net.minecraft.server.level.ServerPlayer",
        "net.minecraft.world.entity.Entity",
        "net.minecraft.world.entity.EntityType",
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.LivingEntity",
        "net.minecraft.world.entity.npc.Villager",
        "net.minecraft.world.entity.npc.VillagerData",
        "net.minecraft.world.entity.npc.VillagerProfession",
        "net.minecraft.world.entity.npc.VillagerType",
        "net.minecraft.core.BlockPos",
        "net.minecraft.commands.CommandSourceStack",
        "net.minecraft.commands.Commands",
        "net.minecraft.network.chat.Component",
        "net.minecraft.world.phys.Vec3",
    ]
    targets_fields = [
        ("net.minecraft.world.entity.EntityType", "VILLAGER"),
        ("net.minecraft.world.entity.npc.VillagerProfession", "NITWIT"),
        ("net.minecraft.world.entity.npc.VillagerProfession", "NONE"),
        ("net.minecraft.world.entity.npc.VillagerType", "PLAINS"),
    ]
    targets_methods = [
        ("net.minecraft.client.Minecraft", "getInstance"),
        ("net.minecraft.server.MinecraftServer", "getPlayerList"),
        ("net.minecraft.world.entity.EntityType", "create"),
        ("net.minecraft.world.entity.EntityType", "spawn"),
        ("net.minecraft.world.entity.Entity", "setPos"),
        ("net.minecraft.world.entity.Entity", "setCustomName"),
        ("net.minecraft.world.entity.Entity", "setCustomNameVisible"),
        ("net.minecraft.world.entity.npc.Villager", "setVillagerData"),
        ("net.minecraft.server.level.ServerPlayer", "getX"),
        ("net.minecraft.server.level.ServerPlayer", "getY"),
        ("net.minecraft.server.level.ServerPlayer", "getZ"),
        ("net.minecraft.server.level.ServerPlayer", "level"),
        ("net.minecraft.commands.CommandSourceStack", "getPlayerOrException"),
        ("net.minecraft.commands.CommandSourceStack", "sendSuccess"),
        ("net.minecraft.network.chat.Component", "literal"),
        ("net.minecraft.world.phys.Vec3", "x"),
        ("net.minecraft.world.phys.Vec3", "y"),
        ("net.minecraft.world.phys.Vec3", "z"),
    ]

    print("Parsing client.txt ...", file=sys.stderr)
    client_pg = parse_proguard(MAPPING_DIR / "client.txt")
    print("Parsing server.txt ...", file=sys.stderr)
    server_pg = parse_proguard(MAPPING_DIR / "server.txt")
    # Merge (client wins on conflict)
    combined = dict(server_pg)
    combined.update(client_pg)

    print("Parsing intermediary ...", file=sys.stderr)
    cls_map, field_map, method_map = parse_intermediary(INTERMEDIARY_FILE)

    out = {"classes": {}, "fields": {}, "methods": {}}

    for named in targets_cls:
        info = combined.get(named)
        if not info:
            out["classes"][named] = {"error": "not_found_in_proguard"}
            continue
        obf_slash = info["obf"].replace(".", "/")
        inter = cls_map.get(obf_slash)
        out["classes"][named] = {
            "obf": info["obf"],
            "intermediary": inter,
        }

    for named_cls, field_name in targets_fields:
        info = combined.get(named_cls)
        if not info:
            out["fields"][f"{named_cls}.{field_name}"] = {"error": "class_not_found"}
            continue
        fld = info["fields"].get(field_name)
        if not fld:
            out["fields"][f"{named_cls}.{field_name}"] = {"error": "field_not_found_in_proguard"}
            continue
        obf_slash = info["obf"].replace(".", "/")
        inter = field_map.get((obf_slash, fld["obf"]))
        out["fields"][f"{named_cls}.{field_name}"] = {
            "obf_class": info["obf"], "obf_field": fld["obf"], "intermediary": inter,
        }

    for named_cls, method_name in targets_methods:
        info = combined.get(named_cls)
        if not info:
            out["methods"][f"{named_cls}.{method_name}"] = {"error": "class_not_found"}
            continue
        matches = [m for m in info["methods"] if m["name"] == method_name]
        if not matches:
            out["methods"][f"{named_cls}.{method_name}"] = {"error": "method_not_found_in_proguard"}
            continue
        obf_slash = info["obf"].replace(".", "/")
        results = []
        for m in matches:
            inter = method_map.get((obf_slash, m["obf"]))
            results.append({
                "obf": m["obf"], "ret": m["ret"], "params": m["params"], "intermediary": inter,
            })
        out["methods"][f"{named_cls}.{method_name}"] = results

    json.dump(out, sys.stdout, indent=2, ensure_ascii=False)


if __name__ == "__main__":
    main()
