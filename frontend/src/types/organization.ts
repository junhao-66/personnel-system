export interface DepartmentVO {
  id: number
  departmentName: string
  departmentCode: string
  managerUserId?: number
  selfApproverUserId?: number
  sortOrder: number
  isEnabled: number
}
export interface PositionVO {
  id: number
  positionName: string
  positionCode: string
  departmentId?: number
  description?: string
  isEnabled: number
}
export interface DepartmentSaveReqDTO {
  departmentName: string
  departmentCode: string
  sortOrder: number
}
export interface PositionSaveReqDTO {
  positionName: string
  positionCode: string
  departmentId?: number
  description?: string
}
