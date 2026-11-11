const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const {spawn}=require('node:child_process'),path=require('node:path'),assert=require('node:assert/strict')
async function run(){
 const base='http://127.0.0.1:5219',server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5219','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'})
 let browser
 try{
  for(let i=0;i<100;i++){if(server.exitCode!==null)throw Error('Vite exited');try{if((await fetch(base)).ok)break}catch{}if(i===99)throw Error('Startup timeout');await new Promise(r=>setTimeout(r,200))}
  browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),checks=[],errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message))
  let user={id:7,userName:'admin',realName:'测试管理员',roleCode:'ADMIN',auditStatus:'APPROVED',userStatus:'ENABLED'}
  const rule={id:'rule-one',explanation:'任务探索多条件规则',conditions:[{nodeId:'i',dimension:'EI',pole:'I',minimumPercent:60},{nodeId:'n',dimension:'SN',pole:'N',minimumPercent:60}],edgeIds:['e1','e2'],actionId:'a',contextId:'c'}
  const graph={id:8,version:'graph-test',contentHash:'a'.repeat(64),status:'PUBLISHED',rules:[rule],nodes:[{id:'c',label:'项目准备'},{id:'a',label:'先准备提纲'}],edges:[{id:'e1',from:'c',to:'a',relation:'SUGGESTS',explanation:'演示关系'}],sources:[{id:'s',title:'项目整理来源',author:'维护者',license:'PROJECT-DEMO',url:'https://example.org',contentVersion:'v1',evidenceLevel:'PROJECT_DEMO',attribution:'仅供演示'}]}
  const rows=[];let failLoad=true,failSave=true,creates=0,publishes=0,archives=0,mode='empty',failTeam=false
  const distribution={employeeCount:3,completedCount:2,notCompletedCount:1,assessmentCount:3,completionRate:66.67,typeDistribution:['INTJ','ESFP'].map(typeCode=>({typeCode,employeeCount:1,percent:50})),departmentDistribution:[],generatedAt:'2026-10-29T10:00:00',scopeNotice:'最新有效结果'}
  const team={departmentId:0,departmentName:'未分配部门',distribution,dimensions:['EI','SN','TF','JP'].map(dimension=>({dimension,firstCount:1,secondCount:1})),collaborationTips:['自愿提供书面准备与口头交流渠道'],ruleVersion:'TEAM_COLLABORATION_DEMO_V1',notice:'仅管理员查看，协作提示不作为绩效评价'}
  await page.route(base+'/api/**',async route=>{
   const req=route.request(),url=new URL(req.url());let data=null
   if(url.pathname==='/api/user/me')data=user
   else if(url.pathname==='/api/admin/positions')data=[{id:1,positionName:'研发岗位',isEnabled:1,departmentId:1}]
   else if(url.pathname==='/api/admin/departments')data=[{id:1,departmentName:'研发部',isEnabled:1}]
   else if(url.pathname==='/api/knowledge-graph')data=graph
   else if(url.pathname==='/api/admin/job-preferences'&&req.method()==='GET'){if(failLoad)return route.fulfill({status:503,json:{code:503,msg:'模拟加载失败'}});data=rows}
   else if(url.pathname==='/api/admin/job-preferences'&&req.method()==='POST'){
    creates++;if(failSave)return route.fulfill({json:{code:400,msg:'模拟保存失败'}})
    const form=req.postDataJSON();data={id:1,status:'DRAFT',contentHash:'b'.repeat(64),definition:{...form,positionName:'研发岗位',graphVersion:graph.version,graphContentHash:graph.contentHash,rules:form.rules.map(r=>({weight:r.weight,rule,context:'项目准备',action:'先准备提纲',nodes:graph.nodes,edges:graph.edges,sources:graph.sources}))}};rows.unshift(data)
   }else if(url.pathname.endsWith('/1/publish')){publishes++;rows[0].status='PUBLISHED'}
   else if(url.pathname.endsWith('/1/archive')){archives++;rows[0].status='ARCHIVED'}
   else if(url.pathname==='/api/job-preferences'){
    data={resultId:mode==='empty'?null:99,scaleVersion:'test-scale',graphVersion:'graph-test',notice:'演示规则覆盖率不是岗位适配度',positions:mode==='empty'?[]:[{profile:rows[0],comparable:mode!=='mismatch',ruleCoveragePercent:mode==='mismatch'?null:100,matchedRuleIds:mode==='mismatch'?[]:['rule-one'],explanation:mode==='mismatch'?'依据版本不同，未计算':'结合真实任务自愿探索'}]}
   }else if(url.pathname==='/api/admin/team-analysis'){if(failTeam)return route.fulfill({status:503,json:{code:503,msg:'模拟分析失败'}});data=team}
   else if(url.pathname==='/api/attendance/today')data={attendanceDate:'2026-10-29',serverTime:'2026-10-29T09:00:00',workStartTime:'09:00:00',workEndTime:'18:00:00',restDay:false,timeZone:'Asia/Shanghai'}
   await route.fulfill({json:{code:200,msg:'success',data}})
  })
  await page.goto(base+'/job-preferences');await page.waitForURL('**/login?**');checks.push('未登录不能进入岗位探索')
  await page.addInitScript(user=>{localStorage.setItem('accessToken','fixture');localStorage.setItem('currentUser',JSON.stringify(user))},user)
  await page.goto(base+'/admin/job-preferences');await page.getByText('加载失败，请重试',{exact:true}).waitFor();assert(await page.getByRole('button',{name:'新建配置',exact:true}).isDisabled());checks.push('加载失败可见且禁用新建')
  failLoad=false;await page.getByRole('button',{name:'刷新列表与图谱',exact:true}).click();await page.getByText('暂无岗位探索配置',{exact:true}).waitFor()
  await page.getByRole('button',{name:'新建配置',exact:true}).click();const dialog=page.locator('.el-dialog:visible')
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();assert.equal(creates,0);checks.push('不完整配置不发送请求')
  await dialog.locator('.el-select').first().click();await page.locator('.el-select-dropdown:visible').getByText('研发岗位',{exact:true}).click()
  const input=(name)=>dialog.locator('.el-form-item').filter({hasText:new RegExp('^'+name)}).locator('input,textarea')
  await input('新版本号').fill('job-test-v1');await input('标题').fill('岗位探索测试');await input('配置依据与维护者归属').fill('项目维护者多维规则整理，未验证')
  await dialog.locator('.rule-row .el-select').click();await page.locator('.el-select-dropdown:visible').getByText('rule-one：任务探索多条件规则',{exact:true}).click()
  await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await page.getByText('模拟保存失败',{exact:true}).waitFor();assert.equal(await input('新版本号').inputValue(),'job-test-v1');checks.push('保存失败保留版本与规则')
  failSave=false;await dialog.getByRole('button',{name:'保存草稿',exact:true}).click();await dialog.waitFor({state:'hidden'});await page.getByText('job-test-v1 · 草稿 · 图谱 graph-test',{exact:true}).waitFor();assert.equal(publishes,0);checks.push('先建草稿不自动发布')
  await page.getByRole('button',{name:'发布',exact:true}).click();await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(publishes,0);await page.locator('.el-message-box').waitFor({state:'hidden'});checks.push('取消发布不修改状态')
  await page.getByRole('button',{name:'发布',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await page.getByText('job-test-v1 · 已发布 · 图谱 graph-test',{exact:true}).waitFor();assert.equal(publishes,1)
  await page.getByRole('button',{name:'复制为新版本',exact:true}).click();assert.equal(await input('新版本号').inputValue(),'');assert.equal(await input('标题').inputValue(),'岗位探索测试');await dialog.getByRole('button',{name:'取消',exact:true}).click();await dialog.waitFor({state:'hidden'});checks.push('复制保留规则并清空版本号')
  await page.goto(base+'/job-preferences');await page.getByText('请先完成一次测评',{exact:true}).waitFor();checks.push('无测评有明确开始入口')
  mode='matched';await page.getByRole('button',{name:'刷新探索',exact:true}).click();await page.getByRole('heading',{name:'演示规则覆盖率：100%',exact:true}).waitFor();await page.getByText('当次快照命中',{exact:true}).waitFor();await page.getByText('查看关系路径与来源',{exact:true}).click();await page.getByRole('link',{name:'项目整理来源',exact:true}).waitFor();checks.push('覆盖率、AND条件、冻结路径与来源可查看')
  mode='mismatch';await page.getByRole('button',{name:'刷新探索',exact:true}).click();await page.getByRole('heading',{name:'演示规则覆盖率：不计算（依据版本不同）',exact:true}).waitFor();checks.push('版本不同不伪造覆盖率')
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('岗位探索390px无横向溢出')
  await page.goto(base+'/admin/team-analysis');await page.getByText('在职人数 3 · 已完成人数 2 · 未完成人数 1 · 完成率 66.67%',{exact:true}).waitFor();await page.getByText('自愿提供书面准备与口头交流渠道',{exact:true}).waitFor();await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('团队人数、端点与协作提示正确显示且适配手机')
  failTeam=true;await page.getByRole('button',{name:'刷新分析',exact:true}).click();await page.getByText('分析加载失败，请重试',{exact:true}).waitFor();assert.equal(await page.getByText('自愿提供书面准备与口头交流渠道',{exact:true}).count(),0)
  failTeam=false;await page.getByRole('button',{name:'刷新分析',exact:true}).click();await page.getByText('自愿提供书面准备与口头交流渠道',{exact:true}).waitFor();checks.push('团队加载失败清除旧结果且重试恢复')
  await page.goto(base+'/admin/job-preferences');await page.getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('button',{name:'确认',exact:true}).click();await page.getByText('job-test-v1 · 已停用 · 图谱 graph-test',{exact:true}).waitFor();assert.equal(archives,1);checks.push('停用需确认并更新状态')
  user={...user,roleCode:'DEPARTMENT_MANAGER'};await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),user);await page.goto(base+'/admin/team-analysis');await page.waitForURL('**/workbench');checks.push('部门负责人不可进入团队分析后台')
  assert.deepEqual(errors,[]);checks.push('无浏览器脚本异常');console.log(JSON.stringify({passed:checks.length,checks},null,2))
 }finally{if(browser)await browser.close();server.kill()}
}
run().catch(e=>{console.error(e);process.exitCode=1})
