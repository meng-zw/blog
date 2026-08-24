<template>
  <div class="markdown-editor">
    <div ref="host" class="markdown-editor__host" />
    <textarea v-if="failed" aria-label="正文" :value="modelValue" @input="fallbackInput" />
    <p v-if="uploading" class="markdown-editor__uploading" role="status">正在上传图片：{{ uploadProgress }}%</p>
    <p v-if="uploadError" role="alert">{{ uploadError }}</p>
    <p v-if="failed" role="alert">编辑器加载失败，已切换到纯文本模式。</p>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { imageFileHint } from '../../media/api'
import { uploadMedia } from '../../media/uploader'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()
const host = ref<HTMLElement>()
const failed = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadError = ref('')
let editor: { getValue?: () => string, setValue?: (value: string) => void, insertValue?: (value: string) => void, destroy?: () => void } | undefined
type VditorUploadHandler = (files: File[]) => string | null | Promise<string> | Promise<null>
let destroyed = false

function escapedAltText(filename: string): string {
  const basename = filename.replace(/\.[^.]+$/, '') || '图片'
  return basename.replace(/[\\\[\]]/g, '\\$&').replace(/[\r\n]+/g, ' ').trim() || '图片'
}

function captureSelection(): Range | undefined {
  const selection = window.getSelection()
  return selection && selection.rangeCount > 0 ? selection.getRangeAt(0).cloneRange() : undefined
}

function restoreSelection(range: Range | undefined): void {
  if (!range) return
  const selection = window.getSelection()
  if (!selection) return
  selection.removeAllRanges()
  selection.addRange(range)
}

async function handleUpload(files: File[]): Promise<string | null> {
  if (uploading.value) return '图片正在上传，请稍候。'
  const acceptedFiles = files.filter((file) => imageFileHint(file) === null)
  const invalidFile = files.find((file) => imageFileHint(file) !== null)
  if (invalidFile) return imageFileHint(invalidFile)!
  if (acceptedFiles.length === 0) return null

  uploading.value = true
  uploadProgress.value = 0
  uploadError.value = ''
  const originalSelection = captureSelection()
  try {
    for (const [index, file] of acceptedFiles.entries()) {
      const media = await uploadMedia(file, 'INLINE_IMAGE', (progress) => {
        if (destroyed) return
        uploadProgress.value = Math.round(((index + progress / 100) / acceptedFiles.length) * 100)
      })
      if (destroyed) return null
      if (index === 0) restoreSelection(originalSelection)
      editor?.insertValue?.(`\n![${escapedAltText(file.name)}](${media.url})\n`)
    }
    return null
  } catch {
    if (destroyed) return null
    uploadError.value = '图片上传失败，请检查网络后重试。'
    return uploadError.value
  } finally {
    if (!destroyed) uploading.value = false
  }
}

onMounted(async () => {
  destroyed = false
  try {
    const [{ default: Vditor }] = await Promise.all([import('vditor'), import('vditor/dist/index.css')])
    if (!host.value) return
    editor = new Vditor(host.value, {
      height: 460,
      mode: 'ir',
      value: props.modelValue,
      cache: { enable: false },
      input: (value: string) => emit('update:modelValue', value),
      placeholder: '用 Markdown 记录你的思考…',
      // Vditor's declaration models Promise<string> and Promise<null> separately,
      // while this async handler may resolve to either based on its upload result.
      upload: { handler: handleUpload as unknown as VditorUploadHandler }
    })
  } catch {
    failed.value = true
  }
})

watch(() => props.modelValue, (value) => { if (editor?.getValue?.() !== value) editor?.setValue?.(value) })
onBeforeUnmount(() => { destroyed = true; editor?.destroy?.() })
function fallbackInput(event: Event): void { emit('update:modelValue', (event.target as HTMLTextAreaElement).value) }
</script>
<style scoped>
.markdown-editor__host,.markdown-editor textarea{width:100%;min-height:460px}.markdown-editor textarea{padding:16px}
.markdown-editor__uploading{margin:8px 0 0;color:#6f4f39}
</style>
