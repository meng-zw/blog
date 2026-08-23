<template>
  <section class="admin-page" aria-labelledby="media-title">
    <header class="admin-page__heading">
      <p class="admin-page__eyebrow">素材</p>
      <h1 id="media-title">媒体上传</h1>
      <p>上传图片并查看本次最近一个成功素材。</p>
    </header>

    <div class="admin-card">
      <label
        class="media-dropzone"
        :class="{ 'media-dropzone--active': dragging }"
        @dragenter.prevent="dragging = true"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="dropFile"
      >
        <strong>{{ uploading ? '正在上传…' : '选择图片或拖放到此处' }}</strong>
        <span>PNG、JPEG 或 GIF，建议不超过 5 MiB；服务器校验为最终结果。</span>
        <input type="file" :accept="ACCEPTED_IMAGE_TYPES" :disabled="uploading" @change="chooseFile">
      </label>

      <div v-if="errorMessage" class="admin-alert admin-alert--error" role="alert">
        {{ errorMessage }}<span v-if="traceId"><br>追踪编号：{{ traceId }}</span>
      </div>
    </div>

    <article v-if="lastUpload" class="admin-card media-result" data-last-upload>
      <div class="media-result__preview"><img :src="lastUpload.url" alt="最近上传图片预览"></div>
      <div>
        <p class="admin-page__eyebrow">最近上传</p>
        <h2>{{ lastUpload.storageKey }}</h2>
        <dl>
          <div><dt>ID</dt><dd>{{ lastUpload.id }}</dd></div>
          <div><dt>类型</dt><dd>{{ lastUpload.contentType }}</dd></div>
          <div><dt>尺寸</dt><dd>{{ dimensions }}</dd></div>
          <div><dt>URL</dt><dd>{{ lastUpload.url }}</dd></div>
        </dl>
      </div>
    </article>
    <p v-else class="admin-card media-empty">当前 v1 后端暂不提供已有媒体库列表。此页只显示本次最近一个上传结果。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import type { MediaUploadResponse } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import { ACCEPTED_IMAGE_TYPES, imageFileHint, uploadMedia } from '../api'

const uploading = ref(false)
const dragging = ref(false)
const errorMessage = ref('')
const traceId = ref('')
const lastUpload = shallowRef<MediaUploadResponse | null>(null)
const dimensions = computed(() => lastUpload.value?.width && lastUpload.value.height
  ? `${lastUpload.value.width} × ${lastUpload.value.height}` : '未提供')

function chooseFile(event: Event): void {
  const input = event.currentTarget as HTMLInputElement
  const file = input.files?.[0]
  if (file) void submitFile(file)
  input.value = ''
}

function dropFile(event: DragEvent): void {
  dragging.value = false
  const file = event.dataTransfer?.files[0]
  if (file) void submitFile(file)
}

async function submitFile(file: File): Promise<void> {
  errorMessage.value = ''
  traceId.value = ''
  const hint = imageFileHint(file)
  if (hint) {
    errorMessage.value = hint
    return
  }
  uploading.value = true
  try {
    lastUpload.value = await uploadMedia(file)
  } catch (error: unknown) {
    if (error instanceof ApiProblem) {
      errorMessage.value = error.detail
      traceId.value = error.traceId ?? ''
    } else {
      errorMessage.value = '上传失败，请检查网络后重试。'
    }
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.media-dropzone { min-height: 210px; display: grid; place-items: center; align-content: center; gap: 8px; padding: 24px; border: 2px dashed #b9aa9d; border-radius: 10px; background: #faf8f5; text-align: center; cursor: pointer; }
.media-dropzone--active { border-color: #6f4f39; background: #f4ede6; }
.media-dropzone span { color: #71665d; }
.media-dropzone input { margin-top: 10px; max-width: 100%; }
.media-result { display: grid; grid-template-columns: minmax(180px, 280px) 1fr; gap: 24px; }
.media-result__preview { aspect-ratio: 1; padding: 12px; border-radius: 8px; background: #101010; }
.media-result__preview img { width: 100%; height: 100%; object-fit: contain; }
dl { display: grid; gap: 8px; }
dl div { display: grid; grid-template-columns: 70px 1fr; gap: 10px; }
dt { color: #74695f; } dd { margin: 0; overflow-wrap: anywhere; }
.media-empty { color: #665e56; }
@media (max-width: 680px) { .media-result { grid-template-columns: 1fr; } }
</style>
