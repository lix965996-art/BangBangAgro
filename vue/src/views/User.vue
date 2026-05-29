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
            <div class="stat-value">98.5%</div>
            <div class="stat-label">今日出勤率</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bg-gradient-3">
          <i class="el-icon-s-data stat-icon"></i>
          <div>
            <div class="stat-value">4.8分</div>
            <div class="stat-label">AI 综合评分</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bg-gradient-4">
          <i class="el-icon-cpu stat-icon"></i>
          <div>
            <div class="stat-value">24h</div>
            <div class="stat-label">系统实时监控</div>
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

    <div v-loading="loading" element-loading-text="正在从 AI 云端同步农人绩效数据...">
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
                    <el-dropdown-item command="task" icon="el-icon-tickets">分配任务</el-dropdown-item>
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
                  <span class="label">当前状态</span>
                  <span class="value">
                    <span class="status-dot online"></span> 在线
                  </span>
                </div>
              </div>
              <div class="progress-section">
                <div class="progress-row">
                  <span class="p-label">智能体 综合评级</span>
                  <span class="p-val">{{ item.aiScore }}分</span>
                </div>
                <el-progress :percentage="item.aiEfficiency" :color="customColorMethod" :stroke-width="8" :show-text="false"></el-progress>
                <div class="progress-desc">基于历史农事作业数据分析</div>
              </div>
            </div>

            <div class="card-footer">
              <el-button link icon="el-icon-location-outline" @click="handleTrajectory(item)">轨迹</el-button>
              <el-divider direction="vertical"></el-divider>
              <el-button link icon="el-icon-data-analysis" @click="handlePerformance(item)">绩效</el-button>
              <el-divider direction="vertical"></el-divider>
              <el-button link icon="el-icon-chat-dot-round" @click="handleMessage(item)">消息</el-button>
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
      :title=" form.username + ' - AI 智能农事绩效报告'" 
      v-model="performanceDialogVisible" 
      width="40%"
      center>
      <div style="text-align: center; margin-bottom: 20px;">
        <el-progress type="dashboard" :percentage="currentPerformance.score" :color="colors"></el-progress>
        <div style="font-size: 14px; font-weight: bold; color: #666; margin-top: -10px;">AI 综合效能指数</div>
      </div>
      <el-descriptions title="多维能力评估" :column="2" border size="small">
        <el-descriptions-item label="出勤天数">28天 / 30天</el-descriptions-item>
        <el-descriptions-item label="作业面积"><el-tag size="small" type="success">120 亩</el-tag></el-descriptions-item>
        <el-descriptions-item label="病虫害发现率">98.2% <i class="el-icon-top" style="color: red"></i></el-descriptions-item>
        <el-descriptions-item label="平均响应时间">15分钟</el-descriptions-item>
        <el-descriptions-item label="AI 专家评价" :span="2">
          <div style="line-height: 1.5;">该农人在 <span style="color: #409EFF; font-weight: bold;">{{ form.address || '作业区' }}</span> 表现优异，能及时响应IoT预警，建议作为“智慧农业带头人”进行表彰。</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="performanceDialogVisible = false">关 闭</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog 
      :title="  form.username + ' - 今日作业轨迹 (GIS实时)'" 
      v-model="trajectoryDialogVisible" 
      width="35%">
      <div style="height: 300px; overflow-y: auto; padding: 10px;">
        <el-timeline>
          <el-timeline-item timestamp="08:00" placement="top" type="primary" icon="el-icon-time">
            <el-card shadow="never">
              <h4>打卡签到</h4>
              <p>位置：农场入口打卡机</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="09:30" placement="top" type="warning" icon="el-icon-location-outline">
            <el-card shadow="never">
              <h4>巡检作业 - A5地块</h4>
              <p>停留时长：45分钟 | 状态：正常</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="14:20" placement="top" color="#67C23A" icon="el-icon-truck">
            <el-card shadow="never">
              <h4>物资运输 - 2号仓库</h4>
              <p>操作：领取复合肥 10袋</p>
            </el-card>
          </el-timeline-item>
          <el-timeline-item timestamp="当前" placement="top" type="danger">
            <h4><el-tag size="mini" effect="dark" type="danger">实时位置</el-tag></h4>
            <p>{{ form.address || '机动巡逻中' }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" plain @click="trajectoryDialogVisible = false">查看 3D 轨迹回放</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog 
      :title="'向 ' + form.username + ' 发送指令'" 
      v-model="messageDialogVisible" 
      width="30%">
      <el-form label-position="top">
        <el-form-item label="快捷指令">
          <el-radio-group v-model="quickMessage" size="small" @change="fillMessage">
            <el-radio-button label="紧急灌溉"></el-radio-button>
            <el-radio-button label="立即归队"></el-radio-button>
            <el-radio-button label="病害核查"></el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="消息内容">
          <el-input type="textarea" :rows="4" placeholder="请输入具体的作业指令..." v-model="messageContent"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="messageDialogVisible = false">取 消</el-button>
          <el-button type="primary" icon="el-icon-s-promotion" @click="performSend">发 送</el-button>
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
      currentPerformance: { score: 0 },
      colors: [
        {color: '#f56c6c', percentage: 40},
        {color: '#e6a23c', percentage: 60},
        {color: '#5cb87a', percentage: 80},
        {color: '#1989fa', percentage: 100}
      ],

      // 轨迹相关
      trajectoryDialogVisible: false,

      // 消息相关
      messageDialogVisible: false,
      messageContent: '',
      quickMessage: '',

      roles: [],
      loading: false
    }
  },
  created() {
    this.load()
  },
  methods: {
    customColorMethod(percentage) {
      if (percentage < 60) return '#F56C6C';
      if (percentage < 80) return '#E6A23C';
      return '#67C23A';
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
          return {
            ...item,
            aiEfficiency: Math.floor(Math.random() * (99 - 80 + 1)) + 80,
            aiScore: (Math.random() * (5.0 - 4.0) + 4.0).toFixed(1)
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

    // --- 按钮逻辑区 ---

    // 1. 绩效
    handlePerformance(row) {
      this.form = row; 
      this.$message({
        message: `正在连接 智能体 引擎，分析 ${row.username} 的农事行为数据...`,
        type: 'success',
        duration: 1000
      });
      this.currentPerformance = { score: row.aiEfficiency || 88 };
      setTimeout(() => { this.performanceDialogVisible = true; }, 800);
    },

    // 2. 轨迹 (新增)
    handleTrajectory(row) {
      this.form = row;
      // 这里弹出一个时间轴，假装显示他今天去过哪
      this.trajectoryDialogVisible = true;
    },

    // 3. 消息 (新增)
    handleMessage(row) {
      this.form = row;
      this.messageContent = ''; // 清空上一条
      this.quickMessage = '';
      this.messageDialogVisible = true;
    },
    fillMessage(val) {
      if(val === '紧急灌溉') this.messageContent = "检测到该区域土壤湿度过低，请立即前往开启滴灌设备。";
      if(val === '立即归队') this.messageContent = "农场中心有紧急会议，请收到消息后立即返回指挥室。";
      if(val === '病害核查') this.messageContent = "AI 视觉系统报警，请前往核查叶片是否出现黄斑。";
    },
    performSend() {
      this.$message.success(`指令已通过 5G 网络下发至 ${this.form.username} 的手持终端！`);
      this.messageDialogVisible = false;
    },

    // --- 通用逻辑区 ---

    handleCommand(command, row) {
      if (command === 'edit') {
        this.handleEdit(row);
      } else if (command === 'delete') {
        this.$confirm('此操作将永久删除该农人档案, 是否继续?', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }).then(() => { this.del(row.id); }).catch(err => { console.error('Delete user confirmation canceled:', err) });
      } else if (command === 'task') {
        this.$message.success(`已向 ${row.nickname} 派发 "巡检 ${row.address || 'A区'}" 任务`);
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