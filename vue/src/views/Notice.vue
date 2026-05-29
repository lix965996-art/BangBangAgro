<template>
  <div class="notice-container">
    
    <div class="ai-dashboard">
      <div class="dashboard-left">
        <div class="big-title">农情预警指挥中心</div>
        <div class="sub-text">通义千问 (Qwen) 语义分析引擎 · 实时监控中</div>
        <div class="pulse-line"></div>
      </div>
      <div class="dashboard-right">
        <div class="metric-box">
          <div class="metric-label">高频词云</div>
          <div class="tags-cloud">
            <el-tag size="mini" type="danger" effect="dark">炭疽病 98%</el-tag>
            <el-tag size="mini" type="warning" effect="dark">低温预警</el-tag>
            <el-tag size="mini" type="success" effect="dark">有机肥</el-tag>
            <el-tag size="mini" color="#626aef" effect="dark" style="color: white">无人机</el-tag>
          </div>
        </div>
        <div class="metric-box">
          <div class="metric-label">知识库容量</div>
          <div class="digital-number">1,204 <span style="font-size: 12px; color: #aaa">条</span></div>
        </div>
      </div>
    </div>

    <el-row :gutter="20" style="margin-top: 20px;">
      
      <el-col :span="16">
        <el-card class="glass-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span><i class="el-icon-timer"></i> 农事作业与预警日志</span>
              <el-button style="float: right; padding: 3px 0; color: #409EFF" link icon="el-icon-plus" @click="handleAdd">发布新日志</el-button>
            </div>
          </template>

          <div class="timeline-wrapper">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in tableData"
                :key="item.id"
                :timestamp="item.time"
                placement="top"
                :color="index === 0 ? '#ef4444' : '#38bdf8'" 
                :size="index === 0 ? 'large' : 'normal'"
                :icon="index === 0 ? 'el-icon-message-solid' : 'el-icon-collection-tag'">
                
                <el-card shadow="hover" :class="['item-card', index === 0 ? 'latest-card' : '']">
                  <div class="new-badge" v-if="index === 0">LATEST</div>

                  <div class="card-inner">
                    <div class="item-header">
                      <span class="item-title">{{ item.name }}</span>
                      <div class="tags-row">
                        <el-tag size="mini" effect="dark" :type="index % 2 === 0 ? 'danger' : 'primary'">{{ index % 2 === 0 ? '紧急' : '常规' }}</el-tag>
                        <el-tag size="mini" type="info" effect="plain" style="margin-left: 5px; border-color: #38bdf8; color: #38bdf8; background: #f0f9ff">Qwen 已归档</el-tag>
                      </div>
                    </div>

                    <div class="item-body">
                      <div class="text-content">
                         <span :class="{'typing-effect': index === 0}">{{ item.content }}</span>
                      </div>
                      
                      <div class="img-wrapper" v-if="item.img">
                        <el-image 
                          class="log-image"
                          style="width: 100px; height: 75px; border-radius: 6px;"
                          :src="item.img" 
                          :preview-src-list="item.img ? [item.img] : []"
                          fit="cover">
                          <template #error>
                            <div class="image-slot" style="display: flex; justify-content: center; align-items: center; width: 100%; height: 100%; background: #f1f5f9; color: #94a3b8;">
                              <i class="el-icon-picture-outline" style="font-size: 24px;"></i>
                            </div>
                          </template>
                        </el-image>
                      </div>
                    </div>

                    <div class="item-footer">
                      <span class="author-info"><i class="el-icon-user"></i> {{ item.user || 'AI 助手' }}</span>
                      <div class="actions">
                        <el-button link size="mini" icon="el-icon-magic-stick" style="color: #6366f1" @click="mockAnalyze">Qwen 深度分析</el-button>
                        <el-button link size="mini" icon="el-icon-edit" style="color: #64748b" @click="handleEdit(item)">编辑</el-button>
                        <el-popconfirm title="删除这条记录？" @confirm="del(item.id)">
                          <template #reference>
                            <el-button link size="mini" icon="el-icon-delete" style="color: #ef4444; margin-left: 10px"></el-button>
                          </template>
                        </el-popconfirm>
                      </div>
                    </div>
                  </div>
                </el-card>

              </el-timeline-item>
            </el-timeline>
            
            <!-- 分页控件 -->
            <div style="padding: 20px 0; text-align: center;">
              <el-pagination
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
                :current-page="pageNum"
                :page-sizes="[5, 10, 20]"
                :page-size="pageSize"
                layout="total, sizes, prev, pager, next, jumper"
                :total="total">
              </el-pagination>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <div class="custom-card mb-20" style="background: white; border-radius: 12px; padding: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.03); margin-bottom: 20px">
          <div class="panel-header" style="color: #334155; font-weight: 600; margin-bottom: 15px;">🔍 智库检索</div>
          <el-input placeholder="输入关键词..." v-model="name" class="search-input custom-input" clearable @clear="load">
            <template #append>
              <el-button icon="el-icon-search" style="background-color: #f1f5f9; color: #475569" @click="load"></el-button>
            </template>
          </el-input>
        </div>

        <div class="custom-card ai-suggest-card mb-20" style="background: linear-gradient(145deg, #1e293b 0%, #334155 100%); border-radius: 12px; padding: 20px; box-shadow: 0 8px 20px rgba(0,0,0,0.15); margin-bottom: 20px">
          <div class="panel-header" style="color: white; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 10px; margin-bottom: 15px; font-weight: 600;">🤖 Qwen 今日建议</div>
          <div class="ai-content">
            <p style="color: #cbd5e1; line-height: 1.6; font-size: 14px; margin: 0;">“根据近 3 天的日志分析，A5 地块湿度持续偏低。建议启用智能灌溉系统，并将阈值调整为 65%。”</p>
            <el-button size="small" round style="background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid rgba(56,189,248, 0.4); margin-top: 15px">一键执行建议</el-button>
          </div>
        </div>

        <div class="custom-card mt-20" style="background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.03); margin-top: 20px">
          <div class="quick-action" @click="handleAdd" style="padding: 30px; text-align: center; cursor: pointer; transition: all 0.3s" onmouseover="this.style.background='#f8fafc'" onmouseout="this.style.background='transparent'">
            <i class="el-icon-camera-solid" style="font-size: 32px; color: #38bdf8; margin-bottom: 10px;"></i>
            <div style="font-size: 16px; color: #334155; font-weight: 500">拍照识别上传</div>
            <div style="font-size: 12px; color: #64748b; margin-top: 5px">自动识别病虫害特征</div>
          </div>
        </div>
      </el-col>

    </el-row>

    <el-dialog :title="form.id ? '编辑日志' : '📝 发布新农情'" v-model="dialogFormVisible" width="50%" center custom-class="custom-dialog ios-dialog">
      <el-form label-width="80px" size="small" :model="form" class="ios-form">
        <el-form-item label="标题">
          <el-input v-model="form.name" placeholder="请输入标题"></el-input>
        </el-form-item>
        <el-form-item label="内容">
          <el-input type="textarea" :rows="5" v-model="form.content" placeholder="输入内容后，可点击下方按钮进行 AI 润色"></el-input>
          <el-input
            v-model="form.polishHint"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="润色侧重（可选）：如「语气正式」「突出防灾」「压缩到 100 字内」——会传给通义千问"
            style="margin-top: 8px"
          />
          <el-button link icon="el-icon-cpu" :loading="polishLoading" @click="polishWithQwen" style="color: #38bdf8; margin-top: 6px;">Qwen 智能润色</el-button>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="时间">
              <el-date-picker v-model="form.time" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%"></el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布人">
              <el-input v-model="form.user"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图片">
          <el-upload :action="apiEndpoints.fileUpload" :headers="uploadHeaders" :on-success="handleImgUploadSuccess" :show-file-list="false">
            <el-button size="small" type="primary" plain class="ios-btn-secondary">上传现场图</el-button>
            <span v-if="form.img" style="margin-left: 10px; color: #4ade80"><i class="el-icon-check"></i> 已上传</span>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button class="ios-btn-secondary" @click="dialogFormVisible = false">取消</el-button>
          <el-button class="ios-btn-primary" @click="save">立即发布</el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script>
import { API_ENDPOINTS } from '@/config/api.config';

export default {
  name: "Notice",
  data() {
    return {
      apiEndpoints: API_ENDPOINTS, // 修复：正确引入 API 终点
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 5,
      name: "",
      form: { polishHint: "" },
      polishLoading: false,
      dialogFormVisible: false,
      user: localStorage.getItem("user") ? JSON.parse(localStorage.getItem("user")) : {}
    }
  },
  created() {
    this.load()
  },
  computed: {
    uploadHeaders() {
      const userStr = localStorage.getItem("user")
      const token = userStr ? (JSON.parse(userStr).token || '') : ''
      return { token }
    }
  },
  methods: {
    load() {
      this.request.get("/notice/page", {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          name: this.name,
        }
      }).then(res => {
        if (res && res.data) {
          this.tableData = res.data.records || []
          this.total = res.data.total || 0
        } else {
          this.tableData = []
          this.total = 0
        }
      }).catch(() => {
        this.tableData = []
        this.total = 0
      })
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.load();
    },
    handleCurrentChange(val) {
      this.pageNum = val;
      this.load();
    },
    /** 从 /api/chat/qwen-proxy 的 data 字段取出纯文本润色结果 */
    extractPolishText(data) {
      if (data == null) return "";
      if (typeof data === "string") return data.trim();
      if (typeof data === "object") {
        if (data.content != null) return String(data.content).trim();
        if (data.text != null) return String(data.text).trim();
        if (data.polished != null) return String(data.polished).trim();
      }
      return String(data).trim();
    },
    /** 调用后端通义千问代理，真实润色农情正文（需已登录且服务端配置 qwen.api-key） */
    polishWithQwen() {
      if (!this.form.content || !String(this.form.content).trim()) {
        return this.$message.warning("请先填写一点内容");
      }
      const title = (this.form.name && String(this.form.name).trim()) ? this.form.name.trim() : "（无标题）";
      const hint = this.form.polishHint != null ? String(this.form.polishHint).trim() : "";
      const extra = hint ? `\n\n【润色侧重】${hint}` : "";
      const systemPrompt =
        "你是农业信息化系统的文案编辑。用户要发布「田间/农情」类短讯。\n" +
        "要求：在尊重原文事实的前提下润色语言（通顺、专业、适合张贴或系统内展示）；不要编造原文未出现的病虫害名称或数据；" +
        "不要加「作为 AI」等套话；不要加前后缀如「润色后：」；输出一段可直接粘贴的正文即可。";
      const prompt =
        `标题：${title}\n\n待润色正文：\n${this.form.content.trim()}${extra}\n\n请只输出润色后的正文。`;
      this.polishLoading = true;
      this.request
        .post("/api/chat/qwen-proxy", { prompt, systemPrompt })
        .then((res) => {
          const ok = res && (res.code === 200 || res.code === "200");
          if (!ok) {
            this.$message.error(res.message || res.msg || "润色失败");
            return;
          }
          const text = this.extractPolishText(res.data);
          if (!text) {
            this.$message.warning("模型未返回有效文本，请稍后重试");
            return;
          }
          this.form.content = text;
          this.$message.success("润色完成");
        })
        .catch((err) => {
          const msg =
            (err.response && err.response.data && (err.response.data.message || err.response.data.msg)) ||
            err.message ||
            "请求失败";
          this.$message.error(msg);
        })
        .finally(() => {
          this.polishLoading = false;
        });
    },
    // 模拟 AI 分析
    mockAnalyze() {
      this.$notify({
        title: '通义千问 分析报告',
        message: '该日志关键词提取：[病害] [湿度]。已自动关联历史相似案例 3 起。',
        type: 'success',
        duration: 4000
      });
    },
    save() {
      const payload = { ...this.form };
      delete payload.polishHint;
      this.request.post("/notice", payload).then(res => {
        if (res.code === '200') {
          this.$message.success(this.form.id ? "日志更新成功" : "日志发布成功");
          this.dialogFormVisible = false;
          this.load();
        } else {
          this.$message.error(res.msg || "保存失败");
        }
      })
    },
    handleAdd() {
      this.form = {
        polishHint: "",
        time: new Date().toISOString().replace("T", " ").substring(0, 19),
        user: this.user.username
      };
      this.dialogFormVisible = true;
    },
    handleEdit(row) {
      const copy = JSON.parse(JSON.stringify(row));
      this.form = Object.assign({}, copy, { polishHint: copy.polishHint || "" });
      this.dialogFormVisible = true;
    },
    del(id) {
      this.request.delete("/notice/" + id).then(res => {
        if (res.code === '200') { this.$message.success("已删除"); this.load(); }
      })
    },
    handleImgUploadSuccess(res) {
      const url = typeof res === 'string' ? res : (res && res.data);
      this.form.img = (url && typeof url === 'string') ? url : res;
      this.$message.success("图片上传成功");
    }
  }
}
</script>

<style scoped>
/* 全局容器：增加一点浅灰底色，突显卡片 */
.notice-container {
  padding: 20px;
  background-color: #f6f8f9;
  min-height: calc(100vh - 60px);
}

/* --- 1. 顶部 AI 仪表盘 (核心视觉点) --- */
.ai-dashboard {
  background: linear-gradient(135deg, #1f2d3d 0%, #324057 100%);
  color: white;
  padding: 25px;
  border-radius: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 10px 20px rgba(0,0,0,0.2);
  margin-bottom: 25px;
  position: relative;
  overflow: hidden;
}
/* 装饰背景圆 */
.ai-dashboard::after {
  content: '';
  position: absolute;
  right: -50px;
  top: -50px;
  width: 200px;
  height: 200px;
  background: rgba(255,255,255,0.05);
  border-radius: 50%;
}

.dashboard-left .big-title {
  font-size: 24px;
  font-weight: bold;
  letter-spacing: 1px;
}
.dashboard-left .sub-text {
  font-size: 13px;
  opacity: 0.7;
  margin-top: 5px;
}
/* 呼吸灯线条 */
.pulse-line {
  width: 50px;
  height: 3px;
  background: #38bdf8; /* 由原先的荧光绿 #00e676 改为科技蓝 */
  margin-top: 15px;
  border-radius: 2px;
  animation: pulse-width 2s infinite;
}
@keyframes pulse-width {
  0% { width: 50px; opacity: 1; }
  50% { width: 100px; opacity: 0.5; }
  100% { width: 50px; opacity: 1; }
}

.dashboard-right {
  display: flex;
  gap: 40px;
  z-index: 1; /* 保证在装饰圆上面 */
}
.metric-box {
  text-align: right;
}
.metric-label {
  font-size: 12px;
  opacity: 0.6;
  margin-bottom: 5px;
}
.digital-number {
  font-family: 'Courier New', Courier, monospace;
  font-size: 28px;
  font-weight: bold;
  color: #38bdf8; /* 由原先的荧光绿 #00e676 改为科技蓝 */
}
.tags-cloud {
  display: flex;
  gap: 5px;
}

/* --- 2. 玻璃拟态卡片 (通用) --- */
.glass-card {
  border: none;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
}
.panel-header {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

/* --- 3. 时间轴卡片 --- */
.item-card {
  border-radius: 8px;
  position: relative;
  transition: all 0.3s;
  border-left: 4px solid transparent; /* 默认无边框 */
}
.item-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.1);
}
/* 最新一条高亮 */
.latest-card {
  border-left: 4px solid #F56C6C; /* 左侧红线 */
  background: #fffcfc; /* 微微泛红的背景 */
}

/* NEW 标签 */
.new-badge {
  position: absolute;
  top: 0;
  right: 0;
  background: #F56C6C;
  color: white;
  font-size: 10px;
  padding: 2px 8px;
  border-bottom-left-radius: 8px;
  border-top-right-radius: 8px;
}

.card-inner {
  padding: 5px 0;
}
.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.item-title {
  font-size: 16px;
  font-weight: bold;
  color: #2c3e50;
}

.item-body {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
}
.text-content {
  flex: 1;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  padding-right: 15px;
  /* 多行省略 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
.img-wrapper .log-image {
  width: 100px;
  height: 75px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

/* 打字机渐显效果 */
.typing-effect {
  display: inline-block;
  opacity: 0;
  animation: fadeInText 1.5s ease-in-out forwards;
}
@keyframes fadeInText {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.item-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  border-top: 1px dashed #eee;
  padding-top: 10px;
}

/* --- 4. 右侧功能区 --- */
.ai-suggest-card {
  border: none;
  color: white;
}
.ai-content p {
  font-size: 14px;
  line-height: 1.5;
  opacity: 0.9;
}

.quick-action {
  padding: 30px;
  text-align: center;
  cursor: pointer;
  background: #fff;
  transition: all 0.3s;
}
.quick-action:hover {
  background: #f8fafc;
}

/* 间距工具类 */
.mb-20 { margin-bottom: 20px; }
.mt-20 { margin-top: 20px; }

/* 强制重置 Manage.vue 中不合理的全局 el-card 样式污染 */
.notice-container :deep(.el-card ){
  border: none !important;
  box-shadow: 0 4px 12px rgba(0,0,0,0.03) !important;
}
.notice-container :deep(.el-card__header ){
  background: transparent !important;
  border-bottom: 1px solid #f0f0f0 !important;
}
.notice-container :deep(.el-card:hover ){
  transform: translateY(-2px) !important;
}
.notice-container :deep(.latest-card ){
  border-left: 4px solid #F56C6C !important;
  background: #fffcfc !important;
}
</style>