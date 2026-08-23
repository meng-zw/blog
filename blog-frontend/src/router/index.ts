import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useSessionStore } from '../features/session/store'
import pinia from '../store'
import { createAdminGuard } from './adminGuard'

export const ROUTE_NAMES = {
  home: 'home',
  articles: 'articles',
  articleDetail: 'article-detail',
  topics: 'topics',
  topicDetail: 'topic-detail',
  notes: 'notes',
  tools: 'tools',
  toolDetail: 'tool-detail',
  about: 'about',
  search: 'search',
  adminLogin: 'admin-login',
  adminHome: 'admin-home',
  adminArticles: 'admin-articles',
  adminArticleNew: 'admin-article-new',
  adminArticleEdit: 'admin-article-edit',
  adminTopics: 'admin-topics',
  adminTaxonomy: 'admin-taxonomy',
  adminTools: 'admin-tools',
  adminToolNew: 'admin-tool-new',
  adminToolEdit: 'admin-tool-edit',
  adminMedia: 'admin-media',
  adminSettings: 'admin-settings',
  adminAccount: 'admin-account',
  notFound: 'not-found'
} as const

export type AppRouteName = typeof ROUTE_NAMES[keyof typeof ROUTE_NAMES]
export type AppLayout = 'public' | 'admin'

declare module 'vue-router' {
  interface RouteMeta {
    layout: AppLayout
    title: string
    public?: boolean
    requiresAdmin?: boolean
  }
}

export function publicRouteMeta(title: string): { layout: 'public'; title: string; public: true } {
  return { layout: 'public', title, public: true }
}

export function adminRouteMeta(title: string): { layout: 'admin'; title: string; requiresAdmin: true } {
  return { layout: 'admin', title, requiresAdmin: true }
}

export function adminLoginMeta(title: string): { layout: 'admin'; title: string; public: true } {
  return { layout: 'admin', title, public: true }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('../app/public/PublicLayout.vue'),
    meta: publicRouteMeta('首页'),
    children: [
      {
        path: '',
        name: ROUTE_NAMES.home,
        component: () => import('../features/site/pages/HomePage.vue'),
        meta: publicRouteMeta('首页')
      },
      {
        path: 'articles', name: ROUTE_NAMES.articles,
        component: () => import('../features/articles/pages/ArticleListPage.vue'), meta: publicRouteMeta('文章')
      },
      {
        path: 'articles/:slug', name: ROUTE_NAMES.articleDetail,
        component: () => import('../features/articles/pages/ArticleDetailPage.vue'), meta: publicRouteMeta('文章详情')
      },
      {
        path: 'topics', name: ROUTE_NAMES.topics,
        component: () => import('../features/topics/pages/TopicListPage.vue'), meta: publicRouteMeta('专题')
      },
      {
        path: 'topics/:slug', name: ROUTE_NAMES.topicDetail,
        component: () => import('../features/topics/pages/TopicDetailPage.vue'), meta: publicRouteMeta('专题详情')
      },
      {
        path: 'notes', name: ROUTE_NAMES.notes,
        component: () => import('../features/articles/pages/ArticleListPage.vue'), meta: publicRouteMeta('随笔')
      },
      {
        path: 'tools', name: ROUTE_NAMES.tools,
        component: () => import('../features/tools/pages/ToolListPage.vue'), meta: publicRouteMeta('工具')
      },
      {
        path: 'tools/:slug', name: ROUTE_NAMES.toolDetail,
        component: () => import('../features/tools/pages/ToolDetailPage.vue'), meta: publicRouteMeta('工具详情')
      },
      {
        path: 'search', name: ROUTE_NAMES.search,
        component: () => import('../features/search/pages/SearchPage.vue'), meta: publicRouteMeta('搜索')
      },
      {
        path: 'about', name: ROUTE_NAMES.about,
        component: () => import('../features/site/pages/AboutPage.vue'), meta: publicRouteMeta('关于')
      }
    ]
  },
  {
    path: '/admin/login',
    name: ROUTE_NAMES.adminLogin,
    component: () => import('../features/session/pages/AdminLoginPage.vue'),
    meta: adminLoginMeta('管理员登录')
  },
  {
    path: '/admin',
    component: () => import('../app/admin/AdminLayout.vue'),
    meta: adminRouteMeta('后台概览'),
    children: [
      {
        path: '',
        name: ROUTE_NAMES.adminHome,
        component: () => import('../features/site/pages/AdminDashboardPage.vue'),
        meta: adminRouteMeta('后台概览')
      },
      {
        path: 'articles', name: ROUTE_NAMES.adminArticles,
        component: () => import('../features/articles/pages/AdminArticleListPage.vue'), meta: adminRouteMeta('文章与随笔')
      },
      {
        path: 'articles/new', name: ROUTE_NAMES.adminArticleNew,
        component: () => import('../features/articles/pages/AdminArticleEditorPage.vue'), meta: adminRouteMeta('新建文章')
      },
      {
        path: 'articles/:id/edit', name: ROUTE_NAMES.adminArticleEdit,
        component: () => import('../features/articles/pages/AdminArticleEditorPage.vue'), meta: adminRouteMeta('编辑文章')
      },
      {
        path: 'topics', name: ROUTE_NAMES.adminTopics,
        component: () => import('../features/topics/pages/AdminTopicPage.vue'), meta: adminRouteMeta('专题')
      },
      {
        path: 'taxonomy', name: ROUTE_NAMES.adminTaxonomy,
        component: () => import('../features/topics/pages/AdminTaxonomyPage.vue'), meta: adminRouteMeta('分类与标签')
      },
      {
        path: 'tools', name: ROUTE_NAMES.adminTools,
        component: () => import('../features/tools/pages/AdminToolListPage.vue'), meta: adminRouteMeta('工具')
      },
      {
        path: 'tools/new', name: ROUTE_NAMES.adminToolNew,
        component: () => import('../features/tools/pages/AdminToolEditorPage.vue'), meta: adminRouteMeta('新建工具')
      },
      {
        path: 'tools/:id/edit', name: ROUTE_NAMES.adminToolEdit,
        component: () => import('../features/tools/pages/AdminToolEditorPage.vue'), meta: adminRouteMeta('编辑工具')
      },
      {
        path: 'media', name: ROUTE_NAMES.adminMedia,
        component: () => import('../features/media/pages/AdminMediaPage.vue'), meta: adminRouteMeta('媒体')
      },
      {
        path: 'settings', name: ROUTE_NAMES.adminSettings,
        component: () => import('../features/site/pages/AdminSettingsPage.vue'), meta: adminRouteMeta('站点设置')
      },
      {
        path: 'account', name: ROUTE_NAMES.adminAccount,
        component: () => import('../features/session/pages/AdminAccountPage.vue'), meta: adminRouteMeta('账号安全')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('../app/public/PublicLayout.vue'),
    meta: publicRouteMeta('页面未找到'),
    children: [
      {
        path: '',
        name: ROUTE_NAMES.notFound,
        component: () => import('../app/public/NotFoundPage.vue'),
        meta: publicRouteMeta('页面未找到')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  }
})

router.beforeEach(createAdminGuard(useSessionStore(pinia)))

export default router
