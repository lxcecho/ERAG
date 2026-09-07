<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <PageWrapper title="个人中心">
    <el-tabs v-model="activeTab">
      <!-- ==================== 基本资料 ==================== -->
      <el-tab-pane label="基本资料" name="profile">
        <div class="tab-body profile-body">
          <div class="avatar-block">
            <el-avatar :size="88" :src="profile.avatar || userStore.userInfo?.avatar || undefined">
              <el-icon :size="40"><UserFilled /></el-icon>
            </el-avatar>
            <div class="avatar-actions">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".png,.jpg,.jpeg,.webp,.gif" :on-change="onAvatarChange">
                <el-button :icon="Upload" :loading="avatarUploading">上传头像</el-button>
              </el-upload>
              <span class="avatar-tip">支持 png/jpg/jpeg/webp/gif，≤ 2MB</span>
            </div>
          </div>

          <el-form ref="profileFormRef" :model="profile" :rules="profileRules" label-width="90px" class="profile-form">
            <el-form-item label="用户名">
              <el-input :model-value="userStore.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profile.nickname" maxlength="64" show-word-limit />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profile.email" placeholder="选填" />
            </el-form-item>
            <el-form-item label="手机" prop="phone">
              <el-input v-model="profile.phone" placeholder="选填" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ==================== 修改密码 ==================== -->
      <el-tab-pane label="修改密码" name="password">
        <div class="tab-body">
          <el-form ref="pwdFormRef" :model="pwd" :rules="pwdRules" label-width="90px" class="password-form">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwd.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwd.newPassword" type="password" show-password placeholder="6-64 位" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwd.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdSaving" @click="savePassword">确认修改</el-button>
            </el-form-item>
            <el-alert type="info" :closable="false" show-icon title="修改成功后需使用新密码重新登录" />
          </el-form>
        </div>
      </el-tab-pane>

      <!-- ==================== 我的任务 ==================== -->
      <el-tab-pane label="我的任务" name="tasks">
        <el-table :data="taskData" v-loading="taskLoading" border stripe>
          <el-table-column label="类型" width="130" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="(row as MyTaskVo).taskType === 'AGENT' ? 'primary' : 'warning'">
                {{ (row as MyTaskVo).taskType === 'AGENT' ? '自主式 Agent' : '自定义 Agent' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="名称" width="180">
            <template #default="{ row }">
              {{ (row as MyTaskVo).taskType === 'AGENT' ? 'Agent 任务' : ((row as MyTaskVo).agentName || '自定义 Agent') }}
            </template>
          </el-table-column>
          <el-table-column prop="title" label="目标/问题" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <StatusTag :status="(row as MyTaskVo).status" />
            </template>
          </el-table-column>
          <el-table-column prop="tokenUsage" label="Token" width="100" align="center" />
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewTask(row as MyTaskVo)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pager"
          v-model:current-page="taskPageNo"
          v-model:page-size="taskPageSize"
          :total="taskTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadTasks"
        />
      </el-tab-pane>
    </el-tabs>
  </PageWrapper>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Upload, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageWrapper from '@/components/PageWrapper/index.vue'
import StatusTag from '@/views/agent/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { updateProfileApi, changePasswordApi, uploadAvatarApi, myTasksApi } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('profile')

/* ---------- 基本资料 ---------- */
const profileFormRef = ref<FormInstance>()
const profileSaving = ref(false)
const avatarUploading = ref(false)
const profile = reactive<ProfileRequest>({
  nickname: '',
  avatar: '',
  email: '',
  phone: ''
})

const profileRules: FormRules = {
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

onMounted(() => {
  const info = userStore.userInfo
  if (info) {
    profile.nickname = info.nickname || ''
    profile.avatar = info.avatar || ''
    profile.email = info.email || ''
    profile.phone = info.phone || ''
  }
})

async function onAvatarChange(file: { raw?: File }) {
  const raw = file.raw
  if (!raw) return
  if (raw.size > 2 * 1024 * 1024) return ElMessage.warning('头像不能超过 2MB')
  avatarUploading.value = true
  try {
    const res = await uploadAvatarApi(raw)
    profile.avatar = res.data
    ElMessage.success('头像已上传，保存资料后生效')
  } finally {
    avatarUploading.value = false
  }
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profileSaving.value = true
  try {
    const res = await updateProfileApi({
      nickname: profile.nickname,
      avatar: profile.avatar || undefined,
      email: profile.email || undefined,
      phone: profile.phone || undefined
    })
    userStore.userInfo = res.data
    ElMessage.success('资料已更新')
  } finally {
    profileSaving.value = false
  }
}

/* ---------- 修改密码 ---------- */
const pwdFormRef = ref<FormInstance>()
const pwdSaving = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, cb) => {
        if (value !== pwd.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function savePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSaving.value = true
  try {
    await changePasswordApi({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirmPassword = ''
    await userStore.logout()
    router.push('/login')
  } finally {
    pwdSaving.value = false
  }
}

/* ---------- 我的任务 ---------- */
const taskLoading = ref(false)
const taskData = ref<MyTaskVo[]>([])
const taskPageNo = ref(1)
const taskPageSize = ref(10)
const taskTotal = ref(0)

async function loadTasks() {
  taskLoading.value = true
  try {
    const res = await myTasksApi({ pageNum: taskPageNo.value, pageSize: taskPageSize.value })
    taskData.value = res.data.records
    taskTotal.value = res.data.total
  } finally {
    taskLoading.value = false
  }
}

function viewTask(row: MyTaskVo) {
  if (row.taskType === 'AGENT') {
    router.push({ path: '/agent/detail', query: { type: 'agent', id: String(row.taskId) } })
  } else if (row.agentId) {
    // 跳转自定义 Agent 对话页（运行记录可在历史中查看）
    router.push({ path: `/agent/custom/${row.agentId}` })
  }
}
</script>

<style scoped lang="scss">
.tab-body {
  padding: 16px 8px;
}

.profile-body {
  display: flex;
  gap: 48px;
  align-items: flex-start;
}

.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-top: 8px;

  .avatar-actions {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;

    .avatar-tip {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}

.profile-form {
  width: 420px;
}

.password-form {
  width: 420px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
