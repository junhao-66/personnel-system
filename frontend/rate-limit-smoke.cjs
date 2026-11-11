const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5213'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5213', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'ignore' })
  let browser
  try {
    for (let i=0;i<100;i++) {
      if(server.exitCode !== null) throw new Error('Vite exited')
      try { if((await fetch(base)).ok) break } catch {}
      if(i===99) throw new Error('Vite timeout')
      await new Promise(resolve=>setTimeout(resolve,200))
    }
    browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
    const page=await browser.newPage(); let calls=0
    await page.route(base+'/api/**',route=>{
      calls++
      return route.fulfill({status:429,headers:{'Retry-After':'12'},json:{code:429,msg:'操作过于频繁'}})
    })
    await page.goto(base+'/login')
    await page.locator('input[autocomplete="username"]').fill('employee')
    await page.locator('input[autocomplete="current-password"]').fill('TestOnly123')
    await page.getByRole('button',{name:'登录',exact:true}).click()
    await page.getByText('操作过于频繁，请在12秒后重试',{exact:true}).waitFor()
    assert.equal(await page.locator('input[autocomplete="username"]').inputValue(),'employee')
    assert.equal(await page.locator('input[autocomplete="current-password"]').inputValue(),'TestOnly123')
    assert.equal(calls,1); assert.match(page.url(),/\/login$/)
    // Exercise the shared request layer with an existing session, without router refresh.
    const result=await page.evaluate(async()=>{
      localStorage.setItem('accessToken','test-preserved-token')
      let expired=false; window.addEventListener('personnel-session-expired',()=>{expired=true})
      const {default:request}=await import('/src/utils/request.ts')
      try { await request.put('/attendance/clock-in') } catch {}
      return {token:localStorage.getItem('accessToken'),expired}
    })
    assert.equal(result.token,'test-preserved-token'); assert.equal(result.expired,false)
    assert.equal(calls,2)
    await Promise.all([
      page.waitForResponse(response=>response.url().endsWith('/api/user/login') && response.status()===429),
      page.getByRole('button',{name:'登录',exact:true}).click()
    ])
    assert.equal(calls,3)
    console.log('PASS: 429 wait message, credentials retained, route retained, session retained, no automatic replay, manual retry')
  } finally { if(browser) await browser.close(); server.kill() }
}
run().catch(error=>{console.error(error);process.exitCode=1})
