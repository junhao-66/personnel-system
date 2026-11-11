// Browser interactions use synthetic responses. Database behavior is tested separately.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5210'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5210', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
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
    const admin = { id: 1, userName: 'admin', realName: '管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    const manager = { ...admin, id: 2, userName: 'manager', realName: '部门负责人', roleCode: 'DEPARTMENT_MANAGER' }
    const deputy = { ...manager, id: 3, userName: 'deputy', realName: '另一负责人' }
    const department = { id: 7, departmentName: '研发部', departmentCode: 'DEV', managerUserId: 2, sortOrder: 0, isEnabled: 1 }
    const fullLeave = { userId: 1, attendanceDate: '2026-10-28', workStartTime: '09:00:00', workEndTime: '18:00:00', restDay: false,
      late: false, early: false, status: 'APPROVED_LEAVE', approvedLeaveMinutes: 540, missingIn: false, missingOut: false, scheduleBasis: 'CURRENT_RULE' }
    let latestQuery, failRecords = false, failConfig = false, configCalls = 0
    await page.addInitScript(user => { localStorage.setItem('accessToken', 'test'); localStorage.setItem('currentUser', JSON.stringify(user)) }, admin)
    await page.route(base + '/api/**', async route => {
      const request = route.request(), url = new URL(request.url()); let data = null
      if (url.pathname === '/api/user/me') data = admin
      else if (url.pathname === '/api/attendance/today') data = { attendanceDate: '2026-10-28', serverTime: '2026-10-28T19:00:00', workStartTime: '09:00:00', workEndTime: '18:00:00', restDay: false, timeZone: 'Asia/Shanghai', dayStatus: fullLeave }
      else if (url.pathname.endsWith('/attendance/records')) {
        latestQuery = Object.fromEntries(url.searchParams)
        if (failRecords) return route.fulfill({ json: { code: 400, msg: '模拟考勤读取失败' } })
        data = { records: [{ attendance: fullLeave }, { attendance: { ...fullLeave, attendanceDate: '2026-10-27', status: 'MISSING_BOTH', approvedLeaveMinutes: 0, missingIn: true, missingOut: true, requiredStart: '2026-10-27T09:00:00', requiredEnd: '2026-10-27T18:00:00' } }], total: 2, pageNum: 1, pageSize: 10 }
      } else if (url.pathname === '/api/admin/departments') data = [department]
      else if (url.pathname === '/api/admin/users') data = [admin, manager, deputy]
      else if (url.pathname === '/api/admin/positions') data = []
      else if (url.pathname.endsWith('/self-approver')) {
        configCalls++
        if (failConfig) return route.fulfill({ json: { code: 400, msg: '模拟配置失败' } })
        department.selfApproverUserId = request.postDataJSON().managerUserId
      }
      return route.fulfill({ json: { code: 200, msg: 'success', data } })
    })
    await page.goto(base + '/workbench')
    await page.locator('.today-panel').getByText('工作时段已获准请假', { exact: true }).waitFor(); checks.push('全天已批请假显示无需到岗')
    assert.equal(await page.getByRole('button', { name: '上班打卡', exact: true }).isEnabled(), true); checks.push('无需到岗仍允许自愿留存打卡')
    await page.getByText('上下班均缺卡', { exact: true }).waitFor()
    assert.equal(latestQuery.includeMissing, 'true'); checks.push('默认查询每日状态并展示缺卡日')
    await page.getByText('当前规则（参考）', { exact: true }).first().waitFor(); checks.push('未打卡日明确当前规则参考依据')
    await Promise.all([page.waitForResponse(response => response.url().includes('/attendance/records?') && response.url().includes('includeMissing=false')), page.locator('.el-switch').click()])
    assert.equal(latestQuery.includeMissing, 'false'); checks.push('可切换仅查询实际打卡记录')
    failRecords = true; await page.getByRole('button', { name: '查询记录', exact: true }).click()
    await page.getByText('考勤记录加载失败，当前数据可能不是最新，请重新查询。', { exact: true }).waitFor()
    failRecords = false; await page.getByRole('button', { name: '查询记录', exact: true }).click()
    await page.getByText('考勤记录加载失败，当前数据可能不是最新，请重新查询。', { exact: true }).waitFor({ state: 'hidden' }); checks.push('每日状态失败可重试')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); checks.push('手机工作台无页面横向溢出')
    await page.screenshot({ path: path.join(__dirname, 'attendance-leave-mobile.png'), fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(base + '/admin/organization')
    await page.getByRole('tab', { name: '请假审批负责人', exact: true }).click()
    const panel = page.locator('#pane-managers')
    const select = panel.locator('.el-select').nth(1)
    await select.click(); await page.getByRole('option', { name: '另一负责人（deputy）', exact: true }).click()
    await panel.getByRole('button', { name: '保存本人审批配置' }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(configCalls, 0); checks.push('取消配置确认不提交')
    failConfig = true; await panel.getByRole('button', { name: '保存本人审批配置' }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '保存配置', exact: true }).click()
    await page.getByText('模拟配置失败', { exact: true }).waitFor(); assert.match(await select.innerText(), /另一负责人/); checks.push('本人审批配置失败保留选择')
    failConfig = false; await panel.getByRole('button', { name: '保存本人审批配置' }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '保存配置', exact: true }).click()
    await page.getByText('本人请假审批人已保存', { exact: true }).waitFor()
    assert.equal(department.selfApproverUserId, 3); checks.push('另一负责人保存并回填')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill() }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
