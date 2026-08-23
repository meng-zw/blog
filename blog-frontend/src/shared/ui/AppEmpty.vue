<template>
  <section class="app-empty" role="status" aria-live="polite">
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <button v-if="actionLabel" type="button" @click="emit('action')">{{ actionLabel }}</button>
  </section>
</template>

<script setup lang="ts">
interface Props {
  title: string
  description?: string
  actionLabel?: string
}

defineProps<Props>()
const emit = defineEmits<{ action: [] }>()
</script>

<style scoped>
.app-empty {
  padding: 2rem;
  border: 1px solid #ded4c6;
  text-align: center;
}

h2,
p {
  margin-top: 0;
}

button {
  min-height: 2.75rem;
  padding: 0.5rem 1rem;
}
</style>
