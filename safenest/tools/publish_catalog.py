#!/usr/bin/env python3
"""Publish SafeNest v1 signed domain snapshots. No server, secrets or production feed is bundled.

Dependency: python -m pip install cryptography
Run --help for commands. Keep the generated PRIVATE key on the publisher machine only.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import os
from pathlib import Path
import re
import sys

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    raise SystemExit("Install the publisher dependency with: python -m pip install cryptography")

MAX_DOMAINS = 100_000
MAX_PAYLOAD_BYTES = 6 * 1024 * 1024
MAX_ENVELOPE_BYTES = 8 * 1024 * 1024 + 1024
UTC = dt.timezone.utc


def write_new(path: Path, data: bytes, mode: int = 0o644) -> None:
    """Refuse to overwrite a key, input, or previously released revision."""
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode), "wb") as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())


def keygen(args: argparse.Namespace) -> None:
    if args.private.exists() or args.public.exists() or args.private.resolve() == args.public.resolve():
        raise ValueError("Choose distinct, nonexistent files for the public and private keys")
    private = ec.generate_private_key(ec.SECP256R1())
    private_bytes = private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
    public_bytes = private.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
    write_new(args.private, private_bytes, 0o600)
    write_new(args.public, public_bytes)
    print(f"Public key: {args.public}")
    print(f"Private key: {args.private} (keep offline/private; never upload it or include it in the app)")
    print("On Windows, restrict the private file's NTFS permissions to the publisher account.")


def canonical_host(raw: str) -> str:
    host = raw.strip()
    if host.endswith("."):
        host = host[:-1]
    if not host or len(host) > 1024 or any(ord(char) < 33 or ord(char) == 127 for char in host):
        raise ValueError("Invalid hostname")
    try:
        host = host.encode("idna").decode("ascii").lower()
    except UnicodeError as error:
        raise ValueError("Invalid internationalized hostname") from error
    labels = host.split(".")
    if len(host) > 253 or len(labels) < 2 or len(labels[-1]) < 2 or labels[-1].isdigit():
        raise ValueError("Use DNS hostnames with at least two labels, not IP addresses")
    if any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label) for label in labels):
        raise ValueError("Use hostnames only: no URL, credentials, wildcard, port or path")
    return host


def read_domains(path: Path | None) -> set[str]:
    if path is None:
        return set()
    with path.open("rb") as source:
        raw = source.read(MAX_PAYLOAD_BYTES + 1)
    if len(raw) > MAX_PAYLOAD_BYTES:
        raise ValueError(f"Input exceeds 6 MiB: {path}")
    domains: set[str] = set()
    for line_number, line in enumerate(raw.decode("utf-8-sig").splitlines(), 1):
        text = line.strip()
        if not text or text.startswith("#"):
            continue
        try:
            domains.add(canonical_host(text))
        except ValueError as error:
            raise ValueError(f"{path}:{line_number}: {error}") from error
        if len(domains) > MAX_DOMAINS:
            raise ValueError("Catalog exceeds 100,000 category/domain records")
    return domains


def parse_time(value: str) -> dt.datetime:
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", value):
        raise ValueError("Use a whole-second UTC timestamp, e.g. 2026-10-28T09:00:00Z")
    return dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)


def stamp(value: dt.datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


def sign(args: argparse.Namespace) -> None:
    if not 0 < args.revision <= 9223372036854775807:
        raise ValueError("Revision must be a positive signed 64-bit integer, greater than every prior release")
    issued = parse_time(args.issued) if args.issued else dt.datetime.now(UTC).replace(microsecond=0)
    expires = parse_time(args.expires)
    if not dt.timedelta(0) < expires - issued <= dt.timedelta(days=366):
        raise ValueError("Expiry must be after issuance and no more than 366 days later")
    if expires <= dt.datetime.now(UTC):
        raise ValueError("The new catalog is already expired")
    gambling, adult = read_domains(args.gambling), read_domains(args.adult)
    count = len(gambling) + len(adult)
    if not 0 < count <= MAX_DOMAINS:
        raise ValueError("Include between 1 and 100,000 category/domain records")
    rows = sorted(["gambling:" + host for host in gambling] + ["adult:" + host for host in adult])
    payload = (f"SAFENEST-CATALOG/1\nrevision:{args.revision}\nissued-at:{stamp(issued)}\nexpires-at:{stamp(expires)}\n"
               + "\n".join(rows) + "\n").encode("ascii")
    if len(payload) > MAX_PAYLOAD_BYTES:
        raise ValueError("Canonical payload exceeds 6 MiB")
    with args.key.open("rb") as source:
        raw_key = source.read(8193)
    if len(raw_key) > 8192:
        raise ValueError("Private key file exceeds the size limit")
    private = serialization.load_pem_private_key(raw_key, password=None)
    if not isinstance(private, ec.EllipticCurvePrivateKey) or not isinstance(private.curve, ec.SECP256R1):
        raise ValueError("Use a P-256 private key created by the keygen command")
    signature = private.sign(payload, ec.ECDSA(hashes.SHA256()))
    envelope = b"SAFENEST-SIGNED-CATALOG/1\npayload:" + base64.b64encode(payload) + b"\nsignature:" + base64.b64encode(signature) + b"\n"
    if len(envelope) > MAX_ENVELOPE_BYTES:
        raise ValueError("Signed envelope exceeds its size limit")
    write_new(args.output, envelope)
    print(f"Signed revision {args.revision}: {count} category/domain records, {len(envelope)} bytes -> {args.output}")
    print(f"Expires at {stamp(expires)}; distribute only the signed .sncatalog and PUBLIC key.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("keygen", help="Generate publisher P-256 keys (never bundle the private key)")
    create.add_argument("--private", type=Path, required=True)
    create.add_argument("--public", type=Path, required=True)
    create.set_defaults(run=keygen)
    publish = commands.add_parser("sign", help="Sign a reviewed domain snapshot")
    publish.add_argument("--key", type=Path, required=True)
    publish.add_argument("--gambling", type=Path, help="UTF-8 text: one hostname per line; # comments allowed")
    publish.add_argument("--adult", type=Path, help="UTF-8 text: one hostname per line; # comments allowed")
    publish.add_argument("--revision", type=int, required=True)
    publish.add_argument("--issued", help="Whole-second UTC timestamp; defaults to now")
    publish.add_argument("--expires", required=True, help="Whole-second UTC expiry, at most 366 days from issuance")
    publish.add_argument("--output", type=Path, required=True)
    publish.set_defaults(run=sign)
    args = parser.parse_args()
    try:
        args.run(args)
    except (ValueError, OSError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
