<template>
  <section class="admin-page">
    <header class="admin-page__heading"><p class="admin-page__eyebrow">内容组织</p><h1>专题管理</h1></header>
    <form class="admin-card filters" aria-label="专题筛选" @submit.prevent="applyFilters"><label>状态<select v-model="statusFilter"><option value="">全部</option><option value="DRAFT">草稿</option><option value="PUBLISHED">发布</option></select></label><label>关键词<input v-model="keywordFilter"></label><button>筛选</button></form>
    <form class="admin-card admin-form" @submit.prevent="save">
      <h2>{{ editingId ? '编辑专题' : '新建专题' }}</h2>
      <label>名称<input v-model="form.name" required maxlength="160"></label>
      <label>描述<textarea v-model="form.description" /></label>
      <label>封面<input type="file" :accept="ACCEPTED_IMAGE_TYPES" @change="uploadCover"></label>
      <img v-if="coverUrl" class="cover" :src="coverUrl" alt="专题封面预览">
      <label>状态<select v-model="form.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">发布</option></select></label>
      <label>排序<input v-model.number="form.sortOrder" type="number" min="0"></label>
      <fieldset><legend>专题文章（拖动或使用按钮调整）</legend>
        <div class="article-search" role="search" aria-label="搜索文章"><label>文章关键词<input v-model="articleKeyword" @keyup.enter.prevent="searchArticles"></label><button type="button" @click="searchArticles">搜索</button></div>
        <label v-for="a in articleResults" :key="a.id"><input type="checkbox" :checked="form.articleIds.includes(a.id)" @change="toggle(a)">{{ a.title }}</label>
        <nav aria-label="文章搜索分页"><button type="button" :disabled="articlePage===0" @click="loadArticlePage(articlePage-1)">上一页</button><span>第 {{articlePage+1}} 页</span><button type="button" :disabled="articlePage+1>=articleTotalPages" @click="loadArticlePage(articlePage+1)">下一页</button></nav>
        <ol><li v-for="(articleId,index) in form.articleIds" :key="articleId" draggable="true" @dragstart="dragIndex=index" @dragover.prevent @drop="dropAt(index)">
          {{ title(articleId) }}
          <button type="button" :aria-label="`移除 ${title(articleId)}`" @click="removeSelected(articleId)">移除</button>
          <button type="button" :disabled="index===0" :aria-label="`上移 ${title(articleId)}`" @click="move(index,-1)">↑</button>
          <button type="button" :disabled="index===form.articleIds.length-1" :aria-label="`下移 ${title(articleId)}`" @click="move(index,1)">↓</button>
        </li></ol>
      </fieldset>
      <p v-if="error" role="alert">{{ error }}</p>
      <button class="admin-button">保存专题</button><button v-if="editingId" type="button" @click="reset">取消编辑</button>
    </form>
    <div class="admin-card"><article v-for="t in topics" :key="t.id" class="row"><div><h3>{{t.name}}</h3><p>{{t.status}} · 排序 {{t.sortOrder}}</p></div><button @click="edit(t)">编辑</button><button class="danger" @click="askDelete(t.id,t.name)">删除</button></article><nav aria-label="专题分页"><button :disabled="page===0" @click="goPage(page-1)">上一页</button><button :disabled="page+1>=totalPages" @click="goPage(page+1)">下一页</button></nav></div>
    <dialog ref="confirmDialog" aria-labelledby="topic-delete-title" aria-describedby="topic-delete-description"><h2 id="topic-delete-title">确认删除专题？</h2><p id="topic-delete-description">删除“{{deleteName}}”后无法恢复。</p><button @click="remove">确认删除</button><button @click="confirmDialog?.close()">取消</button></dialog>
  </section>
</template>
<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { AdminArticleSummaryResponse, TopicWriteRequest } from '../../../shared/api/contracts'
import { ACCEPTED_IMAGE_TYPES } from '../../media/api'
import { uploadMedia } from '../../media/uploader'
import { listAdminArticles, lookupAdminArticles } from '../../articles/admin-api'
import { createTopic, listTopics, loadTopic, removeTopic, updateTopic, type AdminTopic } from '../admin-api'
const route=useRoute(),router=useRouter(),page=ref(Math.max(0,Number(route.query.page)||0)),statusFilter=ref(String(route.query.status??'')),keywordFilter=ref(String(route.query.keyword??'')),totalPages=ref(0),topics=ref<AdminTopic[]>([]),articleResults=ref<AdminArticleSummaryResponse[]>([]),selectedArticles=ref(new Map<number,AdminArticleSummaryResponse>()),articlePage=ref(0),articleTotalPages=ref(0),articleKeyword=ref(''),editingId=ref<number|null>(null),error=ref(''),confirmDialog=ref<HTMLDialogElement>(),deleteId=ref<number|null>(null),deleteName=ref(''),coverUrl=ref(''),dragIndex=ref(-1)
const form=reactive<TopicWriteRequest>({name:'',description:'',coverMediaId:null,status:'DRAFT',articleIds:[],sortOrder:0})
async function load(){const topicPage=await listTopics(page.value,20,statusFilter.value||undefined,keywordFilter.value||undefined);topics.value=topicPage.items;totalPages.value=topicPage.totalPages}
async function loadArticlePage(value=0){const result=await listAdminArticles({page:value,size:20,keyword:articleKeyword.value.trim()||undefined});articleResults.value=result.items;articlePage.value=result.page;articleTotalPages.value=result.totalPages}
async function searchArticles(){await loadArticlePage(0)}
async function applyFilters(){await router.replace({query:{...route.query,page:undefined,status:statusFilter.value||undefined,keyword:keywordFilter.value.trim()||undefined}})}
async function goPage(value:number){await router.replace({query:{...route.query,page:value||undefined}})}
function title(id:number){return selectedArticles.value.get(id)?.title??`文章 #${id}`}
function toggle(article:AdminArticleSummaryResponse){const i=form.articleIds.indexOf(article.id);if(i<0){form.articleIds.push(article.id);selectedArticles.value.set(article.id,article)}else removeSelected(article.id)}
function removeSelected(id:number){const i=form.articleIds.indexOf(id);if(i>=0)form.articleIds.splice(i,1);selectedArticles.value.delete(id)}
function move(i:number,d:number){const j=i+d;if(j<0||j>=form.articleIds.length)return;[form.articleIds[i],form.articleIds[j]]=[form.articleIds[j]!,form.articleIds[i]!]}
function dropAt(index:number){if(dragIndex.value<0||dragIndex.value===index)return;const [id]=form.articleIds.splice(dragIndex.value,1);if(id!==undefined)form.articleIds.splice(index,0,id);dragIndex.value=-1}
async function edit(t:AdminTopic){const detail=await loadTopic(t.id);const selected=detail.articleIds.length?await lookupAdminArticles(detail.articleIds):[];selectedArticles.value=new Map(selected.map(a=>[a.id,a]));editingId.value=detail.id;Object.assign(form,{name:detail.name,description:detail.description??'',status:detail.status,sortOrder:detail.sortOrder,articleIds:[...detail.articleIds],coverMediaId:detail.coverMediaId});coverUrl.value=detail.coverUrl??''}
function reset(){editingId.value=null;selectedArticles.value.clear();Object.assign(form,{name:'',description:'',status:'DRAFT',sortOrder:0,articleIds:[],coverMediaId:null});coverUrl.value=''}
async function uploadCover(e:Event){const file=(e.target as HTMLInputElement).files?.[0];if(!file)return;try{const media=await uploadMedia(file,'TOPIC_COVER');form.coverMediaId=media.mediaId;coverUrl.value=media.url}catch{error.value='专题封面上传失败'}}
async function save(){const request={...form,articleIds:[...form.articleIds]};try{editingId.value?await updateTopic(editingId.value,request):await createTopic(request);reset();await load()}catch{error.value='专题保存失败'}}
async function askDelete(id:number,name:string){deleteId.value=id;deleteName.value=name;if(typeof confirmDialog.value?.showModal==="function")confirmDialog.value.showModal();else confirmDialog.value?.setAttribute("open","");await nextTick();confirmDialog.value?.querySelector<HTMLButtonElement>('button')?.focus()}
async function remove(){if(!deleteId.value)return;try{await removeTopic(deleteId.value);confirmDialog.value?.close();await load()}catch{error.value='专题删除失败'}}

watch(()=>route.query,()=>{page.value=Math.max(0,Number(route.query.page)||0);statusFilter.value=String(route.query.status??'');keywordFilter.value=String(route.query.keyword??'');void load()},{deep:true});onMounted(()=>{void load();void loadArticlePage()})
</script>
<style scoped>.row{display:flex;align-items:center;gap:14px;border-bottom:1px solid #e3dbd3}.row div{flex:1}.danger{color:#8a2f26}fieldset label{display:block;margin:6px 0}.cover{width:min(100%,420px);max-height:240px;object-fit:cover}</style>
