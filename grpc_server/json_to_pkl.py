#!/usr/bin/env python3
"""Convert slots_*.json polygon files to slots_*.pkl for prototype compatibility."""
import json
import pickle
from pathlib import Path

base = Path(__file__).parent
for jf in sorted(base.glob("slots_*.json")):
    with open(jf, encoding="utf-8") as f:
        polygons = json.load(f)
    pk = jf.with_suffix(".pkl")
    with open(pk, "wb") as f:
        pickle.dump(polygons, f)
    print("Wrote", pk.name)
