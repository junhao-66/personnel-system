// Run with PLAYWRIGHT_MODULE pointing to the installed playwright package.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const assert = require('node:assert/strict')
const path = require('node:path')

async function run() {
  const port = 5199
  const base = `http://127.0.0.1:${port}`
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let serverLog = ''
  server.stdout.on('data', chunk => { serverLog += chunk })
  server.stderr.on('data', chunk => { serverLog += chunk })
  let browser
  const passed = []
  try {
    for (let i = 0; i < 60; i += 1) {
      if (server.exitCode !== null) throw new Error(serverLog)
      try { if ((await fetch(base)).ok) break } catch {}
      await new Promise(resolve => setTimeout(resolve, 250))
      if (i === 59) throw new Error('Vite startup timeout: ' + serverLog)
    }
    browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const errors = []
    page.on('pageerror', error => { errors.push(error.message); console.error('PAGE ERROR:', error.message) })
    page.on('console', message => { if (message.type() === 'error') console.error('BROWSER:', message.text()) })
    await page.addInitScript(() => window.addEventListener('unhandledrejection', event => { window.__unhandled = String(event.reason) }))
    const admin = { id: 1, userName: 'admin', realName: '系统管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    let loginCalls = 0
    let createCalls = 0
    let authExpired = false
    let duplicate = false
    let employeeUpdateCalls = 0
    let employeeSaveFails = false
    let employeeSaveData
    let lastEmployeeQuery
    const employees = Array.from({ length: 11 }, (_, index) => ({
      id: index + 10, userName: `staff${index}`, realName: `档案员工${index}`, roleCode: 'EMPLOYEE',
      departmentId: 1, departmentName: '研发部', positionId: 1, positionName: '开发岗位',
      hireDate: '2025-01-01', employmentStatus: 'ACTIVE', userStatus: 'ENABLED',
    }))
    await page.route(new RegExp(`^http://127\\.0\\.0\\.1:${port}/api/`), async route => {
      const url = new URL(route.request().url())
      let data = null
      let code = 200
      let msg = 'success'
      if (authExpired && url.pathname.startsWith('/api/admin/')) return route.fulfill({ status: 401, json: { code: 401, msg: 'expired' } })
      if (url.pathname === '/api/user/login') {
        loginCalls += 1
        if (route.request().postDataJSON().password !== 'admin123') { code = 400; msg = '用户名或密码错误' }
        else data = { accessToken: 'test-token', user: admin }
      } else if (/^\/api\/admin\/employees\/\d+$/.test(url.pathname) && route.request().method() === 'PUT') {
        employeeUpdateCalls += 1
        employeeSaveData = route.request().postDataJSON()
        if (employeeSaveFails) { code = 400; msg = '档案保存失败' }
        else Object.assign(employees.find(item => item.id === Number(url.pathname.split('/').pop())), employeeSaveData)
      } else if (url.pathname === '/api/admin/employees') {
        lastEmployeeQuery = Object.fromEntries(url.searchParams)
        const keyword = url.searchParams.get('keyword') || ''
        const selected = employees.filter(item => item.realName.includes(keyword) || item.userName.includes(keyword))
          .filter(item => !url.searchParams.get('employmentStatus') || item.employmentStatus === url.searchParams.get('employmentStatus'))
        const pageNum = Number(url.searchParams.get('pageNum') || 1)
        const pageSize = Number(url.searchParams.get('pageSize') || 10)
        data = { records: selected.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: selected.length, pageNum, pageSize }
      } else if (url.pathname === '/api/admin/users' && route.request().method() === 'POST') {
        createCalls += 1
        if (duplicate) { code = 400; msg = '用户名已存在' } else data = { ...admin, id: 2 }
      } else if (url.pathname === '/api/admin/users') data = [admin]
      else if (url.pathname === '/api/admin/departments') data = [
        { id: 1, departmentName: '研发部', departmentCode: 'DEV', isEnabled: 1 },
        { id: 2, departmentName: '人事部', departmentCode: 'HR', isEnabled: 1 },
      ]
      else if (url.pathname === '/api/admin/positions') data = [
        { id: 1, positionName: '开发岗位', positionCode: 'DEV', departmentId: 1, isEnabled: 1 },
        { id: 2, positionName: '人事岗位', positionCode: 'HR', departmentId: 2, isEnabled: 1 },
      ]
      else if (url.pathname === '/api/user/pending') data = [{ ...admin, id: 3, realName: '待审员工', roleCode: 'EMPLOYEE' }]
      else if (url.pathname === '/api/system/health') data = { status: 'UP', service: 'personnel-system' }
      return route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/admin/users')
    await page.waitForURL('**/login?**')
    passed.push('未登录访问后台跳转登录')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.getByText('请输入用户名', { exact: true }).waitFor()
    assert.equal(loginCalls, 0)
    passed.push('登录空表单不发送请求')
    await page.locator('input').nth(0).fill('admin')
    await page.locator('input').nth(1).fill('wrong-password')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.getByText('用户名或密码错误', { exact: true }).waitFor()
    assert.match(page.url(), /login/)
    passed.push('HTTP 200 业务失败不会误报登录成功')
    await page.locator('input').nth(1).fill('admin123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL('**/admin/users')
    await page.getByRole('heading', { name: '用户与角色' }).waitFor()
    passed.push('管理员登录后返回原目标页面')
    await page.getByRole('button', { name: '创建用户', exact: true }).click()
    await page.getByRole('button', { name: '创建并启用' }).click()
    await page.getByText('请输入姓名', { exact: true }).waitFor()
    assert.equal(createCalls, 0)
    passed.push('创建用户必填校验')
    const dialog = page.getByRole('dialog')
    await dialog.locator('input').nth(0).fill('testuser')
    await dialog.locator('input').nth(1).fill('private123')
    await dialog.locator('input').nth(2).fill('验收用户')
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await page.getByRole('button', { name: '创建用户', exact: true }).click()
    assert.equal(await dialog.locator('input').nth(1).inputValue(), '')
    passed.push('关闭创建弹窗清空密码及表单')
    await dialog.locator('input').nth(0).fill('testuser')
    await dialog.locator('input').nth(1).fill('private123')
    await dialog.locator('input').nth(2).fill('验收用户')
    await dialog.locator('.el-select').nth(1).click()
    await page.getByRole('option', { name: '研发部' }).click()
    await dialog.locator('.el-select').nth(2).click()
    await page.getByRole('option', { name: '开发岗位' }).waitFor()
    assert.equal(await page.getByRole('option', { name: '人事岗位' }).count(), 0)
    await page.getByRole('option', { name: '开发岗位' }).click()
    await dialog.locator('.el-select').nth(1).click()
    await page.getByRole('option', { name: '人事部' }).click()
    assert.ok(!(await dialog.locator('.el-select').nth(2).innerText()).includes('开发岗位'))
    passed.push('岗位按部门过滤且切换部门清空旧岗位')
    duplicate = true
    await page.getByRole('button', { name: '创建并启用' }).click()
    await page.getByText('用户名已存在', { exact: true }).waitFor()
    assert.equal(await dialog.isVisible(), true)
    passed.push('创建失败显示错误并保留弹窗')
    await page.getByRole('button', { name: '取消', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await page.getByRole('link', { name: '注册审核', exact: true }).click()
    await page.getByRole('button', { name: '拒绝', exact: true }).click()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    passed.push('取消审核确认框正常返回')
    await page.getByRole('link', { name: '部门与岗位', exact: true }).click()
    await page.getByRole('heading', { name: '部门与岗位' }).waitFor()
    await page.getByRole('button', { name: '新增部门' }).click()
    await page.getByText('请输入部门名称', { exact: true }).waitFor()
    passed.push('组织管理导航和必填校验')
    await page.getByRole('link', { name: '员工档案', exact: true }).click()
    await page.getByRole('heading', { name: '员工档案' }).waitFor()
    await page.getByText('档案员工0', { exact: true }).waitFor()
    assert.equal(await page.locator('.el-table__body-wrapper tbody tr').count(), 10)
    passed.push('员工档案导航及分页列表')
    await page.locator('.el-pagination .btn-next').click()
    await page.getByText('档案员工10', { exact: true }).waitFor()
    assert.equal(lastEmployeeQuery.pageNum, '2')
    passed.push('员工档案翻页请求与结果')
    await page.getByPlaceholder('输入关键词').fill('staff0')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await page.getByText('档案员工0', { exact: true }).waitFor()
    assert.equal(lastEmployeeQuery.keyword, 'staff0')
    assert.equal(lastEmployeeQuery.pageNum, '1')
    await page.getByRole('button', { name: '编辑档案', exact: true }).click()
    const employeeDialog = page.getByRole('dialog')
    await employeeDialog.locator('input').nth(0).fill('')
    await page.getByRole('button', { name: '保存档案', exact: true }).click()
    await page.getByText('请输入姓名', { exact: true }).waitFor()
    assert.equal(employeeUpdateCalls, 0)
    passed.push('关键词查询重置页码及员工编辑必填校验')
    await employeeDialog.locator('input').nth(0).fill('更新后的员工')
    await employeeDialog.locator('.el-select').nth(0).click()
    await page.getByRole('option', { name: '人事部', exact: true }).click()
    await employeeDialog.locator('.el-select').nth(1).click()
    await page.getByRole('option', { name: '人事岗位', exact: true }).waitFor()
    assert.equal(await page.getByRole('option', { name: '开发岗位', exact: true }).count(), 0)
    await page.keyboard.press('Escape')
    employeeSaveFails = true
    await page.getByRole('button', { name: '保存档案', exact: true }).click()
    await page.getByText('档案保存失败', { exact: true }).waitFor()
    assert.equal(await employeeDialog.isVisible(), true)
    assert.equal(employeeSaveData.departmentId, 2)
    assert.equal(employeeSaveData.positionId, null)
    passed.push('部门联动清空岗位，失败保存保留编辑内容')
    employeeSaveFails = false
    await page.getByRole('button', { name: '保存档案', exact: true }).click()
    await employeeDialog.waitFor({ state: 'hidden' })
    await page.getByText('更新后的员工', { exact: true }).waitFor()
    assert.equal(employees[0].realName, '更新后的员工')
    passed.push('员工档案保存成功并刷新列表')
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await page.getByText('档案员工1', { exact: true }).waitFor()
    assert.equal(lastEmployeeQuery.keyword, undefined)
    passed.push('员工筛选重置')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    assert.equal(await page.locator('td.el-table-fixed-column--right').first().evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(245, 255, 253)')
    await page.locator('.el-message').first().waitFor({ state: 'hidden' })
    if (process.env.EMPLOYEE_SCREENSHOT) await page.screenshot({ path: process.env.EMPLOYEE_SCREENSHOT, fullPage: true })
    passed.push('员工档案窄屏布局无整页横向溢出')
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(base + '/admin/users')
    await page.getByRole('heading', { name: '用户与角色' }).waitFor()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    passed.push('手机宽度后台无整页横向溢出')
    if (process.env.SMOKE_SCREENSHOT) await page.screenshot({ path: process.env.SMOKE_SCREENSHOT, fullPage: true })
    authExpired = true
    await page.getByRole('link', { name: '部门与岗位', exact: true }).click()
    await page.waitForURL('**/login')
    assert.equal(await page.evaluate(() => localStorage.getItem('accessToken')), null)
    passed.push('401 清理会话并返回登录页')
    await page.evaluate(() => localStorage.setItem('currentUser', '{broken'))
    await page.goto(base + '/admin/users')
    await page.waitForURL('**/login?**')
    await page.getByRole('heading', { name: '登录系统' }).waitFor()
    passed.push('损坏用户缓存不导致白屏')
    await page.goto(base + '/register')
    await page.getByRole('button', { name: '提交注册' }).click()
    await page.getByText('请输入姓名', { exact: true }).waitFor()
    passed.push('注册表单校验')
    await page.goto(base)
    await page.getByRole('button', { name: '检查系统连接' }).click()
    await page.getByText('服务连接正常', { exact: true }).waitFor()
    passed.push('健康检查真实发出接口请求')
    assert.deepEqual(errors, [])
    assert.equal(await page.evaluate(() => window.__unhandled), undefined)
    passed.push('无页面异常及未处理 Promise 拒绝')
    console.log(JSON.stringify({ passed: passed.length, checks: passed, mode: 'browser with mocked API; no database writes' }, null, 2))
  } finally {
    if (browser) await browser.close()
    server.kill()
  }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
