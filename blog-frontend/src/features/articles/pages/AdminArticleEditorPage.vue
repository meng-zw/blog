<template>
  <section class="admin-page" aria-labelledby="editor-title">
    <header class="admin-page__heading"><p class="admin-page__eyebrow">内容工作台</p><h1 id="editor-title">{{ editing ? '编辑内容' : '新建内容' }}</h1><p>当前状态：{{ status }}</p></header>
    <p v-if="loading">正在加载…</p>
    <form v-else class="admin-card admin-form" @submit.prevent="save">
      <label for="title">标题</label><input id="title" v-model="form.title" maxlength="200">
      <label for="slug">固定链接</label><input id="slug" v-model="form.slug" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="留空自动生成">
      <label for="summary">摘要</label><textarea id="summary" v-model="form.summary" maxlength="500" rows="3" />
      <label for="kind">类型</label><select id="kind" v-model="form.contentType"><option value="ARTICLE">文章</option><option value="NOTE">随笔</option></select>
      <label>正文</label><MarkdownEditor v-model="form.markdownContent" />
      <div class="grid"><label>分类<select v-model="form.categoryId"><option :value="null">未分类</option><option v-for="c in options.categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label><fieldset><legend>标签</legend><label v-for="tag in options.tags" :key="tag.id"><input v-model="form.tagIds" type="checkbox" :value="tag.id">{{ tag.name }}</label></fieldset><label>专题<select v-model="form.topicId"><option :value="null">无专题</option><option v-for="t in options.topics" :key="t.id" :value="t.id">{{ t.name }}</option></select></label></div>
      <label for="cover">封面图片</label><input id="cover" type="file" :accept="ACCEPTED_IMAGE_TYPES" @change="uploadCover"><img v-if="coverUrl" class="cover" :src="coverUrl" alt="封面预览">
      <label for="seo-title">SEO 标题</label><input id="seo-title" v-model="form.seoTitle" maxlength="70"><label for="seo-description">SEO 描述</label><textarea id="seo-description" v-model="form.seoDescription" maxlength="160" />
      <p v-if="error" class="admin-alert admin-alert--error" role="alert">{{ error }}</p><p v-if="success" class="admin-alert admin-alert--success" role="status">{{ success }}</p>
      <div class="actions"><button v-if="status !== 'ARCHIVED'" class="admin-button" type="submit" :disabled="busy||stateActionBusy">保存草稿</button><button v-if="canPublish" class="admin-button admin-button--secondary" type="button" :disabled="busy||stateActionBusy" @click="openPublish">发布设置</button><button v-if="canArchive" class="danger" type="button" :disabled="busy||stateActionBusy" @click="confirmArchive">归档</button></div>
    </form>
    <PublishDialog v-if="showPublish" :disabled="stateActionBusy" :busy="stateActionBusy" @close="showPublish=false" @publish="publishNow" @schedule="schedule" />
    <dialog ref="archiveDialog" aria-labelledby="archive-title" aria-describedby="archive-description"><h2 id="archive-title">确认归档？</h2><p id="archive-description">归档后内容将不再公开展示。</p><button type="button" @click="archive">确认归档</button><button type="button" @click="archiveDialog?.close()">取消</button></dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import type { ArticleStatus, ArticleWriteRequest } from '../../../shared/api/contracts'
import { ApiProblem } from '../../../shared/api/problem'
import { ACCEPTED_IMAGE_TYPES, uploadMedia } from '../../media/api'
import { archiveArticle, createArticle, loadAdminArticle, loadArticleOptions, publishArticle, scheduleArticle, updateArticle } from '../admin-api'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import PublishDialog from '../components/PublishDialog.vue'
const route=useRoute(),router=useRouter(),id=ref<number|null>(Number(route.params.id)||null),editing=computed(()=>id.value!==null),status=ref<ArticleStatus>('DRAFT')
const canPublish=computed(()=>status.value==='DRAFT'||status.value==='SCHEDULED'),canArchive=computed(()=>status.value==='PUBLISHED')
const loading=ref(true),busy=ref(false),stateActionBusy=ref(false),dirty=ref(false),ready=ref(false),error=ref(''),success=ref(''),coverUrl=ref(''),showPublish=ref(false),publishSnapshot=ref(''),archiveDialog=ref<HTMLDialogElement>()
const options=reactive<{categories:any[];tags:any[];topics:any[]}>({categories:[],tags:[],topics:[]}),form=reactive<ArticleWriteRequest>({title:'',slug:'',summary:'',markdownContent:'',contentType:'ARTICLE',coverMediaId:null,categoryId:null,topicId:null,tagIds:[],seoTitle:null,seoDescription:null})
watch(form,()=>{if(ready.value)dirty.value=true},{deep:true})
onMounted(async()=>{window.addEventListener('beforeunload',beforeUnload);try{Object.assign(options,await loadArticleOptions());if(id.value){const a=await loadAdminArticle(id.value);status.value=a.status;Object.assign(form,{title:a.title,slug:a.slug,summary:a.summary,markdownContent:a.markdownContent,contentType:a.contentType,categoryId:a.category?.id??null,topicId:a.topic?.id??null,tagIds:a.tags.map(t=>t.id),seoTitle:a.seoTitle,seoDescription:a.seoDescription,coverMediaId:a.coverMediaId});coverUrl.value=a.coverUrl??''}}catch(e){fail(e)}finally{loading.value=false;ready.value=true}})
onBeforeUnmount(()=>window.removeEventListener('beforeunload',beforeUnload));onBeforeRouteLeave(()=>dirty.value?window.confirm('有未保存的更改，确定离开吗？'):true)
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value){e.preventDefault();e.returnValue=''}}function snapshot(){return JSON.stringify(form)}function requestCopy():ArticleWriteRequest{return JSON.parse(snapshot()) as ArticleWriteRequest}
function valid(){if(!form.title.trim()||!form.summary.trim()||!form.markdownContent.trim()){error.value='请填写标题、摘要和正文';return false}return true}
function fail(e:unknown){error.value=e instanceof ApiProblem?(e.status===409&&/\bslug\b/i.test(e.detail)?'固定链接已存在，请更换后重试。':e.detail):'操作失败，请稍后重试。'}
async function persist(){if(!valid())return null;const creating=id.value===null,submitted=snapshot(),request=requestCopy();busy.value=true;error.value='';try{const a=id.value?await updateArticle(id.value,request):await createArticle(request);id.value=a.id;status.value=a.status??status.value;const unchanged=snapshot()===submitted;if(unchanged)dirty.value=false;success.value='草稿已保存';if(creating&&unchanged)await router.replace({name:'admin-article-edit',params:{id:a.id}});return {article:a,submitted}}catch(e){fail(e);return null}finally{busy.value=false}}
async function save(){await persist()}async function openPublish(){const saved=await persist();if(saved){publishSnapshot.value=saved.submitted;showPublish.value=true}}
function finishStateAction(message:string){const unchanged=snapshot()===publishSnapshot.value;if(unchanged)dirty.value=false;success.value=unchanged?message:`${message}保存版本，当前修改尚未保存`}
async function publishNow(){if(!id.value||!canPublish.value||stateActionBusy.value)return;stateActionBusy.value=true;error.value='';success.value='';try{const a=await publishArticle(id.value);status.value=a.status;showPublish.value=false;finishStateAction('已发布')}catch(e){fail(e)}finally{stateActionBusy.value=false}}
async function schedule(iso:string){if(!id.value||!canPublish.value||stateActionBusy.value)return;stateActionBusy.value=true;error.value='';success.value='';try{const a=await scheduleArticle(id.value,iso);status.value=a.status;showPublish.value=false;finishStateAction('已安排发布')}catch(e){fail(e)}finally{stateActionBusy.value=false}}
async function confirmArchive(){if(typeof archiveDialog.value?.showModal==='function')archiveDialog.value.showModal();else archiveDialog.value?.setAttribute('open','');await nextTick();archiveDialog.value?.querySelector<HTMLButtonElement>('button')?.focus()}
async function archive(){if(!id.value||!canArchive.value)return;try{await archiveArticle(id.value);status.value='ARCHIVED';dirty.value=false;archiveDialog.value?.close();await router.push({name:'admin-articles'})}catch(e){fail(e)}}
async function uploadCover(e:Event){const file=(e.target as HTMLInputElement).files?.[0];if(!file)return;try{const media=await uploadMedia(file);form.coverMediaId=media.id;coverUrl.value=media.url}catch(e){fail(e)}}
</script>
<style scoped>.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.grid label{display:grid;gap:6px}.admin-form select{min-height:44px;padding:8px}.actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:16px}.danger{color:#8a2f26;background:#fff;border:1px solid currentColor;border-radius:6px;padding:9px 16px}.cover{width:min(100%,420px);max-height:240px;object-fit:cover;border-radius:8px}dialog{border:1px solid #c9bdb1;border-radius:10px;padding:24px}@media(max-width:760px){.grid{grid-template-columns:1fr}}</style>
