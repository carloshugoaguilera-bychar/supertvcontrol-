#!/usr/bin/env python3
"""
Convierte la carpeta TVs de Flipper-IRDB (archivos .ir) en JSON por marca.

Uso:
    python3 -I tools/build_brands.py <carpeta_TVs> <carpeta_salida>

Salida:
    <salida>/index.json        -> lista corta de marcas: [{"n": "LG", "s": "lg", "c": 120}]
    <salida>/<slug>.json       -> {"brand": "LG", "profiles": [{"n": 1, "m": "modelo", "b": {...}}]}

Cada perfil tiene un numero (n) dentro de su marca: ese es el "numero de codigo".
Los codigos mas repetidos entre archivos (los mas comunes) van primero.
Cada boton es una senal: ["NEC", direccion, comando] o ["RAW", frecuencia, [duraciones_us]].
"""
import json
import os
import re
import sys
from collections import OrderedDict

SUPPORTED = {
    "NEC", "NECext", "Samsung32", "SIRC", "SIRC15", "SIRC20",
    "RC5", "RC5X", "RC6", "RCA", "Kaseikyo",
}

ALIASES = {
    "power": {"power", "pwr", "onoff", "poweronoff", "powertoggle", "standby"},
    "input": {"input", "source", "tvvideo", "av", "inputsource", "sourceinput"},
    "mute": {"mute", "muting"},
    "vol_up": {"volup", "volumeup", "volplus", "volumeplus"},
    "vol_dn": {"voldn", "voldown", "volumedown", "volminus", "volumeminus"},
    "ch_up": {"chnext", "chup", "channelup", "chplus", "channelplus", "chnxt"},
    "ch_dn": {"chprev", "chdn", "chdown", "channeldown", "chminus", "channelminus", "chprevious"},
    "up": {"up"},
    "down": {"down"},
    "left": {"left"},
    "right": {"right"},
    "ok": {"ok", "enter", "select", "center"},
    "menu": {"menu", "setup"},
    "back": {"back", "return"},
    "home": {"home"},
    "exit": {"exit"},
    "info": {"info", "display"},
}
for d in range(10):
    ALIASES["d%d" % d] = {str(d)}

NAME_TO_KEY = {}
for key, names in ALIASES.items():
    for nm in names:
        NAME_TO_KEY.setdefault(nm, key)


def norm_name(raw):
    s = raw.strip().lower().replace("+", "plus").replace("-", "minus")
    return re.sub(r"[^a-z0-9]", "", s)


def le_int(text):
    """'07 00 00 00' -> 7 (little endian)"""
    parts = [p for p in text.split() if p]
    value = 0
    for i, p in enumerate(parts):
        value |= int(p, 16) << (8 * i)
    return value


def parse_ir(path):
    """Devuelve lista de dicts {name, type, protocol, address, command, frequency, data}"""
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        lines = [ln.strip() for ln in fh.read().splitlines()]
    blocks, cur = [], None
    for ln in lines:
        if ln.startswith("name:"):
            cur = {"name": ln.split(":", 1)[1].strip()}
            blocks.append(cur)
        elif cur is not None and ":" in ln and not ln.startswith("#"):
            k, v = ln.split(":", 1)
            cur[k.strip().lower()] = v.strip()
    return blocks


def to_signal(block):
    try:
        typ = block.get("type", "").strip().lower()
        if typ.startswith("parsed"):
            proto = block.get("protocol", "").strip()
            if proto not in SUPPORTED:
                return None
            return [proto, le_int(block["address"]), le_int(block["command"])]
        if typ == "raw":
            freq = int(block.get("frequency", "38000"))
            data = [int(x) for x in block.get("data", "").split()]
            if len(data) < 4 or len(data) > 1200:
                return None
            return ["RAW", freq, data]
    except (KeyError, ValueError):
        return None
    return None


def slugify(name):
    s = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return s or "marca"


def jdump(obj):
    return json.dumps(obj, separators=(",", ":"), sort_keys=True)


def collect(brand_dir, label=""):
    """Lee los .ir de una marca. Devuelve {firma completa: {count, model, buttons}}."""
    perfiles = OrderedDict()
    for root, _dirs, files in os.walk(brand_dir):
        for fn in sorted(files):
            if not fn.lower().endswith(".ir"):
                continue
            buttons = OrderedDict()
            for block in parse_ir(os.path.join(root, fn)):
                key = NAME_TO_KEY.get(norm_name(block.get("name", "")))
                if key is None or key in buttons:
                    continue
                sig = to_signal(block)
                if sig is not None:
                    buttons[key] = sig
            if "power" not in buttons:
                continue
            firma = jdump(buttons)
            model = os.path.splitext(fn)[0].replace("_", " ")
            if label:
                model = label + " \u00b7 " + model
            if firma in perfiles:
                perfiles[firma]["count"] += 1
            else:
                perfiles[firma] = {"count": 1, "model": model, "buttons": buttons}
    return perfiles


def organize(perfiles):
    """
    Deja la lista final de perfiles (sin numerar):
    - cada perfil conserva los botones de UN solo control (no se mezclan senales de controles distintos)
    - se quitan los perfiles contenidos por completo en otro con el mismo encendido
    - los encendidos mas comunes van primero y sus variantes quedan juntas
    """
    por_power = OrderedDict()
    for p in perfiles.values():
        por_power.setdefault(jdump(p["buttons"]["power"]), []).append(p)

    grupos = []
    for lista in por_power.values():
        lista.sort(key=lambda p: (-len(p["buttons"]), -p["count"]))
        kept = []
        for p in lista:
            contenido = False
            for k in kept:
                if all(k["buttons"].get(b) == s for b, s in p["buttons"].items()):
                    k["count"] += p["count"]
                    contenido = True
                    break
            if not contenido:
                kept.append(p)
        kept.sort(key=lambda p: -p["count"])
        grupos.append((sum(p["count"] for p in kept), kept))

    grupos.sort(key=lambda g: -g[0])  # estable: el mas comun primero
    return [p for _total, kept in grupos for p in kept]


def write_brand(out, slug, name, perfiles):
    data = {
        "brand": name,
        "profiles": [
            {"n": i + 1, "m": g["model"], "b": g["buttons"]}
            for i, g in enumerate(perfiles)
        ],
    }
    with open(os.path.join(out, slug + ".json"), "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    index = []
    total = 0
    todas = OrderedDict()
    for brand in sorted(os.listdir(src), key=str.lower):
        bdir = os.path.join(src, brand)
        if not os.path.isdir(bdir):
            continue
        marca = collect(bdir)
        perfiles = organize(marca)
        if not perfiles:
            continue
        slug = slugify(brand)
        write_brand(out, slug, brand, perfiles)
        index.append({"n": brand, "s": slug, "c": len(perfiles)})
        total += len(perfiles)
        # para "Todas las marcas": mismos perfiles con el nombre de la marca en el modelo
        for firma, p in collect(bdir, brand).items():
            if firma in todas:
                todas[firma]["count"] += p["count"]
            else:
                todas[firma] = p
    perfiles_todas = organize(todas)
    write_brand(out, "todas", "Todas las marcas", perfiles_todas)
    index.insert(0, {"n": "Todas las marcas", "s": "todas", "c": len(perfiles_todas)})
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as fh:
        json.dump(index, fh, ensure_ascii=False, separators=(",", ":"))
    print("Marcas: %d | Codigos: %d | Todas las marcas: %d" % (len(index) - 1, total, len(perfiles_todas)))


if __name__ == "__main__":
    main()
