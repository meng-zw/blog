<template>
  <div class="logo" :class="[`logo--${variant}`, { 'logo--link': to }]" @click="handleClick">
    <svg v-if="variant === 'icon'" class="logo__icon" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="logoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#6366f1"/>
          <stop offset="100%" stop-color="#4f46e5"/>
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="10" fill="url(#logoGradient)"/>
      <path d="M12 28V12h4l6 10 6-10h4v16h-4V18l-4 6h-4l-4-6v10h-4z" fill="white"/>
    </svg>
    
    <svg v-else-if="variant === 'text'" class="logo__text" viewBox="0 0 120 32" fill="none" xmlns="http://www.w3.org/2000/svg">
      <g class="logo__letter-m">
        <path d="M0 28V4h6.4l6.4 12 6.4-12h6.4v24h-5V14.4l-4.8 9.6h-3.2l-4.8-9.6V28H0z" fill="currentColor"/>
        <path d="M32 4h6.4v24h-6.4V4z" fill="currentColor"/>
        <path d="M44.8 4h6.4v24h-6.4V4z" fill="currentColor"/>
        <path d="M57.6 4h6.4v24h-6.4V4z" fill="currentColor"/>
      </g>
      <text v-if="showText" x="72" y="22" font-family="var(--font-family-sans)" font-size="14" font-weight="600" fill="currentColor">Blog</text>
    </svg>
    
    <div v-else-if="variant === 'combined'" class="logo__combined">
      <svg class="logo__icon-small" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="logoGradientCombined" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#6366f1"/>
            <stop offset="100%" stop-color="#4f46e5"/>
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="8" fill="url(#logoGradientCombined)"/>
        <path d="M8 24V8h3.2l4.8 8 4.8-8h3.2v16h-3.2V12.8l-3.2 7.2h-2.4l-3.2-7.2V24H8z" fill="white"/>
      </svg>
      <span v-if="showText" class="logo__text-label">M Blog</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

interface Props {
  variant?: 'icon' | 'text' | 'combined'
  showText?: boolean
  to?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'combined',
  showText: true
})

const router = useRouter()

const handleClick = () => {
  if (props.to) {
    router.push(props.to)
  }
}
</script>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-primary);
  transition: transform var(--transition-fast), opacity var(--transition-fast);
}

.logo--link {
  cursor: pointer;
}

.logo--link:hover {
  transform: scale(1.02);
  opacity: 0.9;
}

.logo--link:active {
  transform: scale(0.98);
}

.logo__icon {
  width: 40px;
  height: 40px;
}

.logo__text {
  height: 32px;
}

.logo__combined {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.logo__icon-small {
  width: 32px;
  height: 32px;
}

.logo__text-label {
  font-family: var(--font-family-sans);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
  letter-spacing: var(--letter-spacing-tight);
}

/* 暗色模式 */
@media (prefers-color-scheme: dark) {
  .logo {
    color: var(--color-text-inverse);
  }
  
  .logo__text-label {
    color: var(--color-text-inverse);
  }
}
</style>
