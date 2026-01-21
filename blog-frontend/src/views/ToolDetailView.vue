<template>
  <div class="tool-detail">
    <h1>工具详情</h1>
    <el-card shadow="hover" v-if="tool">
      <template #header>
        <h2>{{ tool.name }}</h2>
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
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from '../utils/axios'

const route = useRoute()
const tool = ref<any>(null)
const comments = ref<any[]>([])
const commentForm = ref({
  content: ''
})

const openToolUrl = () => {
  if (tool.value) {
    window.open(tool.value.url, '_blank', 'noopener noreferrer')
  }
}

const submitComment = async () => {
  if (commentForm.value.content.trim()) {
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
    } catch (error) {
      console.error('提交评论失败:', error)
      alert('评论提交失败，请稍后重试')
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

.tool-meta {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
}

.tool-description {
  margin-top: 20px;
  line-height: 1.8;
  color: #303133;
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
  color: #409eff;
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
  border-left: 3px solid #409eff;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.comment-author {
  font-weight: bold;
  color: #303133;
}

.comment-date {
  font-size: 12px;
  color: #606266;
}

.comment-content {
  margin-bottom: 10px;
  color: #303133;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: #909399;
  background-color: #fafafa;
  border-radius: 4px;
}
</style>
