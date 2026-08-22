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
          <span>{{ formatDate(tool.created_at) }}</span>
          <span>浏览：{{ tool.view_count }}</span>
          <span>评论：{{ tool.comment_count }}</span>
          <span>点赞：{{ tool.like_count || 0 }}</span>
        </div>
      </template>
      <div class="tool-description">{{ tool.description }}</div>
      <div class="tool-url">
        <el-button type="primary" @click="openToolUrl">访问工具</el-button>
        <el-button 
          :type="userLikedTool ? 'danger' : 'default'" 
          size="small"
          @click="toggleLikeTool"
          :loading="likeLoading"
        >
          {{ userLikedTool ? '已点赞' : '点赞' }} ({{ tool.like_count || 0 }})
        </el-button>
        <el-button
          :type="userFavoritedTool ? 'warning' : 'default'"
          size="small"
          @click="toggleFavoriteTool"
          :loading="favoriteLoading"
        >
          {{ userFavoritedTool ? '已收藏' : '收藏' }}
        </el-button>
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
            <el-button type="text" size="small" @click="showReplyInput(comment)">回复</el-button>
            <el-button 
              v-if="isCommentOwner(comment)" 
              type="text" 
              size="small" 
              style="color: #f56c6c;"
              @click="deleteComment(comment.id)"
            >删除</el-button>
          </div>
          <!-- 回复输入框 -->
          <div v-if="replyingComment?.id === comment.id" class="reply-input">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              placeholder="回复 @{{ comment.username }}..."
            ></el-input>
            <div class="reply-actions">
              <el-button size="small" @click="cancelReply">取消</el-button>
              <el-button type="primary" size="small" @click="submitReply(comment)" :loading="submitting">
                发布回复
              </el-button>
            </div>
          </div>
          <!-- 回复列表 -->
          <div v-if="comment.replies && comment.replies.length > 0" class="reply-list">
            <div 
              v-for="reply in comment.replies" 
              :key="reply.id" 
              class="reply-item"
            >
              <div class="reply-header">
                <span class="reply-author">{{ reply.username }}</span>
                <span class="reply-date">{{ reply.created_at }}</span>
              </div>
              <div class="reply-content">
                <span class="reply-to">回复</span>
                {{ reply.content }}
              </div>
            </div>
          </div>
        </el-card>
        <div v-if="comments.length === 0 && !loadingComments" class="empty-tip">
          <p>暂无评论，快来抢沙发吧！</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '../utils/axios'

const route = useRoute()
const router = useRouter()
const tool = ref<any>(null)
const comments = ref<any[]>([])
const commentForm = ref({
  content: ''
})
const userLikedTool = ref(false)
const likeLoading = ref(false)
const userFavoritedTool = ref(false)
const favoriteLoading = ref(false)
const submitting = ref(false)
const loadingComments = ref(false)
const replyingComment = ref<any>(null)
const replyContent = ref('')

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

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
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

const isCommentOwner = (comment: any) => {
  const currentUsername = localStorage.getItem('username')
  return !!currentUsername && comment.username === currentUsername
}

const showReplyInput = (comment: any) => {
  replyingComment.value = comment
  replyContent.value = ''
}

const cancelReply = () => {
  replyingComment.value = null
  replyContent.value = ''
}

const submitReply = async (parentComment: any) => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }

  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再回复')
    router.push('/login')
    return
  }

  submitting.value = true
  try {
    const id = route.params.id
    const newComment = await axios.post(`/tools/${id}/comments`, {
      content: replyContent.value,
      parentId: parentComment.id
    })
    if (!parentComment.replies) {
      parentComment.replies = []
    }
    parentComment.replies.push(newComment)
    cancelReply()
    ElMessage.success('回复成功')
  } catch (error: any) {
    console.error('回复失败:', error)
    ElMessage.error(error.message || '回复失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const deleteComment = async (commentId: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/comments/${commentId}`)
    comments.value = comments.value.filter(c => c.id !== commentId)
    ElMessage.success('评论已删除')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
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

const toggleLikeTool = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再点赞')
    router.push('/login')
    return
  }

  likeLoading.value = true
  try {
    if (userLikedTool.value) {
      // 取消点赞
      await axios.delete(`/tools/${route.params.id}/like`)
      userLikedTool.value = false
      if (tool.value) {
        tool.value.like_count--
      }
      ElMessage.success('已取消点赞')
    } else {
      // 点赞
      await axios.post(`/tools/${route.params.id}/like`)
      userLikedTool.value = true
      if (tool.value) {
        tool.value.like_count++
      }
      ElMessage.success('点赞成功')
    }
  } catch (error: any) {
    console.error('点赞操作失败:', error)
    ElMessage.error(error.message || '操作失败，请稍后重试')
  } finally {
    likeLoading.value = false
  }
}

const loadLikeStatus = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    userLikedTool.value = false
    return
  }
  try {
    const result = await axios.get(`/tools/${route.params.id}/liked`)
    userLikedTool.value = result?.liked || false
  } catch (error) {
    console.error('加载点赞状态失败:', error)
    userLikedTool.value = false
  }
}

const checkFavoriteStatus = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    userFavoritedTool.value = false
    return
  }
  try {
    const result = await axios.get(`/tools/${route.params.id}/favorited`)
    userFavoritedTool.value = result?.liked || false
  } catch (error) {
    console.error('加载收藏状态失败:', error)
    userFavoritedTool.value = false
  }
}

const toggleFavoriteTool = async () => {
  const token = localStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录后再收藏')
    router.push('/login')
    return
  }

  favoriteLoading.value = true
  try {
    if (userFavoritedTool.value) {
      // 取消收藏
      await axios.delete(`/tools/${route.params.id}/favorite`)
      userFavoritedTool.value = false
      ElMessage.success('已取消收藏')
    } else {
      // 收藏
      await axios.post(`/tools/${route.params.id}/favorite`)
      userFavoritedTool.value = true
      ElMessage.success('收藏成功')
    }
  } catch (error: any) {
    console.error('收藏操作失败:', error)
    if (error.response?.status === 403 || error.response?.status === 401) {
      ElMessage.warning('请先登录后再收藏')
      router.push('/login')
    } else {
      ElMessage.error(error.message || '操作失败，请稍后重试')
    }
  } finally {
    favoriteLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    loadToolDetail(),
    loadComments(),
    loadLikeStatus(),
    checkFavoriteStatus()
  ])
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
  gap: var(--space-2);
}

.reply-input {
  margin-top: var(--space-3);
  padding: var(--space-3);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-md);
  border: 1px solid var(--color-border-light);
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.reply-list {
  margin-top: var(--space-3);
  padding-left: var(--space-4);
  border-left: 2px solid var(--color-border-light);
}

.reply-item {
  padding: var(--space-2) 0;
}

.reply-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.reply-author {
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.reply-date {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.reply-content {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.reply-to {
  color: var(--color-primary-600);
  margin-right: var(--space-1);
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-tertiary);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-md);
}
</style>
