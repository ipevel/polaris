'use strict';
/** 清理构建产物与开发数据（不碰源码）。 */

const fs = require('fs');
const path = require('path');

const targets = ['dist', '.devdata'];

for (const t of targets) {
  const p = path.join(__dirname, '..', t);
  if (fs.existsSync(p)) {
    fs.rmSync(p, { recursive: true, force: true });
    console.log('removed', t);
  }
}
console.log('clean done');
