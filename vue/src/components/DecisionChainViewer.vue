<template>
  <div class="decision-chain-viewer">
    <div class="chain-header" v-if="showHeader">
      <h4><i class="el-icon-s-operation"></i> Agent 决策链</h4>
      <el-tag size="small" :type="triggerSourceTag.type">{{ triggerSourceTag.text }}</el-tag>
    </div>

    <div class="chain-timeline" v-if="steps.length > 0">
      <div
        v-for="(step, index) in steps"
        :key="index"
        class="timeline-item"
        :class="'timeline-' + step.stepType"
      >
        <div class="timeline-dot">
          <i :class="getStepIcon(step.stepType)"></i>
        </div>
        <div class="timeline-content">
          <div class="step-header">
            <span class="step-type">{{ getStepLabel(step.stepType) }}</span>
            <span class="step-round" v-if="step.roundNumber">轮次 {{ step.roundNumber }}</span>
            <span class="step-duration" v-if="step.durationMs">{{ step.durationMs }}ms</span>
          </div>
          <div class="step-body">
            <div v-if="step.stepType === 'tool_call'" class="tool-call-info">
              <span class="tool-name">{{ step.stepContent }}</span>
              <pre v-if="step.stepDetail" class="tool-args">{{ formatJson(step.stepDetail) }}</pre>
            </div>
            <div v-else-if="step.stepType === 'tool_result'" class="tool-result-info">
              <pre class="tool-result-content">{{ formatContent(step.stepContent) }}</pre>
            </div>
            <div v-else class="step-text">{{ step.stepContent }}</div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无决策链数据" :image-size="60"></el-empty>
  </div>
</template>

<script>
import request from '@/utils/request';

export default {
  name: 'DecisionChainViewer',
  props: {
    chainId: {
      type: String,
      default: ''
    },
    showHeader: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      steps: [],
      loading: false
    };
  },
  computed: {
    triggerSourceTag() {
      if (this.steps.length === 0) return { type: 'info', text: '未知' };
      const source = this.steps[0].triggerSource;
      const map = {
        user_chat: { type: 'success', text: '用户对话' },
        auto_patrol: { type: 'warning', text: '自动巡检' },
        sensor_event: { type: 'danger', text: '传感器事件' },
        scheduled: { type: 'info', text: '定时任务' }
      };
      return map[source] || { type: 'info', text: source };
    }
  },
  watch: {
    chainId: {
      immediate: true,
      handler(val) {
        if (val) {
          this.fetchChain(val);
        }
      }
    }
  },
  methods: {
    async fetchChain(chainId) {
      this.loading = true;
      try {
        const res = await request.get(`/api/agent/decision-chain/${chainId}`);
        if (res.code === '200') {
          this.steps = res.data || [];
        }
      } catch (e) {
        console.error('获取决策链失败:', e);
      } finally {
        this.loading = false;
      }
    },
    getStepIcon(type) {
      const icons = {
        thinking: 'el-icon-s-opportunity',
        tool_call: 'el-icon-s-tools',
        tool_result: 'el-icon-circle-check',
        final_answer: 'el-icon-s-flag'
      };
      return icons[type] || 'el-icon-info';
    },
    getStepLabel(type) {
      const labels = {
        thinking: '思考',
        tool_call: '调用工具',
        tool_result: '工具返回',
        final_answer: '最终回答'
      };
      return labels[type] || type;
    },
    formatJson(str) {
      try {
        return JSON.stringify(JSON.parse(str), null, 2);
      } catch {
        return str;
      }
    },
    formatContent(str) {
      if (!str) return '';
      try {
        const obj = JSON.parse(str);
        return JSON.stringify(obj, null, 2);
      } catch {
        return str.length > 500 ? str.substring(0, 500) + '...' : str;
      }
    }
  }
};
</script>

<style scoped>
.decision-chain-viewer {
  padding: 12px;
}

.chain-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.chain-header h4 {
  margin: 0;
  font-size: 15px;
  color: #303133;
}

.chain-timeline {
  position: relative;
  padding-left: 24px;
}

.chain-timeline::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e4e7ed;
}

.timeline-item {
  position: relative;
  margin-bottom: 16px;
  padding-left: 20px;
}

.timeline-dot {
  position: absolute;
  left: -24px;
  top: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
}

.timeline-thinking .timeline-dot { background: #909399; }
.timeline-tool_call .timeline-dot { background: #409eff; }
.timeline-tool_result .timeline-dot { background: #67c23a; }
.timeline-final_answer .timeline-dot { background: #e6a23c; }

.timeline-content {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px 14px;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.step-type {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.step-round, .step-duration {
  font-size: 11px;
  color: #909399;
  background: #ebeef5;
  padding: 1px 6px;
  border-radius: 4px;
}

.step-body {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.tool-name {
  color: #409eff;
  font-weight: 600;
  font-family: monospace;
}

.tool-args, .tool-result-content {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
  margin: 6px 0 0;
  max-height: 200px;
  overflow-y: auto;
}

.step-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
