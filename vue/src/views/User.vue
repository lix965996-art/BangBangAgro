<template>
  <div class="dashboard-container">
    
    <el-row :gutter="20" class="mb-4">
      <el-col :span="6">
        <div class="stat-card bg-gradient-1">
          <i class="el-icon-user-solid stat-icon"></i>
          <div>
            <div class="stat-value">{{ total }}</div>
            <div class="stat-label">注册农人数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bg-gradient-2">
          <i class="el-icon-time stat-icon"></i>
          <div>
            <div class="stat-value">{{ attendanceRate }}</div>
            <div class="stat-label">今日出勤率</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bg-gradient-3">
          <i class="el-icon-s-data stat-icon"></i>
          <div>
            <div class="stat-value">{{ healthScore }}</div>
            <div class="stat-label">农田健康指数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bg-gradient-4">
          <i class="el-icon-cpu stat-icon"></i>
          <div>
            <div class="stat-value">{{ runDays }}</div>
            <div class="stat-label">系统运行天数</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="toolbar-container">
      <div class="left-tools">
        <el-input size="medium" style="width: 200px" placeholder="搜索姓名/昵称..." prefix-icon="el-icon-search" v-model="username" clearable @clear="load"></el-input>
        <el-button size="medium" type="primary" icon="el-icon-search" @click="load" class="ml-2">查询</el-button>
        <el-button size="medium" icon="el-icon-refresh" @click="reset" circle></el-button>
      </div>
      <div class="right-tools">
        <el-button size="medium" type="success" icon="el-icon-plus" @click="handleAdd">新增农人</el-button>
        <el-upload :action="apiConfig.userImport" :show-file-list="false" accept="xlsx" :on-success="handleExcelImportSuccess" style="display: inline-block; margin: 0 10px;">
          <el-button size="medium" type="primary" plain icon="el-icon-upload2">导入</el-button>
        </el-upload>
        <el-button size="medium" type="warning" plain icon="el-icon-download" @click="exp">导出</el-button>
      </div>
    </div>

    <div v-loading="loading" element-loading-text="正在加载农人档案...">
      <el-row :gutter="20">
        <el-col :span="6" v-for="item in tableData" :key="item.id" style="margin-bottom: 20px;">
          <el-card shadow="hover" class="farmer-card" :class="{ 'card-banned': item.status === 1 }" :body-style="{ padding: '0px' }">
            <!-- 封禁遮罩 -->
            <div v-if="item.status === 1" class="ban-overlay">
              <i class="el-icon-lock"></i>
              <span>账号已封禁</span>
            </div>

            <div class="card-header">
              <div class="header-content">
                <el-avatar :size="65" :src="item.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="avatar-hover"></el-avatar>
                <div class="user-meta">
                  <div class="name-row">
                    <span class="username">{{ item.username }}</span>
                    <el-tag size="mini" effect="dark" :type="getRoleType(item.role)" style="margin-left: 5px; border-radius: 10px;">
                      {{ getRoleName(item.role) }}
                    </el-tag>
                  </div>
                  <div class="contact-row">
                    <i class="el-icon-phone-outline"></i> {{ item.phone || '暂无电话' }}
                  </div>
                </div>
              </div>
              <el-dropdown trigger="click" @command="handleCommand($event, item)">
                <span class="el-dropdown-link options-btn">
                  <i class="el-icon-more"></i>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" icon="el-icon-edit">编辑档案</el-dropdown-item>
                    <el-dropdown-item v-if="!item.status" command="ban" icon="el-icon-lock" style="color: #E6A23C">封禁账号</el-dropdown-item>
                    <el-dropdown-item v-else command="unban" icon="el-icon-unlock" style="color: #67C23A">解除封禁</el-dropdown-item>
                    <el-dropdown-item command="delete" icon="el-icon-delete" style="color: #F56C6C">离职归档</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <div class="card-body">
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">负责区域</span>
                  <span class="value">{{ item.address ? (item.address.length > 5 ? item.address.substring(0,5)+'...' : item.address) : '机动组' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">最后登录</span>
                  <span class="value">{{ item.lastLoginTime ? fmtLoginTime(item.lastLoginTime) : '从未登录' }}</span>
                </div>
              </div>
              <div class="login-meta" v-if="item.lastLoginIp">
                <i class="el-icon-position"></i>
                <span class="ip">{{ item.lastLoginIp }}</span>
                <span v-if="item.lastLoginRegion" class="region"> · {{ item.lastLoginRegion }}</span>
              </div>
              <div class="progress-section">
                <div class="progress-row">
                  <span class="p-label">智能体 综合评级</span>
                  <span class="p-val">
                    <el-tag v-if="item.hasScore" size="mini" :type="gradeTagType(item.scoreGrade)" style="margin-right:4px">{{ item.scoreGrade }}级</el-tag>
                    {{ item.hasScore ? item.aiScore + '分' : '数据不足' }}
                  </span>
                </div>
                <el-progress v-if="item.hasScore" :percentage="item.aiEfficiency" :color="customColorMethod" :stroke-width="8" :show-text="false"></el-progress>
                <div class="progress-desc">{{ item.hasScore ? '近 7 天滚动评分' : '本周暂无作业数据' }}</div>
              </div>
            </div>

            <div class="card-footer">
              <el-button link icon="el-icon-data-analysis" @click="handlePerformance(item)">绩效</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div style="padding: 20px 0; text-align: center;">
      <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="pageNum"
          :page-sizes="[4, 8, 12, 16]" 
          :page-size="pageSize"
          layout="total, prev, pager, next, jumper"
          :total="total">
      </el-pagination>
    </div>

    <el-dialog title="📝 编辑农人档案" v-model="dialogFormVisible" width="30%" :close-on-click-modal="false">
      <el-form label-width="80px" size="small" :model="form">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="角色/工种">
          <el-select clearable v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="item in roles" :key="item.name" :label="item.name" :value="item.flag"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="负责区域">
          <el-input v-model="form.address" placeholder="例如：A5号智能大棚"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogFormVisible = false">取 消</el-button>
          <el-button type="primary" @click="save">保存档案</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      :title=" form.username + ' - 智能体 综合绩效报告'"
      v-model="performanceDialogVisible"
      width="42%"
      center>
      <div v-if="currentPerformance.loading" v-loading="true" style="height: 220px"></div>
      <template v-else>
        <div v-if="currentPerformance.dataThin" style="text-align:center; margin-bottom:12px;">
          <el-tag type="info" effect="plain" size="small">数据不足 · 本周作业事件过少, 评级仅供参考</el-tag>
        </div>
        <div style="text-align: center; margin-bottom: 20px;">
          <el-progress type="dashboard" :percentage="currentPerformance.score" :color="colors"></el-progress>
          <div style="font-size: 14px; font-weight: bold; color: #666; margin-top: -10px;">
            近 7 天综合评分
            <el-tag v-if="currentPerformance.grade" size="mini" :type="gradeTagType(currentPerformance.grade)" style="margin-left:6px">{{ currentPerformance.grade }}级</el-tag>
          </div>
        </div>
        <el-descriptions title="五维能力评估 (近 7 天滚动)" :column="2" border size="small">
          <el-descriptions-item label="预警响应">{{ fmtSub(currentPerformance.subs.alert) }}</el-descriptions-item>
          <el-descriptions-item label="智能体作业">{{ fmtSub(currentPerformance.subs.ai) }}</el-descriptions-item>
          <el-descriptions-item label="审批把关">{{ fmtSub(currentPerformance.subs.approval) }}</el-descriptions-item>
          <el-descriptions-item label="出勤活跃">{{ fmtSub(currentPerformance.subs.attendance) }}</el-descriptions-item>
          <el-descriptions-item label="知识沉淀">{{ fmtSub(currentPerformance.subs.knowledge) }}</el-descriptions-item>
          <el-descriptions-item label="AI 综合评语" :span="2">
            <div style="line-height: 1.5;">{{ currentPerformance.commentary || '暂无评语' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="performanceDialogVisible = false">关 闭</el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script>
import apiConfig from '@/config/api.config';

export default {
  name: "User",
  data() {
    return {
      apiConfig, // 引入API配置
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 8,
      username: "",
      email: "",
      address: "",
      form: {},
      dialogFormVisible: false,
      
      // 绩效相关
      performanceDialogVisible: false,
      currentPerformance: { score: 0, subs: {}, commentary: '', dataThin: false, available: false, loading: false, grade: '' },
      colors: [
        {color: '#f56c6c', percentage: 40},
        {color: '#e6a23c', percentage: 60},
        {color: '#5cb87a', percentage: 80},
        {color: '#1989fa', percentage: 100}
      ],

      roles: [],
      loading: false,

      // 统计卡片数据
      attendanceRate: '--',
      healthScore: '--',
      runDays: '--'
    }
  },
  created() {
    this.load()
    this.loadStats()
  },
  methods: {
    customColorMethod(percentage) {
      if (percentage < 60) return '#F56C6C';
      if (percentage < 80) return '#E6A23C';
      return '#67C23A';
    },
    fmtSub(v) {
      if (v === null || v === undefined || v === '') return '本周无数据';
      const n = Number(v);
      if (isNaN(n)) return '本周无数据';
      return n.toFixed(0) + '分';
    },
    gradeTagType(g) {
      if (g === 'S') return 'danger';
      if (g === 'A') return 'success';
      if (g === 'B') return 'primary';
      if (g === 'C') return 'warning';
      return 'info';
    },
    
    load() {
      this.loading = true;
      this.request.get("/user/page", {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          username: this.username,
          email: this.email,
          address: this.address,
        }
      }).then(res => {
        if (!res || !res.data || !Array.isArray(res.data.records)) {
          this.tableData = []
          this.total = 0
          this.loading = false
          return
        }
        const records = res.data.records.map(item => {
          const total = Number(item.scoreTotal);
          const hasScore = item.scoreTotal != null && !isNaN(total);
          return {
            ...item,
            scoreGrade: item.scoreGrade || '',
            aiEfficiency: hasScore ? Math.round(total) : 0,
            aiScore: hasScore ? (4.0 + total / 100).toFixed(1) : '—',
            hasScore: hasScore
          }
        });
        this.tableData = records
        this.total = res.data.total
        setTimeout(() => { this.loading = false }, 300)
      }).catch(() => {
        this.loading = false
      })

      this.request.get("/role").then(res => {
        this.roles = res.data
      })
    },

    loadStats() {
      // 出勤率
      this.request.get("/user/stats").then(res => {
        if (res && res.data) {
          this.attendanceRate = res.data.attendanceRate + '%'
        }
      }).catch(() => {})

      // 农田健康指数
      this.request.get("/health-index/calculate/all").then(res => {
        if (res && res.data) {
          const values = Object.values(res.data)
          if (values.length > 0) {
            const avg = values.reduce((sum, v) => sum + (v.healthIndex || 0), 0) / values.length
            this.healthScore = Math.round(avg * 10) / 10 + '分'
          } else {
            this.healthScore = '暂无'
          }
        }
      }).catch(() => {})

      // 系统运行天数
      this.request.get("/api/dashboard/achievements").then(res => {
        if (res && res.data) {
          this.runDays = (res.data.runDays || 0) + '天'
        }
      }).catch(() => {})
    },

    // --- 按钮逻辑区 ---

    // 1. 绩效
    handlePerformance(row) {
      this.form = row;
      this.currentPerformance = { score: 0, subs: {}, commentary: '', dataThin: false, available: false, loading: true, grade: '' };
      this.performanceDialogVisible = true;
      this.request.get("/user/score", { params: { userId: row.id } }).then(res => {
        const d = (res && res.data) || {};
        this.currentPerformance = {
          score: d.available ? Math.round(Number(d.total) || 0) : 0,
          subs: d.subs || {},
          commentary: d.commentary || '',
          dataThin: !!d.dataThin,
          available: !!d.available,
          loading: false,
          grade: d.grade || ''
        };
      }).catch(() => {
        this.currentPerformance.loading = false;
      });
    },

    // 最后登录时间格式化：今天显示"今天 HH:mm"，否则"MM-DD HH:mm"
    fmtLoginTime(t) {
      if (!t) return '从未登录';
      const d = new Date(t);
      if (isNaN(d.getTime())) return String(t);
      const pad = n => String(n).padStart(2, '0');
      const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
      const now = new Date();
      const sameDay = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
      return sameDay ? `今天 ${hm}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`;
    },


    // --- 通用逻辑区 ---

    handleCommand(command, row) {
      if (command === 'edit') {
        this.handleEdit(row);
      } else if (command === 'delete') {
        this.$confirm('此操作将永久删除该农人档案, 是否继续?', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }).then(() => { this.del(row.id); }).catch(err => { console.error('Delete user confirmation canceled:', err) });
      } else if (command === 'ban') {
        this.$confirm(`确定要封禁用户 "${row.username}" 的账号吗？封禁后该用户将无法登录。`, '封禁账号', {
          confirmButtonText: '确定封禁',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.request.post(`/user/${row.id}/ban`).then(res => {
            if (res.code === '200') {
              this.$message.success(`已封禁用户 ${row.username}`);
              this.load();
            } else {
              this.$message.error(res.msg || '操作失败');
            }
          });
        }).catch(() => {});
      } else if (command === 'unban') {
        this.$confirm(`确定要解除对 "${row.username}" 的封禁吗？`, '解除封禁', {
          confirmButtonText: '确定解封',
          cancelButtonText: '取消',
          type: 'info'
        }).then(() => {
          this.request.post(`/user/${row.id}/unban`).then(res => {
            if (res.code === '200') {
              this.$message.success(`已解除 ${row.username} 的封禁`);
              this.load();
            } else {
              this.$message.error(res.msg || '操作失败');
            }
          });
        }).catch(() => {});
      }
    },
    getRoleName(roleFlag) {
      const role = this.roles.find(v => v.flag === roleFlag);
      return role ? role.name : '普通用户';
    },
    getRoleType(roleFlag) {
      if (roleFlag === 'ADMIN' || roleFlag === 'admin') return 'danger';
      if (roleFlag === 'EXPERT') return 'warning';
      return 'success';
    },
    save() {
      this.request.post("/user", this.form).then(res => {
        if (res.code === '200') {
          this.$message.success("档案保存成功")
          this.dialogFormVisible = false
          this.load()
        } else {
          this.$message.error("保存失败")
        }
      })
    },
    handleAdd() { this.dialogFormVisible = true; this.form = {} },
    handleEdit(row) { this.form = JSON.parse(JSON.stringify(row)); this.dialogFormVisible = true },
    del(id) {
      this.request.delete("/user/" + id).then(res => {
        if (res.code === '200') { this.$message.success("删除成功"); this.load() } 
        else { this.$message.error("删除失败") }
      })
    },
    reset() { this.username = ""; this.email = ""; this.address = ""; this.load() },
    handleSizeChange(pageSize) { this.pageSize = pageSize; this.load() },
    handleCurrentChange(pageNum) { this.pageNum = pageNum; this.load() },
    exp() { window.open(this.apiConfig.userExport) },
    handleExcelImportSuccess() { this.$message.success("数据导入成功"); this.load() }
  }
}
</script>

<style scoped>
/* 全局样式 */
.dashboard-container { padding: 10px; background-color: #f0f2f5; min-height: calc(100vh - 80px); }

/* 封禁卡片 */
.card-banned { opacity: 0.75; border: 1.5px solid #f56c6c !important; }
.ban-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(245, 108, 108, 0.12);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; z-index: 10; pointer-events: none; border-radius: 8px;
  font-size: 14px; font-weight: 700; color: #f56c6c;
}
.ban-overlay i { font-size: 28px; }
.farmer-card { position: relative; overflow: hidden; }

/* 统计卡片 */
.stat-card { display: flex; align-items: center; padding: 20px; border-radius: 12px; color: white; box-shadow: 0 4px 10px rgba(0,0,0,0.1); cursor: default; }
.stat-card:hover { transform: none; }
.stat-icon { font-size: 36px; margin-right: 15px; opacity: 0.8; }
.stat-value { font-size: 24px; font-weight: bold; }
.stat-label { font-size: 12px; opacity: 0.9; }
.bg-gradient-1 { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.bg-gradient-2 { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); }
.bg-gradient-3 { background: linear-gradient(135deg, #fc5c7d 0%, #6a82fb 100%); }
.bg-gradient-4 { background: linear-gradient(135deg, #ff9966 0%, #ff5e62 100%); }

/* 工具栏 */
.toolbar-container { display: flex; justify-content: space-between; margin: 20px 0; background: #fff; padding: 15px; border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05); }
.ml-2 { margin-left: 10px; }

/* 卡片样式 */
.farmer-card { border: none; border-radius: 12px; overflow: hidden; }
.farmer-card:hover { transform: none; }
.card-header { background: linear-gradient(120deg, #f0f9eb 0%, #c2e9fb 100%); padding: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.header-content { display: flex; align-items: center; }
.avatar-hover { border: 3px solid #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.1); transition: transform 0.3s; }
.farmer-card:hover .avatar-hover { transform: scale(1.1) rotate(5deg); }
.user-meta { margin-left: 15px; }
.name-row { display: flex; align-items: center; margin-bottom: 5px; }
.username { font-size: 16px; font-weight: bold; color: #333; }
.contact-row { font-size: 12px; color: #666; }
.options-btn { cursor: pointer; color: #666; font-size: 18px; }

.card-body { padding: 20px; }
.info-grid { display: flex; justify-content: space-between; margin-bottom: 15px; }
.info-item { display: flex; flex-direction: column; }
.info-item .label { font-size: 12px; color: #999; margin-bottom: 4px; }
.info-item .value { font-size: 14px; font-weight: 500; color: #333; }
.login-meta { margin-top: 8px; font-size: 12px; color: #909399; display: flex; align-items: center; gap: 4px; }
.login-meta .ip { font-family: monospace; color: #606266; }
.login-meta .region { color: #909399; }
.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
.status-dot.online { background-color: #67C23A; }

.progress-section { background: #f8f9fa; padding: 10px; border-radius: 6px; }
.progress-row { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 5px; }
.p-val { font-weight: bold; color: #409EFF; }
.progress-desc { font-size: 10px; color: #ccc; margin-top: 5px; text-align: right; }

.card-footer { border-top: 1px solid #ebeef5; padding: 10px 0; display: flex; justify-content: space-around; background-color: #fdfdfd; }
.card-footer .el-button { padding: 0; color: #606266; }
.card-footer .el-button:hover { color: #409EFF; }
</style>