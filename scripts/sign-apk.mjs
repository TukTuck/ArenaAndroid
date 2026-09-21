// Signiert ein APK (JAR-v1 + APK-v2 + v3) – reines Node.js, kein Android-SDK nötig.
// Aufruf:  node scripts/sign-apk.mjs <in.apk> <out.apk> <key.pem> <cert.pem>
// Optional: Umgebungsvariable APKSIGN_LIB = Pfad zur apk_sign_ts dist/index.js
import { readFileSync, writeFileSync } from 'node:fs';

const lib = process.env.APKSIGN_LIB || 'apk_sign_ts';
const { ApkSigner, SigningKey } = await import(lib);

const [inApk, outApk, keyPem, certPem] = process.argv.slice(2);
if (!inApk || !outApk || !keyPem || !certPem) {
    console.error('Aufruf: node sign-apk.mjs <in.apk> <out.apk> <key.pem> <cert.pem>');
    process.exit(1);
}

const apk = new Uint8Array(readFileSync(inApk));
const signingKey = SigningKey.fromPEM(
    readFileSync(keyPem, 'utf8'),
    readFileSync(certPem, 'utf8'),
);
const signer = new ApkSigner({ signingKey });
const { signedApk } = await signer.sign(apk);
writeFileSync(outApk, Buffer.from(signedApk));
console.log('signiert:', outApk);
