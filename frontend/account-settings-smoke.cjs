// UI fixtures only; real authorization, persistence and credential revocation are tested in DatabaseIT.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const {spawn}=require('node:child_process'),path=require('node:path'),fs=require('node:fs'),assert=require('node:assert/strict')
async function run(){
 const base='http://127.0.0.1:5221',server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5221','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'})
 let browser
 try{
  for(let i=0;i<100;i++){if(server.exitCode!==null)throw Error('Vite exited');try{if((await fetch(base)).ok)break}catch{}if(i===99)throw Error('Startup timeout');await new Promise(r=>setTimeout(r,200))}
  browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),checks=[],errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message))
  let user={id:7,userName:'fixture',realName:'账号验收',phone:'13900000000',roleCode:'EMPLOYEE',auditStatus:'APPROVED',userStatus:'ENABLED'}
  let settings={assessmentEnabled:true,assessmentNotice:'',announcementEnabled:false,announcementTitle:'',announcementContent:'',revision:0,updatedAt:'2026-10-30T10:00:00'}
  let contactMode='failure',passwordMode='failure',settingsMode='normal',availabilityMode='normal',contactCalls=0,passwordCalls=0,settingsCalls=0,starts=0,holdPassword=false,releasePassword
  let draft=null
  const scale=JSON.parse(fs.readFileSync(path.join(__dirname,'../third-party/oejts/scale-import.json'),'utf8'))
  await page.route(base+'/api/**',async route=>{
   const req=route.request(),url=new URL(req.url());let data=null
   if(url.pathname==='/api/user/me')data=user
   else if(url.pathname==='/api/user/me/contact'){
    contactCalls++;assert.deepEqual(Object.keys(req.postDataJSON()),['phone'])
    if(contactMode==='failure')return route.fulfill({json:{code:400,msg:'模拟联系方式保存失败'}})
    if(contactMode==='limit')return route.fulfill({status:429,headers:{'Retry-After':'7'},json:{code:429,msg:'limit'}})
    user={...user,phone:req.postDataJSON().phone || undefined};data=user
   }else if(url.pathname==='/api/user/me/password'){
    passwordCalls++;assert.deepEqual(Object.keys(req.postDataJSON()).sort(),['currentPassword','newPassword'])
    if(holdPassword)await new Promise(resolve=>{releasePassword=resolve})
    if(passwordMode==='failure')return route.fulfill({json:{code:400,msg:'当前密码不正确'}})
   }else if(url.pathname==='/api/admin/system-settings'){
    if(req.method()==='PUT'){
     settingsCalls++;if(settingsMode==='conflict')return route.fulfill({json:{code:400,msg:'配置已被其他管理员更新，请重新加载后修改'}})
     settings={...req.postDataJSON(),revision:settings.revision+1,updatedAt:'2026-10-30T11:00:00'}
    }else if(settingsMode==='loadFailure')return route.fulfill({status:503,json:{code:503,msg:'模拟配置加载失败'}})
    data=settings
   }else if(url.pathname==='/api/system/settings'){
    if(availabilityMode==='failure')return route.fulfill({status:503,json:{code:503,msg:'模拟开放状态读取失败'}})
    data=settings.announcementEnabled ? settings : {...settings,announcementTitle:'',announcementContent:''}
   }else if(url.pathname==='/api/assessment/scales')data=[{id:1,definition:scale}]
   else if(url.pathname==='/api/assessment/current')data=draft
   else if(url.pathname==='/api/assessment/sessions' && req.method()==='POST'){
    starts++;return route.fulfill({json:{code:400,msg:'暂未开放新测评：服务器已暂停'}})
   }else if(url.pathname==='/api/attendance/today')data={attendanceDate:'2026-10-30',serverTime:'2026-10-30T09:00:00',workStartTime:'09:00:00',workEndTime:'18:00:00',restDay:false,timeZone:'Asia/Shanghai'}
   else if(url.pathname==='/api/attendance/records')data={records:[],total:0,pageNum:1,pageSize:10}
   await route.fulfill({json:{code:200,msg:'success',data}})
  })
  await page.goto(base+'/account-center');await page.waitForURL('**/login?**');checks.push('账号中心未登录跳转登录并保留内部目标')
  const loginAs=async role=>{user={...user,roleCode:role};await page.evaluate(user=>{localStorage.setItem('accessToken','fixture');localStorage.setItem('currentUser',JSON.stringify(user))},user)}
  await loginAs('EMPLOYEE');await page.goto(base+'/account-center');await page.getByRole('heading',{name:'个人信息',exact:true}).waitFor();await page.getByLabel('联系电话',{exact:true}).fill('13800000000')
  assert.equal(await page.getByText('账号验收',{exact:true}).count(),1);checks.push('本人资料与角色显示，姓名和组织信息只读')
  await page.getByRole('button',{name:'保存联系方式',exact:true}).click();await page.getByText('模拟联系方式保存失败',{exact:true}).waitFor();assert.equal(await page.getByLabel('联系电话',{exact:true}).inputValue(),'13800000000');checks.push('联系方式保存失败保留输入')
  contactMode='limit';await page.getByRole('button',{name:'保存联系方式',exact:true}).click();await page.getByText('操作过于频繁，请在7秒后重试',{exact:true}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('accessToken')),'fixture');checks.push('联系方式429保留表单与会话')
  contactMode='normal';await page.getByRole('button',{name:'保存联系方式',exact:true}).click();await page.getByText('联系方式已保存',{exact:true}).waitFor();assert.equal(user.phone,'13800000000');checks.push('联系方式仅提交phone并刷新本人资料')
  await page.getByLabel('联系电话',{exact:true}).fill('');await page.getByRole('button',{name:'保存联系方式',exact:true}).click();await page.waitForFunction(()=>JSON.parse(localStorage.getItem('currentUser')).phone===undefined);checks.push('联系方式可清空')
  const current=page.getByLabel('当前密码',{exact:true}),next=page.getByLabel('新密码',{exact:true}),confirm=page.getByLabel('确认新密码',{exact:true}),change=page.getByRole('button',{name:'修改密码并重新登录',exact:true})
  await current.fill('Fixture123');await next.fill('Changed123!');await confirm.fill('different');await change.click();await page.getByText('两次新密码不一致',{exact:true}).waitFor();assert.equal(passwordCalls,0);checks.push('两次新密码不一致时不发请求')
  await next.fill('密'.repeat(25));await confirm.fill('密'.repeat(25));await change.click();await page.getByText('新密码须为8至64个字符，UTF-8长度不超过72字节',{exact:true}).waitFor();assert.equal(passwordCalls,0);checks.push('多字节新密码长度校验')
  await next.fill('Changed123!');await confirm.fill('Changed123!');await change.click();await page.getByText('当前密码不正确',{exact:true}).waitFor();assert.equal(await current.inputValue(),'Fixture123');assert.equal(await page.evaluate(()=>localStorage.getItem('accessToken')),'fixture');checks.push('当前密码错误时保留表单且不退出')
  passwordMode='normal';holdPassword=true;const before=passwordCalls;await change.click();for(let i=0;i<100&&!releasePassword;i++)await new Promise(r=>setTimeout(r,20));assert.ok(releasePassword);assert.equal(await change.isDisabled(),true);await change.evaluate(button=>button.click());assert.equal(passwordCalls,before+1);checks.push('密码修改期间阻止重复请求')
  releasePassword();await page.waitForURL('**/login?**');assert.equal(await page.evaluate(()=>localStorage.getItem('accessToken')),null);assert.equal(await page.evaluate(()=>JSON.stringify(localStorage).includes('Changed123!')),false);checks.push('改密成功清除旧会话、回到登录且密码不落入本地存储')
  await loginAs('EMPLOYEE');await page.goto(base+'/admin/system-settings');await page.waitForURL('**/workbench');checks.push('普通员工不能进入系统配置页面')
  await loginAs('ADMIN');settingsMode='loadFailure';await page.goto(base+'/admin/system-settings');await page.getByText('系统配置加载失败，暂不可保存，请重新加载。',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'保存系统配置',exact:true}).isDisabled(),true);checks.push('系统配置加载失败禁止保存默认值')
  settingsMode='normal';await page.getByRole('button',{name:'重新加载配置',exact:true}).click();await page.getByText('与服务器一致',{exact:false}).waitFor()
  await page.getByRole('switch',{name:'允许开始新测评',exact:true}).locator('..').click();await page.getByRole('button',{name:'保存系统配置',exact:true}).click();await page.getByText('暂停新测评时请填写说明',{exact:true}).waitFor();assert.equal(settingsCalls,0);checks.push('暂停测评前要求填写说明')
  await page.getByLabel('测评说明（暂停时必填）',{exact:true}).fill('正在核校题库')
  await page.getByRole('switch',{name:'显示公告',exact:true}).locator('..').click();await page.getByRole('button',{name:'保存系统配置',exact:true}).click();await page.getByText('发布公告时请填写标题和内容',{exact:true}).waitFor();checks.push('发布公告要求标题和内容完整')
  const title='<img src=x onerror=alert(1)>';await page.getByLabel('公告标题',{exact:true}).fill(title);await page.getByLabel('公告内容',{exact:true}).fill('请完成已有草稿\n第二行说明')
  assert.equal(await page.getByLabel('公告预览',{exact:true}).locator('img').count(),0);checks.push('公告预览纯文本显示，不执行HTML')
  const save=async()=>{await page.getByRole('button',{name:'保存系统配置',exact:true}).click();await page.getByRole('button',{name:'保存配置',exact:true}).click()}
  settingsMode='conflict';await save();await page.getByText('配置已被其他管理员更新，请重新加载后修改',{exact:true}).waitFor();assert.equal(await page.getByLabel('公告标题',{exact:true}).inputValue(),title);checks.push('并发配置冲突保留编辑内容，不自动覆盖')
  settingsMode='normal';await save();await page.getByText('系统配置已保存',{exact:true}).waitFor();assert.equal(settings.assessmentEnabled,false);assert.equal(settings.announcementEnabled,true);checks.push('保存公告和测评状态并更新配置版本')
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('系统配置手机布局无整页横向溢出')
  await loginAs('EMPLOYEE');await page.goto(base+'/workbench');await page.getByRole('heading',{name:title,exact:true}).waitFor();assert.equal(await page.getByLabel('系统公告').locator('img').count(),0);checks.push('员工工作台显示已发布公告且不解析HTML')
  await page.goto(base+'/assessment');await page.getByText('暂未开放新测评：正在核校题库',{exact:true}).waitFor();await page.getByText('我已阅读并同意上述测评与数据使用说明',{exact:true}).click();assert.equal(await page.getByRole('button',{name:'开始测评',exact:true}).isDisabled(),true);checks.push('暂停时说明可见且禁止创建新测评')
  settings.assessmentEnabled=true;await page.getByRole('button',{name:'刷新开放状态',exact:true}).click();await page.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='开始测评')?.disabled);checks.push('刷新开放状态后恢复新测评入口')
  await page.getByRole('button',{name:'开始测评',exact:true}).click();await page.getByText('暂未开放新测评：服务器已暂停',{exact:true}).waitFor();assert.equal(starts,1);checks.push('页面状态过期时展示后端拦截并保留同意状态')
  availabilityMode='failure';await page.getByRole('button',{name:'刷新开放状态',exact:true}).click();await page.getByText('测评开放状态加载失败，暂不可开始新测评；已有草稿可继续完成。',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'开始测评',exact:true}).isDisabled(),true);checks.push('开放状态失败时不误允许新测评')
  availabilityMode='normal';settings.assessmentEnabled=false;draft={id:88,userId:user.id,scaleId:1,scale,status:'DRAFT',revision:0,answers:{},updatedAt:'2026-10-30T10:00:00'};await page.goto(base+'/assessment');await page.getByText('暂未开放新测评：正在核校题库。本次草稿仍可继续完成。',{exact:true}).waitFor();assert.equal(await page.getByRole('radio').first().isDisabled(),false);checks.push('暂停新测评后已有草稿仍可继续答题')
  await page.goto(base+'/account-center');await page.getByRole('heading',{name:'账号中心',exact:true}).waitFor();await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('账号中心手机布局无整页横向溢出')
  assert.deepEqual(errors,[]);checks.push('账号与系统配置无浏览器脚本异常')
  console.log(JSON.stringify({passed:checks.length,checks},null,2))
 }finally{if(browser)await browser.close();server.kill()}
}
run().catch(error=>{console.error(error);process.exitCode=1})
