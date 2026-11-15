"""Sign the built release APK using an external keystore and password file."""
import argparse
import hashlib
from pathlib import Path
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--build-tools', required=True, type=Path)
p.add_argument('--keystore', required=True, type=Path)
p.add_argument('--password-file', required=True, type=Path)
p.add_argument('--version', required=True)
args = p.parse_args()
root = Path(__file__).resolve().parents[1]
source = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
output = root / f'artifacts/releases/CouchJoust-{args.version}.apk'
output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([str(args.build_tools/'zipalign.exe'), '-c', '-P', '16', '4', str(source)], check=True)
signer = ['java', '-jar', str(args.build_tools/'lib/apksigner.jar')]
subprocess.run(signer + ['sign', '--ks', str(args.keystore), '--ks-key-alias', 'couchjoust',
                        '--ks-pass', 'file:'+str(args.password_file),
                        '--out', str(output), str(source)], check=True)
subprocess.run(signer + ['verify', '--verbose', '--print-certs', str(output)], check=True)
digest = hashlib.sha256(output.read_bytes()).hexdigest()
(output.parent/'SHA256SUMS.txt').write_text(f'{digest}  {output.name}\n')
print(f'Signed {output.name}; SHA-256 {digest}')
