const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
async function run(){
 const base='http://127.0.0.1:5222',checks=[]
 const server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5222','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'});let browser
 try{
  for(let i=0;i<100;i++){try{if((await fetch(base)).ok)break}catch{}if(i===99)throw Error('Startup timeout');await new Promise(r=>setTimeout(r,200))}
  browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000)
  const definition=JSON.parse(fs.readFileSync(path.join(__dirname,'../third-party/oejts/scale-import.json'),'utf8')),original=JSON.stringify(definition)
  const source={id:1,status:'PUBLISHED',contentHash:'original',definition};let versions=[source],posts=0,publishes=0,fail=false,lastPayload
  const user={id:1,userName:'admin',realName:'验收管理员',roleCode:'ADMIN',auditStatus:'APPROVED',userStatus:'ENABLED'}
  await page.addInitScript(user=>{localStorage.setItem('accessToken','test-token');localStorage.setItem('currentUser',JSON.stringify(user))},user)
  await page.route(base+'/api/**',async route=>{const req=route.request(),url=new URL(req.url());let data=null,code=200,msg='success'
   if(url.pathname==='/api/user/me')data=user
   else if(url.pathname==='/api/admin/assessment/scales'){
    if(req.method()==='POST'){posts++;lastPayload=req.postDataJSON();if(fail){code=400;msg='模拟保存失败'}else{data={id:2,status:'DRAFT',contentHash:'new',definition:lastPayload};versions=[data,source]}}
    else data=versions
   }else if(url.pathname.endsWith('/publish'))publishes++
   await route.fulfill({json:{code,msg,data}})
  })
  await page.goto(base+'/admin/assessment-scales');await page.getByRole('button',{name:'表单新建量表',exact:true}).click()
  const editor=page.locator('.scale-editor'),save=editor.getByRole('button',{name:'保存为待核验新版本',exact:true})
  await save.click();await page.getByText('请填写量表名称',{exact:true}).waitFor();assert.equal(posts,0);checks.push('空表单阻止提交')
  await editor.getByRole('button',{name:'退出题库编辑',exact:true}).click()
  await page.getByRole('button',{name:'复制并编辑',exact:true}).click()
  const field=label=>editor.locator('.el-form-item').filter({has:page.locator('label').getByText(label,{exact:true})}).locator('input').first()
  await field('新版本号').fill(definition.version);await save.click();await page.getByText('请使用新版本号，不能覆盖原量表',{exact:true}).waitFor();assert.equal(posts,0);checks.push('复制版本禁止覆盖原版本')
  await field('新版本号').fill('editor-test-v2')
  await editor.getByRole('button',{name:'编辑题目',exact:true}).first().click()
  const dialog=page.getByRole('dialog',{name:'编辑题目',exact:true})
  await dialog.locator('input').first().fill(definition.questions[1].key);await dialog.getByRole('button',{name:'保存题目到副本',exact:true}).click();await page.getByText('题目标识不能重复',{exact:true}).waitFor();checks.push('题目标识去重')
  await dialog.locator('input').first().fill(definition.questions[0].key)
  await dialog.locator('textarea').first().fill('修改后的测试题目')
  await dialog.getByRole('button',{name:'保存题目到副本',exact:true}).click();await editor.getByText('修改后的测试题目',{exact:true}).waitFor();checks.push('题目表单修改保存在隔离副本')
  fail=true;await save.click();try{await page.getByText('模拟保存失败',{exact:true}).waitFor()}catch(e){console.error('Save diagnostics',posts,await page.locator('.el-message').allTextContents());throw e}await editor.getByText('修改后的测试题目',{exact:true}).waitFor();assert.equal(await field('新版本号').inputValue(),'editor-test-v2');checks.push('保存失败保留完整副本')
  await editor.getByRole('button',{name:'退出题库编辑',exact:true}).click();await page.getByRole('button',{name:'继续编辑',exact:true}).click();await editor.waitFor();checks.push('未保存退出可以取消')
  await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('移动端无整页横向溢出')
  fail=false;await save.click();await editor.waitFor({state:'detached'});assert.equal(posts,2);assert.equal(publishes,0);assert.equal(lastPayload.questions.length,32);assert.equal(lastPayload.questions[0].leftText,'修改后的测试题目');assert.ok(lastPayload.questions.every(q=>!('editorId'in q)));assert.equal(JSON.stringify(definition),original);checks.push('另存待核验版本不自动发布且不修改来源')
  assert.deepEqual(errors,[]);checks.push('无浏览器脚本异常');console.log(JSON.stringify({passed:checks.length,checks},null,2))
 }finally{if(browser)await browser.close();server.kill()}
}
run().catch(e=>{console.error(e);process.exitCode=1})
