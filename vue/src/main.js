import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import request from '@/utils/request'
import { assetUrl } from '@/utils/assetUrl'
import { installLegacyElementIcons, mountLegacyElementIconFallbacks } from '@/utils/legacyElementIcons'
import { handleApiError } from '@/utils/errorHandler'
import './assets/gloable.css'
import './assets/green-theme.css'
import './assets/showcase-operations.css'
import './assets/legacy-element-icons.css'

const app = createApp(App)

app.config.errorHandler = (err, vm, info) => {
  console.error('Vue Error:', err, info)
  handleApiError(err, '系统发生异常', { showConsole: false })
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { size: 'small' })
installLegacyElementIcons(app)

app.config.globalProperties.request = request
app.config.globalProperties.$assetUrl = assetUrl

app.mount('#app')
mountLegacyElementIconFallbacks()
