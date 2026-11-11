// Read-only full-stack smoke test. Requires a built backend jar and local MySQL configuration.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path')
const assert = require('node:assert/strict')

async function waitForServer(url, process, getLog) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (process.exitCode !== null) throw new Error(getLog())
    try { if ((await fetch(url)).ok) return } catch {}
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error('Startup timeout: ' + getLog())
}

async function run() {
  const backendUrl = 'http://127.0.0.1:18080'
  const frontendUrl = 'http://127.0.0.1:5201'
  const jar = path.resolve(__dirname, '../backend/target/personnel-system-0.0.1-SNAPSHOT.jar')
  const backend = spawn(process.env.JAVA_EXE || 'D:/jdk/jdk17/bin/java.exe', ['-jar', jar, '--server.port=18080', '--spring.sql.init.mode=never'], { cwd: path.resolve(__dirname, '../backend'), windowsHide: true, stdio: 'pipe' })
  const frontend = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5201', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let backendLog = '', frontendLog = '', browser
  backend.stdout.on('data', chunk => { backendLog += chunk })
  backend.stderr.on('data', chunk => { backendLog += chunk })
  frontend.stdout.on('data', chunk => { frontendLog += chunk })
  frontend.stderr.on('data', chunk => { frontendLog += chunk })
  try {
    await Promise.all([waitForServer(backendUrl + '/api/system/health', backend, () => backendLog), waitForServer(frontendUrl, frontend, () => frontendLog)])
    assert.equal((await fetch(backendUrl + '/api/admin/employees')).status, 401)
    browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 960 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    // Relay to the isolated real backend without changing the project's development proxy.
    await page.route(/^http:\/\/127\.0\.0\.1:5201\/api\//, async route => {
      const url = new URL(route.request().url())
      const response = await route.fetch({ url: backendUrl + url.pathname + url.search })
      await route.fulfill({ response })
    })
    await page.goto(frontendUrl + '/login')
    await page.locator('input').nth(0).fill(process.env.SMOKE_ADMIN_USER || 'admin')
    await page.locator('input').nth(1).fill(process.env.SMOKE_ADMIN_PASSWORD || 'admin123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/admin/users')
    const loaded = page.waitForResponse(response => response.url().includes('/api/admin/employees') && response.request().method() === 'GET')
    await page.getByRole('link', { name: '员工档案', exact: true }).click()
    const payload = await (await loaded).json()
    assert.equal(payload.code, 200)
    assert.ok(Array.isArray(payload.data.records))
    assert.ok(payload.data.records.length > 0)
    assert.ok(payload.data.records.every(item => !('passwordHash' in item)))
    await page.getByRole('heading', { name: '员工档案', exact: true }).waitFor()
    await page.getByRole('button', { name: '编辑档案', exact: true }).first().click()
    await page.getByRole('dialog').waitFor()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.locator('.el-message').first().waitFor({ state: 'hidden' })
    if (process.env.LIVE_SCREENSHOT) await page.screenshot({ path: process.env.LIVE_SCREENSHOT, fullPage: true })
    const ruleLoaded = page.waitForResponse(response => response.url().includes('/api/attendance/rule'))
    await page.getByRole('link', { name: '考勤管理', exact: true }).click()
    const rule = await (await ruleLoaded).json()
    assert.equal(rule.code, 200)
    assert.equal(rule.data.timeZone, 'Asia/Shanghai')
    await page.getByRole('heading', { name: '固定作息规则', exact: true }).waitFor()
    await page.getByRole('heading', { name: '员工考勤记录', exact: true }).waitFor()
    const todayLoaded = page.waitForResponse(response => response.url().includes('/api/attendance/today'))
    await page.getByRole('link', { name: '个人工作台', exact: true }).click()
    const today = await (await todayLoaded).json()
    assert.equal(today.code, 200)
    assert.equal(today.data.timeZone, 'Asia/Shanghai')
    assert.ok(/^\d{4}-\d{2}-\d{2}$/.test(today.data.attendanceDate))
    await page.getByRole('heading', { name: '今日考勤', exact: true }).waitFor()
    await page.getByRole('heading', { name: '我的考勤记录', exact: true }).waitFor()
    if (process.env.LIVE_WORKBENCH_SCREENSHOT) await page.screenshot({ path: process.env.LIVE_WORKBENCH_SCREENSHOT, fullPage: true })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ passed: true, checks: ['未登录接口拦截', '真实管理员登录', '真实数据库档案分页响应', '密码字段不外泄', '档案编辑回填与取消', '真实考勤规则及管理员页面', '真实今日考勤和服务器时区', '工作台和个人记录展示', '无页面异常'], databaseWrites: 'none from smoke requests' }, null, 2))
  } finally {
    if (browser) await browser.close()
    backend.kill()
    frontend.kill()
  }
}

run().catch(error => { console.error(error); process.exitCode = 1 })
