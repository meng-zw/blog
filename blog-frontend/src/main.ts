import { createApp } from 'vue'
import './styles/base.css'
import './styles/public-layout.css'
import './styles/public-pages.css'
import App from './App.vue'
import router from './router'
import pinia from './store'

const app = createApp(App)

app.use(router)
app.use(pinia)

app.mount('#app')
