// Synthetic API fixtures; the actual browser exercises account and review flows without database writes.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const assert = require('node:assert/strict'), path = require('node:path')
async function run() {
  const base = 'http://127.0.0.1:5207'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5207', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let browser, logs = ''; server.stdout.on('data', v => { logs += v }); server.stderr.on('data', v => { logs += v })
  const checks = []
  try {
    for (let i = 0; i < 100; i++) {
      if (server.exitCode !== null) throw new Error(logs)
      try { if ((await fetch(base)).ok) break } catch {}
      if (i === 99) throw new Error('Vite startup timeout')
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    const admin = { id: 7, userName: 'review-admin', realName: '验收管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    const employee = { ...admin, id: 8, userName: 'review-employee', realName: '验收员工', roleCode: 'EMPLOYEE' }
    const pending = { ...employee, id: 9, userName: 'review-pending', realName: '待审核员工', auditStatus: 'PENDING' }
    let state = 'PENDING', failReview = false, accessFail = false, reviewCalls = 0
    const accessRequests = [], auditRequests = []
    await page.route(base + '/api/**', async route => {
      const request = route.request(), url = new URL(request.url()); let data = null, code = 200, msg = 'success'
      if (url.pathname === '/api/user/me') data = admin
      else if (url.pathname === '/api/user/review-status') {
        reviewCalls++; assert.equal(request.method(), 'POST')
        if (failReview) { code = 400; msg = '用户名或密码错误' }
        else data = { userName: request.postDataJSON().userName, auditStatus: state, userStatus: 'ENABLED', auditReason: state === 'REJECTED' ? '请核实入职信息' : undefined, reviewedAt: state === 'PENDING' ? undefined : '2026-10-29T11:00:00' }
      } else if (url.pathname === '/api/admin/users') data = [admin, employee, pending]
      else if (url.pathname === '/api/admin/departments' || url.pathname === '/api/admin/positions') data = []
      else if (url.pathname === '/api/admin/users/8/access') {
        accessRequests.push(request.postDataJSON()); if (accessFail) { code = 400; msg = '模拟权限保存失败' }
        else Object.assign(employee, request.postDataJSON())
      } else if (url.pathname === '/api/user/pending') data = pending.auditStatus === 'PENDING' ? [pending] : []
      else if (url.pathname === '/api/user/9/audit') { auditRequests.push(request.postDataJSON()); Object.assign(pending, request.postDataJSON()) }
      await route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/login'); await page.getByRole('button', { name: '查询注册审核状态', exact: true }).click(); await page.waitForURL('**/review-status'); checks.push('登录页可进入审核状态查询')
    await page.getByRole('button', { name: '查询审核结果', exact: true }).click(); await page.getByText('请输入注册用户名', { exact: true }).waitFor(); assert.equal(reviewCalls, 0); checks.push('审核查询必填校验')
    const username = page.locator('.review-card input[autocomplete="username"]'), password = page.locator('.review-card input[autocomplete="current-password"]')
    await username.fill('review-pending'); await password.fill('TestOnly123'); await page.getByRole('button', { name: '查询审核结果', exact: true }).click(); await page.getByRole('heading', { name: '待审核', exact: true }).waitFor(); assert.equal(await password.inputValue(), ''); assert.equal(await page.evaluate(() => localStorage.getItem('accessToken')), null); checks.push('待审核可查询本人状态且不创建登录会话')
    state = 'REJECTED'; await password.fill('TestOnly123'); await page.getByRole('button', { name: '查询审核结果', exact: true }).click(); await page.getByText('拒绝原因：请核实入职信息', { exact: true }).waitFor(); checks.push('拒绝原因与审核时间可查询')
    await username.fill('another-account'); assert.equal(await page.locator('.review-result').count(), 0); checks.push('切换账号清除原审核结果')
    failReview = true; await password.fill('WrongPassword'); await page.getByRole('button', { name: '查询审核结果', exact: true }).click(); await page.getByText('查询失败，请核对账号密码后重试。', { exact: true }).waitFor(); assert.equal(await page.locator('.review-result').count(), 0); checks.push('凭据错误不暴露审核内容')
    failReview = false; state = 'APPROVED'; await password.fill('TestOnly123'); await page.getByRole('button', { name: '查询审核结果', exact: true }).click(); await page.getByText('账号已审核通过，可以返回登录页登录。', { exact: true }).waitFor(); checks.push('查询失败可重试并展示审核通过状态')
    await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); await page.screenshot({ path: path.join(__dirname, 'account-review-mobile.png'), fullPage: true }); checks.push('移动端审核查询无横向溢出')
    await page.addInitScript(value => { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) }, admin)
    await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto(base + '/admin/users')
    await page.getByRole('button', { name: '当前登录账号', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '当前登录账号', exact: true }).isDisabled(), true); checks.push('当前登录账号不能从界面停用或改角色')
    const employeeRow = page.locator('.el-table__body tr').filter({ hasText: 'review-employee' })
    await employeeRow.getByRole('button', { name: '调整账号权限', exact: true }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '目标账号：review-employee' })
    await dialog.locator('.el-select').nth(0).click(); await page.getByRole('option', { name: '部门负责人', exact: true }).click()
    await dialog.locator('.el-select').nth(1).click(); await page.getByRole('option', { name: '停用', exact: true }).click()
    await dialog.getByRole('button', { name: '保存账号权限', exact: true }).click(); await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click(); assert.equal(accessRequests.length, 0); checks.push('取消权限确认不会发送变更')
    accessFail = true; await dialog.getByRole('button', { name: '保存账号权限', exact: true }).click(); await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click(); await page.getByText('模拟权限保存失败', { exact: true }).waitFor(); assert.equal(await dialog.isVisible(), true); checks.push('权限保存失败保留编辑内容')
    accessFail = false; await dialog.getByRole('button', { name: '保存账号权限', exact: true }).click(); await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click(); await dialog.waitFor({ state: 'hidden' }); assert.deepEqual(accessRequests.at(-1), { roleCode: 'DEPARTMENT_MANAGER', userStatus: 'DISABLED' }); await employeeRow.getByText('停用', { exact: true }).waitFor(); checks.push('角色及停用状态保存后刷新列表')
    await page.goto(base + '/admin/review'); await page.getByRole('button', { name: '拒绝', exact: true }).click()
    const prompt = page.locator('.el-message-box')
    await prompt.getByRole('button', { name: '确定', exact: true }).click(); await page.getByText('请填写500字以内的拒绝原因', { exact: true }).waitFor(); assert.equal(auditRequests.length, 0); checks.push('拒绝审核必须填写原因')
    await prompt.locator('textarea').fill('请核实入职信息'); await prompt.getByRole('button', { name: '取消', exact: true }).click(); assert.equal(auditRequests.length, 0); checks.push('取消拒绝审核不产生变更')
    await page.getByRole('button', { name: '拒绝', exact: true }).click(); await prompt.locator('textarea').fill('请核实入职信息'); await prompt.getByRole('button', { name: '确定', exact: true }).click(); await page.getByText('暂无待审核申请', { exact: true }).waitFor(); assert.deepEqual(auditRequests.at(-1), { auditStatus: 'REJECTED', auditReason: '请核实入职信息' }); checks.push('拒绝原因提交后移出待审核列表')
    await page.goto(base + '/admin/users'); await page.getByText('请核实入职信息', { exact: true }).waitFor(); await page.screenshot({ path: path.join(__dirname, 'account-admin-desktop.png'), fullPage: true }); checks.push('管理员可查询已保存拒绝原因')
    assert.deepEqual(errors, []); checks.push('无浏览器脚本异常')
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill(); await new Promise(resolve => { if (server.exitCode !== null) resolve(); else server.once('exit', resolve) }) }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
