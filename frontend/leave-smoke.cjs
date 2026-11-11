// Functional browser checks using mocked API; does not write business data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path')
const assert = require('node:assert/strict')

async function run() {
  const base = 'http://127.0.0.1:5203'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5203', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let logs = '', browser
  server.stdout.on('data', data => { logs += data }); server.stderr.on('data', data => { logs += data })
  const checks = []
  try {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (server.exitCode !== null) throw new Error(logs)
      try { if ((await fetch(base)).ok) break } catch {}
      if (attempt === 99) throw new Error('Vite startup timeout')
      await new Promise(resolve => setTimeout(resolve, 200))
    }
    browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, timezoneId: 'America/New_York' })
    page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => window.addEventListener('unhandledrejection', event => { window.__unhandled = String(event.reason) }))
    let user = { id: 9, userName: 'employee', realName: '验收员工', roleCode: 'EMPLOYEE', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    let items = [], reviewCalls = 0, createCalls = 0, failList = false, failCreate = false, unavailable = false, crossDayAllowed = true, managerCalls = 0, lastQuery
    await page.addInitScript(value => { if (!localStorage.getItem('accessToken')) { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) } }, user)
    await page.route(base + '/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let data = null, code = 200, msg = 'success'
      if (url.pathname === '/api/user/me') data = user
      else if (url.pathname === '/api/leave/options') data = { leaveTypes: ['事假', '病假', '年假'], minimumMinutes: 30, crossDayAllowed, timeZone: 'Asia/Shanghai', approverName: unavailable ? undefined : '部门主管', unavailableReason: unavailable ? '所属部门尚未配置请假审批负责人，请联系管理员' : undefined }
      else if (url.pathname === '/api/leave' && req.method() === 'POST') {
        createCalls++
        if (failCreate) { code = 400; msg = '与待审批或已批准的请假时间重叠，请勿重复申请' }
        else { data = { ...req.postDataJSON(), id: createCalls, userId: 9, realName: '验收员工', departmentId: 1, status: 'PENDING', createTime: '2026-10-28T09:00:00' }; items = [data, ...items] }
      } else if (url.pathname === '/api/leave' || url.pathname === '/api/leave/reviews') {
        lastQuery = Object.fromEntries(url.searchParams)
        if (failList) { code = 400; msg = '模拟查询失败' }
        else { const filtered = items.filter(item => !lastQuery.status || item.status === lastQuery.status); data = { records: filtered, total: filtered.length, pageNum: 1, pageSize: 10 } }
      } else if (/\/leave\/\d+\/review$/.test(url.pathname)) {
        reviewCalls++; const value = req.postDataJSON(); items = items.map(item => ({ ...item, ...value, reviewerId: 2, reviewedAt: '2026-10-28T10:00:00' }))
      } else if (url.pathname === '/api/admin/users') data = [{ ...user, id: 2, userName: 'manager', realName: '部门主管', roleCode: 'DEPARTMENT_MANAGER' }]
      else if (url.pathname === '/api/admin/departments') data = [{ id: 1, departmentName: '研发部', departmentCode: 'DEV', isEnabled: 1, sortOrder: 0, managerUserId: 2 }]
      else if (url.pathname === '/api/admin/positions') data = []
      else if (url.pathname.endsWith('/manager')) managerCalls++
      return route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/leave'); await page.getByRole('heading', { name: '请假与审批', exact: true }).waitFor()
    await page.getByText('部门主管', { exact: false }).waitFor()
    assert.equal(await page.getByRole('tab', { name: '员工请假审批' }).count(), 0)
    checks.push('员工仅显示本人请假入口')
    await page.getByRole('button', { name: '提交请假', exact: true }).click()
    await page.getByText('请填写请假原因', { exact: true }).waitFor(); assert.equal(createCalls, 0)
    checks.push('空表单不发送申请')
    await page.locator('.leave-panel .el-select').first().click(); await page.getByRole('option', { name: '事假', exact: true }).click()
    await page.getByPlaceholder('选择开始时间').fill('2026-11-02 09:00'); await page.getByPlaceholder('选择开始时间').press('Tab')
    await page.getByPlaceholder('选择结束时间').fill('2026-11-02 10:00'); await page.getByPlaceholder('选择结束时间').press('Tab')
    await page.getByPlaceholder('请填写请假原因').fill('个人事务')
    await page.getByRole('link',{name:'返回个人工作台',exact:true}).click();await page.getByRole('button',{name:'继续填写',exact:true}).click();assert.equal(new URL(page.url()).pathname,'/leave');assert.equal(await page.getByPlaceholder('请填写请假原因').inputValue(),'个人事务');checks.push('未提交离开有确认且取消保留表单')
    await page.getByRole('button',{name:'清空申请表单',exact:true}).click();await page.getByRole('button',{name:'继续填写',exact:true}).click();assert.equal(await page.getByPlaceholder('请填写请假原因').inputValue(),'个人事务');checks.push('清空申请须确认且取消保留内容')
    await page.getByPlaceholder('选择结束时间').fill('2026-11-02 09:45');await page.getByPlaceholder('选择结束时间').press('Tab')
    await page.getByRole('button',{name:'提交请假',exact:true}).click();assert.equal(createCalls,0);await page.getByText('请假时长须为30分钟的整数倍',{exact:true}).first().waitFor();checks.push('非规则整数倍时长在提交前拦截')
    crossDayAllowed=false;await page.getByRole('button',{name:'刷新请假数据',exact:true}).click()
    await page.getByPlaceholder('选择结束时间').fill('2026-11-03 09:00');await page.getByPlaceholder('选择结束时间').press('Tab');await page.getByRole('button',{name:'提交请假',exact:true}).click();assert.equal(createCalls,0);await page.getByText('当前规则不允许跨日请假',{exact:true}).first().waitFor();checks.push('跨日限制与服务端规则一致且不发送非法申请')
    await page.getByPlaceholder('选择结束时间').fill('2026-11-02 10:00');await page.getByPlaceholder('选择结束时间').press('Tab');await page.getByText('日历时长：60 分钟。包含非工作时间；实际考勤覆盖按已批准的工作时段计算。',{exact:true}).waitFor();checks.push('非上海浏览器时区仍按服务器墙上时间展示日历时长')
    failCreate = true
    await page.getByRole('button', { name: '提交请假', exact: true }).click(); await page.getByText('与待审批或已批准的请假时间重叠，请勿重复申请', { exact: true }).waitFor()
    assert.equal(await page.getByPlaceholder('请填写请假原因').inputValue(), '个人事务')
    checks.push('提交失败保留表单并显示服务端提示')
    failCreate = false
    await page.getByRole('button', { name: '提交请假', exact: true }).click(); await page.getByText('请假申请已提交', { exact: true }).waitFor()
    await page.getByRole('cell', { name: '待审批', exact: true }).waitFor(); assert.equal(createCalls, 2)
    checks.push('提交成功刷新记录且重置表单')
    assert.equal(await page.getByPlaceholder('请填写请假原因').inputValue(),'');assert.ok(await page.getByRole('button',{name:'清空申请表单',exact:true}).isDisabled());checks.push('提交成功清除未提交状态')
    failList = true
    await page.getByRole('button', { name: '刷新请假数据', exact: true }).click(); await page.getByText('请假记录加载失败，请刷新重试', { exact: true }).waitFor()
    failList = false
    await page.getByRole('button', { name: '刷新请假数据', exact: true }).click(); await page.getByRole('cell', { name: '待审批', exact: true }).waitFor()
    checks.push('列表加载失败可刷新恢复')
    await page.setViewportSize({ width: 390, height: 844 })
    items[0].reason='长原因测试'.repeat(80)+'\n第二段说明';await page.getByRole('button',{name:'刷新请假数据',exact:true}).click();await page.getByRole('button',{name:'查看详情',exact:true}).click()
    const detail=page.getByRole('dialog',{name:'请假申请详情',exact:true});await detail.getByText(items[0].reason,{exact:true}).waitFor();await detail.getByText('等待审批',{exact:true}).waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.getByRole('button',{name:'关闭请假详情',exact:true}).click();checks.push('手机详情完整显示长原因与等待状态且无整页溢出')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.waitForFunction(() => document.querySelectorAll('.el-message').length === 0)
    await page.screenshot({ path: path.join(__dirname, 'leave-mobile.png'), fullPage: true })
    checks.push('390px请假页无整页横向溢出')
    unavailable = true
    await page.getByRole('button', { name: '刷新请假数据', exact: true }).click(); await page.getByText('所属部门尚未配置请假审批负责人，请联系管理员', { exact: true }).waitFor()
    assert.ok(await page.getByRole('button', { name: '提交请假', exact: true }).isDisabled())
    checks.push('缺少审批负责人时禁用提交并解释原因')
    unavailable = false; user = { ...user, id: 2, roleCode: 'DEPARTMENT_MANAGER', realName: '部门主管' }
    await page.goto(base + '/leave'); await page.getByRole('tab', { name: '员工请假审批' }).click()
    await page.getByRole('button', { name: '批准', exact: true }).click(); await page.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(reviewCalls, 0); checks.push('取消审批不发送请求')
    await page.getByRole('button', { name: '拒绝', exact: true }).click()
    await page.getByRole('button', { name: '确认拒绝', exact: true }).click(); assert.equal(reviewCalls, 0)
    await page.locator('.el-message-box textarea').fill('请调整时间')
    await page.getByRole('button', { name: '确认拒绝', exact: true }).click()
    await page.getByRole('cell', { name: '已拒绝', exact: true }).waitFor(); assert.equal(reviewCalls, 1)
    checks.push('拒绝必须填写原因且处理后不再显示审批按钮')
    await page.getByRole('tab', { name: '我的请假', exact: true }).click()
    await page.getByRole('cell', { name: '请调整时间', exact: true }).waitFor()
    checks.push('本人记录显示拒绝原因')
    await page.getByRole('button',{name:'查看详情',exact:true}).click();await detail.getByText('请调整时间',{exact:true}).waitFor();await detail.getByText('2026-10-28 10:00',{exact:true}).waitFor();await page.getByRole('button',{name:'关闭请假详情',exact:true}).click();checks.push('拒绝详情显示完整审批意见与处理时间')
    await page.locator('.status-select').click(); await page.getByRole('option', { name: '已拒绝', exact: true }).click()
    const filtered = page.waitForResponse(response => response.url().includes('status=REJECTED'))
    await page.getByRole('button', { name: '查询请假', exact: true }).click(); await filtered
    assert.equal(lastQuery.status, 'REJECTED'); checks.push('状态筛选传递给服务端')
    user = { ...user, id: 1, roleCode: 'ADMIN' }
    await page.evaluate(value => localStorage.setItem('currentUser', JSON.stringify(value)), user)
    await page.goto(base + '/admin/organization'); await page.getByRole('tab', { name: '请假审批负责人' }).click()
    await page.getByRole('button', { name: '保存负责人', exact: true }).click(); await page.getByRole('button', { name: '确认设置', exact: true }).click()
    await page.getByText('审批负责人已设置', { exact: true }).waitFor(); assert.equal(managerCalls, 1)
    checks.push('管理员可在组织页面保存部门审批负责人')
    assert.deepEqual(errors, []); assert.equal(await page.evaluate(() => window.__unhandled), undefined)
    checks.push('无页面错误或未处理拒绝')
    console.log(JSON.stringify({ passed: checks.length, checks, mode: 'mocked API; no business data writes' }, null, 2))
  } finally { if (browser) await browser.close(); server.kill() }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
