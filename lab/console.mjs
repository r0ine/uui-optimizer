// Starts the server and forwards every line appended to the command file to its console.
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, watchFile } from 'node:fs';
import { join, resolve } from 'node:path';

const serverDir = resolve(process.argv[2]);
const cmdFile = resolve(process.argv[3]);
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';

writeFileSync(cmdFile, '');
let sent = 0;

const mc = spawn(java, ['-Xms2G', '-Xmx2G', '-jar', 'paper.jar', '--nogui'], { cwd: serverDir });
mc.stdout.pipe(process.stdout);
mc.stderr.pipe(process.stderr);
mc.on('exit', code => process.exit(code ?? 0));

watchFile(cmdFile, { interval: 300 }, () => {
  const lines = readFileSync(cmdFile, 'utf8').split('\n');
  for (; sent < lines.length - 1; sent++) mc.stdin.write(lines[sent].trim() + '\n');
});
