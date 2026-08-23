<template>
  <aside
    id="admin-navigation"
    ref="sidebar"
    class="admin-sidebar"
    :class="{ 'admin-sidebar--open': open }"
    aria-label="后台导航区"
    :aria-hidden="navigationHidden ? 'true' : undefined"
    :inert="navigationHidden ? true : undefined"
  >
    <RouterLink class="admin-sidebar__brand" to="/admin" @click="emit('close')">
      <span class="admin-sidebar__mark">小M</span>
      <span><strong>思与行</strong><small>内容管理</small></span>
    </RouterLink>
    <nav aria-label="后台主导航">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :aria-current="isActive(item.to) ? 'page' : undefined"
        @click="emit('close')"
      >
        <span aria-hidden="true">{{ item.icon }}</span>
        {{ item.label }}
      </RouterLink>
    </nav>
    <RouterLink class="admin-sidebar__public" to="/" @click="emit('close')">← 查看公开站点</RouterLink>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const route = useRoute()
const sidebar = ref<HTMLElement | null>(null)
const desktop = ref(false)
let mediaQuery: MediaQueryList | null = null
let previousOverflow = ''
let scrollLocked = false

const items = [
  { to: '/admin', label: '概览', icon: '▦' },
  { to: '/admin/articles', label: '文章与随笔', icon: '≡' },
  { to: '/admin/topics', label: '专题', icon: '◇' },
  { to: '/admin/taxonomy', label: '分类与标签', icon: '#' },
  { to: '/admin/tools', label: '工具', icon: '⚒' },
  { to: '/admin/media', label: '媒体', icon: '▣' },
  { to: '/admin/settings', label: '站点设置', icon: '⚙' },
  { to: '/admin/account', label: '账号安全', icon: '○' }
] as const
const navigationHidden = computed(() => !props.open && !desktop.value)

function isActive(path: string): boolean {
  return path === '/admin' ? route.path === path : route.path === path || route.path.startsWith(`${path}/`)
}

function lockScroll(): void {
  if (scrollLocked) return
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  scrollLocked = true
}

function unlockScroll(): void {
  if (!scrollLocked) return
  document.body.style.overflow = previousOverflow
  scrollLocked = false
}

function focusableControls(): HTMLElement[] {
  if (!sidebar.value) return []
  return Array.from(sidebar.value.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'))
    .filter((control) => !control.hasAttribute('disabled') && control.getAttribute('aria-hidden') !== 'true')
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open || desktop.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab') return

  const controls = focusableControls()
  const first = controls.at(0)
  const last = controls.at(-1)
  if (!first || !last) return
  const active = document.activeElement
  if (event.shiftKey && (active === first || !sidebar.value?.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (active === last || !sidebar.value?.contains(active))) {
    event.preventDefault()
    first.focus()
  }
}

function onPointerDown(event: PointerEvent): void {
  if (!props.open) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (sidebar.value?.contains(target)) return
  if (target instanceof Element && target.closest('[data-admin-menu-button]')) return
  emit('close')
}

function onBreakpoint(event: MediaQueryListEvent): void {
  desktop.value = event.matches
  if (event.matches && props.open) emit('close')
}

watch(() => props.open, (open, wasOpen) => {
  if (open) {
    lockScroll()
    if (!desktop.value) {
      void nextTick(() => focusableControls().at(0)?.focus())
    }
  } else {
    unlockScroll()
    if (wasOpen && !desktop.value) {
      void nextTick(() => {
        const button = document.querySelector<HTMLElement>('[data-admin-menu-button]')
        button?.focus()
      })
    }
  }
}, { immediate: true })
watch(() => route.fullPath, () => { if (props.open) emit('close') })

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onPointerDown)
  mediaQuery = window.matchMedia('(min-width: 960px)')
  desktop.value = mediaQuery.matches
  mediaQuery.addEventListener('change', onBreakpoint)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onPointerDown)
  mediaQuery?.removeEventListener('change', onBreakpoint)
  unlockScroll()
})
</script>

<style scoped>
.admin-sidebar { position: fixed; z-index: 40; inset: 0 auto 0 0; width: 264px; display: flex; flex-direction: column; padding: 20px 14px; background: #241f1b; color: #eee8e1; font-family: var(--sans); transform: translateX(-100%); transition: transform 180ms ease; }
.admin-sidebar--open { transform: translateX(0); }
.admin-sidebar__brand { display: flex; align-items: center; gap: 12px; padding: 6px 10px 22px; border-bottom: 1px solid rgb(255 255 255 / 12%); }
.admin-sidebar__brand span:last-child { display: grid; }
.admin-sidebar__brand small { color: #b9afa6; }
.admin-sidebar__mark { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 6px; background: #080808; font: 700 14px var(--serif); }
nav { display: grid; gap: 4px; padding-top: 20px; }
nav a { display: flex; gap: 12px; align-items: center; min-height: 44px; padding: 8px 12px; border-radius: 7px; color: #d9d1c9; }
nav a:hover { background: rgb(255 255 255 / 8%); color: #fff; }
nav a[aria-current="page"] { background: #70523c; color: #fff; font-weight: 700; }
nav a span { width: 20px; text-align: center; }
.admin-sidebar__public { margin-top: auto; padding: 12px; color: #c8bdb3; text-decoration: underline; text-underline-offset: 3px; }
@media (min-width: 960px) { .admin-sidebar { transform: translateX(0); } }
</style>
