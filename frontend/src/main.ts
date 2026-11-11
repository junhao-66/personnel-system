import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Programmatic services have no template tags for the component resolver to inspect.
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'
import './styles/reset.scss'
import './styles/design-tokens.scss'
import './styles/workbench-theme.scss'
import './styles/light-tech-effects.scss'

createApp(App).use(createPinia()).use(router).mount('#app')
