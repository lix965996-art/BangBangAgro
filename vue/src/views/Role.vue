<template>
  <div class="bento-spatial-container">
    <div class="ambient-background">
      <div class="dot-matrix"></div>
      <div class="blob eco-green"></div>
      <div class="blob tech-blue"></div>
    </div>

    <div class="content-wrapper">
      <div class="bento-panel header-panel">
        <div class="header-left">
          <div class="title-box">
            <div class="icon-cube">
              <i class="el-icon-cpu"></i>
            </div>
            <div>
              <h1>角色权限控制中枢</h1>
              <div class="live-status">
                <span class="pulse-dot"></span>
                <span class="status-text">系统实时调度中</span>
              </div>
            </div>
          </div>
          <p class="subtitle">数字孪生环境下的权限边界定义与系统功能动态授权</p>
        </div>
        <div class="header-right">
          <el-button class="bento-btn-primary" icon="el-icon-plus" @click="handleAdd">部署新角色</el-button>
          <el-button class="bento-btn-ghost" icon="el-icon-refresh" @click="load" :loading="loading">状态同步</el-button>
        </div>
      </div>

      <div class="bento-panel toolbar-panel">
        <div class="search-box">
          <el-input 
            placeholder="输入标识符或角色名称进行全域检索..." 
            prefix-icon="el-icon-search" 
            v-model="name" 
            class="bento-input search-input" 
            clearable 
            @clear="load" 
            @keyup.enter.native="load">
          </el-input>
          <el-button class="bento-btn-ghost" @click="load" :loading="loading">精准扫描</el-button>
        </div>
      </div>

      <div class="matrix-container" v-loading="loading" element-loading-background="rgba(240, 244, 248, 0.5)">
        <transition-group name="bento-list" tag="div" class="el-row" style="margin-left: -12px; margin-right: -12px;">
          
          <el-col :span="6" style="padding: 12px;" key="add-btn">
            <div class="bento-card add-card" @click="handleAdd">
              <div class="scan-line"></div>
              <div class="holographic-inner">
                <div class="add-icon-wrapper">
                  <i class="el-icon-plus"></i>
                </div>
                <p>初始化新角色</p>
                <span>Click to Initialize Node</span>
              </div>
            </div>
          </el-col>

          <el-col :span="6" v-for="item in tableData" :key="item.id" style="padding: 12px;">
            <div class="bento-card role-card">
              
              <div class="card-header">
                <div class="role-title-group">
                  <div class="role-avatar" :class="getAvatarClass(item.flag)">
                    {{ item.name.substring(0,1) }}
                  </div>
                  <div class="role-title-info">
                    <h3 class="role-name">{{ item.name }}</h3>
                    <span class="role-flag" :class="getFlagClass(item.flag)">
                      <i class="el-icon-connection"></i> {{ item.flag }}
                    </span>
                  </div>
                </div>
                <el-dropdown trigger="click" class="card-more" @command="handleCommand($event, item)">
                  <i class="el-icon-more"></i>
                  <el-dropdown-menu slot="dropdown" class="bento-dropdown">
                    <el-dropdown-item command="edit" icon="el-icon-setting">参数配置</el-dropdown-item>
                    <el-dropdown-item command="delete" icon="el-icon-delete" style="color: #FF3B30">注销角色</el-dropdown-item>
                  </el-dropdown-menu>
                </el-dropdown>
              </div>

              <div class="card-body">
                <p class="role-desc">{{ item.description || '当前实体暂无拓扑描述，请补充相关职能数据。' }}</p>
              </div>

              <div class="card-visual">
                <div class="stat-row">
                  <span class="label">权限网格覆盖率</span>
                  <span class="value" :style="{ color: getProgressColor(item.flag) }">{{ getPermissionRate(item.id) }}%</span>
                </div>
                <div class="bento-progress-track">
                  <div class="bento-progress-fill" 
                       :style="{ width: getPermissionRate(item.id) + '%', background: getProgressColor(item.flag), boxShadow: `0 0 12px ${getProgressColor(item.flag)}40` }">
                  </div>
                </div>
              </div>

              <div class="card-footer">
                <button class="action-btn" @click="selectMenu(item)">
                  <i class="el-icon-set-up"></i> 拓扑权限分配
                </button>
              </div>
              
            </div>
          </el-col>
        </transition-group>
        
        <transition name="el-fade-in">
          <div v-if="tableData.length === 0 && !loading" class="bento-empty-state bento-panel">
            <div class="radar-scan"></div>
            <h3>区域内未侦测到目标角色</h3>
            <p>请调整检索频率或通过中枢初始化新角色节点。</p>
          </div>
        </transition>
      </div>

      <div class="pagination-section bento-panel" v-if="total > 0">
         <el-pagination
            background
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            :current-page="pageNum"
            :page-sizes="[7, 11, 23]"
            :page-size="pageSize"
            layout="total, prev, pager, next, jumper"
            :total="total"
            class="bento-pagination">
        </el-pagination>
      </div>
    </div>

    <el-dialog :title="form.id ? '重构角色属性' : '创建实体角色'" :visible.sync="dialogFormVisible" width="440px" custom-class="bento-dialog" :close-on-click-modal="false" append-to-body>
      <el-form label-position="top" size="medium" class="bento-form" @submit.native.prevent>
        <el-form-item label="角色节点名称">
          <el-input v-model="form.name" placeholder="请输入标识名称..."></el-input>
        </el-form-item>
        <el-form-item label="唯一识别码 (Flag)">
          <el-input v-model="form.flag" placeholder="例：ROLE_ADMINISTRATOR"></el-input>
        </el-form-item>
        <el-form-item label="系统职能拓扑描述">
          <el-input type="textarea" v-model="form.description" rows="3" placeholder="描述该节点在系统中的行为边界..."></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button class="bento-btn-ghost" @click="dialogFormVisible = false" :disabled="btnLoading">中止</el-button>
        <el-button class="bento-btn-primary" @click="save" :loading="btnLoading">写入系统</el-button>
      </div>
    </el-dialog>

    <el-drawer
      title="配置菜单权限"
      :visible.sync="menuDialogVis"
      direction="rtl"
      size="420px"
      custom-class="bento-drawer"
      :with-header="false"
      append-to-body>
      
      <div class="drawer-header">
        <div class="drawer-title">
          <div class="icon-cube mini"><i class="el-icon-share"></i></div>
          功能网格授权
        </div>
        <i class="el-icon-close close-btn" @click="menuDialogVis = false"></i>
      </div>

      <div class="drawer-body" v-loading="treeLoading">
         <el-tree
            :props="props"
            :data="menuData"
            show-checkbox
            node-key="id"
            ref="tree"
            :default-expanded-keys="expends"
            :default-checked-keys="checks"
            class="bento-tree">
            <span class="custom-tree-node" slot-scope="{ data }">
              <span><i :class="data.icon" style="margin-right: 8px; font-size: 16px; color: #64748B;"></i> {{ data.name }}</span>
            </span>
         </el-tree>
      </div>

      <div class="drawer-footer">
        <el-button class="bento-btn-ghost" @click="menuDialogVis = false" style="flex: 1" :disabled="btnLoading">放弃更改</el-button>
        <el-button class="bento-btn-primary" @click="saveRoleMenu" style="flex: 1" :loading="btnLoading">确认授权变更</el-button>
      </div>
    </el-drawer>

  </div>
</template>

<script>
export default {
  name: "Role",
  data() {
    return {
      loading: false,
      btnLoading: false,  
      treeLoading: false, 
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 11,
      name: "",
      form: {},
      dialogFormVisible: false,
      menuDialogVis: false,
      menuData: [],
      props: { label: 'name' },
      expends: [],
      checks: [],
      roleId: 0,
      roleFlag: '',
      ids: [],
      permissionRateMap: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [pageRes, idsRes] = await Promise.all([
          this.request.get("/role/page", {
            params: { pageNum: this.pageNum, pageSize: this.pageSize, name: this.name }
          }),
          this.request.get("/menu/ids")
        ]);
        const roles = pageRes && pageRes.data && Array.isArray(pageRes.data.records) ? pageRes.data.records : [];
        this.tableData = roles;
        this.total = pageRes && pageRes.data ? pageRes.data.total : 0;
        this.ids = Array.isArray(idsRes.data) ? idsRes.data : [];
        await this.loadPermissionRates(roles);
      } catch (error) {
        this.$message.error("数据链路加载异常");
      } finally {
        this.loading = false;
      }
    },
    async loadPermissionRates(roles) {
      if (!Array.isArray(roles) || roles.length === 0 || this.ids.length === 0) {
        this.permissionRateMap = {};
        return;
      }
      const totalMenus = this.ids.length;
      const requests = roles.map(role =>
        this.request.get("/role/roleMenu/" + role.id)
          .then(res => ({ id: role.id, res }))
          .catch(() => ({ id: role.id, res: null }))
      );
      const results = await Promise.all(requests);
      const nextMap = {};
      results.forEach(({ id, res }) => {
        const assigned = res && res.code === '200' && Array.isArray(res.data) ? res.data.length : 0;
        nextMap[id] = Math.round((assigned / totalMenus) * 100);
      });
      this.permissionRateMap = nextMap;
    },
    getPermissionRate(id) {
      const value = this.permissionRateMap[id];
      return Number.isFinite(value) ? value : 0;
    },
    getAvatarClass(flag) {
      if(flag === 'ROLE_ADMIN') return 'av-red';
      if(flag && flag.includes('MANAGER')) return 'av-orange';
      return 'av-cyan';
    },
    getFlagClass(flag) {
      if(flag === 'ROLE_ADMIN') return 'fg-red';
      if(flag && flag.includes('MANAGER')) return 'fg-orange';
      return 'fg-cyan';
    },
    getProgressColor(flag) {
      if(flag === 'ROLE_ADMIN') return '#FF3B30'; 
      if(flag && flag.includes('MANAGER')) return '#F59E0B'; 
      return '#10B981'; // 帮帮农主题绿
    },

    handleCommand(command, row) {
      if(command === 'edit') this.handleEdit(row);
      if(command === 'delete') this.del(row.id);
    },
    async save() {
      this.btnLoading = true;
      try {
        // 与 RoleController 一致：仅 @PostMapping 做 saveOrUpdate，无 PUT
        const res = await this.request.post("/role", this.form);
        if (res.code === '200') {
          this.$message.success("角色参数写入成功");
          this.dialogFormVisible = false;
          this.load();
        } else {
          this.$message.error(res.msg || "写入被拒绝");
        }
      } catch (error) {
        this.$message.error("核心系统异常");
      } finally {
        this.btnLoading = false;
      }
    },
    async saveRoleMenu() {
      this.btnLoading = true;
      try {
        const keys = this.$refs.tree.getCheckedKeys();
        const res = await this.request.post("/role/roleMenu/" + this.roleId, keys);
        if (res.code === '200') {
          this.$message.success('网格授权已生效');
          this.menuDialogVis = false;
          if (this.roleFlag === 'ROLE_ADMIN') {
            this.$store.commit("logout");
          }
        } else {
          this.$message.error(res.msg || "授权失败");
        }
      } catch (error) {
        this.$message.error("系统异常，变更撤回");
      } finally {
        this.btnLoading = false;
      }
    },
    handleAdd() {
      this.dialogFormVisible = true;
      this.form = {};
    },
    handleEdit(row) {
      this.form = JSON.parse(JSON.stringify(row));
      this.dialogFormVisible = true;
    },
    del(id) {
      this.$confirm('注销此角色将导致依赖该权限的实体脱机，是否强行阻断?', '高危指令', {
        confirmButtonText: '强制注销',
        cancelButtonText: '取消',
        type: 'error',
        customClass: 'bento-confirm'
      }).then(async () => {
        try {
          const res = await this.request.delete("/role/" + id);
          if (res.code === '200') {
            this.$message.success("实体注销成功");
            this.load();
          } else {
            this.$message.error("注销指令被驳回");
          }
        } catch (error) {
          this.$message.error("系统阻断了该请求");
        }
      }).catch(() => {});
    },
    handleSizeChange(pageSize) {
      this.pageSize = pageSize;
      this.load();
    },
    handleCurrentChange(pageNum) {
      this.pageNum = pageNum;
      this.load();
    },
    async selectMenu(role) {
      this.roleId = role.id;
      this.roleFlag = role.flag;
      this.menuDialogVis = true;
      this.treeLoading = true;
      
      try {
        const [menuRes, roleMenuRes] = await Promise.all([
          this.request.get("/menu"),
          this.request.get("/role/roleMenu/" + this.roleId)
        ]);
        
        this.menuData = menuRes.data;
        this.expends = this.menuData.map(v => v.id);
        
        this.checks = roleMenuRes.data;
        this.$nextTick(() => {
          this.$refs.tree.setCheckedKeys(this.checks);
          this.ids.forEach(id => {
            if (!this.checks.includes(id)) {
              this.$refs.tree.setChecked(id, false);
            }
          });
        });
      } catch (error) {
        this.$message.error("获取拓扑信息失败");
      } finally {
        this.treeLoading = false;
      }
    }
  }
}
</script>

<style scoped>
/* ========================================================== */
/* Bento Box x Digital Twin (数字孪生高对比度重构版) */
/* ========================================================== */

::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #CBD5E1; border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: #94A3B8; }

/* --- 1. 背景层重构：增加对比度与粒子网格 --- */
.bento-spatial-container {
  position: relative;
  min-height: 100vh;
  padding: 24px;
  font-family: -apple-system, "SF Pro Display", "PingFang SC", sans-serif;
  overflow: hidden;
  background-color: #E2E8F0; /* 加深底色，让纯白卡片跳出来 */
  color: #0F172A;
}

.ambient-background {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%; z-index: 0; pointer-events: none; overflow: hidden;
}
/* 粒子矩阵背景 - 贴合数字孪生大屏质感 */
.dot-matrix {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  background-image: radial-gradient(#94A3B8 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.3;
}
.blob {
  position: absolute; border-radius: 50%; filter: blur(100px); opacity: 0.5; animation: float 20s infinite alternate ease-in-out;
}
.eco-green {
  width: 700px; height: 700px; background: rgba(16, 185, 129, 0.4); top: -200px; right: -100px;
}
.tech-blue {
  width: 600px; height: 600px; background: rgba(14, 165, 233, 0.3); bottom: -100px; left: -200px; animation-delay: -5s;
}
@keyframes float { 0% { transform: translate(0, 0) scale(1); } 100% { transform: translate(100px, 50px) scale(1.1); } }

.content-wrapper { position: relative; z-index: 1; display: flex; flex-direction: column; gap: 20px;}

/* --- 公共 Bento 面板 --- */
.bento-panel {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid #FFFFFF;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.04), inset 0 1px 0 rgba(255,255,255,1);
  border-radius: 20px;
}

/* --- 2. 顶部中枢控制台 --- */
.header-panel {
  display: flex; justify-content: space-between; align-items: center; padding: 20px 28px;
}
.header-left { display: flex; flex-direction: column; gap: 4px;}
.title-box { display: flex; align-items: center; gap: 16px;}
.icon-cube {
  width: 44px; height: 44px; border-radius: 12px; 
  background: linear-gradient(135deg, #10B981, #0EA5E9);
  display: flex; align-items: center; justify-content: center; color: white; font-size: 24px; 
  box-shadow: 0 8px 16px rgba(16, 185, 129, 0.25), inset 0 2px 4px rgba(255,255,255,0.3);
}
.icon-cube.mini { width: 32px; height: 32px; font-size: 16px; border-radius: 8px;}
.title-box h1 { font-size: 22px; font-weight: 800; margin: 0; color: #0F172A; letter-spacing: 0.5px;}

.live-status { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; background: #ECFDF5; border-radius: 20px; border: 1px solid #A7F3D0; margin-top: 4px;}
.pulse-dot { width: 8px; height: 8px; background-color: #10B981; border-radius: 50%; box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); animation: pulse-ring 2s infinite cubic-bezier(0.66, 0, 0, 1); }
.status-text { font-size: 12px; font-weight: 700; color: #059669; }
@keyframes pulse-ring { 100% { box-shadow: 0 0 0 10px rgba(16, 185, 129, 0); } }

.subtitle { font-size: 14px; color: #64748B; margin: 0; font-weight: 500;}

/* 按钮组 */
.bento-btn-primary {
  background: #10B981 !important; border: none !important; color: white !important; /* 帮帮农绿 */
  border-radius: 12px !important; font-weight: 600 !important; padding: 12px 24px !important;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3); transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.bento-btn-primary:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(16, 185, 129, 0.4); background: #059669 !important;}
.bento-btn-ghost {
  background: #FFFFFF !important; border: 1px solid #E2E8F0 !important; color: #334155 !important;
  border-radius: 12px !important; font-weight: 600 !important; padding: 12px 24px !important; transition: 0.3s;
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
}
.bento-btn-ghost:hover { background: #F8FAFC !important; transform: translateY(-2px); border-color: #CBD5E1 !important;}

/* --- 3. 检索矩阵 --- */
.toolbar-panel { padding: 16px 28px; }
.search-box { display: flex; gap: 16px; max-width: 500px;}
::v-deep .bento-input .el-input__inner {
  background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 12px;
  height: 44px; line-height: 44px; font-size: 14px; font-weight: 500; transition: all 0.3s; color: #0F172A;
}
::v-deep .bento-input .el-input__inner:focus { background: #FFFFFF; border-color: #10B981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.1);}

/* --- 过渡动画 --- */
.bento-list-enter-active, .bento-list-leave-active { transition: all 0.5s cubic-bezier(0.25, 1, 0.5, 1); }
.bento-list-enter, .bento-list-leave-to { opacity: 0; transform: translateY(20px) scale(0.98); }
.bento-list-move { transition: transform 0.5s cubic-bezier(0.25, 1, 0.5, 1); }

/* --- 4. 角色卡片 (核心 Bento 块) --- */
.bento-card {
  background: #FFFFFF;
  border: 1px solid #E2E8F0; border-radius: 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03), 0 1px 3px rgba(0,0,0,0.02);
  display: flex; flex-direction: column; justify-content: space-between;
  height: 250px; padding: 24px; position: relative; overflow: hidden;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.bento-card:hover { transform: translateY(-6px); box-shadow: 0 20px 40px rgba(0,0,0,0.08); border-color: #CBD5E1;}

/* 初始化卡片升级：类似数据上传的 Dropzone */
.add-card { background: rgba(255, 255, 255, 0.5); border: 2px dashed #CBD5E1; cursor: pointer; padding: 0; box-shadow: none; overflow: hidden; position: relative;}
.scan-line { position: absolute; top: -100%; left: 0; width: 100%; height: 100%; background: linear-gradient(to bottom, transparent, rgba(16, 185, 129, 0.1), transparent); animation: scan 3s linear infinite; pointer-events: none;}
@keyframes scan { 0% { top: -100%; } 100% { top: 100%; } }
.holographic-inner {
  width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center;
  color: #64748B; transition: 0.3s; z-index: 1; position: relative;
}
.add-icon-wrapper { width: 56px; height: 56px; border-radius: 16px; background: #F1F5F9; display: flex; align-items: center; justify-content: center; margin-bottom: 16px; transition: 0.3s;}
.holographic-inner i { font-size: 28px; color: #94A3B8;}
.holographic-inner p { font-size: 16px; font-weight: 800; margin: 0 0 6px 0; color: #1E293B;}
.holographic-inner span { font-size: 12px; font-family: 'SF Mono', monospace; color: #94A3B8;}
.add-card:hover { border-color: #10B981; background: #FFFFFF;}
.add-card:hover .add-icon-wrapper { background: #ECFDF5; transform: scale(1.1);}
.add-card:hover .holographic-inner i { color: #10B981;}

/* 卡片内部排版 */
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px;}
.role-title-group { display: flex; align-items: center; gap: 12px;}
.role-avatar { width: 46px; height: 46px; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 800; font-family: 'SF Mono', monospace;}
.av-cyan { background: #E0F2FE; color: #0284C7; }
.av-orange { background: #FEF3C7; color: #D97706; }
.av-red { background: #FEE2E2; color: #DC2626; }

.role-title-info { display: flex; flex-direction: column; align-items: flex-start; gap: 4px;}
.role-name { font-size: 17px; font-weight: 800; color: #0F172A; margin: 0; }
.role-flag { font-size: 12px; padding: 2px 8px; border-radius: 6px; font-weight: 700; font-family: 'SF Mono', monospace; display: flex; align-items: center; gap: 4px;}
.fg-cyan { background: #F0F9FF; color: #0284C7; border: 1px solid #BAE6FD;}
.fg-orange { background: #FFFBEB; color: #D97706; border: 1px solid #FDE68A;}
.fg-red { background: #FEF2F2; color: #DC2626; border: 1px solid #FECACA;}

.card-more { color: #94A3B8; cursor: pointer; padding: 6px; font-size: 20px; border-radius: 8px; transition: 0.2s;}
.card-more:hover { color: #0F172A; background: #F1F5F9; }

.role-desc { font-size: 14px; color: #475569; line-height: 1.6; margin: 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-weight: 500;}

/* 数据条重构：贴合仪表的科技感 */
.card-visual { margin-bottom: 16px; }
.stat-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;}
.stat-row .label { font-size: 13px; color: #64748B; font-weight: 700;}
.stat-row .value { font-size: 14px; font-weight: 800; font-family: 'SF Mono', monospace;}
.bento-progress-track { width: 100%; height: 8px; background: #F1F5F9; border-radius: 4px; overflow: hidden; position: relative;}
.bento-progress-fill { height: 100%; border-radius: 4px; transition: width 1s cubic-bezier(0.25, 1, 0.5, 1); background-image: linear-gradient(45deg, rgba(255,255,255,0.15) 25%, transparent 25%, transparent 50%, rgba(255,255,255,0.15) 50%, rgba(255,255,255,0.15) 75%, transparent 75%, transparent); background-size: 1rem 1rem;}

.card-footer { border-top: 1px solid #F1F5F9; padding-top: 16px;}
.action-btn {
  width: 100%; padding: 12px 0; border: none; border-radius: 12px;
  background: #F8FAFC; color: #0F172A; font-size: 14px; font-weight: 700;
  cursor: pointer; transition: 0.3s; display: flex; align-items: center; justify-content: center; gap: 8px;
}
.action-btn:hover { background: #F0F9FF; color: #0EA5E9; }

/* 雷达扫描空状态 */
.bento-empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80px 0;}
.radar-scan { width: 72px; height: 72px; border-radius: 50%; border: 2px solid #E2E8F0; position: relative; margin-bottom: 20px; overflow: hidden;}
.radar-scan::before { content: ''; position: absolute; top: 50%; left: 50%; width: 50%; height: 50%; background: conic-gradient(from 0deg, transparent 70%, rgba(16, 185, 129, 0.4) 100%); transform-origin: 0 0; animation: spin 2s linear infinite; border-radius: 0 100% 0 0;}
@keyframes spin { 100% { transform: rotate(360deg); } }
.bento-empty-state h3 { font-size: 18px; font-weight: 800; color: #0F172A; margin: 0 0 8px 0;}
.bento-empty-state p { font-size: 14px; color: #64748B; margin: 0;}

/* --- 分页 --- */
.pagination-section { display: flex; justify-content: center; padding: 16px; border-radius: 20px;}
::v-deep .bento-pagination.el-pagination.is-background .el-pager li:not(.disabled).active { background: #10B981; color: #FFF; border-radius: 10px; border: none; font-weight: 800;}
::v-deep .bento-pagination.el-pagination.is-background .el-pager li { border-radius: 10px; background: #F8FAFC; border: 1px solid #E2E8F0; font-weight: 700; color: #475569; transition: all 0.2s;}
::v-deep .bento-pagination.el-pagination.is-background .el-pager li:hover { background: #FFFFFF; color: #10B981; border-color: #10B981;}

/* ================= 弹窗与抽屉（匹配整体 Bento 风格） ================= */
::v-deep .bento-dialog { background: #FFFFFF; border-radius: 24px; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25); border: 1px solid #E2E8F0;}
::v-deep .bento-dialog .el-dialog__header { padding: 24px 24px 0; }
::v-deep .bento-dialog .el-dialog__title { font-weight: 800; color: #0F172A; font-size: 18px;}
::v-deep .bento-dialog .el-dialog__body { padding: 24px; }
::v-deep .bento-dialog .el-dialog__footer { padding: 16px 24px 24px; text-align: right; border-top: 1px solid #F1F5F9;}

::v-deep .bento-form .el-form-item__label { font-weight: 700; color: #334155; padding-bottom: 6px; line-height: 1.2;}
::v-deep .bento-form .el-input__inner,
::v-deep .bento-form .el-textarea__inner { background: #F8FAFC; border-radius: 12px; border: 1px solid #E2E8F0; transition: 0.3s; color: #0F172A; font-weight: 500;}
::v-deep .bento-form .el-input__inner:focus,
::v-deep .bento-form .el-textarea__inner:focus { background: #FFFFFF; border-color: #10B981; box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15); }

/* 抽屉重构 */
::v-deep .bento-drawer { background: #FFFFFF; outline: none; border-top-left-radius: 32px; border-bottom-left-radius: 32px; box-shadow: -25px 0 50px -12px rgba(0, 0, 0, 0.25); display: flex; flex-direction: column; border-left: 1px solid #E2E8F0;}
.drawer-header { padding: 32px 24px 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #F1F5F9;}
.drawer-title { font-size: 20px; font-weight: 800; color: #0F172A; display: flex; align-items: center; gap: 12px;}
.close-btn { font-size: 20px; color: #64748B; cursor: pointer; padding: 8px; border-radius: 12px; transition: 0.3s; background: #F8FAFC; border: 1px solid #E2E8F0;}
.close-btn:hover { background: #FEE2E2; color: #EF4444; border-color: #FECACA;}

.drawer-body { flex: 1; padding: 20px 24px; overflow-y: auto; }
.drawer-footer { padding: 24px; display: flex; gap: 16px; border-top: 1px solid #F1F5F9; background: #FFFFFF;}

::v-deep .bento-tree { background: transparent; }
::v-deep .bento-tree .el-tree-node__content { height: 44px; border-radius: 12px; transition: 0.2s; margin-bottom: 4px; font-weight: 600; color: #334155;}
::v-deep .bento-tree .el-tree-node__content:hover { background: #F8FAFC; color: #0F172A;}
::v-deep .bento-tree .el-checkbox__inner { border-radius: 6px; border: 1px solid #94A3B8; width: 18px; height: 18px;}
::v-deep .bento-tree .el-checkbox__input.is-checked .el-checkbox__inner { background-color: #10B981; border-color: #10B981;}
::v-deep .bento-tree .el-checkbox__inner::after { top: 3px; left: 6px; }

::v-deep .bento-confirm { border-radius: 24px !important; padding-bottom: 24px; border: 1px solid #E2E8F0; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);}
</style>
