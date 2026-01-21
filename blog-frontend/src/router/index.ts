import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue')
  },
  {
    path: '/article/:id',
    name: 'article-detail',
    component: () => import('../views/ArticleDetailView.vue')
  },
  {
    path: '/write',
    name: 'write-article',
    component: () => import('../views/WriteArticleView.vue')
  },
  {
    path: '/tool',
    name: 'tool-list',
    component: () => import('../views/ToolListView.vue')
  },
  {
    path: '/tool/:id',
    name: 'tool-detail',
    component: () => import('../views/ToolDetailView.vue')
  },
  {
    path: '/share-tool',
    name: 'share-tool',
    component: () => import('../views/ShareToolView.vue')
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router
