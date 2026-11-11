const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base = 'http://127.0.0.1:5217'
  const server = spawn(process.execPath, [path.join(__dirname, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5217', '--strictPort'], { cwd: __dirname, windowsHide: true, stdio: 'ignore' })
  let browser
  try {
    for (let i=0;i<100;i++) {
      if (server.exitCode !== null) throw new Error('Vite exited')
      try { if ((await fetch(base)).ok) break } catch {}
      if (i===99) throw new Error('Vite timeout')
      await new Promise(resolve=>setTimeout(resolve,200))
    }
    browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
    const page=await browser.newPage({viewport:{width:1440,height:1000}}); page.setDefaultTimeout(10000)
    const errors=[], checks=[], submissions=[]; page.on('pageerror',e=>errors.push(e.message))
    const fixture=JSON.parse(fs.readFileSync(path.join(__dirname,'../graph-example.json'),'utf8'))
    const original={...fixture,id:1,status:'PUBLISHED',contentHash:'original'}
    const user={id:9,userName:'test-admin',realName:'测试管理员',roleCode:'ADMIN',auditStatus:'APPROVED',userStatus:'ENABLED'}
    await page.addInitScript(user=>{localStorage.setItem('accessToken','test-token');localStorage.setItem('currentUser',JSON.stringify(user))},user)
    await page.route(base+'/api/**',async route=>{
      const req=route.request(), url=new URL(req.url()); let data=null
      if(url.pathname==='/api/user/me') data=user
      else if(req.method()==='GET') data=[original]
      else {submissions.push(req.postDataJSON());return route.fulfill({json:{code:400,msg:'模拟失败保留副本'}})}
      await route.fulfill({json:{code:200,msg:'success',data}})
    })
    await page.goto(base+'/admin/knowledge-graph')
    await page.getByRole('button',{name:'编辑为新版本',exact:true}).click()
    const editor=page.locator('.graph-editor')
    await editor.getByRole('tab',{name:/依据与规则/}).click()
    const evidence=editor.locator('.evidence-editor'), dialog=()=>page.locator('.el-dialog:visible')
    const item=name=>dialog().locator('.el-form-item').filter({hasText:new RegExp('^'+name)})
    const field=(name,tag='input')=>item(name).locator(tag)
    const apply=()=>dialog().getByRole('button',{name:'应用资料到副本',exact:true}).click()
    const close=async()=>{await dialog().getByRole('button',{name:'取消',exact:true}).click();await dialog().waitFor({state:'hidden'})}
    const select=async(locator,option)=>{await locator.click();const id=await locator.getByRole('combobox').getAttribute('aria-controls');await page.locator(`[id="${id}"]`).getByRole('option',{name:option,exact:true}).click()}
    const firstSource=evidence.locator('.el-table__row').filter({has:page.getByText(fixture.sources[0].id,{exact:true})})
    await firstSource.getByRole('button',{name:'删除',exact:true}).click()
    await page.getByText('来源仍被节点或关系引用，请先处理引用',{exact:true}).waitFor();checks.push('来源引用保护')
    await firstSource.getByRole('button',{name:'编辑来源',exact:true}).click()
    assert.equal(await field('来源标识').isDisabled(),true)
    await field('来源标题').fill('来源表单编辑验证')
    await field('来源链接').fill('https://user:password@example.com')
    await apply();await page.getByText('来源链接须为不含账号密码的HTTP或HTTPS地址',{exact:true}).waitFor();checks.push('不安全来源链接拦截')
    await field('来源链接').fill(fixture.sources[0].url);await apply();await dialog().waitFor({state:'hidden'})
    await firstSource.getByText('来源表单编辑验证',{exact:true}).waitFor();checks.push('稳定来源标识与资料编辑')
    await evidence.getByRole('button',{name:'新增来源',exact:true}).click()
    for(const [name,value] of Object.entries({'来源标识':'temporary-source','来源标题':'临时参考','作者或机构':'测试','来源链接':'https://example.com','许可说明':'仅测试','内容版本':'test-v1'})) await field(name).fill(value)
    await field('归属与使用说明','textarea').fill('不作为科学证据')
    await apply();await dialog().waitFor({state:'hidden'})
    const temporary=evidence.locator('.el-table__row').filter({hasText:'temporary-source'})
    await temporary.waitFor();await temporary.getByRole('button',{name:'删除',exact:true}).click();await page.getByRole('button',{name:'删除',exact:true}).last().click();await temporary.waitFor({state:'hidden'});checks.push('独立来源新增和确认删除')
    const ruleRow=evidence.locator('.el-table__row').filter({has:page.getByText(fixture.rules[0].id,{exact:true})})
    await ruleRow.getByRole('button',{name:'编辑规则',exact:true}).click()
    assert.equal(await field('规则标识').isDisabled(),true)
    const originalRule=fixture.rules[0]
    await field('规则说明','textarea').fill('多条件路径编辑验证')
    await select(item('条件2维度').locator('.el-select'),originalRule.conditions[0].dimension)
    await apply();await page.getByText('条件维度不能重复',{exact:true}).waitFor();checks.push('重复条件维度拦截')
    await select(item('条件2维度').locator('.el-select'),originalRule.conditions[1].dimension)
    await select(dialog().locator('.condition-row').nth(1).locator('.el-select').nth(1),originalRule.conditions[1].pole)
    // Use a different context with no selected outgoing path.
    const unreachable=fixture.nodes.find(n=>n.type==='CONTEXT' && n.id!==originalRule.contextId && !originalRule.edgeIds.some(id=>fixture.edges.find(e=>e.id===id).from===n.id))
    await select(item('情境节点').locator('.el-select'),`${unreachable.label} (${unreachable.id})`)
    await apply();await page.getByText('所选关系须使每个偏好条件和情境均沿有向路径到达行动节点',{exact:true}).waitFor();checks.push('有向路径不完整拦截')
    const context=fixture.nodes.find(n=>n.id===originalRule.contextId)
    await select(item('情境节点').locator('.el-select'),`${context.label} (${context.id})`)
    await apply();await dialog().waitFor({state:'hidden'});await ruleRow.getByText('多条件路径编辑验证',{exact:true}).waitFor();checks.push('有效规则编辑与原路径保留')
    await evidence.getByRole('button',{name:'新增规则',exact:true}).click()
    await field('规则标识').fill(originalRule.id);await apply();await page.getByText('规则标识已存在',{exact:true}).waitFor();checks.push('重复规则标识拦截');await close()
    const lastRule=fixture.rules.at(-1)
    await evidence.locator('.el-table__row').filter({has:page.getByText(lastRule.id,{exact:true})}).getByRole('button',{name:'删除',exact:true}).click()
    await page.getByRole('button',{name:'删除',exact:true}).last().click();checks.push('规则确认删除')
    await editor.getByPlaceholder('例如 graph-v2').fill('rules-test-v2')
    await editor.getByRole('button',{name:'保存为新版本草稿',exact:true}).click()
    await page.getByText('模拟失败保留副本',{exact:true}).waitFor()
    assert.equal(submissions.length,1);assert.equal(submissions[0].sources[0].title,'来源表单编辑验证')
    assert.equal(submissions[0].rules[0].explanation,'多条件路径编辑验证')
    assert.equal(submissions[0].rules.length,fixture.rules.length-1)
    assert.deepEqual(submissions[0].rules[0].edgeIds,originalRule.edgeIds)
    assert.equal(original.sources[0].title,fixture.sources[0].title);assert.notEqual(original.sources[0].title,'来源表单编辑验证')
    await ruleRow.getByText('多条件路径编辑验证',{exact:true}).waitFor();checks.push('提交完整副本、失败保留与原版本保护')
    assert.deepEqual(errors,[]);checks.push('无浏览器运行异常')
    console.log(JSON.stringify({passed:checks.length,checks},null,2))
  } finally {if(browser)await browser.close();server.kill()}
}
run().catch(e=>{console.error(e);process.exitCode=1})
