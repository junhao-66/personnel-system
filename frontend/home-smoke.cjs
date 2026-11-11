// Synthetic API fixtures. Exercise public navigation, login continuation, role entry points and mobile layout.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5211'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5211', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let logs = '', browser; server.stdout.on('data', v => { logs += v }); server.stderr.on('data', v => { logs += v })
  const checks = []
  try {
    for (let i = 0; i < 100; i++) {
      if (server.exitCode !== null) throw new Error(logs)
      try { if ((await fetch(base)).ok) break } catch {}
      if (i === 99) throw new Error(logs)
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    let user = { id: 7, userName: 'employee', realName: '验收员工', roleCode: 'EMPLOYEE', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    let failHealth = true, healthCalls = 0
    await page.route(base + '/api/**', async route => {
      const url = new URL(route.request().url()); let data = null
      if (url.pathname === '/api/system/health') {
        healthCalls++
        if (failHealth) return route.fulfill({ status: 503, json: { code: 503, msg: '模拟连接失败' } })
        data = { status: 'UP' }
      } else if (url.pathname === '/api/user/login') data = { accessToken: 'test-token', user }
      else if (url.pathname === '/api/user/me') data = user
      else if (url.pathname === '/api/assessment/scales') data = []
      else if (url.pathname === '/api/attendance/today') data = { attendanceDate: '2026-10-29', serverTime: '2026-10-29T09:00:00', workStartTime: '09:00:00', workEndTime: '18:00:00', restDay: false, timeZone: 'Asia/Shanghai' }
      else if (url.pathname.endsWith('/attendance/records')) data = { records: [], total: 0, pageNum: 1, pageSize: 10 }
      await route.fulfill({ json: { code: 200, msg: 'success', data } })
    })
    const login = async () => {
      await page.locator('input[autocomplete="username"]').fill('employee')
      await page.locator('input[autocomplete="current-password"]').fill('TestOnly123')
      await page.getByRole('button', { name: '登录', exact: true }).click()
    }
    await page.goto(base)
    await page.getByRole('heading', { name: '从个人了解，到日常协作' }).waitFor()
    assert.equal(await page.evaluate(() => localStorage.getItem('accessToken')), null)
    assert.equal(await page.getByRole('link', { name: '进入管理后台', exact: true }).count(), 0); checks.push('访客可读介绍且无管理入口')
    await page.keyboard.press('Tab'); assert.equal(await page.locator('.skip-link').evaluate(node => document.activeElement === node), true)
    await page.keyboard.press('Enter'); await page.waitForURL('**/#home-features'); checks.push('键盘可跳过导航进入功能介绍')
    await page.getByRole('navigation', { name: '首页导航' }).getByRole('link', { name: '测评说明', exact: true }).click()
    await page.waitForURL('**/#home-assessment'); checks.push('首页锚点导航可用')
    const question = page.locator('details').filter({ hasText: '谁能看到我的结果，数据会保存多久？' })
    await question.locator('summary').click(); assert.equal(await question.evaluate(node => node.open), true)
    await question.getByText('本人和管理员可查看个人完整报告。', { exact: false }).waitFor(); checks.push('常见问题可展开查看可见范围和期限说明')
    const source = page.getByRole('link', { name: '查看参考题库来源（新窗口）', exact: true })
    assert.match(await source.getAttribute('href'), /openjung\/core\/tree\/4d7917df/)
    assert.equal(await source.getAttribute('rel'), 'noopener noreferrer'); checks.push('题库来源指向固定版本并安全打开新窗口')
    await page.getByRole('button', { name: '检查系统连接', exact: true }).click()
    await page.getByText('连接失败，请稍后重试或联系项目维护者', { exact: true }).waitFor()
    failHealth = false; await page.getByRole('button', { name: '检查系统连接', exact: true }).click()
    await page.getByText('服务连接正常', { exact: true }).waitFor(); assert.equal(healthCalls, 2); checks.push('服务连接失败后可重试成功')
    await page.locator('.hero-card').getByRole('link', { name: '查询注册审核状态', exact: true }).click()
    await page.waitForURL('**/review-status'); await page.getByRole('heading', { name: '查询注册审核状态', exact: true }).waitFor(); checks.push('首页审核查询入口可达')
    await page.goto(base); await page.locator('.hero-card').getByRole('link', { name: '注册账号', exact: true }).click()
    await page.waitForURL('**/register'); checks.push('首页注册入口可达')
    await page.goto(base); await page.getByRole('link', { name: '进入人格偏好测评', exact: false }).click()
    await page.waitForURL('**/login?**'); assert.equal(new URL(page.url()).searchParams.get('redirect'), '/assessment')
    await login(); await page.waitForURL('**/assessment'); await page.getByRole('heading', { name: '暂未开放正式测评' }).waitFor(); checks.push('访客进入测评登录后回到原功能')
    await page.goto(base); await page.locator('.hero-card').getByRole('link', { name: '进入个人工作台', exact: true }).waitFor()
    assert.equal(await page.locator('.hero-card').getByRole('link', { name: '登录系统', exact: true }).count(), 0)
    assert.equal(await page.getByRole('link', { name: '进入管理后台', exact: true }).count(), 0); checks.push('员工首页显示工作台且不显示管理员入口')
    user = { ...user, roleCode: 'DEPARTMENT_MANAGER' }
    await page.evaluate(value => localStorage.setItem('currentUser', JSON.stringify(value)), user); await page.reload()
    await page.locator('.hero-card').getByRole('link', { name: '请假与审批', exact: true }).waitFor()
    assert.equal(await page.getByRole('link', { name: '进入管理后台', exact: true }).count(), 0); checks.push('负责人首页显示审批入口且无报告管理入口')
    user = { ...user, roleCode: 'ADMIN' }
    await page.evaluate(value => localStorage.setItem('currentUser', JSON.stringify(value)), user); await page.reload()
    await page.getByRole('link', { name: '进入管理后台', exact: true }).waitFor(); checks.push('管理员首页显示管理后台入口')
    await page.screenshot({ path: path.join(__dirname, 'home-desktop.png'), fullPage: true })
    await page.evaluate(() => localStorage.clear()); await page.setViewportSize({ width: 390, height: 844 }); await page.goto(base)
    await page.getByRole('heading', { name: '从个人了解，到日常协作' }).waitFor()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.screenshot({ path: path.join(__dirname, 'home-mobile.png'), fullPage: true }); checks.push('390px首页无整页横向溢出')
    user = { ...user, roleCode: 'EMPLOYEE' }
    for (const target of ['https://example.invalid', '//example.invalid', '/admin/users']) {
      await page.evaluate(() => localStorage.clear()); await page.goto(base + '/login?redirect=' + encodeURIComponent(target))
      await login(); await page.waitForURL('**/workbench')
    }
    checks.push('登录续跳拒绝外部地址与员工管理后台目标')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill() }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
