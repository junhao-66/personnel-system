// Bounded source checks for the project's layer boundaries and frontend coding red lines.
// Uses the existing Vue/TypeScript parsers; does not claim to replace semantic Java analysis.
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '..')
const ts = require(path.join(root, 'frontend/node_modules/typescript'))
const { parse } = require(path.join(root, 'frontend/node_modules/@vue/compiler-sfc'))
const { baseParse } = require(path.join(root, 'frontend/node_modules/@vue/compiler-dom'))
const failures = []
function files(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? files(file) : [file]
  })
}
function fail(file, message) { failures.push(`${path.relative(root, file)}: ${message}`) }
function checkScript(file, content) {
  const tree = ts.createSourceFile(file + '.ts', content, ts.ScriptTarget.Latest, true)
  function visit(node) {
    if (node.kind === ts.SyntaxKind.AnyKeyword) fail(file, 'Use a specific type or unknown instead of any')
    if (ts.isVariableDeclarationList(node) && !(node.flags & ts.NodeFlags.BlockScoped)) fail(file, 'Use const/let instead of var')
    if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression) && node.expression.expression.getText(tree) === 'console' && node.expression.name.text === 'log') fail(file, 'Remove console.log from product source')
    if (ts.isImportDeclaration(node) && node.moduleSpecifier.text === 'axios' && !file.endsWith(path.join('utils', 'request.ts'))) fail(file, 'Use the shared request/API layer instead of importing axios')
    ts.forEachChild(node, visit)
  }
  visit(tree)
}
const frontendFiles = files(path.join(root, 'frontend/src')).filter(file => /\.(vue|ts)$/.test(file) && !file.endsWith('.d.ts'))
for (const file of frontendFiles) {
  const content = fs.readFileSync(file, 'utf8')
  if (file.endsWith('.ts')) { checkScript(file, content); continue }
  const { descriptor, errors } = parse(content)
  if (errors.length) fail(file, 'Invalid Vue SFC')
  if ((descriptor.script || descriptor.scriptSetup) && (!descriptor.scriptSetup || descriptor.scriptSetup.lang !== 'ts')) fail(file, 'Use script setup with TypeScript when the component has script logic')
  for (const block of [descriptor.script, descriptor.scriptSetup]) if (block) checkScript(file, block.content)
  if (descriptor.template) {
    function visitTemplate(node) {
      if (node.type === 1) {
        const directives = node.props.filter(prop => prop.type === 7)
        if (directives.some(prop => prop.name === 'if') && directives.some(prop => prop.name === 'for')) fail(file, 'Do not combine v-if and v-for on the same element')
        if (directives.some(prop => prop.name === 'for') && !node.props.some(prop => (prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'key') || (prop.type === 6 && prop.name === 'key'))) fail(file, 'Add a stable key to v-for')
      }
      for (const child of node.children || []) visitTemplate(child)
    }
    visitTemplate(baseParse(descriptor.template.content))
  }
}
const javaFiles = files(path.join(root, 'backend/src/main/java')).filter(file => file.endsWith('.java'))
for (const file of javaFiles) {
  const content = fs.readFileSync(file, 'utf8'), relative = path.relative(root, file).replaceAll('\\', '/')
  if (/\/controller\//.test(relative)) {
    if (/import\s+(com\.personnel\.(mapper|entity)\.|com\.baomidou\.|org\.springframework\.jdbc\.)/.test(content)) fail(file, 'Controller must use services and VO/DTO, not mapper/entity/database APIs')
    if (/@Transactional\b/.test(content)) fail(file, 'Place business transactions in ServiceImpl')
  }
  if (/\/mapper\//.test(relative) && /import\s+com\.personnel\.(service|controller)\./.test(content)) fail(file, 'Mapper must not depend on services/controllers')
  if (/\/(service|mapper)\//.test(relative) && /\bHttpServlet(Request|Response)\b/.test(content)) fail(file, 'Do not access HTTP request/response in Service/Mapper')
  if (/System\.(out|err)\.print/.test(content)) fail(file, 'Use structured logging instead of System.out/err')
}
for (const file of files(path.join(root, 'backend/src/main/resources/mapper')).filter(file => file.endsWith('.xml'))) {
  if (/\bselect\s+\*/i.test(fs.readFileSync(file, 'utf8'))) fail(file, 'List SQL columns explicitly instead of SELECT *')
}
if (failures.length) { console.error(failures.join('\n')); process.exitCode = 1 }
else console.log(`Source checks passed: ${frontendFiles.length} frontend files; ${javaFiles.length} Java files.`)
