<template>
  <section class="admin-page admin-account" aria-labelledby="account-title">
    <header class="admin-page__heading">
      <p class="admin-page__eyebrow">账号安全</p>
      <h1 id="account-title">修改管理员密码</h1>
      <p>更新后当前会话按后端定义保持登录，其他会话将失效。</p>
    </header>

    <form class="admin-card admin-form" novalidate @submit.prevent="submit">
      <label for="current-password">当前密码</label>
      <input id="current-password" ref="currentPasswordInput" v-model="currentPassword" name="currentPassword" type="password" autocomplete="current-password" required :disabled="busy">

      <label for="new-password">新密码</label>
      <input id="new-password" ref="newPasswordInput" v-model="newPassword" name="newPassword" type="password" autocomplete="new-password" required minlength="12" maxlength="72" :disabled="busy">
      <p class="admin-form__hint">12 至 72 个字符。</p>

      <label for="password-confirmation">确认新密码</label>
      <input id="password-confirmation" ref="confirmationInput" v-model="confirmation" name="confirmation" type="password" autocomplete="new-password" required minlength="12" maxlength="72" :disabled="busy">

      <div v-if="errorMessage" class="admin-alert admin-alert--error" role="alert">
        {{ errorMessage }}<span v-if="traceId"><br>追踪编号：{{ traceId }}</span>
      </div>
      <p v-if="successMessage" class="admin-alert admin-alert--success" role="status">{{ successMessage }}</p>
      <button class="admin-button" type="submit" :disabled="busy">{{ busy ? '正在保存…' : '更新密码' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { ApiProblem } from '../../../shared/api/problem'
import { changePassword } from '../api'

const currentPassword = ref('')
const newPassword = ref('')
const confirmation = ref('')
const busy = ref(false)
const errorMessage = ref('')
const traceId = ref('')
const successMessage = ref('')
const currentPasswordInput = ref<HTMLInputElement | null>(null)
const newPasswordInput = ref<HTMLInputElement | null>(null)
const confirmationInput = ref<HTMLInputElement | null>(null)

function clearSecrets(): void {
  currentPassword.value = ''
  newPassword.value = ''
  confirmation.value = ''
}

async function rejectClientValidation(message: string, target: HTMLInputElement | null): Promise<void> {
  errorMessage.value = message
  clearSecrets()
  await nextTick()
  target?.focus()
}

async function submit(): Promise<void> {
  errorMessage.value = ''
  traceId.value = ''
  successMessage.value = ''
  if (!currentPassword.value) {
    await rejectClientValidation('请输入当前密码。', currentPasswordInput.value)
    return
  }
  if (newPassword.value.length < 12 || newPassword.value.length > 72) {
    await rejectClientValidation('请将新密码设为 12 至 72 个字符。', newPasswordInput.value)
    return
  }
  if (newPassword.value !== confirmation.value) {
    await rejectClientValidation('新密码与确认密码不一致。', confirmationInput.value)
    return
  }

  busy.value = true
  const request = {
    currentPassword: currentPassword.value,
    newPassword: newPassword.value,
    confirmation: confirmation.value
  }
  try {
    await changePassword({ ...request })
    successMessage.value = '密码已更新。当前管理会话保持有效。'
  } catch (error: unknown) {
    if (error instanceof ApiProblem) {
      errorMessage.value = error.detail
      traceId.value = error.traceId ?? ''
    } else {
      errorMessage.value = '无法更新密码，请检查网络后重试。'
    }
  } finally {
    request.currentPassword = ''
    request.newPassword = ''
    request.confirmation = ''
    clearSecrets()
    busy.value = false
    if (errorMessage.value) {
      await nextTick()
      currentPasswordInput.value?.focus()
    }
  }
}

onBeforeUnmount(clearSecrets)
</script>
