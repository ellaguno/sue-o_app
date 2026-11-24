#!/usr/bin/env node
/**
 * Google Play Developer API (sin dependencias): pistas de prueba y subida del
 * AAB. Lo usa el workflow de Android y quien lo ejecute a mano.
 *
 * Credencial (nunca se imprime): el JSON de una cuenta de servicio con permiso
 * para publicar en Play Console, en PLAY_SERVICE_ACCOUNT_JSON (contenido) o en
 * PLAY_SERVICE_ACCOUNT_PATH (ruta al archivo).
 *
 * Uso:
 *   node scripts/android/play.mjs tracks
 *       pistas y versiones publicadas en cada una
 *   node scripts/android/play.mjs upload <app.aab> <pista> [mapping.txt] [notas.txt]
 *       sube el AAB (y el mapa de R8), lo publica en la pista y confirma
 *
 * Variables opcionales: PACKAGE_NAME (com.sesolibre.somnia).
 *
 * Copiado de sesolibre-mind; cambio local: las notas de versión solo se envían
 * en los idiomas que ya tienen ficha en Play (si no, Play rechaza la versión).
 */
import { createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';

const PACKAGE = process.env.PACKAGE_NAME ?? 'com.sesolibre.somnia';
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE}`;
const UPLOAD = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE}`;

function fail(msg) {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

function account() {
  const raw =
    process.env.PLAY_SERVICE_ACCOUNT_JSON ??
    (process.env.PLAY_SERVICE_ACCOUNT_PATH
      ? readFileSync(process.env.PLAY_SERVICE_ACCOUNT_PATH, 'utf8')
      : null);
  if (!raw) fail('falta PLAY_SERVICE_ACCOUNT_JSON o PLAY_SERVICE_ACCOUNT_PATH');
  const sa = JSON.parse(raw);
  if (!sa.client_email || !sa.private_key) fail('el JSON no es de una cuenta de servicio');
  return sa;
}

/** Token OAuth de la cuenta de servicio (JWT RS256 → token de acceso, 1 h). */
async function accessToken() {
  const sa = account();
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const head = b64({ alg: 'RS256', typ: 'JWT' });
  const body = b64({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: sa.token_uri ?? 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  });
  const sig = createSign('RSA-SHA256').update(`${head}.${body}`).sign(sa.private_key, 'base64url');
  const res = await fetch(sa.token_uri ?? 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${head}.${body}.${sig}`,
    }),
  });
  const data = await res.json();
  if (!res.ok) fail(`no se obtuvo token de Google: ${data.error_description ?? data.error}`);
  return data.access_token;
}

const TOKEN = await accessToken();

async function call(url, init = {}) {
  const res = await fetch(url, {
    ...init,
    headers: { Authorization: `Bearer ${TOKEN}`, ...(init.headers ?? {}) },
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) {
    const msg = data.error?.message ?? text;
    if (res.status === 401 || res.status === 403)
      fail(
        `${res.status}: ${msg}\n  ¿La cuenta de servicio está invitada en Play Console con permiso ` +
          'para publicar en pistas de prueba de esta app?',
      );
    fail(`${res.status} ${init.method ?? 'GET'} ${url.replace(/\?.*/, '')} → ${msg}`);
  }
  return data;
}

const json = (body) => ({
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

async function withEdit(fn, commit) {
  const edit = await call(`${API}/edits`, { method: 'POST', ...json({}) });
  try {
    const out = await fn(edit.id);
    if (commit) await call(`${API}/edits/${edit.id}:commit`, { method: 'POST' });
    else await call(`${API}/edits/${edit.id}`, { method: 'DELETE' });
    return out;
  } catch (e) {
    await call(`${API}/edits/${edit.id}`, { method: 'DELETE' }).catch(() => {});
    throw e;
  }
}

async function tracks() {
  await withEdit(async (id) => {
    const r = await call(`${API}/edits/${id}/tracks`);
    for (const t of r.tracks ?? []) {
      const rel = (t.releases ?? [])
        .map((x) => `${x.name ?? '?'} [${(x.versionCodes ?? []).join(',')}] ${x.status}`)
        .join(' · ');
      console.log(`• ${t.track}: ${rel || '(sin versiones)'}`);
    }
  }, false);
}

async function upload(aab, track, mapping, notesFile) {
  if (!aab || !track) fail('uso: upload <app.aab> <pista> [mapping.txt] [notas.txt]');
  await withEdit(async (id) => {
    const bundle = await call(`${UPLOAD}/edits/${id}/bundles?uploadType=media`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: readFileSync(aab),
    });
    const vc = bundle.versionCode;
    console.log(`✓ AAB subido: versionCode ${vc}`);
    if (mapping) {
      await call(
        `${UPLOAD}/edits/${id}/apks/${vc}/deobfuscationFiles/proguard?uploadType=media`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/octet-stream' },
          body: readFileSync(mapping),
        },
      );
      console.log('✓ Mapa de R8 (mapping.txt) subido');
    }
    // Notas en español e inglés: el archivo trae «es:» y «en:» en líneas aparte.
    const releaseNotes = notesFile
      ? readFileSync(notesFile, 'utf8')
          .split(/\n(?=(?:es|en):)/)
          .map((block) => block.match(/^(es|en):\s*([\s\S]*)$/))
          .filter(Boolean)
          .map(([, lang, text]) => ({
            language: lang === 'es' ? 'es-419' : 'en-US',
            text: text.trim().slice(0, 500),
          }))
      : undefined;
    // Solo idiomas con ficha en Play: unas notas en un idioma sin ficha hacen fallar la versión.
    let notes = releaseNotes;
    if (notes) {
      const listings = await call(`${API}/edits/${id}/listings`);
      const langs = new Set((listings.listings ?? []).map((l) => l.language));
      const kept = notes.filter((n) => langs.has(n.language));
      const skipped = notes.filter((n) => !langs.has(n.language)).map((n) => n.language);
      if (skipped.length) console.log(`• Notas omitidas (sin ficha en ese idioma): ${skipped.join(', ')}`);
      notes = kept.length ? kept : undefined;
    }
    await call(`${API}/edits/${id}/tracks/${encodeURIComponent(track)}`, {
      method: 'PUT',
      ...json({
        track,
        releases: [
          { versionCodes: [String(vc)], status: 'completed', ...(notes && { releaseNotes: notes }) },
        ],
      }),
    });
    console.log(`✓ Versión ${vc} en la pista «${track}»`);
  }, true);
  console.log('✓ Cambios confirmados: Google los revisa y publica en la prueba.');
}

const [cmd, ...args] = process.argv.slice(2);
if (cmd === 'tracks') await tracks();
else if (cmd === 'upload') await upload(...args);
else fail('uso: play.mjs tracks | upload <app.aab> <pista> [mapping.txt] [notas.txt]');
