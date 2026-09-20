import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import '@wc/shared/styles/style.css'
import './mui/m-theme.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
