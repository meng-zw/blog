<template>
  <div class="admin-layout">
    <AdminSidebar :open="menuOpen" @close="menuOpen = false" />
    <button v-if="menuOpen" class="admin-layout__scrim" type="button" aria-label="关闭后台导航" tabindex="-1" @click="menuOpen = false" />
    <div
      class="admin-layout__workspace"
      :aria-hidden="menuOpen ? 'true' : undefined"
      :inert="menuOpen ? true : undefined"
    >
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <AdminTopbar :menu-open="menuOpen" @toggle-menu="menuOpen = !menuOpen" />
      <main id="main-content" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import 'element-plus/dist/index.css'
import './admin-element-plus.css'
import './admin-shell.css'
import AdminSidebar from './AdminSidebar.vue'
import AdminTopbar from './AdminTopbar.vue'

const menuOpen = ref(false)
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f4f2ef;
  color: #2b2723;
  font-family: var(--sans);
}

.admin-layout__workspace { min-width: 0; min-height: 100vh; }
.admin-layout__workspace main { min-height: calc(100vh - 72px); padding: clamp(22px, 4vw, 44px); }
.admin-layout__scrim { position: fixed; z-index: 30; inset: 0; width: 100%; border: 0; background: rgb(20 16 12 / 48%); }

.skip-link {
  position: fixed;
  z-index: 1000;
  top: 0.5rem;
  left: 0.5rem;
  padding: 0.75rem 1rem;
  background: #25272b;
  color: #fff;
  transform: translateY(-150%);
}

.skip-link:focus {
  transform: translateY(0);
}

main:focus {
  outline: none;
}

@media (min-width: 960px) {
  .admin-layout__workspace { margin-left: 264px; }
  .admin-layout__scrim { display: none; }
}
</style>
