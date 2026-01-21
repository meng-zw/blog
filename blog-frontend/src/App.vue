<template>
  <div id="app" class="app">
    <el-container class="app__container">
      <el-header class="app__header">
        <header class="header">
          <div class="header__inner">
            <Logo variant="combined" :show-text="!isMobile" to="/" class="header__logo" />
            
            <nav class="header__nav" :class="{ 'header__nav--open': mobileMenuOpen }">
              <ul class="nav-list">
                <li v-for="item in navItems" :key="item.index" class="nav-item">
                  <router-link 
                    :to="item.to" 
                    class="nav-link"
                    :class="{ 'nav-link--active': activeIndex === item.index }"
                    @click="handleNavClick(item.index)"
                  >
                    <Icon :name="item.icon" size="sm" />
                    <span>{{ item.label }}</span>
                  </router-link>
                </li>
              </ul>
            </nav>
            
            <div class="header__actions">
              <template v-if="!isLoggedIn">
                <el-button type="primary" size="small" @click="goToLogin">
                  <Icon name="login" size="sm" />
                  <span>登录</span>
                </el-button>
              </template>
              <template v-else>
                <el-dropdown trigger="click" @command="handleUserCommand">
                  <div class="user-avatar">
                    <Icon name="user" size="md" />
                  </div>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="profile">
                        <Icon name="user" size="sm" />
                        个人中心
                      </el-dropdown-item>
                      <el-dropdown-item command="settings">
                        <Icon name="tool" size="sm" />
                        设置
                      </el-dropdown-item>
                      <el-dropdown-item command="logout" divided>
                        <Icon name="logout" size="sm" />
                        退出登录
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              
              <button class="mobile-menu-btn" @click="toggleMobileMenu" aria-label="菜单">
                <Icon :name="mobileMenuOpen ? 'close' : 'menu'" size="lg" />
              </button>
            </div>
          </div>
        </header>
      </el-header>
      
      <el-main class="app__main">
        <main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </main>
      </el-main>
      
      <el-footer class="app__footer">
        <footer class="footer">
          <div class="footer__inner">
            <div class="footer__brand">
              <Logo variant="icon" />
              <p class="footer__tagline">分享知识，创造价值</p>
            </div>
            
            <div class="footer__links">
              <div class="footer__group">
                <h4 class="footer__title">导航</h4>
                <ul>
                  <li><router-link to="/">首页</router-link></li>
                  <li><router-link to="/article">文章</router-link></li>
                  <li><router-link to="/tool">工具</router-link></li>
                </ul>
              </div>
              
              <div class="footer__group">
                <h4 class="footer__title">关于</h4>
                <ul>
                  <li><a href="#">关于我们</a></li>
                  <li><a href="#">联系我们</a></li>
                  <li><a href="#">反馈建议</a></li>
                </ul>
              </div>
              
              <div class="footer__group">
                <h4 class="footer__title">链接</h4>
                <ul>
                  <li><a href="#" target="_blank" rel="noopener">GitHub</a></li>
                  <li><a href="#" target="_blank" rel="noopener">文档</a></li>
                  <li><a href="#" target="_blank" rel="noopener">更新日志</a></li>
                </ul>
              </div>
            </div>
          </div>
          
          <div class="footer__bottom">
            <p>© {{ currentYear }} M Blog. All rights reserved.</p>
            <p class="footer__tech">
              Powered by <a href="https://vuejs.org" target="_blank" rel="noopener">Vue 3</a> + 
              <a href="https://spring.io/projects/spring-boot" target="_blank" rel="noopener">Spring Boot</a>
            </p>
          </div>
        </footer>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Logo from './components/Logo.vue'
import Icon from './components/Icon.vue'

interface NavItem {
  index: string
  label: string
  to: string
  icon: string
}

const router = useRouter()
const activeIndex = ref('home')
const isLoggedIn = ref(false)
const mobileMenuOpen = ref(false)
const isMobile = ref(false)

const currentYear = new Date().getFullYear()

const navItems: NavItem[] = [
  { index: 'home', label: '首页', to: '/', icon: 'home' },
  { index: 'article', label: '文章', to: '/article', icon: 'article' },
  { index: 'tool', label: '工具', to: '/tool', icon: 'tool' },
  { index: 'write', label: '写文章', to: '/write', icon: 'write' },
  { index: 'share-tool', label: '分享工具', to: '/share-tool', icon: 'share' }
]

const handleSelect = (key: string, keyPath: string[]) => {
  activeIndex.value = key
}

const handleNavClick = (index: string) => {
  activeIndex.value = index
  mobileMenuOpen.value = false
}

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

const goToLogin = () => {
  router.push('/login')
}

const handleUserCommand = (command: string) => {
  switch (command) {
    case 'profile':
      break
    case 'settings':
      break
    case 'logout':
      logout()
      break
  }
}

const logout = () => {
  localStorage.removeItem('token')
  isLoggedIn.value = false
  router.push('/login')
}

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

const checkLoginStatus = () => {
  const token = localStorage.getItem('token')
  isLoggedIn.value = !!token
}

onMounted(() => {
  checkLoginStatus()
  checkMobile()
  window.addEventListener('resize', checkMobile)
  // 监听路由变化，每次跳转后检查登录状态
  router.beforeEach(() => {
    checkLoginStatus()
    return true
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app__container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app__header {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  padding: 0;
  background-color: var(--color-bg-primary);
  box-shadow: var(--shadow-sm);
}

.header {
  background-color: var(--color-bg-primary);
}

.header__inner {
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: 0 var(--space-4);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
}

.header__logo {
  flex-shrink: 0;
}

.header__nav {
  flex: 1;
  display: flex;
  justify-content: center;
}

.nav-list {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  list-style: none;
  margin: 0;
  padding: 0;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  color: var(--color-text-secondary);
  text-decoration: none;
  border-radius: var(--border-radius-lg);
  font-weight: var(--font-weight-medium);
  transition: all var(--transition-fast);
}

.nav-link:hover {
  color: var(--color-primary-600);
  background-color: var(--color-primary-50);
}

.nav-link--active {
  color: var(--color-primary-600);
  background-color: var(--color-primary-50);
}

.header__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.user-avatar {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-gray-100);
  border-radius: var(--border-radius-full);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.user-avatar:hover {
  background-color: var(--color-primary-100);
  color: var(--color-primary-600);
}

.mobile-menu-btn {
  display: none;
  padding: var(--space-2);
  color: var(--color-text-primary);
  cursor: pointer;
  border-radius: var(--border-radius-md);
  transition: all var(--transition-fast);
}

.mobile-menu-btn:hover {
  background-color: var(--color-gray-100);
}

.app__main {
  flex: 1;
  padding: 0;
  background-color: var(--color-bg-secondary);
}

.main-content {
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: var(--space-6) var(--space-4);
  min-height: 100%;
}

.app__footer {
  padding: 0;
  background-color: var(--color-gray-900);
  color: var(--color-gray-300);
}

.footer {
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: var(--space-8) var(--space-4) var(--space-6);
}

.footer__inner {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--space-8);
  padding-bottom: var(--space-8);
  border-bottom: 1px solid var(--color-gray-700);
}

.footer__brand {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.footer__tagline {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-gray-400);
}

.footer__links {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-6);
}

.footer__group h4 {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-inverse);
}

.footer__group ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.footer__group a {
  color: var(--color-gray-400);
  font-size: var(--font-size-sm);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.footer__group a:hover {
  color: var(--color-text-inverse);
}

.footer__bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-6);
  font-size: var(--font-size-sm);
  color: var(--color-gray-500);
}

.footer__tech a {
  color: var(--color-gray-400);
  text-decoration: none;
}

.footer__tech a:hover {
  color: var(--color-primary-400);
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 768px) {
  .header__inner {
    height: 56px;
    padding: 0 var(--space-3);
  }
  
  .header__nav {
    position: fixed;
    top: 56px;
    left: 0;
    right: 0;
    background-color: var(--color-bg-primary);
    padding: var(--space-4);
    box-shadow: var(--shadow-lg);
    transform: translateY(-100%);
    opacity: 0;
    visibility: hidden;
    transition: all var(--transition-normal);
  }
  
  .header__nav--open {
    transform: translateY(0);
    opacity: 1;
    visibility: visible;
  }
  
  .nav-list {
    flex-direction: column;
    align-items: stretch;
  }
  
  .nav-link {
    padding: var(--space-3) var(--space-4);
  }
  
  .mobile-menu-btn {
    display: flex;
  }
  
  .header__actions .el-button span {
    display: none;
  }
  
  .main-content {
    padding: var(--space-4) var(--space-3);
  }
  
  .footer__inner {
    grid-template-columns: 1fr;
    gap: var(--space-6);
  }
  
  .footer__links {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-4);
  }
  
  .footer__bottom {
    flex-direction: column;
    gap: var(--space-2);
    text-align: center;
  }
}

@media (max-width: 480px) {
  .footer__links {
    grid-template-columns: 1fr;
  }
}
</style>
