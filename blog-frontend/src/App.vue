<template>
  <div v-if="sessionStore.logoutNotice" class="app-session-notice" role="alert">
    <span>{{ sessionStore.logoutNotice }}</span>
    <button type="button" aria-label="关闭退出提示" @click="sessionStore.clearLogoutNotice">关闭</button>
  </div>
  <RouterView />
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useSessionStore } from './features/session/store'

const sessionStore = useSessionStore()
</script>

<style scoped>
.app-session-notice {
  position: fixed;
  z-index: 1200;
  top: 16px;
  right: 16px;
  max-width: min(520px, calc(100vw - 32px));
  display: flex;
  align-items: start;
  gap: 14px;
  padding: 13px 14px;
  border: 1px solid #d6aaa5;
  border-radius: 7px;
  background: #fff1ee;
  color: #76271f;
  box-shadow: 0 12px 28px rgb(62 35 30 / 14%);
  font-family: var(--sans);
}

.app-session-notice button {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>
