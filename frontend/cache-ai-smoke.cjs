const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright')
const {spawn}=require('node:child_process'),path=require('node:path'),assert=require('node:assert/strict')
async function run(){
 const base='http://127.0.0.1:5220',server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5220','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'});let browser
 try{
  for(let i=0;i<100;i++){if(server.exitCode!==null)throw Error('Vite exited');try{if((await fetch(base)).ok)break}catch{}if(i===99)throw Error('Startup timeout');await new Promise(r=>setTimeout(r,200))}
  browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(10000)
  const checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message));let user={id:7,userName:'admin',realName:'测试管理员',roleCode:'ADMIN',auditStatus:'APPROVED',userStatus:'ENABLED'}
  const cache={enabled:false,state:'DISABLED',ttlSeconds:300,failureCount:0,catalogs:['catalog-scales','catalog-graph','catalog-jobs','catalog-prompts','catalog-dictionary'],notice:'不缓存个人报告'}
  let refreshes=0,cacheFailure=false,askFailure=true,askCount=0,expands=0,expandFailure=true
  const answer={mode:'MOCK',reason:'LOCAL_DEMO',promptVersion:'GROUNDED_EXPANSION_V1',contextVersion:'冻结上下文',resultId:99,paragraphs:[{evidenceId:'result',text:'依据当前保存的信息：本次类型编码INTJ。<img src=x onerror=alert(1)>',basis:'冻结报告'}],notice:'不改写原始报告，不作能力评价'}
  const result={id:99,userId:7,scaleName:'测试量表',scaleVersion:'scale-test',scoringVersion:'score-test',contentHash:'s',typeCode:'INTJ',submittedAt:'2026-10-29T10:00:00',dimensions:[],scale:{license:'PROJECT-DEMO',chineseVersion:'演示'},report:{reportVersion:'TEST',disclaimer:'原始报告演示说明',preferenceSummary:['原始偏好记录'],strengths:[],developmentDirections:[],workPreferences:[],careerSuggestions:[],growthSuggestions:[],sourceBasis:'原始来源',limitations:'测试局限'}}
  await page.route(base+'/api/**',async route=>{
   const req=route.request(),url=new URL(req.url());let data=null
   if(url.pathname==='/api/user/me')data=user
   else if(url.pathname==='/api/admin/static-cache'){if(cacheFailure)return route.fulfill({status:503,json:{code:503,msg:'状态读取失败'}});data=cache}
   else if(url.pathname==='/api/admin/static-cache/refresh'){refreshes++;cache.state='MYSQL_FALLBACK';cache.failureCount++;data=cache}
   else if(url.pathname==='/api/ai/status')data={configuredMode:'MOCK',externalEnabled:false,remoteConfigured:false,notice:'本地演示'}
   else if(url.pathname==='/api/admin/departments')data=[{id:1,departmentName:'研发部',isEnabled:1}]
   else if(url.pathname.match(/^\/api\/admin\/ai\/(employees|departments)\/\d+\/ask$/)){
    askCount++;if(askFailure)return route.fulfill({status:503,json:{code:503,msg:'模拟请求失败'}})
    const question=req.postDataJSON().question;data=question.includes('能力')?{...answer,mode:'REFUSED',reason:'OUT_OF_SCOPE',paragraphs:[{evidenceId:'scope',text:'不能判断能力或绩效',basis:'回答范围'}]}:url.pathname.includes('/departments/')?{...answer,resultId:null,paragraphs:[{evidenceId:'team-counts',text:'在职人数3，已完成人数2',basis:'部门聚合'}]}:answer
   }else if(url.pathname==='/api/assessment/history')data={records:[result],total:1,pageNum:1,pageSize:10}
   else if(url.pathname==='/api/assessment/results/99')data=result
   else if(url.pathname==='/api/ai/reports/99/expand'){expands++;if(expandFailure)return route.fulfill({status:429,headers:{'Retry-After':'1'},json:{code:429,msg:'模拟AI额度已用完'}});data={...answer,mode:'RULE_FALLBACK',reason:'REMOTE_UNAVAILABLE'}}
   else if(url.pathname==='/api/attendance/today')data={attendanceDate:'2026-10-29',serverTime:'2026-10-29T09:00:00',workStartTime:'09:00:00',workEndTime:'18:00:00',restDay:false,timeZone:'Asia/Shanghai'}
   await route.fulfill({json:{code:200,msg:'success',data}})
  })
  await page.goto(base+'/admin/ai-assistant');await page.waitForURL('**/login?**');checks.push('未登录不可进入问答')
  await page.addInitScript(user=>{localStorage.setItem('accessToken','fixture');localStorage.setItem('currentUser',JSON.stringify(user))},user)
  await page.goto(base+'/admin/static-cache');await page.getByRole('heading',{name:'未开启，直接读取数据库',exact:true}).waitFor();assert(await page.getByRole('button',{name:'清理并重新加载静态缓存',exact:true}).isDisabled());checks.push('缓存关闭状态明确且不可刷新')
  cache.enabled=true;cache.state='READY';await page.getByRole('button',{name:'刷新状态',exact:true}).click();await page.getByRole('heading',{name:'允许尝试缓存',exact:true}).waitFor()
  await page.getByRole('button',{name:'清理并重新加载静态缓存',exact:true}).click();await page.getByRole('heading',{name:'缓存故障冷却中，已回源数据库',exact:true}).waitFor();assert.equal(refreshes,1);checks.push('刷新遇故障显示回源状态')
  cacheFailure=true;await page.getByRole('button',{name:'刷新状态',exact:true}).click();await page.getByText('状态读取失败，请重试',{exact:true}).waitFor();cacheFailure=false;await page.getByRole('button',{name:'刷新状态',exact:true}).click();await page.getByText('状态读取失败，请重试',{exact:true}).waitFor({state:'hidden'});checks.push('缓存状态失败重试可恢复')
  await page.goto(base+'/admin/ai-assistant?userId=7');await page.getByText('配置模式 MOCK · 外部调用已关闭 · 真实API未完整配置',{exact:true}).waitFor();assert.equal(await page.locator('.el-input-number input').inputValue(),'7');checks.push('档案入口自动填入用户ID并标明Mock及外部关闭')
  const question=page.locator('textarea');await question.fill('请整理最新测评的成长行动建议');await page.getByRole('button',{name:'整理回答',exact:true}).click();await page.getByText('问答请求失败，已保留问题，可重试',{exact:true}).waitFor();assert.equal(await question.inputValue(),'请整理最新测评的成长行动建议');assert.equal(askCount,1);checks.push('失败保留问题且不会自动重复调用')
  askFailure=false;await page.getByRole('button',{name:'整理回答',exact:true}).click();await page.getByRole('heading',{name:'本地Mock演示（非真实模型生成）',exact:true}).waitFor();assert.equal(await page.locator('.answer img').count(),0);await page.getByText('依据 result：冻结报告',{exact:true}).waitFor();checks.push('Mock明确标识、依据可见、返回内容按纯文本显示')
  await question.fill('谁的能力最好');await page.locator('.answer').waitFor({state:'hidden'});await page.getByRole('button',{name:'整理回答',exact:true}).click();await page.getByRole('heading',{name:'超出可回答范围',exact:true}).waitFor();checks.push('问题改变清除旧回答，越界问题显示拒答')
  await page.getByText('部门团队',{exact:true}).click();await question.fill('部门有多少人完成测评');await page.getByRole('button',{name:'整理回答',exact:true}).click();await page.getByText('在职人数3，已完成人数2',{exact:true}).waitFor();checks.push('部门问答使用独立聚合入口')
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('问答页面390px无横向溢出')
  await page.goto(base+'/assessment/history');await page.getByRole('button',{name:'查看报告',exact:true}).click();await page.getByText('原始偏好记录',{exact:true}).waitFor()
  await page.getByRole('button',{name:'整理本次报告',exact:true}).click();await page.getByText('辅助整理暂不可用，可重试；原始报告仍可正常阅读',{exact:true}).waitFor();await page.getByText('原始偏好记录',{exact:true}).waitFor();assert.equal(expands,1);assert(await page.evaluate(()=>!!localStorage.getItem('accessToken')));checks.push('AI限流失败仍保留原始报告与登录状态')
  expandFailure=false;await page.getByRole('button',{name:'整理本次报告',exact:true}).click();await page.getByRole('heading',{name:'规则模板降级',exact:true}).waitFor();await page.getByText('原始偏好记录',{exact:true}).waitFor();checks.push('降级正文与原始报告独立展示')
  user={...user,roleCode:'DEPARTMENT_MANAGER'};await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),user);await page.goto(base+'/admin/ai-assistant');await page.waitForURL('**/workbench');await page.goto(base+'/admin/static-cache');await page.waitForURL('**/workbench');checks.push('部门负责人不可进入缓存维护或管理员问答')
  assert.deepEqual(errors,[]);checks.push('无浏览器未捕获异常');console.log(JSON.stringify({passed:checks.length,checks},null,2))
 }finally{if(browser)await browser.close();server.kill()}
}
run().catch(e=>{console.error(e);process.exitCode=1})
