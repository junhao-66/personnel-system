// Synthetic API fixtures exercise the real Vue routes. SQL correctness is tested separately against MySQL.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const { spawn } = require('node:child_process')
const path = require('node:path'), assert = require('node:assert/strict')

async function run() {
  const base = 'http://127.0.0.1:5206'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5206', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let logs = '', browser
  server.stdout.on('data', v => { logs += v }); server.stderr.on('data', v => { logs += v })
  const checks = []
  try {
    for (let i = 0; i < 100; i++) { if (server.exitCode !== null) throw new Error(logs); try { if ((await fetch(base)).ok) break } catch {} if (i === 99) throw new Error('Startup timeout'); await new Promise(resolve => setTimeout(resolve, 200)) }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = [], requests = []
    page.on('pageerror', error => errors.push(error.message))
    const admin = { id: 9, userName: 'test-admin', realName: '验收管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    let user = admin, failDashboard = false, failEmployees = false, failDetail = false, failOptions = false
    const employee = (id, name, departmentId, typeCode, count, employmentStatus = 'ACTIVE') => ({ userId: id, userName: 'test' + id, realName: name, roleCode: 'EMPLOYEE', userStatus: 'ENABLED', departmentId, departmentName: departmentId === 1 ? '研发部' : departmentId === 2 ? '支持部' : '未分配部门', positionId: departmentId === 1 ? 1 : null, positionName: departmentId === 1 ? '工程岗' : '未分配岗位', hireDate: null, employmentStatus, assessmentCount: count, latestResultId: count ? id * 10 : null, typeCode, submittedAt: count ? '2026-10-29T10:00:00' : null, scaleName: count ? '非商业参考量表' : null, scaleVersion: count ? 'v1' : null, scoringVersion: count ? 'test-v1' : null })
    const fixture = [employee(1, '验收甲', 1, 'INTJ', 2), employee(2, '验收乙', 1, null, 0), employee(3, '验收丙', null, 'ENFP', 1), employee(4, '离职验收', 2, 'INTJ', 1, 'RESIGNED')]
    const filtered = params => fixture.filter(e => (!params.has('departmentId') || (Number(params.get('departmentId')) === 0 ? e.departmentId === null : e.departmentId === Number(params.get('departmentId')))) && (!params.has('positionId') || (Number(params.get('positionId')) === 0 ? e.positionId === null : e.positionId === Number(params.get('positionId')))) && (params.get('employmentStatus') === 'ALL' || e.employmentStatus === (params.get('employmentStatus') || 'ACTIVE')) && (params.get('assessmentStatus') !== 'COMPLETED' || e.assessmentCount > 0) && (params.get('assessmentStatus') !== 'NOT_COMPLETED' || e.assessmentCount === 0) && (!params.has('typeCode') || e.typeCode === params.get('typeCode')))
    const rate = (part, total) => total ? Math.round(part * 10000 / total) / 100 : null
    const stats = records => {
      const completed = records.filter(e => e.assessmentCount > 0)
      return { employeeCount: records.length, completedCount: completed.length, notCompletedCount: records.length - completed.length, assessmentCount: records.reduce((sum, e) => sum + e.assessmentCount, 0), completionRate: rate(completed.length, records.length), typeDistribution: ['INTJ', 'INTP', 'ENTJ', 'ENTP', 'INFJ', 'INFP', 'ENFJ', 'ENFP', 'ISTJ', 'ISFJ', 'ESTJ', 'ESFJ', 'ISTP', 'ISFP', 'ESTP', 'ESFP'].map(typeCode => ({ typeCode, employeeCount: completed.filter(e => e.typeCode === typeCode).length, percent: rate(completed.filter(e => e.typeCode === typeCode).length, completed.length) })), departmentDistribution: [...new Set(records.map(e => e.departmentId))].map(departmentId => { const group = records.filter(e => e.departmentId === departmentId), finished = group.filter(e => e.assessmentCount > 0).length; return { departmentId, departmentName: group[0].departmentName, employeeCount: group.length, completedCount: finished, notCompletedCount: group.length - finished, assessmentCount: group.reduce((sum, e) => sum + e.assessmentCount, 0), completionRate: rate(finished, group.length) } }), generatedAt: '2026-10-29T10:00:00', scopeNotice: '仅统计审核通过的人员，类型表示偏好，不代表能力。' }
    }
    await page.route(base + '/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); requests.push({ path: url.pathname, params: Object.fromEntries(url.searchParams), method: req.method() })
      let data = null, code = 200, msg = 'success'
      if (url.pathname === '/api/user/login') data = { accessToken: 'test-token', user }
      else if (url.pathname === '/api/user/me') data = user
      else if (url.pathname === '/api/admin/departments') { if (failOptions) code = 400; else data = [{ id: 1, departmentName: '研发部', departmentCode: 'RD', isEnabled: 1 }, { id: 2, departmentName: '支持部', departmentCode: 'SUP', isEnabled: 0 }] }
      else if (url.pathname === '/api/admin/positions') data = [{ id: 1, positionName: '工程岗', positionCode: 'ENG', departmentId: 1, isEnabled: 1 }]
      else if (url.pathname === '/api/admin/talent/dashboard') { if (failDashboard) code = 400; else data = stats(filtered(url.searchParams)) }
      else if (url.pathname === '/api/admin/talent/employees') {
        if (failEmployees) code = 400
        else { const records = filtered(url.searchParams), pageNum = Number(url.searchParams.get('pageNum') || 1), pageSize = Number(url.searchParams.get('pageSize') || 10); data = { records: records.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: records.length, pageNum, pageSize } }
      } else if (/\/api\/admin\/talent\/employees\/\d+$/.test(url.pathname)) { if (failDetail) code = 400; else data = fixture.find(e => e.userId === Number(url.pathname.split('/').at(-1))) }
      else if (/\/api\/admin\/assessment\/users\/\d+\/history$/.test(url.pathname)) data = { records: [], total: 0, pageNum: 1, pageSize: 10 }
      else if (url.pathname === '/api/attendance/records') data = { records: [], total: 0, pageNum: 1, pageSize: 10 }
      else if (url.pathname === '/api/attendance/today') data = { attendanceDate: '2026-10-29', serverTime: '2026-10-29T10:00:00', workStartTime: '09:00:00', workEndTime: '18:00:00', restDay: false, record: null }
      else throw new Error('Unexpected API: ' + req.method() + ' ' + url.pathname)
      if (code !== 200) msg = '模拟加载失败'
      await route.fulfill({ json: { code, msg, data } })
    })
    const gotoDashboard = async query => { await page.goto(base + '/admin/talent-dashboard' + (query || '')); await page.locator('.metrics').waitFor() }
    const row = name => page.locator('.el-table__row').filter({ hasText: name })
    const metric = name => page.locator('.metric').filter({ has: page.locator('span', { hasText: new RegExp('^' + name + '$') }) })
    await page.goto(base + '/admin/talent-dashboard'); await page.waitForURL('**/login?**'); checks.push('未登录访问看板进入登录')
    await page.evaluate(value => { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) }, admin)
    await page.goto(base + '/admin'); await page.waitForURL('**/talent-dashboard'); await page.locator('.metrics').waitFor(); checks.push('管理员后台默认打开人才看板')
    assert.equal(await metric('审核通过人数').locator('strong').innerText(), '3'); assert.equal(await metric('已完成人数').locator('strong').innerText(), '2'); assert.equal(await metric('有效测评次数').locator('strong').innerText(), '3'); assert.equal(await metric('完成率').locator('strong').innerText(), '66.67%'); checks.push('默认在职统计展示人数、完成率和重复提交次数')
    await page.screenshot({ path: path.join(__dirname, 'talent-desktop.png'), fullPage: true })
    await metric('已完成人数').click(); await page.waitForURL('**/talent-employees?**'); await row('验收甲').waitFor(); assert.ok(page.url().includes('assessmentStatus=COMPLETED')); assert.equal(await page.locator('.el-table__row').count(), 2); checks.push('完成指标下钻携带条件且人数一致')
    await row('验收甲').getByRole('button', { name: '员工详情', exact: true }).click(); await page.getByRole('dialog').getByText('INTJ', { exact: true }).waitFor(); await page.getByRole('button', { name: '查看历次报告与对比' }).click(); await page.waitForURL('**/assessment-history/1'); await page.getByRole('heading', { name: '员工测评历史 · 1' }).waitFor(); assert.ok(requests.some(r => r.path === '/api/admin/assessment/users/1/history')); checks.push('员工详情到该员工历次报告链路可用')
    await gotoDashboard(); await metric('未完成人数').click(); await row('验收乙').waitFor(); assert.equal(await page.locator('.el-table__row').count(), 1); await row('验收乙').getByRole('button', { name: '员工详情' }).click(); await page.getByText('尚未完成测评', { exact: true }).waitFor(); checks.push('未完成下钻与无报告详情有清晰说明')
    await gotoDashboard(); await page.locator('.type-item').filter({ has: page.locator('span', { hasText: /^INTJ$/ }) }).click(); await row('验收甲').waitFor(); assert.ok(page.url().includes('typeCode=INTJ')); assert.equal(await page.locator('.el-table__row').count(), 1); checks.push('类型分布点击进入相同类型员工')
    await gotoDashboard(); await row('未分配部门').getByRole('button', { name: '查看人员' }).click(); await row('验收丙').waitFor(); assert.ok(page.url().includes('departmentId=0')); checks.push('未分配部门可下钻，不误变成全部部门')
    await gotoDashboard('?departmentId=1&positionId=1&employmentStatus=ACTIVE'); assert.equal(await metric('审核通过人数').locator('strong').innerText(), '2'); await metric('审核通过人数').click(); await row('验收乙').waitFor(); assert.equal(await page.locator('.el-table__row').count(), 2); await page.getByRole('link', { name: '返回当前范围看板' }).click(); await page.locator('.metrics').waitFor(); assert.ok(page.url().includes('positionId=1')); checks.push('部门岗位筛选及返回看板保留原条件')
    const selects = page.locator('.filter-form .el-select')
    await selects.nth(2).click(); await page.getByRole('option', { name: '离职', exact: true }).click(); await page.getByRole('button', { name: '查询', exact: true }).click(); await page.locator('.metrics').waitFor(); await page.getByText('类型分布暂无数据', { exact: false }).waitFor(); checks.push('筛选不自动合并矛盾的组织与在职条件')
    await page.getByRole('button', { name: '重置', exact: true }).click(); await page.locator('.metrics').waitFor(); assert.equal(await metric('审核通过人数').locator('strong').innerText(), '3'); await page.goBack(); await page.locator('.metrics').waitFor(); assert.equal(await metric('审核通过人数').locator('strong').innerText(), '0'); checks.push('重置及浏览器返回恢复筛选和统计')
    await gotoDashboard('?employmentStatus=RESIGNED'); assert.equal(await metric('审核通过人数').locator('strong').innerText(), '1'); checks.push('离职筛选与默认在职分开统计')
    await gotoDashboard('?departmentId=999'); assert.equal(await metric('完成率').locator('strong').innerText(), '暂无数据'); assert.equal(await metric('已完成人数').isDisabled(), true); checks.push('空范围显示暂无数据并禁用零人数下钻')
    await gotoDashboard(); failDashboard = true; await page.getByRole('button', { name: '刷新看板' }).click(); await page.getByText('看板加载失败，请刷新重试。当前不展示旧统计。', { exact: true }).waitFor(); assert.equal(await page.locator('.metrics').count(), 0); failDashboard = false; await page.getByRole('button', { name: '刷新看板' }).click(); await page.locator('.metrics').waitFor(); checks.push('失败移除旧统计，刷新可恢复')
    await metric('审核通过人数').click(); await row('验收甲').waitFor(); failDetail = true; await row('验收甲').getByRole('button', { name: '员工详情' }).click(); await page.getByRole('button', { name: '重试详情' }).waitFor(); failDetail = false; await page.getByRole('button', { name: '重试详情' }).click(); await page.getByRole('dialog').getByText('INTJ', { exact: true }).waitFor(); await page.locator('.el-dialog__headerbtn').click(); checks.push('详情失败可重试，不遗留旧员工详情')
    failEmployees = true; await page.getByRole('button', { name: '刷新人员' }).click(); await page.getByText('人员列表加载失败，请刷新重试。', { exact: true }).waitFor(); assert.equal(await page.locator('.el-table__row').count(), 0); failEmployees = false; await page.getByRole('button', { name: '刷新人员' }).click(); await row('验收甲').waitFor(); checks.push('列表失败清除旧人员，刷新可恢复')
    await page.goto(base + '/admin/talent-employees?pageNum=999&pageSize=1'); await page.waitForURL('**pageNum=3**'); await row('验收丙').waitFor(); assert.ok(requests.some(r => r.path === '/api/admin/talent/employees' && r.params.pageNum === '3')); checks.push('超出末页时自动回到实际末页')
    failOptions = true; await gotoDashboard(); await page.getByText('组织选项加载中或加载失败，可重试；当前统计不会使用残缺选项替换筛选。', { exact: true }).waitFor(); failOptions = false; await page.getByRole('button', { name: '重新加载选项' }).click(); await page.getByText('组织选项加载中或加载失败，可重试；当前统计不会使用残缺选项替换筛选。', { exact: true }).waitFor({ state: 'hidden' }); checks.push('组织选项失败可单独重试')
    await page.setViewportSize({ width: 390, height: 844 }); await gotoDashboard(); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); await page.screenshot({ path: path.join(__dirname, 'talent-mobile.png'), fullPage: true }); checks.push('390px看板无整页横向溢出')
    await metric('审核通过人数').click(); await row('验收甲').waitFor(); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); await page.screenshot({ path: path.join(__dirname, 'talent-employees-mobile.png'), fullPage: true }); checks.push('390px明细可访问，表格只在容器内横向滚动')
    user = { ...admin, roleCode: 'DEPARTMENT_MANAGER' }; await page.evaluate(value => localStorage.setItem('currentUser', JSON.stringify(value)), user); await page.goto(base + '/admin/talent-dashboard'); await page.waitForURL('**/workbench'); checks.push('负责人前端路由不能进入管理员看板')
    assert.deepEqual(errors, []); checks.push('无浏览器脚本异常')
    assert.ok(requests.every(r => r.method === 'GET')); checks.push('看板浏览不发出写入请求')
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill(); await new Promise(resolve => { if (server.exitCode !== null) resolve(); else server.once('exit', resolve) }) }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
