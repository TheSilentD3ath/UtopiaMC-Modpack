#!/usr/bin/env python3
"""Report recipe-backed dependencies between Utopia Create unlock nodes.

The report deliberately separates item ingredients from processing machines.
It reads recipe JSON directly from the installed mod JARs and never modifies
the instance.
"""

from __future__ import annotations

import argparse
import json
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


PROCESSING_MACHINES = {
    "create:compacting": ("create:basin", "create:mechanical_press"),
    "create:crushing": ("create:crushing_wheel",),
    "create:cutting": ("create:mechanical_saw",),
    "create:deploying": ("create:deployer",),
    "create:filling": ("create:spout",),
    "create:mechanical_crafting": ("create:mechanical_crafter",),
    "create:milling": ("create:millstone",),
    "create:mixing": ("create:basin", "create:mechanical_mixer"),
    "create:pressing": ("create:mechanical_press",),
    "create:sandpaper_polishing": ("create:sand_paper",),
}

IGNORED_INGREDIENT_KEYS = {
    "result",
    "results",
    "transitionalItem",
    "transitional_item",
}


def item_ids(value: Any, *, ignore_outputs: bool = False) -> Iterable[str]:
    if isinstance(value, list):
        for child in value:
            yield from item_ids(child, ignore_outputs=ignore_outputs)
        return
    if not isinstance(value, dict):
        return
    for key, child in value.items():
        if ignore_outputs and key in IGNORED_INGREDIENT_KEYS:
            continue
        if key == "item" and isinstance(child, str):
            yield child
        else:
            yield from item_ids(child, ignore_outputs=ignore_outputs)


def recipe_outputs(recipe: dict[str, Any]) -> set[str]:
    outputs: set[str] = set()
    for key in ("result", "results", "output", "outputs"):
        value = recipe.get(key)
        if isinstance(value, str):
            outputs.add(value)
        else:
            outputs.update(item_ids(value))
    return outputs


def recipe_types(value: Any) -> Iterable[str]:
    if isinstance(value, list):
        for child in value:
            yield from recipe_types(child)
        return
    if not isinstance(value, dict):
        return
    recipe_type = value.get("type")
    if isinstance(recipe_type, str):
        yield recipe_type
    for child in value.values():
        yield from recipe_types(child)


def load_recipes(
    mod_dir: Path, recipe_namespace: str
) -> dict[str, list[tuple[str, dict[str, Any]]]]:
    recipes_by_output: dict[str, list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    for jar_path in sorted(mod_dir.glob("*.jar")):
        if jar_path.name.endswith(".jar.disabled"):
            continue
        try:
            with zipfile.ZipFile(jar_path) as jar:
                for entry in jar.infolist():
                    recipe_root = f"data/{recipe_namespace}/recipes/"
                    if not entry.filename.startswith(recipe_root):
                        continue
                    if not entry.filename.endswith(".json"):
                        continue
                    try:
                        recipe = json.loads(jar.read(entry))
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        continue
                    if not isinstance(recipe, dict):
                        continue
                    source = f"{jar_path.name}!/{entry.filename}"
                    for output in recipe_outputs(recipe):
                        recipes_by_output[output].append((source, recipe))
        except zipfile.BadZipFile:
            continue
    return recipes_by_output


def ancestor_nodes(nodes: dict[str, dict[str, Any]], node_id: str) -> set[str]:
    """Return every transitive parent and reject cycles while traversing."""
    ancestors: set[str] = set()
    pending = list(nodes[node_id].get("parents", []))
    while pending:
        parent = pending.pop()
        if parent == node_id:
            raise ValueError(f"cycle reaches {node_id}")
        if parent in ancestors:
            continue
        if parent not in nodes:
            raise ValueError(f"{node_id} references missing parent {parent}")
        ancestors.add(parent)
        pending.extend(nodes[parent].get("parents", []))
    return ancestors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tree", type=Path)
    parser.add_argument("mod_dir", type=Path)
    parser.add_argument("--namespace", default="create")
    parser.add_argument("--recipe-namespace")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    tree = json.loads(args.tree.read_text(encoding="utf-8"))
    nodes: dict[str, dict[str, Any]] = tree["nodes"]
    owner: dict[str, str] = {}
    for node_id, node in nodes.items():
        for unlock in node.get("unlocks", []):
            if not unlock.endswith(":*") and not unlock.startswith("#"):
                owner[unlock] = node_id

    ancestors = {node_id: ancestor_nodes(nodes, node_id) for node_id in nodes}
    violations = 0

    recipes_by_output = load_recipes(
        args.mod_dir, args.recipe_namespace or args.namespace
    )
    for node_id, node in nodes.items():
        unlocks = [
            unlock
            for unlock in node.get("unlocks", [])
            if unlock.startswith(f"{args.namespace}:") and not unlock.endswith(":*")
        ]
        if not unlocks:
            continue
        print(f"\n## {node_id} <- {', '.join(node.get('parents', [])) or '(root)'}")
        for unlock in unlocks:
            recipes = recipes_by_output.get(unlock, [])
            if not recipes:
                print(f"{unlock}\tNO_RECIPE")
                continue
            options: set[tuple[str, ...]] = set()
            for _, recipe in recipes:
                requirements = set(item_ids(recipe, ignore_outputs=True))
                for recipe_type in recipe_types(recipe):
                    requirements.update(PROCESSING_MACHINES.get(recipe_type, ()))
                if recipe.get("heatRequirement") in {"heated", "superheated"}:
                    requirements.add("create:blaze_burner")
                dependency_nodes = sorted(
                    {
                        owner[item]
                        for item in requirements
                        if item in owner and owner[item] != node_id
                    }
                )
                options.add(tuple(dependency_nodes))
            rendered = " OR ".join(
                "+".join(option) if option else "vanilla/current-node"
                for option in sorted(options, key=lambda option: (len(option), option))
            )
            missing_path = not any(
                set(option).issubset(ancestors[node_id]) for option in options
            )
            if missing_path:
                violations += 1
            suffix = "\tMISSING_ANCESTOR_PATH" if missing_path else ""
            print(f"{unlock}\t{rendered}{suffix}")
    print(f"\nAUDIT namespace={args.namespace} missing_ancestor_paths={violations}")
    return 1 if args.strict and violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
