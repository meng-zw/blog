<template>
  <section class="admin-card cloudreve-connection" aria-labelledby="cloudreve-connection-title" :aria-busy="loading ? 'true' : 'false'">
    <div class="cloudreve-connection__heading">
      <div>
        <p class="cloudreve-connection__eyebrow">媒体存储</p>
        <h2 id="cloudreve-connection-title">Cloudreve 连接</h2>
      </div>
      <span v-if="connection" class="cloudreve-connection__status">{{ statusLabel }}</span>
    </div>

    <p v-if="loading" role="status" aria-live="polite">正在读取 Cloudreve 连接状态…</p>
    <template v-else>
      <div v-if="!connection" class="admin-alert admin-alert--error" role="alert">
        <p>无法读取 Cloudreve 连接状态，请检查网络后重试。</p>
        <button class="admin-button admin-button--secondary" type="button" aria-label="重试读取 Cloudreve 连接状态" @click="load">重试</button>
      </div>
      <p v-if="statusMessage" class="cloudreve-connection__result" role="status" aria-live="polite">{{ statusMessage }}</p>

      <template v-if="connection && !connection.configured">
        <p>Cloudreve 尚未配置，管理员需在服务器端完成配置。</p>
      </template>

      <template v-else-if="connection">
        <dl class="cloudreve-connection__details">
          <template v-if="connection.status !== 'DISCONNECTED'">
            <dt>授权用户</dt>
            <dd>{{ authorizedIdentity }}</dd>
            <dt>权限范围</dt>
            <dd>{{ connection.grantedScopes.length ? connection.grantedScopes.join('、') : '未提供' }}</dd>
            <dt>访问权限到期</dt>
            <dd>{{ connection.accessTokenExpiresAt ?? '未提供' }}</dd>
            <dt>续期权限到期</dt>
            <dd>{{ connection.refreshTokenExpiresAt ?? '未提供' }}</dd>
          </template>
          <dt>存储根目录</dt>
          <dd>{{ connection.rootPath }}</dd>
        </dl>

        <p v-if="actionError" class="admin-alert admin-alert--error" role="alert">{{ actionError }}</p>

        <div class="cloudreve-connection__actions">
          <button
            v-if="showAuthorize"
            class="admin-button"
            type="button"
            :disabled="busy !== null"
            @click="startAuthorization"
          >{{ authorizationLabel }}</button>
          <button
            v-if="showDisconnect"
            class="admin-button admin-button--secondary"
            type="button"
            :disabled="busy !== null"
            @click="disconnect"
          >{{ busy === 'disconnect' ? '正在断开…' : '断开连接' }}</button>
        </div>
      </template>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { CloudreveConnectionResponse } from '../../../shared/api/contracts'
import { authorizeCloudreve, disconnectCloudreve, getCloudreveConnection, navigateToCloudreveAuthorization } from '../api'

const props = defineProps<{ callbackOutcome?: string | null }>()

const connection = ref<CloudreveConnectionResponse | null>(null)
const loading = ref(true)
const busy = ref<'authorize' | 'disconnect' | null>(null)
const actionError = ref('')
const statusMessage = ref(callbackMessage(props.callbackOutcome))

function callbackMessage(outcome: string | null | undefined): string {
  if (outcome === 'connected') return 'Cloudreve 已连接。'
  if (outcome === 'authorization_failed') return 'Cloudreve 授权未完成，请重试。'
  return ''
}

const statusLabel = computed(() => {
  switch (connection.value?.status) {
    case 'CONNECTED': return '已连接'
    case 'REFRESHING': return '正在刷新'
    case 'REAUTH_REQUIRED': return '需要重新授权'
    default: return '未连接'
  }
})
const authorizedIdentity = computed(() => connection.value?.authorizedDisplayName
  ?? connection.value?.authorizedSubject
  ?? '未提供')
const showAuthorize = computed(() => connection.value?.status === 'DISCONNECTED'
  || connection.value?.status === 'CONNECTED'
  || connection.value?.status === 'REAUTH_REQUIRED')
const authorizationLabel = computed(() => connection.value?.status === 'DISCONNECTED' ? '连接 Cloudreve' : '重新授权')
const showDisconnect = computed(() => connection.value?.status === 'CONNECTED' || connection.value?.status === 'REFRESHING' || connection.value?.status === 'REAUTH_REQUIRED')

async function load(): Promise<void> {
  loading.value = true
  actionError.value = ''
  try {
    connection.value = await getCloudreveConnection()
  } catch {
    connection.value = null
  } finally {
    loading.value = false
  }
}

async function startAuthorization(): Promise<void> {
  actionError.value = ''
  statusMessage.value = ''
  busy.value = 'authorize'
  try {
    navigateToCloudreveAuthorization(await authorizeCloudreve(connection.value?.trustedInternalAuthorizationOrigin ?? null))
  } catch {
    actionError.value = '无法发起 Cloudreve 授权，请检查配置后重试。'
  } finally {
    busy.value = null
  }
}

async function disconnect(): Promise<void> {
  if (!window.confirm('确定断开 Cloudreve 连接吗？断开不会删除已上传文件。')) return
  actionError.value = ''
  statusMessage.value = ''
  busy.value = 'disconnect'
  try {
    await disconnectCloudreve()
    if (connection.value) {
      connection.value = {
        ...connection.value,
        status: 'DISCONNECTED',
        authorizedSubject: null,
        authorizedDisplayName: null,
        grantedScopes: [],
        accessTokenExpiresAt: null,
        refreshTokenExpiresAt: null
      }
    }
    statusMessage.value = 'Cloudreve 已断开连接。'
  } catch {
    actionError.value = '无法断开 Cloudreve 连接，请稍后重试。'
  } finally {
    busy.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.cloudreve-connection { display: grid; gap: 14px; }
.cloudreve-connection__heading { display: flex; align-items: start; justify-content: space-between; gap: 16px; }
.cloudreve-connection__heading h2 { margin: 0; }
.cloudreve-connection__eyebrow { margin: 0 0 4px; color: #78695d; font-size: 13px; }
.cloudreve-connection__status { padding: 4px 8px; border-radius: 999px; background: #eee7df; color: #49382c; font-size: 13px; white-space: nowrap; }
.cloudreve-connection__details { display: grid; grid-template-columns: minmax(110px, max-content) 1fr; gap: 8px 16px; margin: 0; }
.cloudreve-connection__details dt { color: #78695d; }
.cloudreve-connection__details dd { margin: 0; overflow-wrap: anywhere; }
.cloudreve-connection__result { margin: 0; color: #275d38; }
.cloudreve-connection__actions { display: flex; flex-wrap: wrap; gap: 10px; }
.cloudreve-connection__actions .admin-button--secondary { margin-left: 0; }
</style>
