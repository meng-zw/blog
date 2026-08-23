<template>
  <section class="admin-page" aria-labelledby="dashboard-title">
    <header class="admin-page__heading admin-dashboard__hero">
      <div>
        <p class="admin-page__eyebrow">工作台</p>
        <h1 id="dashboard-title">你好，{{ displayName }}</h1>
        <p>在这里维护小M的公开内容和站点资料。数据统计接口尚未提供，因此概览不展示虚构计数。</p>
      </div>
      <div class="admin-dashboard__connection" :class="`is-${sessionStore.state}`" role="status">
        <span aria-hidden="true" />
        <div><strong>{{ connectionTitle }}</strong><small>{{ connectionDetail }}</small></div>
      </div>
    </header>

    <div class="admin-dashboard__links">
      <RouterLink to="/admin/articles"><strong>写作与发布</strong><span>新建或管理文章、随笔</span></RouterLink>
      <RouterLink to="/admin/topics"><strong>组织内容</strong><span>管理专题、分类和标签</span></RouterLink>
      <RouterLink to="/admin/media"><strong>上传图片</strong><span>获取可用于内容的媒体资产</span></RouterLink>
      <RouterLink to="/admin/settings"><strong>更新站点资料</strong><span>维护站名、简介与个人标识</span></RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useSessionStore } from '../../session/store'

const sessionStore = useSessionStore()
const displayName = computed(() => sessionStore.session?.displayName || sessionStore.session?.username || '管理员')
const connectionTitle = computed(() => sessionStore.state === 'authenticated' ? '后端会话已连接' : '正在确认会话')
const connectionDetail = computed(() => sessionStore.state === 'authenticated'
  ? '身份由服务端 Session 维护' : '请稍候或检查后端连接')
</script>

<style scoped>
.admin-dashboard__hero { display: flex; justify-content: space-between; gap: 24px; align-items: end; }
.admin-dashboard__connection { flex: 0 0 auto; display: flex; align-items: center; gap: 10px; padding: 12px 16px; border: 1px solid #d8d0c7; border-radius: 8px; background: #fff; }
.admin-dashboard__connection > span { width: 10px; height: 10px; border-radius: 50%; background: #aa8d72; }
.admin-dashboard__connection.is-authenticated > span { background: #3f8757; }
.admin-dashboard__connection div { display: grid; }
.admin-dashboard__connection small { color: #72685f; }
.admin-dashboard__links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.admin-dashboard__links a { min-height: 140px; display: grid; align-content: end; padding: 24px; border: 1px solid #ddd5cc; border-radius: 10px; background: #fff; }
.admin-dashboard__links a:hover { border-color: #8c6b51; transform: translateY(-1px); }
.admin-dashboard__links strong { font-size: 20px; }
.admin-dashboard__links span { color: #71665d; }
@media (max-width: 720px) { .admin-dashboard__hero { display: block; } .admin-dashboard__connection { margin-top: 18px; } .admin-dashboard__links { grid-template-columns: 1fr; } }
</style>
