<template>
  <header class="admin-topbar">
    <button
      class="admin-topbar__menu"
      type="button"
      data-admin-menu-button
      :aria-expanded="menuOpen"
      aria-controls="admin-navigation"
      :aria-label="menuOpen ? '关闭后台导航' : '打开后台导航'"
      @click="emit('toggle-menu')"
    >☰</button>
    <div>
      <p class="admin-topbar__label">当前页面</p>
      <strong>{{ title }}</strong>
    </div>
    <div class="admin-topbar__actions">
      <span class="admin-topbar__identity">{{ sessionStore.session?.displayName || sessionStore.session?.username || '管理员' }}</span>
      <button type="button" :disabled="loggingOut" @click="logout">{{ loggingOut ? '退出中…' : '退出' }}</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../../features/session/store'

defineProps<{ menuOpen: boolean }>()
const emit = defineEmits<{ 'toggle-menu': [] }>()
const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const loggingOut = ref(false)
const title = computed(() => String(route.meta.title || '后台'))

async function logout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await sessionStore.logout()
  } catch {
    // The store retains a safe, non-secret notice for the public shell.
  } finally {
    loggingOut.value = false
  }
  await router.replace('/')
}
</script>

<style scoped>
.admin-topbar { position: sticky; z-index: 20; top: 0; min-height: 72px; display: flex; align-items: center; gap: 14px; padding: 10px clamp(16px, 4vw, 34px); border-bottom: 1px solid #e0dbd4; background: rgb(255 255 255 / 94%); backdrop-filter: blur(10px); font-family: var(--sans); }
.admin-topbar__menu { width: 42px; height: 42px; border: 1px solid #cfc7bd; border-radius: 7px; background: #fff; font-size: 20px; }
.admin-topbar__label { margin: 0; color: #776d64; font-size: 11px; letter-spacing: 0.12em; }
.admin-topbar__actions { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.admin-topbar__actions button { border: 1px solid #b8aea4; border-radius: 6px; padding: 7px 11px; background: #fff; }
.admin-topbar__identity { color: #61584f; }
@media (min-width: 960px) { .admin-topbar__menu { display: none; } }
@media (max-width: 540px) { .admin-topbar__identity { display: none; } }
</style>
