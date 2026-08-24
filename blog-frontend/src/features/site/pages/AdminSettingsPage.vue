<template>
  <section class="admin-page" aria-labelledby="settings-title">
    <header class="admin-page__heading">
      <p class="admin-page__eyebrow">站点资料</p>
      <h1 id="settings-title">公开站点设置</h1>
      <p>这些内容会用于公开页面的标题、简介、个人标识和 GitHub 链接。</p>
    </header>

    <p v-if="loading" class="admin-card" role="status">正在读取站点设置…</p>
    <div v-else-if="loadError" class="admin-alert admin-alert--error" role="alert">
      {{ loadError }}
      <button class="admin-button admin-button--secondary" type="button" @click="load">重试</button>
    </div>
    <form v-else class="settings-grid" novalidate @submit.prevent="save">
      <div class="admin-card admin-form">
        <label for="site-title">站点名称</label>
        <input id="site-title" v-model="form.siteTitle" name="siteTitle" required maxlength="160" :disabled="saving">

        <label for="site-subtitle">副标题</label>
        <input id="site-subtitle" v-model="form.subtitle" name="subtitle" required maxlength="160" :disabled="saving">

        <label for="site-nickname">昵称</label>
        <input id="site-nickname" v-model="form.nickname" name="nickname" required maxlength="120" :disabled="saving">

        <label for="site-bio">个人简介</label>
        <textarea id="site-bio" v-model="form.bio" name="bio" required maxlength="10000" rows="7" :disabled="saving" />

        <label for="site-github">GitHub 链接</label>
        <input id="site-github" v-model="form.githubUrl" name="githubUrl" type="url" required maxlength="500" autocomplete="url" :disabled="saving">
      </div>

      <aside class="admin-card settings-avatar" aria-labelledby="avatar-title">
        <h2 id="avatar-title">个人标识</h2>
        <div class="admin-avatar-preview admin-avatar-preview--square">
          <img :src="avatarUrl" alt="当前个人标识预览">
        </div>
        <label for="avatar-file">替换图片</label>
        <input id="avatar-file" name="avatar" type="file" :accept="ACCEPTED_IMAGE_TYPES" :disabled="uploading || saving" @change="chooseAvatar">
        <p class="admin-form__hint">保留黑底完整构图，预览使用等比缩放而不裁切。建议不超过 5 MiB，服务器校验为最终结果。</p>
        <p v-if="uploading" role="status">正在上传图片…</p>
      </aside>

      <div v-if="errorMessage" class="admin-alert admin-alert--error settings-grid__full" role="alert">
        {{ errorMessage }}<span v-if="traceId"><br>追踪编号：{{ traceId }}</span>
      </div>
      <p v-if="successMessage" class="admin-alert admin-alert--success settings-grid__full" role="status">{{ successMessage }}</p>
      <div class="settings-grid__full">
        <button class="admin-button" type="submit" :disabled="saving || uploading">{{ saving ? '正在保存…' : '保存设置' }}</button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { UpdateSiteProfileRequest } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import { ACCEPTED_IMAGE_TYPES, imageFileHint } from '../../media/api'
import { uploadMedia } from '../../media/uploader'
import { loadAdminSettings, updateAdminSettings } from '../admin-api'
import { updateSharedPublicProfile } from '../public-profile'

const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const loadError = ref('')
const errorMessage = ref('')
const traceId = ref('')
const successMessage = ref('')
const avatarUrl = ref('/images/xiao-m-mark.png')
const form = reactive<UpdateSiteProfileRequest>({
  siteTitle: '', subtitle: '', nickname: '', bio: '', githubUrl: '', avatarMediaId: null
})

function showError(error: unknown, fallback: string): void {
  if (error instanceof ApiProblem) {
    errorMessage.value = error.detail
    traceId.value = error.traceId ?? ''
  } else {
    errorMessage.value = fallback
    traceId.value = ''
  }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const profile = await loadAdminSettings()
    Object.assign(form, {
      siteTitle: profile.siteTitle,
      subtitle: profile.subtitle,
      nickname: profile.nickname,
      bio: profile.bio,
      githubUrl: profile.githubUrl,
      avatarMediaId: profile.avatarMediaId
    })
    avatarUrl.value = profile.avatarUrl
  } catch {
    loadError.value = '无法读取站点设置，请检查后端连接后重试。'
  } finally {
    loading.value = false
  }
}

async function chooseAvatar(event: Event): Promise<void> {
  const input = event.currentTarget as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  errorMessage.value = ''
  successMessage.value = ''
  const hint = imageFileHint(file)
  if (hint) {
    errorMessage.value = hint
    return
  }
  uploading.value = true
  try {
    const uploaded = await uploadMedia(file, 'AVATAR')
    form.avatarMediaId = uploaded.mediaId
    avatarUrl.value = uploaded.url
  } catch (error: unknown) {
    showError(error, '无法上传图片，请检查网络后重试。')
  } finally {
    uploading.value = false
  }
}

async function save(): Promise<void> {
  errorMessage.value = ''
  traceId.value = ''
  successMessage.value = ''
  if (!form.siteTitle.trim() || !form.subtitle.trim() || !form.nickname.trim() || !form.bio.trim() || !form.githubUrl.trim()) {
    errorMessage.value = '请完整填写站点资料。'
    return
  }
  saving.value = true
  try {
    const updated = await updateAdminSettings({ ...form })
    avatarUrl.value = updated.avatarUrl
    updateSharedPublicProfile({
      siteTitle: updated.siteTitle,
      subtitle: updated.subtitle,
      nickname: updated.nickname,
      bio: updated.bio,
      avatarUrl: updated.avatarUrl,
      githubUrl: updated.githubUrl
    })
    successMessage.value = '站点设置已保存，公开资料已同步。'
  } catch (error: unknown) {
    showError(error, '无法保存站点设置，请检查网络后重试。')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.settings-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.7fr); gap: 20px; align-items: start; }
.settings-grid__full { grid-column: 1 / -1; }
.settings-avatar { display: grid; gap: 12px; }
.settings-avatar h2 { margin-bottom: 4px; }
.admin-avatar-preview { width: 100%; aspect-ratio: 1; padding: 12px; background: #090909; overflow: hidden; }
.admin-avatar-preview--square { border-radius: 6px; }
.admin-avatar-preview img { width: 100%; height: 100%; object-fit: contain; }
@media (max-width: 760px) { .settings-grid { grid-template-columns: 1fr; } }
</style>
