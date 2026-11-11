import type { FormRules } from 'element-plus'

export const accountRules: FormRules = {
  userName: [
    { required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名需为 4～32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, whitespace: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码需为 6～64 个字符', trigger: 'blur' },
  ],
  realName: [
    { required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' },
  ],
  phone: [{ max: 20, message: '联系方式不能超过 20 个字符', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}
