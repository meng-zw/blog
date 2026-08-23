<template><div class="markdown-editor"><div ref="host" class="markdown-editor__host" /><textarea v-if="failed" aria-label="正文" :value="modelValue" @input="fallbackInput" /><p v-if="failed" role="alert">编辑器加载失败，已切换到纯文本模式。</p></div></template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props=defineProps<{modelValue:string}>(); const emit=defineEmits<{(e:'update:modelValue',v:string):void}>(); const host=ref<HTMLElement>(); const failed=ref(false); let editor:any
onMounted(async()=>{ try { const [{default:Vditor}]=await Promise.all([import('vditor'),import('vditor/dist/index.css')]); if(!host.value)return; editor=new Vditor(host.value,{height:460,mode:'ir',value:props.modelValue,cache:{enable:false},input:(v:string)=>emit('update:modelValue',v),placeholder:'用 Markdown 记录你的思考…'}) } catch { failed.value=true } })
watch(()=>props.modelValue,v=>{ if(editor?.getValue?.()!==v) editor?.setValue?.(v) }); onBeforeUnmount(()=>editor?.destroy?.())
function fallbackInput(e:Event){emit('update:modelValue',(e.target as HTMLTextAreaElement).value)}
</script>
<style scoped>.markdown-editor__host,.markdown-editor textarea{width:100%;min-height:460px}.markdown-editor textarea{padding:16px}</style>
