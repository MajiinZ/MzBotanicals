import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, { stdio: 'inherit', ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
};
run(process.execPath, ['--test', fileURLToPath(new URL('rules.test.mjs', import.meta.url))]);
const env = { ...process.env, APP_ENV: 'emulator', FIREBASE_PROJECT_ID: 'demo-mzbotanicals',
  FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8085' };
if (process.platform === 'win32') {
  run('cmd.exe', ['/d', '/c', 'gradlew.bat -p server test --rerun-tasks --no-daemon'], { cwd: root, env });
} else {
  run('sh', ['./gradlew', '-p', 'server', 'test', '--rerun-tasks', '--no-daemon'], { cwd: root, env });
}
