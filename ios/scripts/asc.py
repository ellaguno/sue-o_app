#!/usr/bin/env python3
"""
Herramienta mínima para la API de App Store Connect (sin fastlane).

La llave privada (.p8) se lee de ASC_PRIVATE_KEY (contenido) o de
ASC_PRIVATE_KEY_PATH (ruta); el Key ID y el Issuer ID de ASC_KEY_ID y
ASC_ISSUER_ID. Nunca se imprime la llave ni el token.

Comandos:
  status    <bundle_id>                      — certificado, Bundle ID, perfil y registro de la app
  register  <bundle_id> <nombre>             — crea el Bundle ID si no existe
  profile   <bundle_id> <nombre_perfil> <out.mobileprovision>
            — usa el perfil App Store vigente con ese nombre o crea uno, y lo guarda
"""
import base64
import os
import sys
import time

import jwt
import requests

API = "https://api.appstoreconnect.apple.com/v1"


def token() -> str:
    key = os.environ.get("ASC_PRIVATE_KEY")
    if not key:
        with open(os.environ["ASC_PRIVATE_KEY_PATH"]) as f:
            key = f.read()
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 15 * 60, "aud": "appstoreconnect-v1"},
        key,
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


SESSION = requests.Session()


def call(method: str, path: str, **kwargs):
    SESSION.headers["Authorization"] = f"Bearer {token()}"
    r = SESSION.request(method, API + path, timeout=60, **kwargs)
    if r.status_code >= 400:
        # Los errores de la API no contienen secretos; se muestran para diagnosticar.
        sys.exit(f"API {method} {path} -> {r.status_code}: {r.text[:800]}")
    return r.json() if r.content else {}


def distribution_certs():
    certs = call("GET", "/certificates", params={"limit": 200})["data"]
    usable = [c for c in certs if c["attributes"]["certificateType"] in ("DISTRIBUTION", "IOS_DISTRIBUTION")]
    usable.sort(key=lambda c: c["attributes"].get("expirationDate") or "", reverse=True)
    return usable


def bundle(identifier: str):
    data = call("GET", "/bundleIds", params={"filter[identifier]": identifier})["data"]
    return next((b for b in data if b["attributes"]["identifier"] == identifier), None)


def status(identifier: str):
    for c in distribution_certs():
        a = c["attributes"]
        print(f"certificado: {a['certificateType']} '{a['name']}' vence {a.get('expirationDate')} id={c['id']}")
    b = bundle(identifier)
    print("bundle id:", f"{identifier} id={b['id']}" if b else "NO REGISTRADO")
    if b:
        profiles = call("GET", f"/bundleIds/{b['id']}/profiles")["data"]
        for p in profiles:
            a = p["attributes"]
            print(f"perfil: '{a['name']}' {a['profileType']} {a['profileState']} vence {a['expirationDate']}")
    apps = call("GET", "/apps", params={"filter[bundleId]": identifier})["data"]
    print("app en App Store Connect:", f"'{apps[0]['attributes']['name']}' id={apps[0]['id']}" if apps else "NO EXISTE")


def register(identifier: str, name: str):
    b = bundle(identifier)
    if b:
        print(f"ya registrado: {identifier} id={b['id']}")
        return
    body = {"data": {"type": "bundleIds", "attributes": {"identifier": identifier, "name": name, "platform": "IOS"}}}
    b = call("POST", "/bundleIds", json=body)["data"]
    print(f"registrado: {identifier} id={b['id']}")


def profile(identifier: str, name: str, out: str):
    b = bundle(identifier)
    if not b:
        sys.exit(f"{identifier} no está registrado")
    existing = [
        p for p in call("GET", f"/bundleIds/{b['id']}/profiles")["data"]
        if p["attributes"]["name"] == name and p["attributes"]["profileState"] == "ACTIVE"
        and p["attributes"]["profileType"] == "IOS_APP_STORE"
    ]
    if existing:
        p = existing[0]
        print(f"perfil existente: '{name}'")
    else:
        certs = distribution_certs()
        if not certs:
            sys.exit("no hay certificado Apple Distribution")
        body = {"data": {
            "type": "profiles",
            "attributes": {"name": name, "profileType": "IOS_APP_STORE"},
            "relationships": {
                "bundleId": {"data": {"type": "bundleIds", "id": b["id"]}},
                "certificates": {"data": [{"type": "certificates", "id": certs[0]["id"]}]},
            },
        }}
        p = call("POST", "/profiles", json=body)["data"]
        print(f"perfil creado: '{name}'")
    with open(out, "wb") as f:
        f.write(base64.b64decode(p["attributes"]["profileContent"]))
    print(f"perfil guardado en {out} (uuid {p['attributes']['uuid']})")


if __name__ == "__main__":
    cmd, *args = sys.argv[1:]
    {"status": status, "register": register, "profile": profile}[cmd](*args)
