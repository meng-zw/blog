<template>
  <div class="tool-detail">
    <h1>工具详情</h1>
    <el-card shadow="hover" v-if="tool">
      <template #header>
        <div class="tool-header">
          <h2>{{ tool.name }}</h2>
          <el-button v-if="isOwner" type="primary" size="small" @click="goToEdit">
            编辑工具
          </el-button>
        </div>
        <div class="tool-meta">
          <span>{{ tool.created_at }}</span>
          <span>浏览量：{{ tool.view_count }}</span>
          <span>评论数：{{ tool.comment_count }}</span>
        </div>
      </template>
      <div class="tool-description">{{ tool.description }}</div>
      <div class="tool-url">
        <el-button type="primary" @click="openToolUrl">访问工具</el-button>
        <a :href="tool.url" target="_blank" rel="noopener noreferrer" class="url-link">{{ tool.url }}</a>
      </div>
    </el-card>
    
    <div v-else class="loading">
      <el-skeleton :rows="10" animated />
    </div>
    
    <div class="comment-section">
      <h2>评论区</h2>
      <el-form :model="commentForm" label-position="top">
        <el-form-item label="写下你的评论">
          <el-input
            type="textarea"
            v-model="commentForm.content"
            :rows="4"
            placeholder="请输入评论内容"
          ></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitComment">提交评论</el-button>
        </el-form-item>
      </el-form>
      
      <div class="comments-list">
        <el-card shadow="hover" v-for="comment in comments" :key="comment.id" class="comment-card">
          <div class="comment-header">
            <span class="comment-author">{{ comment.username }}</span>
            <span class="comment-date">{{ comment.created_at }}</span>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-actions">
            <el-button type="text" size="small" @click="replyComment(comment)">回复</el-button>
          </div>
        </el-card>
        <div v-if="comments.length === 0" class="empty-tip">
          <p>暂无评论，快来抢沙发吧！</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from '../utils/axios'

const route = useRoute()
const router = useRouter()
const tool = ref<any>(null)
const comments = ref<any[]>([])
const commentForm = ref({
  content: ''
})

// 仅工具分享者本人可见编辑入口
const isOwner = computed(() => {
  if (!tool.value?.user?.username) return false
  const currentUsername = localStorage.getItem('username')
  return !!currentUsername && tool.value.user.username === currentUsername
})

const goToEdit = () => {
  if (tool.value) {
    router.push(`/tool/${tool.value.id}/edit`)
  }
}

const openToolUrl = () => {
  if (tool.value) {
    window.open(tool.value.url, '_blank', 'noopener noreferrer')
  }
}

const submitComment = async () => {
  if (!commentForm.value.content.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  
  // 检查登录状态
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再评论')
    router.push('/login')
    return
  }
  
  const id = route.params.id
  try {
    // 真实API调用提交评论
    const newComment = await axios.post(`/tools/${id}/comments`, {
      content: commentForm.value.content
    })
    comments.value.push(newComment)
    commentForm.value.content = ''
    if (tool.value) {
      tool.value.comment_count++
    }
  } catch (error: any) {
    console.error('提交评论失败:', error)
    if (error.response?.status === 403 || error.response?.status === 401) {
      ElMessage.warning('请先登录后再评论')
      router.push('/login')
    } else {
      ElMessage.error(error.message || '评论提交失败，请稍后重试')
    }
  }
}

const replyComment = (comment: any) => {
  console.log('回复评论:', comment)
  // 后续实现回复功能
}

const loadToolDetail = async () => {
  const id = route.params.id
  try {
    // 真实API调用获取工具详情
    tool.value = await axios.get(`/tools/${id}`)
  } catch (error) {
    console.error('加载工具详情失败:', error)
  }
}

const loadComments = async () => {
  const id = route.params.id
  try {
    // 真实API调用获取评论列表
    comments.value = await axios.get(`/tools/${id}/comments`)
  } catch (error) {
    console.error('加载评论失败:', error)
    // 如果API调用失败，使用模拟数据
    comments.value = [
      {
        id: 1,
        username: '用户1',
        content: '这个工具很好用，界面简洁，功能齐全！',
        created_at: '2026-01-25 10:00:00'
      },
      {
        id: 2,
        username: '用户2',
        content: '感谢分享，已经收藏了！',
        created_at: '2026-01-25 11:00:00'
      }
    ]
  }
}

onMounted(() => {
  loadToolDetail()
  loadComments()
})
</script>

<style scoped>
.tool-detail {
  text-align: left;
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.tool-header h2 {
  margin: 0;
  font-family: var(--font-family-display);
  letter-spacing: var(--letter-spacing-wide);
}

.tool-meta {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.tool-description {
  margin-top: 20px;
  line-height: 1.8;
  color: var(--color-text-primary);
  margin-bottom: 20px;
}

.tool-url {
  margin-top: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.url-link {
  color: var(--color-primary-600);
  text-decoration: none;
  word-break: break-all;
}

.url-link:hover {
  text-decoration: underline;
}

.loading {
  margin-bottom: 20px;
}

.comment-section {
  margin-top: 40px;
}

.comment-section h2 {
  margin-bottom: 20px;
}

.comments-list {
  margin-top: 30px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.comment-card {
  border-left: 2px solid var(--color-border-default);
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.comment-author {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.comment-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.comment-content {
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-tertiary);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-md);
}
</style>
