<template>
  <div class="today-task-list">
    <div class="task-header">
      <h3>
        <i class="el-icon-s-order"></i>
        今日农场任务清单
      </h3>
      <el-badge :value="taskList.length" class="task-badge" v-if="taskList.length > 0">
        <el-button size="mini" @click="refreshTasks">刷新</el-button>
      </el-badge>
    </div>

    <div class="task-content" v-if="taskList.length > 0">
      <div 
        v-for="(task, index) in taskList" 
        :key="index"
        class="task-item"
        :class="getTaskLevelClass(task.alertLevel)"
      >
        <div class="task-icon">
          <i :class="getTaskIcon(task.alertType)"></i>
        </div>
        <div class="task-info">
          <div class="task-title">{{ task.farmlandName }}</div>
          <div class="task-message">{{ task.message }}</div>
          <div class="task-suggestion" v-if="task.suggestion">
            <i class="el-icon-light-rain"></i>
            {{ task.suggestion }}
          </div>
          <div class="task-time">{{ formatTime(task.createTime) }}</div>
        </div>
        <div class="task-actions">
          <el-button 
            size="mini" 
            type="success" 
            @click="processTask(task)"
            :loading="processingIds.includes(task.id)"
          >
            标记已处理
          </el-button>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无任务" :image-size="80"></el-empty>
  </div>
</template>

<script>
export default {
  name: 'TodayTaskList',
  data() {
    return {
      taskList: [],
      processingIds: [],
      refreshTimer: null
    }
  },
  mounted() {
    this.loadTasks();
    // 每30秒自动刷新
    this.refreshTimer = setInterval(() => {
      this.loadTasks();
    }, 30000);
  },
  beforeUnmount() {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  },
  methods: {
    async loadTasks() {
      try {
        const res = await this.request.get('/alert/today/tasks');
        if (res.code === '200') {
          this.taskList = res.data || [];
        }
      } catch (error) {
        console.error('加载任务列表失败:', error);
      }
    },
    async processTask(task) {
      this.processingIds.push(task.id);
      try {
        const res = await this.request.post(`/alert/${task.id}/process`);
        if (res.code === '200') {
          this.$message.success('任务已标记为已处理');
          this.loadTasks();
        } else {
          this.$message.error('处理失败');
        }
      } catch (error) {
        console.error('处理任务失败:', error);
        this.$message.error('处理失败');
      } finally {
        const index = this.processingIds.indexOf(task.id);
        if (index > -1) {
          this.processingIds.splice(index, 1);
        }
      }
    },
    refreshTasks() {
      this.loadTasks();
    },
    getTaskLevelClass(level) {
      const classMap = {
        'high': 'task-item-high',
        'medium': 'task-item-medium',
        'low': 'task-item-low'
      };
      return classMap[level] || 'task-item-low';
    },
    getTaskIcon(type) {
      const iconMap = {
        'temperature': 'el-icon-thermometer',
        'soil_humidity': 'el-icon-water-cup',
        'air_humidity': 'el-icon-cloudy',
        'ph': 'el-icon-data-analysis',
        'carbon': 'el-icon-wind-power',
        'light': 'el-icon-sunny'
      };
      return iconMap[type] || 'el-icon-warning';
    },
    formatTime(timeStr) {
      if (!timeStr) return '';
      const date = new Date(timeStr);
      const hours = String(date.getHours()).padStart(2, '0');
      const minutes = String(date.getMinutes()).padStart(2, '0');
      return `${hours}:${minutes}`;
    }
  }
}
</script>

<style scoped>
.today-task-list {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  padding: 15px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 2px solid #f0f0f0;
}

.task-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-badge {
  margin-right: 10px;
}

.task-content {
  max-height: 500px;
  overflow-y: auto;
}

.task-item {
  display: flex;
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 6px;
  border-left: 4px solid #909399;
  background: #f5f7fa;
  transition: all 0.3s;
}

.task-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transform: translateX(2px);
}

.task-item-high {
  border-left-color: #F56C6C;
  background: #FEF0F0;
}

.task-item-medium {
  border-left-color: #E6A23C;
  background: #FDF6EC;
}

.task-item-low {
  border-left-color: #409EFF;
  background: #ECF5FF;
}

.task-icon {
  font-size: 24px;
  color: #409EFF;
  margin-right: 12px;
  display: flex;
  align-items: center;
}

.task-item-high .task-icon {
  color: #F56C6C;
}

.task-item-medium .task-icon {
  color: #E6A23C;
}

.task-info {
  flex: 1;
}

.task-title {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  margin-bottom: 5px;
}

.task-message {
  font-size: 13px;
  color: #606266;
  margin-bottom: 5px;
}

.task-suggestion {
  font-size: 12px;
  color: #67C23A;
  margin-top: 5px;
  padding: 5px 8px;
  background: rgba(103, 194, 58, 0.1);
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.task-time {
  font-size: 11px;
  color: #909399;
  margin-top: 5px;
}

.task-actions {
  display: flex;
  align-items: center;
  margin-left: 10px;
}
</style>

