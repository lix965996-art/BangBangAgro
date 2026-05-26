<template>
  <div id="app">
    <router-view />

    <!-- 全局研判助手入口 -->
    <div 
      v-if="showFloatingChat"
      class="ai-chat-container" 
      :style="containerStyle"
      ref="fabContainer"
    >
      <div 
        class="fab-btn" 
        @mousedown="startDrag"
        @click="handleFabClick"
        :title="`${assistantName}，拖动可移动位置`"
        style="display: flex !important; width: 60px; height: 60px; background: linear-gradient(135deg, #42d392, #3bb2b8); border-radius: 50%; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 4px 15px rgba(66, 211, 146, 0.4); transition: all 0.3s ease;"
      >
        <img src="@/assets/ai2.png" :alt="assistantName" style="width: 32px; height: 32px; object-fit: contain;" />
      </div>

      <transition name="fade">
        <div v-if="showChat" class="chat-window">
          <div class="chat-header">
            <div class="title">
              <span class="status-dot"></span>
              {{ assistantName }}
            </div>
            <div class="chat-header-actions">
              <button
                type="button"
                class="memory-link-btn"
                @click.stop="toggleMemoryPanel"
                :disabled="isLoading || isRecording"
                title="查看或清空服务端持久记忆"
              >
                记忆
              </button>
              <button
                type="button"
                class="memory-link-btn"
                @click.stop="rememberLastUserLine"
                :disabled="isLoading || isRecording"
                title="把上一句用户发言写入长期偏好"
              >
                记入偏好
              </button>
              <span class="close-btn" @click="showChat = false">×</span>
            </div>
          </div>

          <div v-if="showMemoryPanel" class="memory-panel">
            <div v-if="memoryLoading" class="memory-panel-loading">加载中…</div>
            <div v-else-if="memoryLoadError" class="memory-panel-error">{{ memoryLoadError }}</div>
            <div v-else class="memory-panel-inner">
              <div class="memory-block">
                <div class="memory-block-title">偏好（「记入偏好」）</div>
                <div class="memory-block-body">{{ memorySnippetPrefs }}</div>
              </div>
              <div class="memory-block">
                <div class="memory-block-title">对话滚动摘要（后端自动写入）</div>
                <div class="memory-block-body">{{ memorySnippetSummary }}</div>
              </div>
              <div class="memory-panel-actions">
                <button type="button" class="memory-mini-btn" @click="rememberLastUserLine" :disabled="memoryActionBusy || isLoading">记入上一句</button>
                <button type="button" class="memory-mini-btn" @click="clearMemoryRemote('preferences')" :disabled="memoryActionBusy">清偏好</button>
                <button type="button" class="memory-mini-btn" @click="clearMemoryRemote('summary')" :disabled="memoryActionBusy">清摘要</button>
                <button type="button" class="memory-mini-btn danger" @click="clearMemoryRemote('all')" :disabled="memoryActionBusy">清空全部</button>
              </div>
            </div>
          </div>

          <div class="chat-body" ref="chatBody">
            <div v-for="(msg, index) in messages" :key="index" :class="['message-row', msg.role]">
              <div class="avatar">
                <img v-if="msg.role === 'ai'" src="@/assets/ai2.png" :alt="assistantName" class="avatar-img" />
                <img v-else src="@/assets/nongming.png" alt="农民" class="avatar-img" />
              </div>
              <div class="bubble">
                <div class="message-text">{{ msg.content }}</div>
              </div>
            </div>
            
            <div v-if="isLoading" class="message-row ai">
              <div class="avatar"><img src="@/assets/ai2.png" :alt="assistantName" class="avatar-img" /></div>
              <div class="bubble loading">
                <span>.</span><span>.</span><span>.</span>
              </div>
            </div>
          </div>

          <div class="chat-footer">
            <button 
              class="voice-btn" 
              :class="{ 'recording': isRecording }" 
              @click="toggleVoiceRecognition"
              :disabled="isLoading"
              :title="isRecording ? '点击停止录音' : '点击语音输入'"
            >
              <i :class="isRecording ? 'el-icon-microphone' : 'el-icon-microphone'"></i>
              <span v-if="isRecording" class="recording-wave"></span>
            </button>
            <input 
              v-model="inputMsg" 
              @keyup.enter="sendMessage"
              type="text" 
              :placeholder="isRecording ? '正在聆听...' : '例如：帮我判断当前优先处理哪条预警'" 
            />
            <button type="button" class="send-btn" @click="sendMessage" :disabled="isLoading || isRecording">发送</button>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script>
import request from '@/utils/request';
import { eventBus, EVENTS } from '@/utils/eventBus';

const ASSISTANT_NAME = '禾序';
const MAIN_STAGE_ROUTES = new Set([
  '/home',
  '/aether-monitor',
  '/fruit-detect',
  '/farm-map-gaode',
  '/farmmap3d',
  '/alert-center',
  '/business-analysis'
]);
const MAIN_STAGE_ACTION_TYPES = new Set([
  'irrigation_on',
  'irrigation_off',
  'led_on',
  'led_off',
  'drone_spray'
]);

export default {
  name: 'App',
  data() {
    return {
      showChat: false,
      inputMsg: '',
      isLoading: false,
      messages: [
        {
          role: 'ai',
          content: '你好，我是禾序。我可以帮你梳理当前页面重点、建议优先处理项，并跳转到主线页面。'
        }
      ],
      // 拖动相关
      isDragging: false,
      hasDragged: false,
      dragStartX: 0,
      dragStartY: 0,
      fabX: null,
      fabY: null,
      // 语音识别相关
      isRecording: false,
      recognition: null,
      voiceSupported: false,
      assistantName: ASSISTANT_NAME,
      showMemoryPanel: false,
      memoryLoading: false,
      memoryLoadError: null,
      memorySnapshot: null,
      memoryActionBusy: false
    };
  },
  computed: {
    memorySnippetPrefs() {
      const s = this.memorySnapshot && this.memorySnapshot.preferences;
      const t = s != null ? String(s).trim() : '';
      return t || '（暂无）';
    },
    memorySnippetSummary() {
      const s = this.memorySnapshot && this.memorySnapshot.conversationSummary;
      const t = s != null ? String(s).trim() : '';
      return t || '（暂无）';
    },
    showFloatingChat() {
      return MAIN_STAGE_ROUTES.has(this.$route.path);
    },
    containerStyle() {
      const baseStyle = {
        position: 'fixed !important',
        zIndex: '99999 !important',
        display: 'block !important',
        userSelect: 'none'
      };
      
      if (this.fabX !== null && this.fabY !== null) {
        return {
          ...baseStyle,
          left: this.fabX + 'px',
          top: this.fabY + 'px',
          right: 'auto',
          bottom: 'auto'
        };
      }
      
      // 默认位置：右下角
      return {
        ...baseStyle,
        right: '30px',
        bottom: '30px'
      };
    }
  },
  watch: {
    '$route.path'(path) {
      if (!MAIN_STAGE_ROUTES.has(path)) {
        this.showChat = false;
        this.showMemoryPanel = false;
      }
    }
  },
  mounted() {
    this.loadFabPosition();
    document.addEventListener('mousemove', this.onDrag);
    document.addEventListener('mouseup', this.stopDrag);
    window.addEventListener('resize', this.handleResize);
    this.initVoiceRecognition();
  },
  beforeDestroy() {
    document.removeEventListener('mousemove', this.onDrag);
    document.removeEventListener('mouseup', this.stopDrag);
    window.removeEventListener('resize', this.handleResize);
    this.stopVoiceRecognition();
  },
  methods: {
    // 拖动功能
    loadFabPosition() {
      const saved = localStorage.getItem('fabPosition');
      if (saved) {
        try {
          const pos = JSON.parse(saved);
          this.fabX = pos.x;
          this.fabY = pos.y;
        } catch (e) {
          console.error('加载位置失败:', e);
        }
      } else {
      }
    },
    saveFabPosition() {
      const position = {
        x: this.fabX,
        y: this.fabY
      };
      localStorage.setItem('fabPosition', JSON.stringify(position));
    },
    startDrag(e) {
      this.isDragging = true;
      this.hasDragged = false;
      const rect = this.$refs.fabContainer.getBoundingClientRect();
      this.dragStartX = e.clientX - rect.left;
      this.dragStartY = e.clientY - rect.top;
      e.preventDefault();
    },
    onDrag(e) {
      if (!this.isDragging) return;
      this.hasDragged = true;
      
      let newX = e.clientX - this.dragStartX;
      let newY = e.clientY - this.dragStartY;
      
      // 限制在窗口范围内，留出按钮尺寸的边距
      const buttonSize = 60;
      const maxX = window.innerWidth - buttonSize;
      const maxY = window.innerHeight - buttonSize;
      
      newX = Math.max(0, Math.min(newX, maxX));
      newY = Math.max(0, Math.min(newY, maxY));
      
      this.fabX = newX;
      this.fabY = newY;
      
    },
    stopDrag() {
      if (this.isDragging && this.hasDragged) {
        this.saveFabPosition();
      }
      this.isDragging = false;
    },
    handleFabClick() {
      // 只有没有拖动时才触发点击
      if (!this.hasDragged) {
        this.toggleChat();
      }
      this.hasDragged = false;
    },
    toggleChat() {
      this.showChat = !this.showChat;
      if (!this.showChat) {
        this.showMemoryPanel = false;
      }
      if (this.showChat) {
        this.scrollToBottom();
      }
    },
    buildPlanHistoryForPlan() {
      const out = [];
      const prior = this.messages.length >= 1 ? this.messages.slice(0, -1) : [];
      for (let i = 0; i < prior.length; i++) {
        const m = prior[i];
        if (!m || !m.content || !String(m.content).trim()) continue;
        const role = m.role === 'ai' ? 'assistant' : m.role === 'user' ? 'user' : null;
        if (!role) continue;
        out.push({ role, content: String(m.content).trim() });
      }
      const max = 32;
      return out.length > max ? out.slice(out.length - max) : out;
    },
    toggleMemoryPanel() {
      this.showMemoryPanel = !this.showMemoryPanel;
      if (this.showMemoryPanel) {
        this.loadMemorySnapshot();
      }
    },
    async loadMemorySnapshot() {
      this.memoryLoading = true;
      this.memoryLoadError = null;
      this.memorySnapshot = null;
      try {
        const res = await request.get('/api/agent/memory');
        if (res && (res.code === '200' || res.code === 200)) {
          this.memorySnapshot = res.data;
        } else {
          this.memoryLoadError = (res && res.msg) ? res.msg : '加载失败';
        }
      } catch (e) {
        console.error(e);
        this.memoryLoadError = '无法加载（请确认已登录）';
      } finally {
        this.memoryLoading = false;
      }
    },
    async clearMemoryRemote(scope) {
      if (scope === 'all') {
        try {
          await this.$confirm('确定清空全部持久记忆？不可恢复。', '确认', { type: 'warning' });
        } catch (e) {
          return;
        }
      }
      this.memoryActionBusy = true;
      try {
        const res = await request.post('/api/agent/memory/clear', { scope });
        if (res && (res.code === '200' || res.code === 200)) {
          this.memorySnapshot = res.data;
          this.messages.push({
            role: 'ai',
            content: scope === 'all' ? '已清空全部记忆。' : scope === 'preferences' ? '已清空偏好记录。' : '已清空对话摘要。'
          });
          this.scrollToBottom();
        } else {
          this.$message.warning((res && res.msg) || '清空失败');
        }
      } catch (e) {
        console.error(e);
        this.$message.error('清空失败');
      } finally {
        this.memoryActionBusy = false;
      }
    },
    // 简单的文本换行处理
    formatText(text) {
      return text.replace(/\n/g, '<br>');
    },
    // 滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        if (this.$refs.chatBody) {
          this.$refs.chatBody.scrollTop = this.$refs.chatBody.scrollHeight;
        }
      });
    },
    // 窗口大小变化时调整位置
    handleResize() {
      if (this.fabX !== null && this.fabY !== null) {
        const buttonSize = 60;
        const maxX = window.innerWidth - buttonSize;
        const maxY = window.innerHeight - buttonSize;
        
        // 如果当前位置超出边界，调整到边界内
        let newX = Math.max(0, Math.min(this.fabX, maxX));
        let newY = Math.max(0, Math.min(this.fabY, maxY));
        
        if (newX !== this.fabX || newY !== this.fabY) {
          this.fabX = newX;
          this.fabY = newY;
          this.saveFabPosition();
        }
      }
    },
    async sendMessage() {
      if (!this.inputMsg.trim()) return;

      const question = this.inputMsg;
      this.messages.push({ role: 'user', content: question });
      this.inputMsg = '';
      this.isLoading = true;
      this.scrollToBottom();

      try {
        // 调用Agent API生成计划
        const planRes = await request.post('/api/agent/plan', {
          question: question,
          history: this.buildPlanHistoryForPlan()
        });

        // request 拦截器已经处理了响应，直接使用 planRes（业务 code 与 HTTP 状态分离）
        if (planRes && (planRes.code === '500' || planRes.code === 500)) {
          this.messages.push({
            role: 'ai',
            content: (planRes.msg && String(planRes.msg).trim()) ? planRes.msg : '服务暂时不可用，请稍后再试。'
          });
        } else if (planRes && (planRes.code === '200' || planRes.code === 200) && planRes.data) {
          const plan = planRes.data;
          const filteredActions = this.filterCompetitionActions(plan.actions || []);
          
          // 显示建议摘要
          this.messages.push({ 
            role: 'ai', 
            content: plan.advice || '已生成执行计划。'
          });
          this.scrollToBottom();

          if ((plan.actions || []).length > 0 && filteredActions.length === 0) {
            this.messages.push({
              role: 'ai',
              content: '当前建议已整理为文字摘要；这里仅保留主线页面跳转和主线相关动作。'
            });
          }

          if (filteredActions.length > 0) {
            // 分离导航动作和需要确认的设备控制动作
            const navigateActions = filteredActions.filter(a => a.type === 'navigate');
            const deviceActions = filteredActions.filter(a => a.type !== 'navigate');
            
            // 导航动作直接执行，无需确认（低风险操作）
            if (navigateActions.length > 0) {
              const firstNav = navigateActions[0];
              this.messages.push({
                role: 'ai',
                content: `正在为您跳转到${firstNav.title || '目标页面'}...`
              });
              this.scrollToBottom();
              // 延迟500ms后自动跳转，让用户看到提示
              setTimeout(() => {
                this.navigateTo(firstNav.route);
              }, 500);
            }
            
            // 设备控制动作直接自动执行（删除二次确认）
            if (deviceActions.length > 0) {
              deviceActions.forEach(action => {
                this.executeDeviceAction(action);
              });
            }
          }
        } else {
          // 如果返回的数据格式不符合预期，显示更详细的错误信息
          console.warn('Agent API返回数据格式异常:', planRes);
          this.messages.push({ role: 'ai', content: '抱歉，我没有理解您的意图，请尝试其他表述。' });
        }

      } catch (error) {
        console.error('调用Agent API失败:', error);
        let errorMsg = `${this.assistantName}暂时不可用，请稍后再试。`;
        const resData = error.response && error.response.data;
        if (resData && typeof resData === 'object' && (resData.msg || resData.message)) {
          errorMsg = resData.msg || resData.message;
        } else if (error.response) {
          const st = error.response.status;
          errorMsg = st === 401 || st === 403 ? '登录已失效，请重新登录后再试。' : `服务器错误：${st}`;
        } else if (error.message) {
          errorMsg = `网络错误：${error.message}`;
        }
        this.messages.push({ role: 'ai', content: errorMsg });
      } finally {
        this.isLoading = false;
        this.scrollToBottom();
      }
    },
    async rememberLastUserLine() {
      const lastUser = [...this.messages].reverse().find((m) => m.role === 'user');
      if (!lastUser || !String(lastUser.content || '').trim()) {
        this.messages.push({ role: 'ai', content: '没有可记录的上一句用户发言。' });
        this.scrollToBottom();
        return;
      }
      try {
        const res = await request.post('/api/agent/memory/note', {
          note: String(lastUser.content).trim()
        });
        if (res && (res.code === '200' || res.code === 200)) {
          this.messages.push({
            role: 'ai',
            content: '已写入长期偏好，之后禾序会结合数据库里的记录来回答。'
          });
          if (this.showMemoryPanel) {
            this.loadMemorySnapshot();
          }
        } else {
          this.messages.push({
            role: 'ai',
            content: (res && res.msg) ? res.msg : '写入失败，请确认已登录且后端已执行建表 SQL。'
          });
        }
      } catch (e) {
        console.error(e);
        this.messages.push({
          role: 'ai',
          content: '写入失败：请确认已登录，且数据库已创建 agent_user_memory 表。'
        });
      }
      this.scrollToBottom();
    },
    filterCompetitionActions(actions) {
      return actions.filter(action => this.isAllowedCompetitionAction(action));
    },
    isAllowedCompetitionAction(action) {
      if (!action || !action.type) {
        return false;
      }
      if (action.type === 'navigate') {
        return MAIN_STAGE_ROUTES.has(action.route);
      }
      return MAIN_STAGE_ACTION_TYPES.has(action.type);
    },
    
    // 跳转到指定路由
    navigateTo(route) {
      if (!route || !MAIN_STAGE_ROUTES.has(route)) {
        this.messages.push({
          role: 'ai',
          content: '当前入口仅保留主线页面跳转，请从左侧导航进入其他功能。'
        });
        return;
      }
      if (this.$route.path === route) {
        this.messages.push({ role: 'ai', content: '您已经在该页面了。' });
        return;
      }
      this.$router.push(route).catch(() => {});
      // 跳转后保持聊天窗口打开，方便用户继续对话
    },
    
    // 执行设备控制动作（需要用户确认的不可逆操作）
    // ========== 语音识别功能 ==========
    initVoiceRecognition() {
      // 检查浏览器是否支持语音识别
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      
      if (!SpeechRecognition) {
        console.warn('当前浏览器不支持语音识别');
        this.voiceSupported = false;
        return;
      }
      
      this.voiceSupported = true;
      this.recognition = new SpeechRecognition();
      
      // 配置语音识别
      this.recognition.lang = 'zh-CN'; // 中文识别
      this.recognition.continuous = false; // 单次识别
      this.recognition.interimResults = true; // 显示临时结果
      this.recognition.maxAlternatives = 1;
      
      // 识别结果事件
      this.recognition.onresult = (event) => {
        let transcript = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          transcript += event.results[i][0].transcript;
        }
        
        // 更新输入框
        this.inputMsg = transcript;
        
        // 如果是最终结果
        if (event.results[event.results.length - 1].isFinal) {
        }
      };
      
      // 识别结束事件
      this.recognition.onend = () => {
        this.isRecording = false;
      };
      
      // 识别错误事件
      this.recognition.onerror = (event) => {
        console.error('语音识别错误:', event.error);
        this.isRecording = false;
        
        let errorMsg = '语音识别失败';
        switch (event.error) {
          case 'no-speech':
            errorMsg = '未检测到语音，请重试';
            break;
          case 'audio-capture':
            errorMsg = '无法访问麦克风，请检查权限';
            break;
          case 'not-allowed':
            errorMsg = '麦克风权限被拒绝，请在浏览器设置中允许';
            break;
          case 'network':
            errorMsg = '网络错误，请检查网络连接';
            break;
        }
        
        this.$message.warning(errorMsg);
      };
      
      // 开始识别事件
      this.recognition.onstart = () => {
        this.isRecording = true;
      };
      
    },
    
    toggleVoiceRecognition() {
      if (!this.voiceSupported) {
        this.$message.warning('当前浏览器不支持语音识别，请使用 Chrome 浏览器');
        return;
      }
      
      if (this.isRecording) {
        this.stopVoiceRecognition();
      } else {
        this.startVoiceRecognition();
      }
    },
    
    startVoiceRecognition() {
      if (!this.recognition) {
        this.initVoiceRecognition();
      }
      
      if (!this.recognition) {
        this.$message.warning('语音识别初始化失败');
        return;
      }
      
      try {
        this.recognition.start();
        this.$message.info('请开始说话...');
      } catch (e) {
        console.error('启动语音识别失败:', e);
        // 如果已经在录音，先停止再重新开始
        if (e.message.includes('already started')) {
          this.recognition.stop();
          setTimeout(() => {
            this.recognition.start();
          }, 100);
        }
      }
    },
    
    stopVoiceRecognition() {
      if (this.recognition && this.isRecording) {
        this.recognition.stop();
        this.isRecording = false;
      }
    },
    
    // 根据动作类型返回按钮样式类
    getActionButtonClass(action) {
      if (!action) return 'device-btn';
      switch (action.type) {
        case 'delete_farm':
          return 'device-btn danger-btn';
        case 'create_farm':
          return 'device-btn success-btn';
        default:
          return 'device-btn';
      }
    },
    
    // 根据动作类型返回按钮图标
    getActionButtonIcon(action) {
      if (!action) return 'el-icon-check';
      switch (action.type) {
        case 'delete_farm':
          return 'el-icon-delete';
        case 'create_farm':
          return 'el-icon-plus';
        case 'irrigation_on':
        case 'irrigation_off':
          return 'el-icon-water-cup';
        case 'led_on':
        case 'led_off':
          return 'el-icon-sunny';
        case 'drone_spray':
          return 'el-icon-position';
        default:
          return 'el-icon-check';
      }
    },
    
    // 根据动作类型返回按钮文字
    getActionButtonText(action) {
      if (!action) return '确认执行';
      switch (action.type) {
        case 'delete_farm':
          return '确认删除';
        case 'create_farm':
          return '确认创建';
        default:
          return '确认执行';
      }
    },
    
    async executeDeviceAction(action) {
      if (!action) return;
      if (!this.isAllowedCompetitionAction(action)) {
        this.messages.push({
          role: 'ai',
          content: '当前入口仅保留主线相关动作，其余操作请在对应页面处理。'
        });
        this.scrollToBottom();
        return;
      }
      
      this.messages.push({
        role: 'ai',
        content: `正在执行：${action.title || action.type}...`
      });
      this.scrollToBottom();
      
      try {
        const executeRes = await request.post('/api/agent/execute', {
          actions: [action]
        });
        
        if (executeRes && (executeRes.code === '200' || executeRes.code === 200) && executeRes.data) {
          executeRes.data.forEach(result => {
            this.messages.push({
              role: 'ai',
              content: result.message,
              status: result.status
            });
            
            // 同步通知其他组件更新状态
            if (result.status === 'success') {
              if (result.type === 'irrigation_on') {
                eventBus.$emit(EVENTS.IRRIGATION_ON);
              } else if (result.type === 'irrigation_off') {
                eventBus.$emit(EVENTS.IRRIGATION_OFF);
              } else if (result.type === 'led_on') {
                eventBus.$emit(EVENTS.LED_ON);
              } else if (result.type === 'led_off') {
                eventBus.$emit(EVENTS.LED_OFF);
              } else if (result.type === 'create_farm' || result.type === 'delete_farm') {
                // 农田创建/删除成功，刷新农田数据
                eventBus.$emit('FARM_DATA_UPDATED');
                this.$message.success(result.message);
              } else if (result.type === 'drone_spray') {
                // 飞防消杀成功
                eventBus.$emit('DRONE_SPRAY_ON');
                this.$message.success(result.message);
              }
            }
          });
        } else {
          this.messages.push({
            role: 'ai',
            content: '操作执行失败，请稍后重试。'
          });
        }
      } catch (error) {
        console.error('执行设备动作失败:', error);
        this.messages.push({
          role: 'ai',
          content: '操作执行异常：' + (error.message || '未知错误')
        });
      }
      
      this.scrollToBottom();
    }
  }
};
</script>

<style>
/* 这里保留你原来的全局样式 */
html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}

.el-main {
  width: 100%;
  height: 100%;
  padding: 0px !important;
  padding-left: 10px !important;
}

/* --- 👇👇👇 AI 聊天组件样式（全新升级版） 👇👇👇 --- */

/* 1. 容器定在右下角 */
.ai-chat-container {
  position: fixed !important;
  bottom: 30px;
  right: 30px;
  z-index: 99999 !important;
  font-family: 'PingFang SC', 'Segoe UI', Roboto, sans-serif;
  pointer-events: auto !important;
}

/* 2. 悬浮球按钮 - 科技感升级 */
.fab-btn {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #2af598 0%, #009efd 100%);
  border-radius: 50%;
  box-shadow: 0 8px 32px rgba(0, 158, 253, 0.4);
  display: flex !important;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  user-select: none;
  position: relative;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.2);
}
.fab-btn::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.4) 0%, transparent 60%);
  opacity: 0;
  transform: scale(0.5);
  transition: opacity 0.4s, transform 0.4s;
}
.fab-btn:hover { 
  transform: scale(1.1) translateY(-4px); 
  box-shadow: 0 12px 40px rgba(0, 158, 253, 0.5);
}
.fab-btn:hover::after {
  opacity: 1;
  transform: scale(1);
}
.fab-btn:active { 
  transform: scale(0.95);
}

/* 3. 聊天窗口 - 毛玻璃效果 */
.chat-window {
  position: absolute;
  bottom: 85px;
  right: 0;
  width: 380px;
  height: 600px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15), 0 0 1px rgba(0,0,0,0.1);
  transform-origin: bottom right;
  animation: popIn 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
}

/* 顶部栏 */
.chat-header {
  background: linear-gradient(135deg, #ffffff 0%, #f0f7ff 100%);
  padding: 18px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
}
.chat-header .title { 
  font-weight: 700; 
  font-size: 16px;
  color: #1a1a1a;
  display: flex; 
  align-items: center; 
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.memory-link-btn {
  border: none;
  background: transparent;
  color: #009efd;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s;
}
.memory-link-btn:hover:not(:disabled) {
  background: rgba(0, 158, 253, 0.1);
  color: #0078d4;
}
.memory-link-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.memory-panel {
  flex-shrink: 0;
  max-height: 220px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: #f0f7ff;
  font-size: 12px;
  color: #334;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.memory-panel-loading,
.memory-panel-error {
  padding: 12px 16px;
  text-align: center;
  color: #666;
}
.memory-panel-error {
  color: #c45656;
}
.memory-panel-inner {
  padding: 10px 14px 12px;
  overflow-y: auto;
  min-height: 0;
}
.memory-block {
  margin-bottom: 10px;
}
.memory-block-title {
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
  font-size: 11px;
  text-transform: none;
  letter-spacing: 0;
}
.memory-block-body {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 72px;
  overflow-y: auto;
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(0, 158, 253, 0.12);
  line-height: 1.45;
  color: #2c3e50;
}
.memory-panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}
.memory-mini-btn {
  border: 1px solid rgba(0, 158, 253, 0.35);
  background: #fff;
  color: #009efd;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.memory-mini-btn:hover:not(:disabled) {
  background: rgba(0, 158, 253, 0.08);
}
.memory-mini-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.memory-mini-btn.danger {
  border-color: rgba(196, 86, 86, 0.45);
  color: #c45656;
}
.memory-mini-btn.danger:hover:not(:disabled) {
  background: rgba(196, 86, 86, 0.08);
}

.status-dot { 
  width: 8px; 
  height: 8px; 
  background: #00d2ff; 
  border-radius: 50%; 
  position: relative;
}
.status-dot::after {
  content: '';
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border-radius: 50%;
  background: rgba(0, 210, 255, 0.2);
  animation: pulse-dot 2s infinite;
}
.close-btn { 
  cursor: pointer; 
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999; 
  transition: all 0.2s;
}
.close-btn:hover { 
  background: rgba(0,0,0,0.05);
  color: #333; 
}

/* 内容区 */
.chat-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f8fbff;
  scroll-behavior: smooth;
}
.chat-body::-webkit-scrollbar {
  width: 6px;
}
.chat-body::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.1);
  border-radius: 3px;
}

.message-row { 
  display: flex; 
  margin-bottom: 24px; 
  align-items: flex-start;
  animation: slideUp 0.3s ease-out;
}
.message-row.user { flex-direction: row-reverse; }

.avatar { 
  width: 40px; 
  height: 40px; 
  min-width: 40px; 
  background: #fff; 
  border-radius: 12px; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  margin: 0 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  border: 1px solid rgba(255,255,255,0.8);
}
.avatar-img { width: 28px; height: 28px; object-fit: contain; }

.bubble {
  max-width: 75%;
  padding: 14px 18px;
  border-radius: 18px;
  border-top-left-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  color: #2c3e50;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  position: relative;
  word-wrap: break-word;
}
.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.message-row.user .bubble {
  background: linear-gradient(135deg, #2af598 0%, #009efd 100%);
  color: #fff;
  border-radius: 18px;
  border-top-right-radius: 4px;
  box-shadow: 0 4px 15px rgba(0, 158, 253, 0.2);
}

/* 设备控制按钮美化 */
.device-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: #fff;
  color: #ff9800;
  border: 1px solid #ff9800;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s;
  box-shadow: 0 2px 6px rgba(255, 152, 0, 0.1);
}
.device-btn:hover {
  background: #ff9800;
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 152, 0, 0.3);
}

/* 创建农田按钮 - 绿色 */
.device-btn.success-btn {
  color: #52c41a;
  border-color: #52c41a;
}
.device-btn.success-btn:hover {
  background: #52c41a;
  color: #fff;
  box-shadow: 0 6px 16px rgba(82, 196, 26, 0.3);
}

/* 删除农田按钮 - 红色警告 */
.device-btn.danger-btn {
  color: #ff4d4f;
  border-color: #ff4d4f;
}
.device-btn.danger-btn:hover {
  background: #ff4d4f;
  color: #fff;
  box-shadow: 0 6px 16px rgba(255, 77, 79, 0.3);
}

/* 导航按钮美化 */
.nav-btn {
  margin-top: 12px;
  padding: 8px 16px;
  background: linear-gradient(90deg, #00c6ff, #0072ff);
  border: none;
  border-radius: 20px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(0, 114, 255, 0.3);
}
.nav-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 114, 255, 0.4);
}

/* 底部输入区 */
.chat-footer {
  padding: 12px 14px;
  background: #fff;
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid rgba(0,0,0,0.05);
  flex-shrink: 0;
}

.chat-footer input {
  flex: 1 1 0;
  min-width: 0;
  padding: 12px 16px;
  border-radius: 24px;
  border: 2px solid #f0f2f5;
  background: #f8f9fa;
  color: #333;
  outline: none;
  font-size: 14px;
  transition: all 0.3s;
}
.chat-footer input:focus {
  background: #fff;
  border-color: #009efd;
  box-shadow: 0 0 0 4px rgba(0, 158, 253, 0.1);
}

.chat-footer button {
  padding: 10px 24px;
  background: linear-gradient(135deg, #2af598 0%, #009efd 100%);
  border: none;
  border-radius: 24px;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 158, 253, 0.3);
  transition: all 0.3s;
}
.chat-footer button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 158, 253, 0.4);
}
.chat-footer button:disabled { 
  background: #e0e0e0; 
  color: #999;
  cursor: not-allowed; 
  box-shadow: none;
  transform: none;
}

.chat-footer .send-btn {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 10px 18px;
}

.chat-footer .voice-btn {
  padding: 0;
  flex-shrink: 0;
}
.voice-btn {
  width: 44px;
  height: 44px;
  min-width: 44px;
  border-radius: 50%;
  border: none;
  background: #f0f2f5;
  color: #666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  box-shadow: none;
}
.voice-btn:hover {
  background: #e6e8eb;
  color: #009efd;
  transform: scale(1.05);
}
.voice-btn.recording {
  background: linear-gradient(135deg, #ff416c, #ff4b2b);
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 75, 43, 0.4);
  animation: pulse-recording 1.5s infinite;
}

/* 动画定义 */
@keyframes popIn {
  from { opacity: 0; transform: scale(0.9) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
@keyframes slideUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes pulse-dot {
  0% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(2.5); opacity: 0; }
  100% { transform: scale(1); opacity: 0; }
}
@keyframes pulse-recording {
  0% { box-shadow: 0 0 0 0 rgba(255, 75, 43, 0.4); }
  70% { box-shadow: 0 0 0 10px rgba(255, 75, 43, 0); }
  100% { box-shadow: 0 0 0 0 rgba(255, 75, 43, 0); }
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s, transform 0.3s; }
.fade-enter, .fade-leave-to { opacity: 0; transform: translateY(20px); }

/* Loading 动画优化 */
.loading span {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #009efd;
  border-radius: 50%;
  margin: 0 2px;
  animation: bounce 1.4s infinite ease-in-out both;
}
.loading span:nth-child(1) { animation-delay: -0.32s; }
.loading span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>


