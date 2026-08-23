<template>
  <header ref="header" class="site-header">
    <div class="site-header__inner public-container">
      <a class="site-brand" href="/" :aria-label="`${profile.siteTitle}首页`" @click="closeMenu">
        <span class="site-brand__title">{{ profile.siteTitle }}</span>
        <span class="site-brand__subtitle">{{ profile.subtitle }}</span>
      </a>

      <button
        ref="menuButton"
        class="menu-toggle"
        type="button"
        aria-controls="public-navigation"
        :aria-expanded="menuOpen"
        :aria-label="menuOpen ? '关闭导航菜单' : '打开导航菜单'"
        @click="toggleMenu"
      >
        <span aria-hidden="true"></span>
        <span aria-hidden="true"></span>
      </button>

      <div id="public-navigation" class="site-navigation" :class="{ 'is-open': menuOpen }">
        <nav aria-label="主导航">
          <a v-for="item in navigation" :key="item.href" :href="item.href" :aria-current="isCurrent(item.href) ? 'page' : undefined" @click="closeMenu">
            {{ item.label }}
          </a>
        </nav>
        <a class="search-link" href="/search" aria-label="搜索" :aria-current="isCurrent('/search') ? 'page' : undefined" @click="closeMenu">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20">
            <circle cx="10.8" cy="10.8" r="6.5"></circle>
            <path d="m16 16 4.2 4.2"></path>
          </svg>
          <span>搜索</span>
        </a>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { routeLocationKey } from 'vue-router'

import { usePublicProfile } from '../public-profile'

const navigation = [
  { label: '首页', href: '/' },
  { label: '文章', href: '/articles' },
  { label: '专题', href: '/topics' },
  { label: '随笔', href: '/notes' },
  { label: '工具', href: '/tools' },
  { label: '关于', href: '/about' }
] as const

const header = ref<HTMLElement | null>(null)
const menuButton = ref<HTMLButtonElement | null>(null)
const menuOpen = ref(false)
const route = inject(routeLocationKey, null)
const currentPath = computed(() => route?.path ?? window.location.pathname)
const { profile } = usePublicProfile()
let priorBodyOverflow = ''
let desktopMedia: MediaQueryList | null = null

function isCurrent(href: string): boolean {
  if (href === '/') return currentPath.value === '/'
  return currentPath.value === href || currentPath.value.startsWith(`${href}/`)
}

function lockBody(): void {
  priorBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
}

function unlockBody(): void {
  document.body.style.overflow = priorBodyOverflow
}

function openMenu(): void {
  if (menuOpen.value) return
  menuOpen.value = true
  lockBody()
}

function closeMenu(): void {
  if (!menuOpen.value) return
  menuOpen.value = false
  unlockBody()
}

function toggleMenu(): void {
  if (menuOpen.value) closeMenu()
  else openMenu()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !menuOpen.value) return
  closeMenu()
  menuButton.value?.focus()
}

function onPointerDown(event: PointerEvent): void {
  if (!menuOpen.value || header.value?.contains(event.target as Node)) return
  closeMenu()
}

function onBreakpointChange(event: MediaQueryListEvent): void {
  if (event.matches) closeMenu()
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onPointerDown)
  if (typeof window.matchMedia === 'function') {
    desktopMedia = window.matchMedia('(min-width: 768px)')
    desktopMedia.addEventListener('change', onBreakpointChange)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onPointerDown)
  desktopMedia?.removeEventListener('change', onBreakpointChange)
  closeMenu()
})
</script>
