// Real browser UI with synthetic audit API responses. Never mutates employee data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5206'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5206', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let browser, logs = ''
  server.stdout.on('data', v => { logs += v }); server.stderr.on('data', v => { logs += v })
  const checks = []
  try {
    for (let i = 0; i < 100; i++) {
      if (server.exitCode !== null) throw new Error(logs)
      try { if ((await fetch(base)).ok) break } catch {}
      if (i === 99) throw new Error('Vite startup timeout')
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    page.setDefaultTimeout(10000)
    const errors = [], requests = []; let fail = false, empty = false, slow = false
    page.on('pageerror', e => errors.push(e.message))
    const user = { id: 7, userName: 'audit-admin', realName: '验收管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    const rows = Array.from({ length: 11 }, (_, i) => ({ id: 100 - i, actorUserId: i === 10 ? undefined : 7, actorRole: i === 10 ? 'SYSTEM' : 'ADMIN', operation: 'EMPLOYEE_UPDATE', targetType: 'EMPLOYEE', targetId: 8, changedFields: ['phone', 'employmentStatus'], occurredAt: '2026-10-29T11:00:00' }))
    const extensions = [
      ['LEAVE_CREATE', '提交请假申请', 'LEAVE', '请假申请', 'reason', '请假原因'],
      ['LEAVE_REVIEW', '审批请假', 'LEAVE', '请假申请', 'reviewComment', '审批意见'],
      ['SCALE_IMPORT', '导入量表', 'SCALE', '测评量表', 'definitionJson', '版本内容'],
      ['SCALE_PUBLISH', '发布量表', 'SCALE', '测评量表', 'publishedAt', '发布时间'],
      ['SCALE_ARCHIVE', '停用量表', 'SCALE', '测评量表', 'status', '状态'],
      ['GRAPH_IMPORT', '导入知识图谱', 'GRAPH', '知识图谱', 'definitionJson', '版本内容'],
      ['GRAPH_PUBLISH', '发布知识图谱', 'GRAPH', '知识图谱', 'status', '状态'],
      ['GRAPH_ARCHIVE', '停用知识图谱', 'GRAPH', '知识图谱', 'status', '状态'],
      ['ATTENDANCE_RULE_UPDATE', '修改考勤规则', 'ATTENDANCE_RULE', '考勤规则', 'workStartTime', '上班时间'],
    ]
    await page.route(base + '/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let data = null, code = 200
      if (url.pathname === '/api/user/me') data = user
      if (url.pathname === '/api/admin/operation-audits') {
        assert.equal(req.method(), 'GET'); requests.push(Object.fromEntries(url.searchParams))
        if (slow) { slow = false; await new Promise(resolve => setTimeout(resolve, 500)); data = { records: [{ ...rows[0], id: 999, targetId: 999 }], total: 1, pageNum: 1, pageSize: 10 } }
        else if (fail) code = 400
        else {
          const size = Number(url.searchParams.get('pageSize') || 10), pageNum = Number(url.searchParams.get('pageNum') || 1)
          const extension = extensions.find(value => value[0] === url.searchParams.get('operation'))
          const filtered = empty || url.searchParams.get('targetId') === '99999' ? [] : extension
            ? [{ ...rows[0], operation: extension[0], targetType: extension[2], changedFields: [extension[4]] }] : rows
          data = { records: filtered.slice((pageNum - 1) * size, pageNum * size), total: filtered.length, pageNum, pageSize: size }
        }
      }
      await route.fulfill({ json: { code, msg: code === 200 ? 'success' : '模拟审计查询失败', data } })
    })
    await page.goto(base + '/admin/operation-audits'); await page.waitForURL('**/login?**'); checks.push('未登录跳转登录页')
    await page.addInitScript(value => { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) }, user)
    await page.goto(base + '/admin/operation-audits'); await page.getByRole('heading', { name: '操作审计', exact: true }).waitFor()
    await page.locator('.el-table__body-wrapper').getByText('修改员工档案', { exact: true }).first().waitFor(); assert.equal(await page.locator('.el-table__body tr').count(), 10); checks.push('管理员菜单与审计列表加载')
    await page.getByText('联系方式', { exact: true }).first().waitFor(); await page.getByText('在职状态', { exact: true }).first().waitFor(); assert.ok((await page.locator('.audit-page').innerText()).includes('不保存请假原因')); checks.push('字段中文映射与数据范围提示')
    await page.locator('.el-pagination .btn-next').click(); await page.locator('.el-table__body-wrapper').getByText('系统/内部调用', { exact: false }).waitFor(); assert.equal(requests.at(-1).pageNum, '2'); checks.push('分页与系统操作者展示')
    await page.getByPlaceholder('输入操作者编号').fill('7'); await page.getByPlaceholder('输入对象编号').fill('8'); await page.getByRole('button', { name: '查询', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.el-table__body tr').length === 10)
    assert.equal(requests.at(-1).actorUserId, '7'); assert.equal(requests.at(-1).targetId, '8'); assert.equal(requests.at(-1).pageNum, '1'); checks.push('编号查询重置到第一页')
    const before = requests.length; await page.getByPlaceholder('输入对象编号').fill('-1'); await page.getByRole('button', { name: '查询', exact: true }).click(); await page.getByText('编号须为有效的正整数', { exact: true }).waitFor(); assert.equal(requests.length, before); checks.push('非法编号不发送查询')
    await page.getByRole('button', { name: '重置', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('.el-loading-mask')); assert.equal(requests.at(-1).targetId, undefined); assert.equal(await page.getByPlaceholder('输入对象编号').inputValue(), ''); checks.push('重置清空已应用筛选')
    await page.locator('.audit-filters .el-select').nth(0).click(); await page.getByRole('option', { name: '员工档案', exact: true }).click()
    await page.locator('.audit-filters .el-select').nth(1).click(); await page.getByRole('option', { name: '修改员工档案', exact: true }).click()
    await page.getByRole('button', { name: '查询', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('.el-loading-mask')); assert.equal(requests.at(-1).targetType, 'EMPLOYEE'); assert.equal(requests.at(-1).operation, 'EMPLOYEE_UPDATE'); checks.push('对象及操作类型筛选正确发送')
    for (const [operation, label, targetType, targetLabel, , fieldLabel] of extensions) {
      await page.locator('.audit-filters .el-select').nth(0).click(); await page.getByRole('option', { name: targetLabel, exact: true }).click()
      await page.locator('.audit-filters .el-select').nth(1).click(); await page.getByRole('option', { name: label, exact: true }).click()
      await Promise.all([
        page.waitForResponse(response => response.url().includes('/api/admin/operation-audits?')),
        page.getByRole('button', { name: '查询', exact: true }).click(),
      ])
      await page.locator('.el-table__body-wrapper').getByText(label, { exact: true }).waitFor()
      await page.locator('.el-table__body-wrapper').getByText(fieldLabel, { exact: true }).waitFor()
      assert.equal(requests.at(-1).operation, operation); assert.equal(requests.at(-1).targetType, targetType)
      checks.push(`${label}筛选和字段展示`)
    }
    await page.getByRole('button', { name: '重置', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.el-table__body tr').length === 10)
    await page.getByPlaceholder('开始日期').fill('2026-10-28'); await page.getByPlaceholder('结束日期').fill('2026-10-29'); await page.getByPlaceholder('结束日期').press('Tab')
    await page.getByRole('button', { name: '查询', exact: true }).click(); await page.waitForFunction(() => !document.querySelector('.el-loading-mask')); assert.equal(requests.at(-1).startDate, '2026-10-28'); assert.equal(requests.at(-1).endDate, '2026-10-29'); checks.push('日期范围正确发送')
    fail = true; await page.getByRole('button', { name: '刷新记录', exact: true }).click(); await page.getByText('审计记录加载失败，请刷新重试。', { exact: true }).waitFor(); assert.equal(await page.locator('.el-table__body tr').count(), 0); checks.push('错误时清除旧记录并显示重试入口')
    fail = false; empty = true; await page.getByRole('button', { name: '刷新记录', exact: true }).click(); await page.getByText('暂无符合条件的审计记录', { exact: true }).waitFor(); checks.push('空列表有明确说明')
    empty = false; slow = true; await page.getByRole('button', { name: '刷新记录', exact: true }).click(); await page.getByRole('button', { name: '重置', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.el-table__body tr').length === 10); await page.waitForTimeout(600); assert.ok(!(await page.locator('.el-table__body-wrapper').innerText()).includes('#999')); checks.push('迟到响应不会覆盖新筛选结果')
    await page.screenshot({ path: path.join(__dirname, 'audit-desktop.png'), fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); await page.screenshot({ path: path.join(__dirname, 'audit-mobile.png'), fullPage: true }); checks.push('390px页面无整页横向溢出')
    await page.addInitScript(value => localStorage.setItem('currentUser', JSON.stringify({ ...value, roleCode: 'DEPARTMENT_MANAGER' })), user)
    await page.goto(base + '/admin/operation-audits'); await page.waitForURL('**/workbench'); checks.push('部门负责人无法进入审计管理页面')
    assert.deepEqual(errors, []); checks.push('无浏览器脚本异常')
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill(); await new Promise(resolve => { if (server.exitCode !== null) resolve(); else server.once('exit', resolve) }) }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
