// Browser interaction tests with isolated mocked API. No database writes.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path')
const assert = require('node:assert/strict')

async function run() {
  const base = 'http://127.0.0.1:5202'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5202', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let log = '', browser
  server.stdout.on('data', data => { log += data }); server.stderr.on('data', data => { log += data })
  const passed = []
  try {
    for (let attempt = 0; attempt < 120; attempt += 1) {
      if (server.exitCode !== null) throw new Error(log)
      try { if ((await fetch(base)).ok) break } catch {}
      if (attempt === 119) throw new Error('Vite startup timeout')
      await new Promise(resolve => setTimeout(resolve, 250))
    }
    browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => window.addEventListener('unhandledrejection', event => { window.__unhandled = String(event.reason) }))
    let currentUser = { id: 9, userName: 'employee', realName: '验收员工', roleCode: 'EMPLOYEE', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    let record, inCalls = 0, outCalls = 0, ruleCalls = 0, failClockIn = true, expire = false, lastQuery
    await page.route(/^http:\/\/127\.0\.0\.1:5202\/api\//, async route => {
      const request = route.request(), url = new URL(request.url())
      let data = null, code = 200, msg = 'success'
      if (expire && url.pathname !== '/api/user/login') return route.fulfill({ status: 401, json: { code: 401, msg: 'expired' } })
      if (url.pathname === '/api/user/login') {
        if (request.postDataJSON().userName === 'admin') currentUser = { ...currentUser, id: 1, userName: 'admin', realName: '验收管理员', roleCode: 'ADMIN' }
        data = { accessToken: 'test-token', user: currentUser }
      } else if (url.pathname === '/api/user/me') data = currentUser
      else if (url.pathname === '/api/attendance/rule') data = { workStartTime: '09:00:00', workEndTime: '18:00:00', timeZone: 'Asia/Shanghai', weekendRest: true }
      else if (url.pathname === '/api/admin/attendance/rule') { ruleCalls += 1; data = null }
      else if (url.pathname === '/api/attendance/today') data = { attendanceDate: '2026-10-28', serverTime: '2026-10-28T09:03:00', workStartTime: '09:00:00', workEndTime: '18:00:00', timeZone: 'Asia/Shanghai', restDay: false, record }
      else if (url.pathname === '/api/attendance/clock-in') {
        inCalls += 1
        if (failClockIn) { code = 400; msg = '模拟打卡失败' }
        else { record = { id: 1, userId: 9, attendanceDate: '2026-10-28', clockInTime: '2026-10-28T09:03:00', workStartTime: '09:00:00', workEndTime: '18:00:00', restDay: false, late: true, early: false, status: 'LATE_IN_PROGRESS' }; data = record }
      } else if (url.pathname === '/api/attendance/clock-out') {
        outCalls += 1; record = { ...record, clockOutTime: '2026-10-28T17:30:00', early: true, status: 'LATE_AND_EARLY' }; data = record
      } else if (url.pathname.endsWith('/attendance/records')) {
        lastQuery = Object.fromEntries(url.searchParams)
        data = { records: record ? [{ attendance: record, realName: '验收员工', userName: 'employee', departmentName: '研发部' }] : [], total: record ? 1 : 0, pageNum: 1, pageSize: 10 }
      } else if (url.pathname === '/api/admin/departments') data = [{ id: 1, departmentName: '研发部', departmentCode: 'DEV', isEnabled: 1 }]
      else if (url.pathname === '/api/admin/users') data = [currentUser]
      else if (url.pathname === '/api/admin/positions') data = []
      return route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/workbench')
    await page.waitForURL('**/login?**')
    passed.push('未登录工作台跳转登录')
    await page.locator('input').nth(0).fill('employee')
    await page.locator('input').nth(1).fill('TestOnly123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/workbench')
    const inButton = page.getByRole('button', { name: '上班打卡', exact: true })
    const outButton = page.getByRole('button', { name: '下班打卡', exact: true })
    await inButton.waitFor(); await page.waitForFunction(() => !document.querySelector('.today-panel')?.classList.contains('is-loading'))
    assert.equal(await outButton.isDisabled(), true)
    passed.push('普通员工登录进入工作台且未上班不可下班')
    await inButton.click()
    await page.getByText('模拟打卡失败', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('.today-panel .el-button.is-loading'))
    assert.equal(inCalls, 1); assert.equal(await outButton.isDisabled(), true)
    passed.push('打卡失败不误报成功并刷新服务器状态')
    failClockIn = false
    await inButton.click()
    await page.getByRole('button', { name: '已完成上班打卡', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '已完成上班打卡', exact: true }).isDisabled(), true)
    await page.getByText('迟到，待下班', { exact: true }).first().waitFor()
    passed.push('上班打卡完成后禁止重复并显示服务端判定')
    await outButton.click()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(outCalls, 0)
    passed.push('取消下班确认不发送打卡请求')
    await outButton.click()
    await page.getByRole('button', { name: '确认打卡', exact: true }).click()
    await page.getByRole('button', { name: '已完成下班打卡', exact: true }).waitFor()
    await page.getByText('迟到且早退', { exact: true }).first().waitFor()
    assert.equal(outCalls, 1)
    passed.push('下班打卡完成并刷新个人记录')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.locator('.el-message').first().waitFor({ state: 'hidden' })
    if (process.env.ATTENDANCE_SCREENSHOT) await page.screenshot({ path: process.env.ATTENDANCE_SCREENSHOT, fullPage: true })
    passed.push('390px工作台无整页横向溢出')
    await page.goto(base + '/admin/attendance')
    await page.waitForURL('**/workbench')
    passed.push('员工前端不可进入管理考勤页')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await page.waitForURL('**/login')
    await page.goto(base + '/login?redirect=/admin/users')
    await page.locator('input').nth(0).fill('admin'); await page.locator('input').nth(1).fill('admin123')
    await page.getByRole('button', { name: '登录', exact: true }).click(); await page.waitForURL('**/admin/users')
    await page.getByRole('link', { name: '考勤管理', exact: true }).click()
    await page.getByRole('heading', { name: '固定作息规则', exact: true }).waitFor()
    await page.getByRole('button', { name: '保存规则', exact: true }).click()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(ruleCalls, 0)
    passed.push('取消规则确认不保存')
    await page.getByRole('button', { name: '保存规则', exact: true }).click()
    await page.getByRole('button', { name: '确认保存', exact: true }).click()
    await page.getByText('考勤规则已保存', { exact: true }).waitFor()
    assert.equal(ruleCalls, 1)
    passed.push('管理员保存作息规则并重新读取')
    await page.getByPlaceholder('输入员工关键词').fill('employee')
    const filteredResponse = page.waitForResponse(response => response.url().includes('keyword=employee'))
    await page.getByRole('button', { name: '查询记录', exact: true }).click()
    await filteredResponse
    assert.equal(lastQuery.keyword, 'employee')
    passed.push('管理员员工关键词筛选')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    passed.push('390px管理员考勤页无整页横向溢出')
    expire = true
    await page.getByRole('button', { name: '重新读取规则', exact: true }).click()
    await page.waitForURL('**/login')
    assert.equal(await page.evaluate(() => localStorage.getItem('accessToken')), null)
    passed.push('账号失效清除会话并退出')
    assert.deepEqual(errors, []); assert.equal(await page.evaluate(() => window.__unhandled), undefined)
    passed.push('无页面异常和未处理拒绝')
    console.log(JSON.stringify({ passed: passed.length, checks: passed, mode: 'browser mocked API; no database writes' }, null, 2))
  } finally { if (browser) await browser.close(); server.kill() }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
