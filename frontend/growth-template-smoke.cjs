const { chromium }=require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn }=require('node:child_process')
const path=require('node:path'), assert=require('node:assert/strict')
async function run() {
  const base='http://127.0.0.1:5215'
  const server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5215','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'})
  let browser
  try {
    for(let i=0;i<100;i++) {if(server.exitCode!==null) throw new Error('Vite exited');try{if((await fetch(base)).ok)break}catch{}if(i===99)throw new Error('Startup timeout');await new Promise(resolve=>setTimeout(resolve,200))}
    browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
    const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(10000)
    const errors=[],checks=[]; page.on('pageerror',error=>errors.push(error.message))
    const builtin={id:null,version:'BUILTIN_V1',title:'内置通用行动模板',status:'PUBLISHED',suggestedCycle:'自愿尝试一周',observationTarget:'观察真实体验',reviewPrompt:'复盘适用情况',fallbackContext:'通用任务',fallbackAction:'记录真实体验'}
    const reportResult={id:99,userId:7,scaleName:'测试量表',scaleVersion:'test-v1',scoringVersion:'test',contentHash:'scale-hash',typeCode:'INTJ',submittedAt:'2026-10-29T09:00:00',dimensions:[],scale:{license:'PROJECT-DEMO-ONLY',chineseVersion:'测试'},report:{reportVersion:'GRAPH_GROWTH_PLAN_V3',disclaimer:'演示报告',preferenceSummary:[],strengths:[],developmentDirections:[],workPreferences:[],careerSuggestions:[],growthSuggestions:[],sourceBasis:'项目模板',limitations:'非诊断',growthPlans:[{id:'p1',context:'冻结情境',action:'冻结行动',suggestedCycle:'当次周期',observationTarget:'当次观察',reviewPrompt:'当次复盘',basis:'不是量表作者结论',sourceIds:[],evidenceLevel:'PROJECT_TEMPLATE',templateVersion:'plan-v2',templateContentHash:'a'.repeat(64)}]}}
    let user={id:7,userName:'admin',realName:'管理员',roleCode:'ADMIN',auditStatus:'APPROVED',userStatus:'ENABLED'}
    const rows=[];let loadFailed=true,saveFailed=true,creates=[],publishes=0,archives=0
    await page.route(base+'/api/**',async route=>{
      const req=route.request(),url=new URL(req.url());let data=null
      if(url.pathname==='/api/user/me')data=user
      else if(url.pathname==='/api/assessment/history')data={records:[reportResult],total:1,pageNum:1,pageSize:10}
      else if(url.pathname==='/api/assessment/results/99')data=reportResult
      else if(url.pathname==='/api/admin/growth-templates' && req.method()==='GET') {
        if(loadFailed)return route.fulfill({status:503,json:{code:503,msg:'模拟加载失败'}})
        data=rows
      } else if(url.pathname==='/api/admin/growth-templates/effective')data=rows.find(row=>row.status==='PUBLISHED')||builtin
      else if(url.pathname==='/api/admin/growth-templates' && req.method()==='POST') {
        creates.push(req.postDataJSON())
        if(saveFailed)return route.fulfill({json:{code:400,msg:'模拟保存失败'}})
        data={...req.postDataJSON(),id:1,status:'DRAFT',contentHash:'a'.repeat(64)};rows.unshift(data)
      } else if(url.pathname.endsWith('/1/publish')){publishes++;rows[0].status='PUBLISHED'}
      else if(url.pathname.endsWith('/1/archive')){archives++;rows[0].status='ARCHIVED'}
      else if(url.pathname==='/api/attendance/today')data={attendanceDate:'2026-10-29',serverTime:'2026-10-29T09:00:00',workStartTime:'09:00:00',workEndTime:'18:00:00',restDay:false,timeZone:'Asia/Shanghai'}
      await route.fulfill({json:{code:200,msg:'success',data}})
    })
    await page.goto(base+'/admin/growth-templates'); await page.waitForURL('**/login?**');checks.push('未登录不能进入模板管理')
    await page.addInitScript(user=>{localStorage.setItem('accessToken','test-token');localStorage.setItem('currentUser',JSON.stringify(user))},user)
    await page.goto(base+'/admin/growth-templates');await page.getByText('模板加载失败，请刷新重试',{exact:true}).waitFor()
    assert.equal(await page.getByRole('button',{name:'新建模板版本',exact:true}).isDisabled(),true);checks.push('加载失败有提示且不可使用缺失数据')
    loadFailed=false;await page.getByRole('button',{name:'刷新模板',exact:true}).click()
    await page.getByRole('heading',{name:'当前生效：内置通用行动模板 · BUILTIN_V1',exact:true}).waitFor();checks.push('刷新恢复并显示内置回退模板')
    await page.getByRole('button',{name:'新建模板版本',exact:true}).click()
    const dialog=page.locator('.el-dialog:visible')
    assert.equal(await dialog.getByRole('textbox',{name:'建议周期',exact:true}).inputValue(),builtin.suggestedCycle)
    await dialog.getByRole('button',{name:'保存模板草稿',exact:true}).click()
    await page.getByText('版本号仅支持1至64位字母、数字、点、下划线和短横线',{exact:true}).waitFor();assert.equal(creates.length,0);checks.push('新版本必填，默认复制当前生效内容')
    await dialog.getByRole('textbox',{name:'新版本号',exact:true}).fill('BUILTIN_V1')
    await dialog.getByRole('button',{name:'保存模板草稿',exact:true}).click()
    await page.getByText('版本号已存在或为内置版本，请使用新版本号',{exact:true}).waitFor();assert.equal(creates.length,0);checks.push('内置及已有版本号不可覆盖')
    await dialog.getByRole('textbox',{name:'新版本号',exact:true}).fill('plan-v2')
    await dialog.getByRole('textbox',{name:'模板标题',exact:true}).fill('自建建议模板')
    await dialog.getByRole('textbox',{name:'建议周期',exact:true}).fill('按个人安排自愿尝试两次')
    await dialog.getByRole('button',{name:'保存模板草稿',exact:true}).click()
    await page.getByText('模拟保存失败',{exact:true}).waitFor()
    assert.equal(await dialog.getByRole('textbox',{name:'新版本号',exact:true}).inputValue(),'plan-v2')
    assert.equal(await dialog.getByRole('textbox',{name:'建议周期',exact:true}).inputValue(),'按个人安排自愿尝试两次')
    assert.equal(creates.length,1);checks.push('失败保留表单且不自动重试')
    saveFailed=false;await dialog.getByRole('button',{name:'保存模板草稿',exact:true}).click()
    await page.getByRole('cell',{name:'待发布',exact:true}).waitFor();await dialog.waitFor({state:'hidden'});assert.equal(publishes,0);assert.equal(await dialog.count(),0);checks.push('保存创建草稿而非直接生效')
    await page.getByRole('button',{name:'发布',exact:true}).click();await page.getByRole('button',{name:'取消',exact:true}).click()
    assert.equal(publishes,0);checks.push('取消发布不会修改状态')
    await page.locator('.el-message-box').waitFor({state:'hidden'})
    await page.getByRole('button',{name:'发布',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click()
    await page.getByRole('heading',{name:'当前生效：自建建议模板 · plan-v2',exact:true}).waitFor();assert.equal(publishes,1);checks.push('发布后生效模板正确更新')
    await page.getByRole('button',{name:'预览当前模板',exact:true}).click()
    await page.locator('.preview').getByText('按个人安排自愿尝试两次',{exact:true}).waitFor()
    await page.locator('.preview').getByText('内容摘要：'+'a'.repeat(64),{exact:true}).waitFor();checks.push('预览展示实际文案和摘要')
    await page.getByRole('button',{name:'复制为新版本',exact:true}).click()
    assert.equal(await dialog.getByRole('textbox',{name:'新版本号',exact:true}).inputValue(),'')
    assert.equal(await dialog.getByRole('textbox',{name:'建议周期',exact:true}).inputValue(),'按个人安排自愿尝试两次')
    await dialog.getByRole('button',{name:'取消编辑',exact:true}).click();await page.getByRole('button',{name:'继续编辑',exact:true}).click()
    assert.equal(await dialog.count(),1)
    await dialog.getByRole('button',{name:'取消编辑',exact:true}).click();await page.getByRole('button',{name:'放弃修改',exact:true}).click()
    assert.equal(creates.length,2);checks.push('复制保留内容但清空版本号，退出需确认')
    await page.getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click()
    await page.getByRole('heading',{name:'当前生效：内置通用行动模板 · BUILTIN_V1',exact:true}).waitFor();assert.equal(archives,1);checks.push('停用后明确回到内置模板')
    await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('390px无整页横向溢出')
    await page.goto(base+'/assessment/history');await page.getByRole('button',{name:'查看报告',exact:true}).click()
    await page.getByText('当次建议模板：plan-v2',{exact:false}).waitFor()
    await page.getByText('摘要：'+'a'.repeat(64),{exact:false}).waitFor()
    await page.getByText('冻结行动',{exact:true}).waitFor();checks.push('报告展示冻结的模板版本摘要及行动文案')
    delete reportResult.report.growthPlans[0].templateVersion;delete reportResult.report.growthPlans[0].templateContentHash
    await page.goto(base+'/assessment/history');await page.getByRole('button',{name:'查看报告',exact:true}).click()
    await page.getByText('冻结行动',{exact:true}).waitFor();assert.equal(await page.getByText('当次建议模板：',{exact:false}).count(),0);checks.push('旧报告未记录模板时不补造版本标识')
    user={...user,roleCode:'EMPLOYEE'}
    await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),user)
    await page.goto(base+'/admin/growth-templates');await page.waitForURL('**/workbench');checks.push('普通员工不可进入模板后台')
    assert.deepEqual(errors,[]);checks.push('无浏览器脚本异常')
    console.log(JSON.stringify({passed:checks.length,checks},null,2))
  }finally{if(browser)await browser.close();server.kill()}
}
run().catch(error=>{console.error(error);process.exitCode=1})
