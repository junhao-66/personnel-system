import type { GraphEdge, GraphNode, GraphRule } from '@/types/knowledgeGraph'

/** Validate AND conditions and directed evidence paths using only this rule's selected edges. */
export function validateGraphRule(
  rule: GraphRule,
  nodes: GraphNode[],
  edges: GraphEdge[],
): string | undefined {
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(rule.id)) return '规则标识须为1至40位字母、数字、下划线或短横线'
  if (!rule.explanation.trim() || rule.explanation.length > 500)
    return '请填写规则说明（最多500字）'
  if (!nodes.some((n) => n.id === rule.actionId && n.type === 'ACTION')) return '请选择有效行动节点'
  if (!nodes.some((n) => n.id === rule.contextId && n.type === 'CONTEXT'))
    return '请选择有效情境节点'
  if (rule.conditions.length < 2 || rule.conditions.length > 4)
    return '每条规则须有2至4个不同维度的条件'
  const dimensions = new Set<string>()
  for (const condition of rule.conditions) {
    if (
      !['EI', 'SN', 'TF', 'JP'].includes(condition.dimension) ||
      dimensions.has(condition.dimension)
    )
      return '条件维度不能重复'
    dimensions.add(condition.dimension)
    if (
      !condition.dimension.includes(condition.pole) ||
      condition.pole.length !== 1 ||
      condition.nodeId !== condition.pole ||
      !nodes.some((n) => n.id === condition.nodeId && n.type === 'PREFERENCE')
    )
      return '请选择与维度对应的偏好节点'
    if (
      !Number.isInteger(condition.minimumPercent) ||
      condition.minimumPercent < 56 ||
      condition.minimumPercent > 100
    )
      return '偏好阈值须为56至100的整数'
  }
  if (
    rule.edgeIds.length < 2 ||
    rule.edgeIds.length > 32 ||
    new Set(rule.edgeIds).size !== rule.edgeIds.length
  )
    return '请选择2至32条不重复的路径关系'
  const selected = edges.filter((edge) => rule.edgeIds.includes(edge.id))
  if (selected.length !== rule.edgeIds.length) return '路径包含已不存在的关系'
  const reachesAction = (start: string) => {
    const visited = new Set([start])
    const queue = [start]
    for (const node of queue) {
      if (node === rule.actionId) return true
      for (const edge of selected)
        if (edge.from === node && !visited.has(edge.to)) {
          visited.add(edge.to)
          queue.push(edge.to)
        }
    }
    return false
  }
  if (![rule.contextId, ...rule.conditions.map((c) => c.nodeId)].every(reachesAction))
    return '所选关系须使每个偏好条件和情境均沿有向路径到达行动节点'
}
