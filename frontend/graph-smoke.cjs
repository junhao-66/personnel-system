// Mock-API UI checks against the actual licensed graph example. No employee data or external writes.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5205'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5205', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let logs = '', browser; server.stdout.on('data', v => { logs += v }); server.stderr.on('data', v => { logs += v })
  const checks = []
  try {
    for (let i = 0; i < 100; i++) { if (server.exitCode !== null) throw new Error(logs); try { if ((await fetch(base)).ok) break } catch {} if (i === 99) throw new Error('Startup timeout'); await new Promise(resolve => setTimeout(resolve, 200)) }
    browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true })
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    let user = { id: 9, userName: 'test', realName: '验收员工', roleCode: 'EMPLOYEE', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, '../graph-example.json'), 'utf8'))
    const scale = JSON.parse(fs.readFileSync(path.join(__dirname, '../third-party/oejts/scale-import.json'), 'utf8'))
    const rule = fixture.rules.find(r => r.id === 'rule-in'), pathEdges = fixture.edges.filter(e => rule.edgeIds.includes(e.id))
    const nodeIds = new Set(pathEdges.flatMap(e => [e.from, e.to]))
    const reportResult = { id: 99, userId: 9, scaleName: scale.name, scaleVersion: scale.version, scoringVersion: scale.scoringVersion, contentHash: 'test-hash', typeCode: 'INTJ', submittedAt: '2026-10-29T10:00:00', scale,
      dimensions: ['EI', 'SN', 'TF', 'JP'].map((dimension, i) => ({ dimension, firstPercent: i < 2 ? 0 : 100, secondPercent: i < 2 ? 100 : 0, preferredPole: 'INTJ'[i], weakPreference: false, balanced: false })),
      report: { reportVersion: 'GRAPH_EXPLANATION_V1', disclaimer: '非商业毕设参考', preferenceSummary: ['软件测试快照'], strengths: ['反思提示'], developmentDirections: ['探索方向'], workPreferences: ['工作方式'], careerSuggestions: ['职业探索'], growthSuggestions: ['小行动实验'], sourceBasis: '合成浏览器报告，仅用于测试显示', limitations: '非诊断',
        graphExplanation: { graphVersion: fixture.version, contentHash: 'test-hash', notice: '仅展示本次保存的条件关联路径', matches: [{ ruleId: rule.id, context: '团队方案讨论', action: '会前整理两种方案并与伙伴验证', explanation: rule.explanation, matchedConditions: ['I端 100% ≥ 60%', 'N端 100% ≥ 60%'], nodes: fixture.nodes.filter(n => nodeIds.has(n.id)), edges: pathEdges, sources: fixture.sources }] } } }
    let graph = { ...fixture, id: 1, status: 'PUBLISHED', contentHash: 'test-hash' }, versions = [graph], fail = false, noGraph = false, imports = 0, publishes = 0, archives = 0
    await page.route(base + '/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let data = null, code = 200, msg = 'success'
      if (url.pathname === '/api/user/me') data = user
      else if (url.pathname === '/api/assessment/history') data = { records: [reportResult], total: 1, pageNum: 1, pageSize: 10 }
      else if (url.pathname === '/api/assessment/results/99') data = reportResult
      else if (url.pathname === '/api/knowledge-graph') { if (fail) { code = 400; msg = '模拟加载失败' } else data = noGraph ? null : graph }
      else if (url.pathname === '/api/admin/knowledge-graph') {
        if (req.method() === 'POST') { imports++; data = { ...req.postDataJSON(), id: 2, status: 'DRAFT', contentHash: 'next-hash' }; versions = [data, ...versions] } else data = versions
      } else if (url.pathname.endsWith('/publish')) { publishes++; versions[0].status = 'PUBLISHED' }
      else if (url.pathname.endsWith('/archive')) { archives++; versions[0].status = 'ARCHIVED' }
      await route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/knowledge-graph'); await page.waitForURL('**/login?**'); checks.push('未登录不能浏览图谱')
    await page.addInitScript(value => { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) }, user)
    await page.goto(base + '/knowledge-graph'); await page.getByRole('heading', { name: '人格知识关系图', exact: true }).waitFor()
    await page.getByText('100节点 / 152关系 / 8多维规则', { exact: false }).waitFor(); assert.ok(await page.locator('svg line').count() > 0); checks.push('完整示例加载并绘制当前节点相邻关系')
    const search = page.getByPlaceholder('搜索题目、偏好、情境或建议')
    const canvas = page.locator('.canvas-panel svg'), viewport = canvas.locator(':scope > g')
    await page.getByRole('button', { name: '放大', exact: true }).click(); await page.locator('.controls output').getByText('120%', { exact: true }).waitFor()
    await page.getByRole('button', { name: '重置视图与布局', exact: true }).click(); assert.equal(await page.locator('.controls output').innerText(), '100%'); checks.push('缩放按钮与重置生效')
    await canvas.focus(); await page.keyboard.press('+'); assert.equal(await page.locator('.controls output').innerText(), '120%'); await page.keyboard.press('0')
    await page.keyboard.press('ArrowRight'); assert.match(await viewport.getAttribute('transform'), /translate\(20,0\)/); await page.keyboard.press('0'); checks.push('键盘缩放平移及重置')
    const center = page.locator('[data-node-id="I"]'), oldPosition = await center.getAttribute('transform')
    const circle = await center.locator('circle').boundingBox(); await page.mouse.move(circle.x + circle.width / 2, circle.y + circle.height / 2); await page.mouse.down(); await page.mouse.move(circle.x + circle.width / 2 + 45, circle.y + circle.height / 2 + 25, { steps: 5 }); await page.mouse.up()
    assert.notEqual(await center.getAttribute('transform'), oldPosition); assert.notEqual(await canvas.locator('line').first().getAttribute('x1'), '400'); await page.locator('.panel').getByRole('heading', { name: fixture.nodes.find(n => n.id === 'I').label, exact: true }).waitFor(); checks.push('拖动节点及连线改变位置且不误触展开')
    await page.getByRole('button', { name: '重置视图与布局', exact: true }).click(); assert.equal(await center.getAttribute('transform'), oldPosition)
    await canvas.hover(); await page.mouse.wheel(0, -50000); await page.waitForFunction(() => document.querySelector('.controls output').textContent === '250%'); await page.mouse.wheel(0, 50000); await page.waitForFunction(() => document.querySelector('.controls output').textContent === '50%'); checks.push('滚轮缩放遵守50%至250%边界')
    await page.getByRole('button', { name: '重置视图与布局', exact: true }).click()
    const box = await canvas.boundingBox(); await page.mouse.move(box.x + 10, box.y + 10); await page.mouse.down(); await page.mouse.move(box.x + 60, box.y + 35); await page.mouse.up(); assert.notEqual(await viewport.getAttribute('transform'), 'translate(0,0) scale(1)'); checks.push('拖动画布空白可平移')
    await page.getByRole('button', { name: '重置视图与布局', exact: true }).click()
    await search.fill('oejts1'); await page.locator('.node-list').getByRole('button', { name: 'Q1 你如何记录任务？ QUESTION', exact: true }).click()
    await page.locator('.panel').getByRole('heading', { name: 'Q1 你如何记录任务？', exact: true }).waitFor(); checks.push('搜索题目后可聚焦节点')
    await page.locator('.filters .el-select').click(); await page.getByRole('option', { name: '题目计分归属', exact: true }).click()
    assert.equal(await page.locator('svg line').count(), 1); checks.push('按关系类型筛选')
    await page.locator('.relationships').getByRole('button', { name: 'Q1 你如何记录任务？ → 题目计分归属 → JP偏好维度', exact: true }).click()
    await page.getByRole('heading', { name: '关系依据', exact: true }).waitFor(); await page.locator('.evidence-panel').getByText('CC BY-NC-SA 4.0', { exact: false }).waitFor(); checks.push('关系显示作者、许可、固定版本和来源')
    await page.getByRole('button', { name: '展开相邻节点', exact: true }).click(); await page.locator('.panel').getByRole('heading', { name: 'JP偏好维度', exact: true }).waitFor(); assert.equal(await page.locator('svg line').count(), 8); checks.push('可沿关系连续展开到维度及关联题目')
    await page.setViewportSize({ width: 390, height: 844 }); await page.waitForFunction(() => document.documentElement.scrollWidth <= innerWidth); checks.push('390px图谱无整页横向溢出')
    await page.screenshot({ path: path.join(__dirname, 'graph-mobile.png'), fullPage: false })
    fail = true; await page.getByRole('button', { name: '刷新图谱', exact: true }).click(); await page.getByText('图谱加载失败，请刷新重试', { exact: true }).waitFor()
    fail = false; noGraph = true; await page.getByRole('button', { name: '刷新图谱', exact: true }).click(); await page.getByText('尚无已发布图谱，请联系管理员导入并发布。', { exact: true }).waitFor(); checks.push('加载失败可重试，无已发布版本有说明')
    noGraph = false; user = { ...user, roleCode: 'ADMIN' }
    await page.addInitScript(value => localStorage.setItem('currentUser', JSON.stringify(value)), user)
    await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto(base + '/admin/knowledge-graph')
    const input = page.getByPlaceholder('粘贴图谱JSON：version、title、sources、nodes、edges、rules')
    await input.fill('{'); await page.getByRole('button', { name: '导入图谱草稿' }).click(); await page.getByText('JSON格式不正确', { exact: true }).waitFor(); assert.equal(imports, 0); checks.push('错误JSON不发送导入请求')
    await input.fill(JSON.stringify({ ...fixture, version: 'browser-next' })); await page.getByRole('button', { name: '导入图谱草稿' }).click(); await page.getByRole('cell', { name: '待发布', exact: true }).waitFor(); assert.equal(imports, 1)
    await page.getByRole('button', { name: '发布', exact: true }).click(); await page.getByRole('button', { name: '取消', exact: true }).click(); assert.equal(publishes, 0); checks.push('取消发布不修改图谱')
    await page.locator('.el-message-box').waitFor({ state: 'hidden' }); await page.getByRole('button', { name: '发布', exact: true }).click(); await page.getByRole('button', { name: '确认', exact: true }).click(); await page.waitForFunction(() => !document.body.innerText.includes('待发布')); assert.equal(publishes, 1); checks.push('核查确认后发布新版本')
    await page.getByRole('button', { name: '停用', exact: true }).first().click(); await page.getByRole('button', { name: '确认', exact: true }).click(); await page.getByRole('cell', { name: '已停用', exact: true }).waitFor(); assert.equal(archives, 1); checks.push('管理员可以停用已发布版本')
    await page.goto(base + '/knowledge-graph'); await page.getByRole('heading', { name: '人格知识关系图', exact: true }).waitFor(); await page.screenshot({ path: path.join(__dirname, 'graph-desktop.png'), fullPage: false })
    await page.goto(base + '/assessment/history'); await page.getByRole('button', { name: '查看报告', exact: true }).click()
    await page.getByRole('heading', { name: '知识图谱解释路径', exact: true }).waitFor(); await page.getByText('命中前提：I端 100% ≥ 60% 且 N端 100% ≥ 60%', { exact: true }).waitFor(); checks.push('历史报告显示当次图谱版本与多维命中前提')
    assert.equal(await page.getByRole('heading', { name: '成长行动计划', exact: true }).count(), 0); await page.getByRole('heading', { name: '成长行动建议', exact: true }).waitFor(); checks.push('旧报告不补造周期或行动计划')
    await page.getByText('展开当次依据路径和来源', { exact: true }).click(); await page.getByText('项目演示假设，未经实证验证', { exact: false }).waitFor(); await page.getByRole('link', { name: '探索当前已发布关系图（可能与当次版本不同）', exact: true }).click(); await page.waitForURL('**/knowledge-graph'); checks.push('报告可展开冻结路径并进入当前图谱探索')
    reportResult.report.growthPlans = [{ id: 'plan-rule-in', context: '团队方案讨论', action: '会前整理两种方案并与伙伴验证', suggestedCycle: '建议1周，在2次真实任务中自愿尝试', observationTarget: '记录舒适程度及具体反馈', reviewPrompt: '何时有帮助？下次如何调整？', basis: '周期与观察目标为本项目编写的通用行动模板，不是量表作者结论。', ruleId: rule.id, graphVersion: fixture.version, sourceIds: fixture.sources.map(s => s.id), evidenceLevel: 'PROJECT_DEMO' }]
    await page.goto(base + '/assessment/history'); await page.getByRole('button', { name: '查看报告', exact: true }).click()
    await page.getByRole('heading', { name: '成长行动计划', exact: true }).waitFor(); await page.getByText('记录舒适程度及具体反馈', { exact: true }).waitFor(); assert.equal(await page.getByRole('heading', { name: '成长行动建议', exact: true }).count(), 0); checks.push('结构化计划展示周期目标复盘并避免重复旧建议')
    const basisLink = page.getByRole('link', { name: `查看当次规则依据（${fixture.version}）`, exact: true }); assert.equal(await basisLink.getAttribute('href'), '#graph-rule-' + rule.id); assert.equal(await page.locator('#graph-rule-' + rule.id).count(), 1); checks.push('行动计划链接指向当次报告规则')
    await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); checks.push('移动端报告无横向溢出')
    assert.deepEqual(errors, []); checks.push('无浏览器脚本异常')
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill(); await new Promise(resolve => { if (server.exitCode !== null) resolve(); else server.once('exit', resolve) }) }
}
run().catch(e => { console.error(e); process.exitCode = 1 })
