<template>
  <section class="admin-page" aria-labelledby="media-title">
    <header class="admin-page__heading"><p class="admin-page__eyebrow">素材</p><h1 id="media-title">媒体库</h1><p>图片、封面与公开附件均通过统一媒体模块管理。</p></header>
    <div class="admin-card media-toolbar">
      <label>状态<select v-model="status" @change="load"><option value="">全部</option><option v-for="value in statuses" :key="value">{{ value }}</option></select></label>
      <label>用途<select v-model="purpose" @change="load"><option value="">全部</option><option v-for="value in purposes" :key="value">{{ value }}</option></select></label>
      <label class="media-dropzone"><strong aria-live="polite">{{ uploading ? `正在上传 ${progress}%` : '上传图片' }}</strong><input type="file" :accept="ACCEPTED_IMAGE_TYPES" :disabled="uploading" @change="chooseFile"></label>
    </div>
    <p v-if="errorMessage" class="admin-alert admin-alert--error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" role="status">正在读取媒体库…</p>
    <div v-else class="media-grid" aria-label="媒体列表">
      <article v-for="media in assets" :key="media.mediaId" class="admin-card media-item">
        <img v-if="media.contentType.startsWith('image/')" :src="media.url" :alt="media.filename">
        <div v-else class="media-file" aria-hidden="true">文件</div>
        <strong>{{ media.filename }}</strong><small>{{ media.contentType }} · {{ formatSize(media.byteSize) }}</small>
        <p><span class="badge">{{ media.provider }}</span><span class="badge">{{ media.status }}</span><span class="badge">{{ media.purpose }}</span><span class="badge" :class="media.referenced ? 'badge--used' : 'badge--unused'">{{ media.referenced ? '已使用' : '未使用' }}</span></p>
        <button v-if="!media.referenced && media.status === 'READY'" type="button" class="danger" :disabled="deleting === media.mediaId" @click="remove(media.mediaId)">删除</button>
        <small v-else>已被引用或未完成的资源不可删除</small>
      </article>
    </div>
    <p v-if="!loading && !assets.length" class="admin-card">暂无匹配媒体。</p>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { AdminMediaAssetResponse, MediaPurpose, MediaStatus } from '../../../shared/api/contracts'
import { ACCEPTED_IMAGE_TYPES, deleteMedia, imageFileHint, listMedia } from '../api'
import { uploadMedia } from '../uploader'
const assets=ref<AdminMediaAssetResponse[]>([]),loading=ref(false),uploading=ref(false),progress=ref(0),deleting=ref<number|null>(null),errorMessage=ref(''),status=ref<MediaStatus|''>(''),purpose=ref<MediaPurpose|''>('')
const statuses:MediaStatus[]=['PENDING_UPLOAD','READY','FAILED','ABANDONED','DELETED'];const purposes:MediaPurpose[]=['AVATAR','ARTICLE_COVER','TOPIC_COVER','TOOL_COVER','INLINE_IMAGE','ATTACHMENT']
async function load(){loading.value=true;errorMessage.value='';try{assets.value=(await listMedia(0,100,status.value||undefined,purpose.value||undefined)).items}catch(e){errorMessage.value=e instanceof Error?e.message:'媒体库读取失败'}finally{loading.value=false}}
async function chooseFile(e:Event){const input=e.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;const hint=imageFileHint(file);if(hint){errorMessage.value=hint;return}uploading.value=true;progress.value=0;try{await uploadMedia(file,'INLINE_IMAGE',value=>progress.value=value);await load()}catch(e){errorMessage.value=e instanceof Error?e.message:'上传失败，请检查网络后重试。'}finally{uploading.value=false}}
async function remove(id:number){deleting.value=id;errorMessage.value='';try{await deleteMedia(id);assets.value=assets.value.filter(item=>item.mediaId!==id)}catch(e){errorMessage.value=e instanceof Error?e.message:'删除失败'}finally{deleting.value=null}}
function formatSize(value:number){return value<1024?`${value} B`:value<1024*1024?`${(value/1024).toFixed(1)} KiB`:`${(value/1024/1024).toFixed(1)} MiB`}
onMounted(load)
</script>
<style scoped>.media-toolbar{display:flex;gap:16px;align-items:end;flex-wrap:wrap}.media-toolbar label{display:grid;gap:6px}.media-dropzone{padding:10px;border:1px dashed #b9aa9d;border-radius:8px;cursor:pointer}.media-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px}.media-item{display:grid;gap:8px}.media-item img,.media-file{width:100%;height:132px;object-fit:contain;background:#f5f1ec}.media-file{display:grid;place-items:center}.badge{display:inline-block;margin:0 4px 4px 0;padding:2px 5px;border-radius:4px;background:#eee7df;font-size:11px}.badge--used{background:#e4e0d8}.badge--unused{background:#e7f0e4}.danger{color:#8a2f26}.media-item small{overflow-wrap:anywhere}</style>
