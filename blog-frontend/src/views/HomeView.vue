<template>
  <div class="home-view">
    <section class="hero-section">
      <div class="hero__content">
        <h1 class="hero__title">探索 · 分享 · 成长</h1>
        <p class="hero__subtitle">在这里，记录技术足迹，分享实用工具，与志同道合的开发者一起进步</p>
        <div class="hero__actions">
          <SearchBar placeholder="搜索文章或工具..." @search="handleSearch" @clear="handleClear" />
        </div>
      </div>
    </section>

    <div class="content-wrapper">
      <section class="article-section">
        <div class="section-header">
          <div class="section-header__left">
            <Icon name="article" size="lg" color="primary" />
            <h2 class="section-title">{{ isSearching ? '搜索结果' : '最新文章' }}</h2>
          </div>
          <router-link to="/article" class="section-link">
            查看更多
            <Icon name="arrow-right" size="sm" />
          </router-link>
        </div>

        <div v-if="isSearching" class="search-result-hint">
          <p>共找到 {{ articles.length }} 篇相关文章，<router-link to="/article" class="view-all">查看全部</router-link></p>
        </div>

        <div class="article-grid">
          <el-card
            v-for="(article, index) in articles"
            :key="article.id"
            class="article-card"
            :class="{ 'has-cover': article.cover_image }"
            :style="{ animationDelay: `${index * 100}ms` }"
            shadow="hover"
          >
            <div class="article-card__body">
              <!-- 封面图（无封面时显示渐变占位） -->
              <div v-if="article.cover_image" class="article-card__cover" @click="goToArticle(article.id)">
                <img :src="article.cover_image" :alt="article.title" loading="lazy" />
              </div>
              <div class="article-card__meta">
                <span class="article-card__tag" @click="goToArticleList">
                  <Icon name="tag" size="xs" />
                  {{ article.category?.name || '技术' }}
                </span>
                <span class="article-card__date">
                  <Icon name="calendar" size="xs" />
                  {{ formatDate(article.publish_time || article.created_at) }}
                </span>
              </div>
              <h3 class="article-card__title" @click="goToArticle(article.id)">
                <el-tag v-if="article.is_top" size="small" type="danger" class="article-card__top-tag">置顶</el-tag>
                {{ article.title }}
              </h3>
              <p class="article-card__excerpt">{{ article.content?.substring(0, 120) }}...</p>
              <div class="article-card__footer">
                <div class="article-card__stats">
                  <span>
                    <Icon name="eye" size="xs" />
                    {{ article.view_count || 0 }}
                  </span>
                  <span>
                    <Icon name="comment" size="xs" />
                    {{ article.comment_count || 0 }}
                  </span>
                </div>
                <el-button type="primary" size="small" @click="goToArticle(article.id)">
                  阅读全文
                  <Icon name="arrow-right" size="xs" />
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
        
        <div v-if="articles.length === 0" class="empty-state">
          <div class="empty-state__icon">
            <Icon name="article" size="xl" />
          </div>
          <h3 class="empty-state__title">暂无文章</h3>
          <p class="empty-state__description">成为第一个分享知识的人吧！</p>
          <el-button type="primary" @click="goToWrite">
            <Icon name="write" size="sm" />
            写第一篇文章
          </el-button>
        </div>
      </section>
      
      <aside class="sidebar">
        <section class="sidebar__section">
          <div class="section-header">
            <div class="section-header__left">
              <Icon name="tool" size="md" color="success" />
              <h3 class="section-title">{{ isSearching ? '相关工具' : '热门工具' }}</h3>
            </div>
          </div>

          <div v-if="isSearching && tools.length > 0" class="search-result-hint">
            <p>共找到 {{ tools.length }} 个相关工具，<router-link to="/tool" class="view-all">查看全部</router-link></p>
          </div>

          <div class="tool-list">
            <div
              v-for="(tool, index) in tools"
              :key="tool.id"
              class="tool-item"
              :style="{ animationDelay: `${index * 50}ms` }"
            >
              <div class="tool-item__icon">
                <Icon name="tool" size="md" />
              </div>
              <div class="tool-item__content">
                <h4 class="tool-item__name" @click="goToTool(tool.id)">{{ tool.name }}</h4>
                <p class="tool-item__desc">{{ tool.description?.substring(0, 40) }}...</p>
              </div>
              <el-button type="success" size="small" text @click="openToolUrl(tool)">
                <Icon name="arrow-right" size="sm" />
              </el-button>
            </div>
          </div>

          <router-link to="/tool" class="sidebar__link">
            查看更多工具
            <Icon name="arrow-right" size="sm" />
          </router-link>
        </section>
        
        <section class="sidebar__section sidebar__section--highlight">
          <div class="sidebar__card">
            <Icon name="write" size="xl" color="primary" />
            <h3>分享你的工具</h3>
            <p>发现有趣的实用工具？分享给更多开发者吧！</p>
            <el-button type="primary" size="small" @click="goToShareTool">
              分享工具
            </el-button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from '../utils/axios'
import Icon from '../components/Icon.vue'
import SearchBar from '../components/SearchBar.vue'

const router = useRouter()
const articles = ref<any[]>([])
const tools = ref<any[]>([])
const isSearching = ref(false)
const searchKeyword = ref('')

const goToArticle = (id: number) => {
  router.push(`/article/${id}`)
}

const goToArticleList = () => {
  router.push('/article')
}

const goToTool = (id: number) => {
  router.push(`/tool/${id}`)
}

const openToolUrl = (tool: any) => {
  if (!tool?.url) {
    ElMessage.info('该工具暂无链接')
    return
  }
  window.open(tool.url, '_blank', 'noopener noreferrer')
}

const goToWrite = () => {
  router.push('/write')
}

const goToShareTool = () => {
  router.push('/share-tool')
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const handleSearch = async (keyword: string) => {
  searchKeyword.value = keyword
  isSearching.value = true
  try {
    const [articlesRes, toolsRes] = await Promise.all([
      axios.get('/search/articles', { params: { keyword } }),
      axios.get('/search/tools', { params: { keyword } })
    ])
    articles.value = articlesRes || []
    tools.value = toolsRes || []
  } catch (error) {
    console.error('搜索失败:', error)
    articles.value = []
    tools.value = []
  }
}

const handleClear = () => {
  searchKeyword.value = ''
  isSearching.value = false
  // 重新加载默认数据
  loadDefaultData()
}

const loadDefaultData = async () => {
  try {
    const articleResponse = await axios.get('/articles/latest')
    articles.value = articleResponse
    const toolResponse = await axios.get('/tools/popular')
    tools.value = toolResponse
  } catch (error) {
    console.error('获取数据失败:', error)
  }
}

onMounted(async () => {
  await loadDefaultData()
})
</script>

<style scoped>
.home-view {
  animation: none;
  position: relative;
}

/* 右侧竖排年份装饰（参考图时间轴元素） */
.home-view::after {
  content: "2026";
  position: fixed;
  right: 22px;
  top: 50%;
  transform: translateY(-50%);
  writing-mode: vertical-rl;
  font-family: var(--font-family-ui);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.6em;
  color: var(--color-text-placeholder);
  border-left: 1px solid var(--color-border-default);
  padding-left: var(--space-2);
  pointer-events: none;
  z-index: 1;
}

@media (max-width: 1360px) {
  .home-view::after {
    display: none;
  }
}

.hero-section {
  background-color: transparent;
  border: none;
  border-top: 1px solid var(--color-border-default);
  border-bottom: 1px solid var(--color-border-default);
  border-radius: 0;
  padding: var(--space-10) var(--space-6);
  margin-bottom: var(--space-8);
  text-align: center;
}

.hero__title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  font-family: var(--font-family-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-3);
  letter-spacing: var(--letter-spacing-wide);
}

/* 标题两侧短线（参考图横线框架） */
.hero__title::before,
.hero__title::after {
  content: "";
  width: 56px;
  height: 1px;
  background: var(--color-border-strong);
  flex-shrink: 0;
}

.hero__subtitle {
  font-family: var(--font-family-sans);
  font-size: var(--font-size-base);
  color: var(--color-text-tertiary);
  max-width: 560px;
  margin: 0 auto var(--space-6);
  line-height: var(--line-height-relaxed);
}

.hero__actions {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
}

.content-wrapper {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--space-6);
  align-items: start;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}

.section-header__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* 栏目标隐藏图标，纯文字楷体 */
.section-header__left :deep(svg) {
  display: none;
}

.section-title {
  font-family: var(--font-family-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
  letter-spacing: var(--letter-spacing-wide);
}

.section-link {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-primary-600);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  transition: gap var(--transition-fast);
}

.section-link:hover {
  gap: var(--space-2);
}

/* 两列等宽卡片（参考图版式） */
.article-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-6);
}

.article-card {
  border-radius: var(--border-radius-xl);
  overflow: hidden;
  animation: none;
  border: 1px solid var(--color-border-light);
  transition: border-color var(--transition-normal);
}

.article-card:hover {
  border-color: var(--color-border-default);
}

.article-card__body {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 !important;
}

/* 卡片顶部暖色封面区（模拟参考图配图位） */
.article-card__body::before {
  content: "";
  display: block;
  height: 150px;
  flex-shrink: 0;
  background: linear-gradient(
    135deg,
    var(--color-primary-100) 0%,
    var(--color-primary-200) 55%,
    var(--color-accent-100) 100%
  );
  border-bottom: 1px solid var(--color-border-light);
}

/* 有封面图时隐藏渐变占位 */
.article-card.has-cover .article-card__body::before {
  display: none;
}

.article-card__cover {
  height: 150px;
  flex-shrink: 0;
  overflow: hidden;
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
}

.article-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--transition-normal);
}

.article-card:hover .article-card__cover img {
  transform: scale(1.03);
}

.article-card__top-tag {
  margin-right: var(--space-2);
  vertical-align: middle;
}

.article-card__meta,
.article-card__title,
.article-card__excerpt,
.article-card__footer {
  margin-left: var(--space-5);
  margin-right: var(--space-5);
}

.article-card__meta,
.article-card__title {
  margin-top: var(--space-4);
}

.article-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

/* 橙色圆角分类标签（参考图强调色） */
.article-card__tag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--space-3);
  background-color: var(--color-accent-400);
  color: #ffffff;
  border-radius: var(--border-radius-full);
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-family: var(--font-family-ui);
  font-weight: var(--font-weight-medium);
  transition: background-color var(--transition-fast);
}

.article-card__tag :deep(svg) {
  display: none;
}

.article-card__tag:hover {
  background-color: var(--color-accent-500);
  color: #ffffff;
}

.article-card__date {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.article-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
  line-height: var(--line-height-tight);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.article-card__title:hover {
  color: var(--color-primary-600);
}

.article-card__excerpt {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  margin-bottom: var(--space-4);
  flex: 1;
}

.article-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding: var(--space-3) 0 var(--space-4);
  border-top: 1px solid var(--color-border-light);
}

.article-card__stats {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.article-card__stats span {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.empty-state {
  text-align: center;
  padding: var(--space-12);
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-lg);
  border: 1px dashed var(--color-border-default);
}

.empty-state__icon {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-full);
  color: var(--color-text-tertiary);
}

.empty-state__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.empty-state__description {
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-6);
}

.sidebar {
  position: sticky;
  top: 80px;
}

.sidebar__section {
  background-color: var(--color-bg-primary);
  border-radius: var(--border-radius-lg);
  padding: var(--space-5);
  margin-bottom: var(--space-5);
  border: 1px solid var(--color-border-light);
}

.sidebar__section--highlight {
  background-color: var(--color-bg-primary);
  border-color: var(--color-border-default);
}

.sidebar__card {
  text-align: center;
  padding: var(--space-4);
}

.sidebar__card h3 {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: var(--space-3) 0 var(--space-2);
}

.sidebar__card p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-4);
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.tool-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-md);
  transition: background-color var(--transition-fast);
}

.tool-item:hover {
  background-color: var(--color-gray-200);
}

.tool-item__icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  color: var(--color-text-tertiary);
  border-radius: var(--border-radius-md);
  flex-shrink: 0;
}

.tool-item__content {
  flex: 1;
  min-width: 0;
}

.tool-item__name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
  cursor: pointer;
  transition: color var(--transition-fast);
}

.tool-item__name:hover {
  color: var(--color-primary-600);
}

.tool-item__desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: var(--space-3);
  margin-top: var(--space-3);
  color: var(--color-primary-600);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
  border-radius: var(--border-radius-md);
  transition: color var(--transition-fast);
}

.sidebar__link:hover {
  background-color: transparent;
  color: var(--color-primary-500);
}

.search-result-hint {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background-color: var(--color-bg-tertiary);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.search-result-hint p {
  margin: 0;
}

.search-result-hint a {
  color: var(--color-primary-600);
  text-decoration: none;
}

.search-result-hint a:hover {
  text-decoration: underline;
}

@media (max-width: 1024px) {
  .content-wrapper {
    grid-template-columns: 1fr;
  }
  
  .sidebar {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-5);
  }
  
  .sidebar__section {
    margin-bottom: 0;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: var(--space-6) var(--space-4);
  }
  
  .hero__title {
    font-size: var(--font-size-xl);
  }
  
  .hero__subtitle {
    font-size: var(--font-size-base);
  }
  
  .hero__actions {
    flex-direction: column;
    gap: var(--space-3);
  }
  
  .article-grid {
    grid-template-columns: 1fr;
  }
  
  .sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
