<template>
  <section class="app-error" role="alert" aria-live="assertive">
    <h2>{{ title }}</h2>
    <p v-if="detail">{{ detail }}</p>
    <button v-if="retryLabel" type="button" @click="emit('retry')">{{ retryLabel }}</button>
  </section>
</template>

<script setup lang="ts">
interface Props {
  title: string
  detail?: string
  retryLabel?: string
}

defineProps<Props>()
const emit = defineEmits<{ retry: [] }>()
</script>

<style scoped>
.app-error {
  padding: 1.5rem;
  border: 1px solid #b05c50;
  color: #542c27;
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
