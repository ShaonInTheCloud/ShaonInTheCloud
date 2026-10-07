#!/usr/bin/env python3
"""Read the APK's actual DEX BuildConfig; no Android SDK or third-party modules.

Exit 2 means the APK is a local lab build, which cannot satisfy trial acceptance.
This is an artifact compatibility check, never evidence of a passing device flow.
"""
import argparse
import hashlib
import json
import struct
import zipfile


def build_config(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Not a DEX file")
    def u32(offset):
        return struct.unpack_from("<I", data, offset)[0]
    def uleb(offset):
        value = shift = 0
        while True:
            byte = data[offset]; offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
            shift += 7
            if shift > 35:
                raise ValueError("Invalid DEX integer")
    strings = []
    for i in range(u32(56)):
        offset = u32(u32(60) + 4 * i)
        _, offset = uleb(offset)
        strings.append(data[offset:data.index(0, offset)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + 4 * i)] for i in range(u32(64))]
    fields = []
    for i in range(u32(80)):
        cls, typ, name = struct.unpack_from("<HHI", data, u32(84) + 8 * i)
        fields.append((types[cls], strings[name]))
    for i in range(u32(96)):
        offset = u32(100) + 32 * i
        if types[u32(offset)] != "Lcom/safenest/app/BuildConfig;":
            continue
        pos = u32(offset + 24)
        count, pos = uleb(pos)
        for _ in range(3):
            _, pos = uleb(pos)
        indices = []; index = 0
        for _ in range(count):
            delta, pos = uleb(pos); index += delta
            _, pos = uleb(pos)
            indices.append(index)
        pos = u32(offset + 28)
        if not pos:
            raise ValueError("Missing BuildConfig static values")
        values, pos = uleb(pos)
        result = {}
        for index in indices[:values]:
            header = data[pos]; pos += 1
            kind, arg = header & 31, header >> 5
            if kind == 31:
                value = bool(arg)
            elif kind == 30:
                value = None
            else:
                size = arg + 1
                value = int.from_bytes(data[pos:pos+size], "little", signed=kind in (0,2,4,6))
                pos += size
                if kind == 23:
                    value = strings[value]
                elif kind not in (0,2,3,4,6):
                    raise ValueError("Unsupported BuildConfig value")
            result[fields[index][1]] = value
        return result
    return None


def inspect(path):
    with zipfile.ZipFile(path) as apk:
        config = next((c for name in apk.namelist() if name.endswith('.dex')
                       for c in [build_config(apk.read(name))] if c), None)
    if not config:
        raise ValueError("BuildConfig not found; cannot establish compatibility")
    compatible = config.get('LOCAL_TEST_BUILD') is False
    return {"apk_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "application_id": config.get('APPLICATION_ID'), "version": config.get('VERSION_NAME'),
            "version_code": config.get('VERSION_CODE'), "local_test_build": config.get('LOCAL_TEST_BUILD'),
            "trial_compatible": compatible, "acceptance_passed": False,
            "reason": "compatible build; device/live checks still required" if compatible else
                      "lab mode replaces Account UI and refuses verified entitlement caching"}


if __name__ == '__main__':
    from pathlib import Path
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    result = inspect(args.apk)
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result['trial_compatible'] else 2)
