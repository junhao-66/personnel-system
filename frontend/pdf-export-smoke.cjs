const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const { spawn } = require('node:child_process')
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
async function run() {
  const base='http://127.0.0.1:5218'
  const server=spawn(process.execPath,[path.join(__dirname,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5218','--strictPort'],{cwd:__dirname,windowsHide:true,stdio:'ignore'})
  let browser
  try {
    for(let i=0;i<100;i++){if(server.exitCode!==null)throw new Error('Vite exited');try{if((await fetch(base)).ok)break}catch{}if(i===99)throw new Error('Startup timeout');await new Promise(r=>setTimeout(r,200))}
    browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true})
    const page=await browser.newPage({viewport:{width:1440,height:1000},acceptDownloads:true});page.setDefaultTimeout(10000)
    const checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message))
    const sample=fs.readFileSync(path.join(__dirname,'../output/pdf/assessment-report-example.pdf'))
    const user={id:7,userName:'pdf-test',realName:'PDF测试',roleCode:'EMPLOYEE',auditStatus:'APPROVED',userStatus:'ENABLED'}
    const result={id:99,userId:7,scaleName:'冻结测试量表',scaleVersion:'v1',scoringVersion:'test',contentHash:'hash',typeCode:'INTJ',submittedAt:'2026-10-29T10:00:00',dimensions:[],scale:{license:'PROJECT-DEMO-ONLY',chineseVersion:'测试'},report:{reportVersion:'V1',disclaimer:'非官方MBTI测评',preferenceSummary:['冻结偏好'],strengths:[],developmentDirections:[],workPreferences:[],careerSuggestions:[],growthSuggestions:[],sourceBasis:'冻结来源',limitations:'冻结局限'}}
    let mode='pdf',calls=0,held=false,release
    await page.addInitScript(user=>{localStorage.setItem('accessToken','pdf-test-token');localStorage.setItem('currentUser',JSON.stringify(user))},user)
    await page.route(base+'/api/**',async route=>{
      const req=route.request(),url=new URL(req.url());let data=null
      if(url.pathname==='/api/user/me')data=user
      else if(url.pathname==='/api/assessment/history')data={records:[result],total:1,pageNum:1,pageSize:10}
      else if(url.pathname==='/api/assessment/results/99')data=result
      else if(url.pathname==='/api/assessment/results/99/pdf'){
        calls++;assert.equal(req.headers().authorization,'Bearer pdf-test-token')
        if(held)await new Promise(resolve=>{release=resolve})
        if(mode==='business')return route.fulfill({json:{code:400,msg:'PDF中文字体不可用，请联系管理员检查字体配置'}})
        if(mode==='limit')return route.fulfill({status:429,headers:{'Retry-After':'7'},json:{code:429,msg:'限流'}})
        if(mode==='forbidden')return route.fulfill({status:403,json:{code:403,msg:'无权限'}})
        if(mode==='expired')return route.fulfill({status:401,json:{code:401,msg:'未登录'}})
        if(mode==='invalid')return route.fulfill({status:200,contentType:'text/html',body:'unavailable'})
        return route.fulfill({contentType:'application/pdf',headers:{'Content-Disposition':'attachment; filename="assessment-report-99.pdf"'},body:sample})
      }
      await route.fulfill({json:{code:200,msg:'success',data}})
    })
    await page.goto(base+'/assessment/history');await page.getByRole('button',{name:'查看报告',exact:true}).click()
    const button=page.getByRole('button',{name:'导出PDF报告',exact:true})
    await button.waitFor();checks.push('历史报告显示PDF导出入口')
    const ready=()=>page.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('导出PDF报告'))?.disabled)
    let downloads=0;page.on('download',()=>downloads++)
    for(const [failure,message] of [['business','PDF中文字体不可用，请联系管理员检查字体配置'],['limit','操作过于频繁，请在7秒后重试'],['forbidden','当前账号没有此操作权限'],['invalid','服务器未返回有效PDF，请稍后重试']]){
      mode=failure;await button.click();await page.getByText(message,{exact:true}).waitFor();await ready()
      await page.getByRole('heading',{name:'INTJ',exact:true}).waitFor();assert.equal(downloads,0)
      checks.push(failure+'响应有明确提示、不下载错误文件且保留报告')
    }
    mode='pdf';held=true;const before=calls
    const downloadPromise=page.waitForEvent('download');await button.click()
    for(let i=0;i<100 && !release;i++)await new Promise(r=>setTimeout(r,20))
    assert.ok(release);assert.equal(await button.isDisabled(),true)
    await button.evaluate(b=>b.click());assert.equal(calls,before+1);checks.push('导出期间阻止重复请求')
    release();held=false;const download=await downloadPromise
    assert.equal(download.suggestedFilename(),'assessment-report-99.pdf')
    assert.deepEqual(fs.readFileSync(await download.path()),sample);await ready();checks.push('携带鉴权下载真实PDF且文件名和字节正确')
    await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>document.documentElement.scrollWidth<=innerWidth);checks.push('手机布局无整页横向溢出')
    await page.evaluate(()=>{window.previewUrls={created:[],revoked:[]};const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);URL.createObjectURL=b=>{const u=create(b);window.previewUrls.created.push(u);return u};URL.revokeObjectURL=u=>{window.previewUrls.revoked.push(u);revoke(u)}})
    const previewButton=page.getByRole('button',{name:'在线预览PDF',exact:true})
    mode='invalid';await previewButton.click();await page.getByText('PDF预览加载失败，报告保持不变，可重试。',{exact:true}).waitFor()
    checks.push('预览拒绝无效PDF且允许重试')
    mode='pdf';await page.getByRole('button',{name:'重新加载PDF预览',exact:true}).click()
    const frame=page.locator('iframe[title="测评报告PDF内容"]');await frame.waitFor();const blobUrl=await frame.getAttribute('src');assert.ok(blobUrl.startsWith('blob:'));assert.ok(!blobUrl.includes('token'))
    checks.push('预览使用鉴权请求及不含令牌的Blob地址')
    const beforePreviewDownload=calls,previewDownloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载当前预览PDF',exact:true}).click()
    const previewDownload=await previewDownloadPromise;assert.deepEqual(fs.readFileSync(await previewDownload.path()),sample);assert.equal(calls,beforePreviewDownload);checks.push('预览下载复用同一PDF字节且不重复请求')
    await page.getByRole('button',{name:'关闭PDF预览',exact:true}).click();await page.waitForFunction(u=>window.previewUrls.revoked.includes(u),blobUrl);checks.push('关闭预览释放Blob资源')
    held=true;release=undefined;await previewButton.click();for(let i=0;i<100&&!release;i++)await new Promise(r=>setTimeout(r,20));assert.ok(release)
    await page.getByRole('button',{name:'关闭PDF预览',exact:true}).click();release();held=false
    await page.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent.includes('在线预览PDF'))?.disabled)
    assert.equal(await frame.count(),0);assert.equal(await page.evaluate(()=>window.previewUrls.created.length),1);checks.push('提前关闭后迟到响应不重新打开预览或创建资源')
    mode='expired';await button.click();await page.waitForURL(/\/login(?:\?|$)/)
    assert.equal(await page.evaluate(()=>localStorage.getItem('accessToken')),null);checks.push('PDF请求401清除会话并返回登录')
    assert.deepEqual(errors,[]);checks.push('无浏览器脚本异常')
    console.log(JSON.stringify({passed:checks.length,checks},null,2))
  } finally {if(browser)await browser.close();server.kill()}
}
run().catch(e=>{console.error(e);process.exitCode=1})
