<template>
  <main class="admin-login" id="main-content" tabindex="-1">
    <section class="admin-login__panel" aria-labelledby="admin-login-title">
      <RouterLink class="admin-login__home" to="/">← 返回博客</RouterLink>
      <p class="admin-login__eyebrow">站点管理</p>
      <h1 id="admin-login-title">管理员登录</h1>
      <p class="admin-login__intro">使用部署时初始化的唯一管理员账号。</p>

      <form novalidate @submit.prevent="submit">
        <label for="admin-username">用户名</label>
        <input
          id="admin-username"
          v-model.trim="username"
          name="username"
          autocomplete="username"
          required
          maxlength="100"
          :disabled="busy"
        >

        <label for="admin-password">密码</label>
        <input
          id="admin-password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          required
          maxlength="72"
          :disabled="busy"
        >

        <div v-if="errorMessage" class="admin-login__error" role="alert">
          <p>{{ errorMessage }}</p>
          <p v-if="traceId" class="admin-login__trace">追踪编号：{{ traceId }}</p>
        </div>

        <button type="submit" :disabled="busy">
          {{ busy ? '正在登录…' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiProblem } from '../../../shared/api/problem'
import { safeAdminRedirect } from '../../../router/adminGuard'
import { useSessionStore } from '../store'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const username = ref('')
const password = ref('')
const busy = ref(false)
const errorMessage = ref('')
const traceId = ref('')

async function submit(): Promise<void> {
  if (busy.value) return
  errorMessage.value = ''
  traceId.value = ''
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入用户名和密码。'
    password.value = ''
    return
  }

  busy.value = true
  const credentials = { username: username.value.trim(), password: password.value }
  try {
    await sessionStore.login(credentials)
    await router.replace(safeAdminRedirect(route.query.redirect) ?? '/admin')
  } catch (error: unknown) {
    if (error instanceof ApiProblem) {
      errorMessage.value = error.detail || '登录失败，请重试。'
      traceId.value = error.traceId ?? ''
    } else {
      errorMessage.value = '无法连接服务器，请检查网络后重试。'
    }
  } finally {
    credentials.password = ''
    password.value = ''
    busy.value = false
  }
}

onBeforeUnmount(() => { password.value = '' })
</script>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  background: #f4f1ec;
  color: #29241f;
  font-family: var(--sans);
}

.admin-login__panel {
  width: min(100%, 440px);
  padding: clamp(28px, 6vw, 48px);
  border: 1px solid #d8d0c5;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 24px 64px rgb(56 45 35 / 10%);
}

.admin-login__home { color: #74523b; text-decoration: underline; text-underline-offset: 3px; }
.admin-login__eyebrow { margin: 32px 0 4px; color: #806149; font-size: 12px; font-weight: 700; letter-spacing: 0.14em; }
h1 { margin-bottom: 8px; font: 700 clamp(28px, 6vw, 38px)/1.25 var(--serif); }
.admin-login__intro { margin-bottom: 28px; color: #6a6159; }
form { display: grid; gap: 10px; }
label { margin-top: 8px; font-weight: 700; }
input { width: 100%; min-height: 46px; padding: 10px 12px; border: 1px solid #aaa096; border-radius: 6px; background: #fff; color: inherit; }
button { min-height: 46px; margin-top: 14px; border: 0; border-radius: 6px; background: #3d3027; color: #fff; font-weight: 700; cursor: pointer; }
button:disabled { cursor: wait; opacity: 0.65; }
.admin-login__error { margin-top: 10px; padding: 12px; border-left: 4px solid #a33327; background: #fff1ee; color: #7b251e; }
.admin-login__error p { margin: 0; }
.admin-login__trace { margin-top: 4px !important; font-size: 12px; overflow-wrap: anywhere; }
</style>
