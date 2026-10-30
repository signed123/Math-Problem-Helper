// Optional DOM integration check of the exact HTML/JS bundled by the native MathText view.
// Install jsdom@24.1.3 outside the application and expose it through NODE_PATH.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {JSDOM, ResourceLoader, VirtualConsole} = require('jsdom');
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets/math');
const kotlin = fs.readFileSync(path.join(root, 'app/src/main/java/com/mathtutor/app/ui/Components.kt'), 'utf8');
const template = kotlin.match(/<!doctype html>[\s\S]*?<\/html>/)[0];
const escapeHtml = s => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
class LocalAssets extends ResourceLoader {
  fetch(url) {
    const prefix = 'https://appassets.androidplatform.net/assets/math/';
    if (!url.startsWith(prefix)) throw new Error('Unexpected external request: '+url);
    const file = path.resolve(assets, url.slice(prefix.length));
    assert(file.startsWith(assets+path.sep));
    return Promise.resolve(fs.readFileSync(file));
  }
}
async function check(name, text, formulas) {
  const errors=[];
  const console=new VirtualConsole();
  console.on('jsdomError', e=>errors.push(String(e)));
  const html=template.replaceAll('${"$$"}', '$$').replaceAll('${"$"}', '$')
    .replace('${android.text.TextUtils.htmlEncode(text)}', escapeHtml(text))
    .replace('$safe', JSON.stringify(text).replaceAll('<','\\u003c'));
  const dom=new JSDOM(html,{url:'https://appassets.androidplatform.net/assets/math/',
    runScripts:'dangerously',resources:new LocalAssets(),virtualConsole:console});
  await new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>reject(new Error('Render timed out')),10000);
    dom.window.addEventListener('load',()=>{clearTimeout(timeout);resolve();},{once:true});
  });
  assert.equal(errors.length,0,errors.join('\n'));
  assert.equal(dom.window.document.querySelectorAll('.katex').length,formulas,name);
  assert.equal(dom.window.__injected,undefined,'HTML must not execute user code');
  assert.equal(dom.window.document.querySelectorAll('img,iframe,form').length,0);
  dom.window.close();
  process.stdout.write(JSON.stringify({name,passed:true,formulas})+'\n');
}
(async()=>{
  await check('inline-display-and-parentheses','先看 $x^2$。\n\n$$f(x)=(x-2)^2-1$$\n\n再看 \\(a+b\\)',3);
  await check('untrusted-markdown-is-sanitized','<img src=x onerror="window.__injected=1">\n<script>window.__injected=2</script>\n\n$\\sqrt{x}$',1);
})().catch(e=>{process.stderr.write(String(e.stack||e)+'\n');process.exitCode=1;});
