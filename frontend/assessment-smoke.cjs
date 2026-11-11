// Browser checks use original synthetic fixtures and mocked APIs, never production instruments/data.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const path = require('node:path')
const assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5204'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5204', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'pipe' })
  let logs = '', browser
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
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } }); page.setDefaultTimeout(10000)
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    let user = { id: 9, userName: 'test', realName: '测试员工', roleCode: 'EMPLOYEE', auditStatus: 'APPROVED', userStatus: 'ENABLED' }
    const dimensions = ['EI', 'SN', 'TF', 'JP'].map(dimension => ({ dimension, firstPercent: 75, secondPercent: 25, preferredPole: dimension[0], balanced: false, weakPreference: false }))
    const definition = { name: '合成软件测试量表', version: 'test-v1', sourceTitle: '软件测试数据', author: 'Project tests', sourceUrl: 'https://example.org', accessedDate: '2026-10-29', license: 'ORIGINAL-TEST-FIXTURE', licenseEvidence: 'tests only', chineseVersion: 'test-zh', translationEvidence: 'tests only', scoringVersion: 'WEIGHTED_BIPOLAR_1_5_V1', scoringEvidence: 'tests only', retentionPolicy: '仅测试', contact: 'test@example.invalid', questions: ['EI', 'SN', 'TF', 'JP'].flatMap(d => [1, 2].map(n => ({ key: d + n, leftText: '测试左端', rightText: '测试右端', dimension: d, highPole: d[0], weight: 1 }))) }
    let scales = [], draft = null, saved = 0, submitted = 0, failSave = false, imports = 0, published = 0
    const result = { id: 101, userId: 9, scaleName: definition.name, scaleVersion: definition.version, scoringVersion: definition.scoringVersion, contentHash: 'test-hash', typeCode: 'ESTJ', submittedAt: '2026-10-29T10:00:00', dimensions, scale: definition, report: { reportVersion: 'TEST', disclaimer: '非官方 MBTI 测评，仅为测试数据', preferenceSummary: ['测试偏好提示'], strengths: ['可探索方向'], developmentDirections: ['反思建议'], workPreferences: ['沟通提示'], careerSuggestions: ['职业探索'], growthSuggestions: ['行动建议'], sourceBasis: '合成测试，不代表量表效度', limitations: '结果不是能力判断' } }
    await page.addInitScript(value => { localStorage.setItem('accessToken', 'test-token'); localStorage.setItem('currentUser', JSON.stringify(value)) }, user)
    await page.route(base + '/api/**', async route => {
      const req = route.request(), url = new URL(req.url()); let data = null, code = 200, msg = 'success'
      if (url.pathname === '/api/user/me') data = user
      else if (url.pathname === '/api/system/settings') data = { assessmentEnabled: true, assessmentNotice: '', announcementEnabled: false, announcementTitle: '', announcementContent: '', revision: 0 }
      else if (url.pathname === '/api/assessment/scales') data = scales
      else if (url.pathname === '/api/assessment/current') data = draft
      else if (url.pathname === '/api/assessment/sessions' && req.method() === 'POST') data = draft = { id: 101, status: 'DRAFT', revision: 0, scale: definition, answers: {}, updatedAt: '2026-10-29T09:00:00' }
      else if (url.pathname.endsWith('/answers')) {
        if (failSave) { code = 400; msg = '模拟保存失败' }
        else { saved++; const payload = req.postDataJSON(); data = draft = { ...draft, answers: payload.answers, revision: payload.revision + 1, updatedAt: '2026-10-29T09:01:00' } }
      } else if (url.pathname.endsWith('/submit')) { submitted++; data = result; draft = null }
      else if (/\/assessment\/sessions\/\d+$/.test(url.pathname)) data = draft
      else if (url.pathname === '/api/assessment/history') data = { records: [result, { ...result, id: 102, scaleVersion: 'test-v2', typeCode: 'INTP' }], total: 2, pageNum: 1, pageSize: 10 }
      else if (/\/assessment\/results\/\d+$/.test(url.pathname)) data = result
      else if (url.pathname === '/api/assessment/compare') data = { comparable: false, explanation: '量表版本不同，不能进行同尺度得分比较。', first: result, second: { ...result, id: 102, typeCode: 'INTP' } }
      else if (url.pathname === '/api/admin/assessment/scales') {
        if (req.method() === 'POST') { imports++; scales = [{ id: 1, status: 'DRAFT', definition, contentHash: 'test-hash' }]; data = scales[0] } else data = scales
      } else if (url.pathname.endsWith('/publish')) { published++; scales[0].status = 'PUBLISHED' }
      await route.fulfill({ json: { code, msg, data } })
    })
    await page.goto(base + '/assessment'); await page.getByRole('heading', { name: '暂未开放正式测评' }).waitFor(); checks.push('无正式题库时解释未开放原因')
    definition.license = 'PROJECT-DEMO-ONLY'
    result.report.disclaimer = '毕设演示报告：未经信效度验证，不用于正式人格评估。'
    scales = [{ id: 1, status: 'PUBLISHED', definition, contentHash: 'test-hash' }]
    await page.getByRole('button', { name: '刷新量表状态' }).click(); await page.getByRole('heading', { name: '开始前请阅读' }).waitFor()
    await page.getByText('毕设演示模式：16道原创情境题，仅演示答题、评分与报告流程，未经信效度验证，不用于正式人格评估。', { exact: true }).waitFor()
    checks.push('演示版开始前明确标注用途与局限')
    assert.ok(await page.getByRole('button', { name: '开始测评', exact: true }).isDisabled()); checks.push('未同意数据使用说明不能开始')
    await page.getByText('我已阅读并同意上述测评与数据使用说明', { exact: true }).click(); await page.getByRole('button', { name: '开始测评', exact: true }).click()
    const navigator=page.getByRole('navigation',{name:'答题题号导航',exact:true})
    assert.equal(await navigator.getByRole('button',{name:/前往第\d+题，未答/}).count(),8)
    await navigator.getByRole('button',{name:'前往第8题，未答',exact:true}).click();assert.equal(await page.evaluate(()=>document.activeElement.id),'assessment-question-7');assert.equal(saved,0);checks.push('题号导航移动焦点且不改答案或触发保存')
    await page.getByRole('button',{name:'定位首个未答题',exact:true}).click();assert.equal(await page.evaluate(()=>document.activeElement.id),'assessment-question-0');checks.push('首个未答题可直接定位')
    await page.getByRole('radio', { name: '第1题，5分', exact: true }).check()
    await page.waitForFunction(() => document.querySelector('.save-status')?.textContent.includes('进度已保存'))
    assert.equal(draft.answers.EI1, 5); assert.equal(saved, 1); checks.push('选择答案后自动保存')
    await page.reload(); await page.getByRole('radio', { name: '第1题，5分', exact: true }).waitFor()
    assert.ok(await page.getByRole('radio', { name: '第1题，5分', exact: true }).isChecked()); checks.push('刷新恢复服务器答卷')
    await navigator.getByRole('button',{name:'前往第1题，已答',exact:true}).waitFor();await page.getByRole('button',{name:'定位首个未答题',exact:true}).click();assert.equal(await page.evaluate(()=>document.activeElement.id),'assessment-question-1');checks.push('恢复草稿后概览保留已答状态并跳过已答题')
    failSave = true; await page.getByRole('radio', { name: '第2题，4分', exact: true }).check()
    await page.getByText('保存失败，答案尚未确认保存。', { exact: false }).waitFor()
    assert.ok(await page.getByRole('radio', { name: '第2题，4分', exact: true }).isChecked()); checks.push('保存失败保留本页答案并提示重试')
    const answeredButton=navigator.getByRole('button',{name:'前往第2题，已答',exact:true});await answeredButton.focus();await page.keyboard.press('Enter');assert.equal(await page.evaluate(()=>document.activeElement.id),'assessment-question-1');checks.push('保存失败时本页题号状态保留且支持键盘定位')
    failSave = false; await page.getByRole('button', { name: '保存当前进度' }).click()
    await page.waitForFunction(() => document.querySelector('.save-status')?.textContent.includes('进度已保存'))
    assert.ok(await page.getByRole('button', { name: '提交测评', exact: true }).isDisabled()); checks.push('未答完全部题禁止提交')
    for (let i = 3; i <= 8; i++) await page.getByRole('radio', { name: `第${i}题，3分`, exact: true }).check()
    assert.ok(await page.getByRole('button',{name:'定位首个未答题',exact:true}).isDisabled());assert.equal(await navigator.getByRole('button',{name:/前往第\d+题，已答/}).count(),8);checks.push('全部完成后关闭未答定位且题号全部标记已答')
    await page.getByRole('button', { name: '提交测评', exact: true }).click(); await page.getByRole('button', { name: '继续答题', exact: true }).click(); assert.equal(submitted, 0); checks.push('取消最终确认不提交')
    await page.locator('.el-message-box').waitFor({ state: 'hidden' })
    await page.waitForFunction(() => document.querySelectorAll('.el-message').length === 0)
    await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); checks.push('390px答题页无整页横向溢出')
    await page.screenshot({ path: path.join(__dirname, 'assessment-mobile.png'), fullPage: true })
    await page.locator('#app').getByRole('button', { name: '提交测评', exact: true }).click(); await page.locator('.el-message-box').getByRole('button', { name: '提交测评', exact: true }).click()
    await page.getByRole('heading', { name: 'ESTJ', exact: true }).waitFor(); assert.equal(submitted, 1); checks.push('提交后显示服务端报告快照')
    await page.getByText('毕设演示报告：未经信效度验证，不用于正式人格评估。', { exact: true }).waitFor()
    checks.push('演示报告保留用途警示')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); checks.push('390px报告无整页横向溢出')
    await page.goto(base + '/assessment/history'); await page.getByRole('button', { name: '查看报告' }).first().click(); await page.getByRole('heading', { name: 'ESTJ', exact: true }).waitFor(); checks.push('历史报告可重新查看')
    await page.getByRole('button', { name: '加入对比' }).first().click(); await page.getByRole('button', { name: '加入对比' }).click(); await page.getByRole('button', { name: '对比两次结果' }).click()
    await page.getByText('量表版本不同，不能进行同尺度得分比较。', { exact: true }).waitFor(); assert.equal(await page.getByText('刻度变化(百分点)', { exact: true }).count(), 0); checks.push('跨版本仅解释差异不绘制数值比较')
    user = { ...user, roleCode: 'ADMIN' }; scales = []
    await page.addInitScript(value => localStorage.setItem('currentUser', JSON.stringify(value)), user)
    await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto(base + '/admin/assessment-scales')
    const textarea = page.getByPlaceholder('粘贴包含来源许可、中文版本、评分依据及全部题目的量表JSON')
    await textarea.fill('{'); await page.getByRole('button', { name: '导入量表版本' }).click(); await page.getByText('JSON格式不正确，请检查逗号、引号及括号', { exact: true }).waitFor(); assert.equal(imports, 0); checks.push('错误JSON不发送导入')
    await textarea.fill(JSON.stringify(definition)); await page.getByRole('button', { name: '导入量表版本' }).click(); await page.getByRole('button', { name: '查看与核验' }).click()
    assert.ok(await page.getByRole('button', { name: '核验并发布' }).isDisabled())
    for (const checkbox of await page.locator('.evidence-panel .el-checkbox__label').all()) await checkbox.click()
    await page.getByRole('button', { name: '核验并发布' }).click(); await page.getByRole('button', { name: '确认发布', exact: true }).click()
    await page.getByRole('cell', { name: '已发布', exact: true }).waitFor(); assert.equal(published, 1); checks.push('导入后必须逐项核验并确认才发布')
    assert.deepEqual(errors, []); checks.push('全流程无浏览器脚本异常')
    console.log(JSON.stringify({ passed: checks.length, checks }, null, 2))
  } finally { if (browser) await browser.close(); server.kill(); await new Promise(resolve => { if (server.exitCode !== null) resolve(); else server.once('exit', resolve) }) }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
