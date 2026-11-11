const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path')
const assert = require('node:assert/strict')

async function run() {
  const port = 5203
  const base = `http://127.0.0.1:${port}`
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let log = ''; server.stdout.on('data', chunk => { log += chunk }); server.stderr.on('data', chunk => { log += chunk })
  let browser
  const passed = []
  try {
    for (let i = 0; i < 60; i++) {
      if (server.exitCode !== null) throw new Error(log)
      try { if ((await fetch(base)).ok) break } catch {}
      await new Promise(resolve => setTimeout(resolve, 250))
      if (i === 59) throw new Error(log)
    }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', error => { errors.push(error.message); console.error(error.message) })
    const admin = { id: 1, userName: 'admin', realName: '管理员', roleCode: 'ADMIN', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    await page.addInitScript(user => { localStorage.setItem('accessToken', 'test'); localStorage.setItem('currentUser', JSON.stringify(user)) }, admin)
    const department = { id: 1, departmentName: '研发部', departmentCode: 'DEV', sortOrder: 0, isEnabled: 1 }
    const position = { id: 2, positionName: '开发工程师', positionCode: 'ENGINEER', departmentId: 1, description: '原说明', isEnabled: 1 }
    let fail = false; let saves = 0
    await page.route(base + '/api/**', async route => {
      const request = route.request(); const url = new URL(request.url()); let data = null
      if (request.method() === 'PUT' && /\/admin\/(departments|positions)\/\d+$/.test(url.pathname)) {
        saves++
        if (fail) return route.fulfill({ json: { code: 400, msg: '编码已存在', data: null } })
        data = Object.assign(url.pathname.includes('departments') ? department : position, request.postDataJSON())
      } else if (url.pathname.endsWith('/departments')) data = [department]
      else if (url.pathname.endsWith('/positions')) data = [position]
      else if (url.pathname.endsWith('/user/me')) data = admin
      else if (url.pathname.endsWith('/users')) data = [admin]
      return route.fulfill({ json: { code: 200, msg: 'success', data } })
    })
    await page.goto(base + '/admin/organization')
    await page.getByRole('heading', { name: '部门与岗位', exact: true }).waitFor().catch(async error => { console.error(page.url(), await page.locator('body').innerText(), errors, log); throw error })
    const departmentPanel = page.locator('#pane-departments')
    await departmentPanel.getByRole('button', { name: '编辑', exact: true }).click()
    await departmentPanel.locator('input').nth(0).fill('新的研发部')
    await departmentPanel.getByRole('button', { name: '保存部门' }).click()
    await departmentPanel.getByText('新的研发部', { exact: true }).waitFor()
    assert.equal(department.departmentName, '新的研发部'); passed.push('部门编辑保存并刷新')
    await departmentPanel.getByRole('button', { name: '编辑', exact: true }).click()
    await departmentPanel.locator('input').nth(0).fill('')
    const before = saves
    await departmentPanel.getByRole('button', { name: '保存部门' }).click()
    await departmentPanel.getByText('请输入部门名称', { exact: true }).waitFor()
    assert.equal(saves, before); passed.push('必填校验阻止空名称请求')
    await departmentPanel.locator('input').nth(0).fill('冲突部门')
    fail = true
    await departmentPanel.getByRole('button', { name: '保存部门' }).click()
    await page.getByText('编码已存在', { exact: true }).waitFor()
    assert.equal(await departmentPanel.locator('input').nth(0).inputValue(), '冲突部门'); passed.push('失败保留内容可重试')
    fail = false
    await departmentPanel.getByRole('button', { name: '保存部门' }).click()
    await departmentPanel.getByText('冲突部门', { exact: true }).waitFor(); passed.push('重试成功')
    await departmentPanel.getByRole('button', { name: '编辑', exact: true }).click()
    await departmentPanel.getByRole('button', { name: '取消编辑' }).click()
    assert.equal(await departmentPanel.locator('input').nth(0).inputValue(), ''); passed.push('取消编辑清空表单')
    await page.getByRole('tab', { name: '岗位管理', exact: true }).click()
    const positionPanel = page.locator('#pane-positions')
    await positionPanel.getByRole('button', { name: '编辑', exact: true }).click()
    await positionPanel.locator('.el-select__wrapper.is-disabled').waitFor()
    passed.push('岗位编辑锁定所属部门')
    await positionPanel.locator('textarea').fill('新的岗位说明')
    await positionPanel.getByRole('button', { name: '保存岗位' }).click()
    await positionPanel.getByText('新的岗位说明', { exact: true }).waitFor()
    assert.equal(position.departmentId, 1); passed.push('岗位说明保存且关联不变')
    await page.setViewportSize({ width: 390, height: 844 })
    await positionPanel.getByRole('button', { name: '编辑', exact: true }).click()
    await positionPanel.locator('textarea').fill('')
    await positionPanel.getByRole('button', { name: '保存岗位' }).click()
    await positionPanel.getByRole('button', { name: '新增岗位' }).waitFor()
    assert.equal(position.description, ''); passed.push('移动端可保存并清空说明')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ passed: passed.length, checks: passed }, null, 2))
  } finally { if (browser) await browser.close(); server.kill() }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
