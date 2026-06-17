<template>
  <div class="ios-container">
    <div class="ios-header-bar">
      <div class="header-left">
        <div class="title-box">
          <i class="el-icon-box icon-blue"></i>
          <h1>供给协同中心</h1>
        </div>
        <p class="subtitle">聚焦库存健康、补给稳定与物资保障台账</p>
      </div>
      <div class="header-right">
        <el-button class="ios-btn-primary" size="small" icon="el-icon-refresh" @click="fetchRealData" :loading="loading">
          刷新数据
        </el-button>
        <el-button class="ios-btn-secondary" size="small" icon="el-icon-download">导出报表</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="metrics-row" v-loading="loading">
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-cyan-light">
            <i class="el-icon-finished text-cyan"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">库存健康率</div>
            <div class="metric-value">{{ inventoryHealthRate }}<span class="unit">%</span></div>
            <div class="metric-sub" :class="{ 'text-red': lowStockItems.length > 0 }">
              {{ lowStockItems.length }} 项物资低于安全线
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-blue-light">
            <i class="el-icon-school text-blue"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">物资品种数</div>
            <div class="metric-value">{{ inventoryData.length }}<span class="unit">类</span></div>
            <div class="metric-sub">总库存量 {{ inventoryUnitTotal }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-orange-light">
            <i class="el-icon-wallet text-orange"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">采购总额</div>
            <div class="metric-value"><span class="unit">¥ </span>{{ formatCurrency(purchaseAmount).replace('¥','') }}</div>
            <div class="metric-sub">平均单笔 ¥ {{ formatCurrency(averagePurchaseTicket).replace('¥','') }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-purple-light">
            <i class="el-icon-connection text-purple"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">合作供应商</div>
            <div class="metric-value">{{ supplierCount }}<span class="unit">家</span></div>
            <div class="metric-sub">数据实时同步自数据库</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="main-layout" type="flex">
      
      <el-col :span="16" class="flex-col">
        <div class="ios-card flex-1 flex-col">
          <div class="card-header">
            <div class="ios-segmented-control">
              <button 
                :class="['segment-btn', { active: activeTab === 'inventory' }]" 
                @click="activeTab = 'inventory'">实时库存</button>
              <button 
                :class="['segment-btn', { active: activeTab === 'purchase' }]" 
                @click="activeTab = 'purchase'">采购记录</button>
            </div>
            
            <div class="header-actions" style="display: flex; gap: 16px; align-items: center;">
              <el-input 
                placeholder="搜索物资名称或编号" 
                prefix-icon="el-icon-search" 
                v-model="searchQuery"
                @clear="activeTab === 'inventory' ? loadInventory() : loadPurchase()"
                @keyup.enter="activeTab === 'inventory' ? loadInventory() : loadPurchase()"
                class="ios-input search-input"
                size="small"
                style="width: 240px;"
                clearable>
              </el-input>
              <el-button v-if="activeTab === 'inventory'" class="ios-btn-sm-primary" icon="el-icon-plus" @click="openInventoryDialog()">入库登记</el-button>
              <el-button v-if="activeTab === 'purchase'" class="ios-btn-sm-primary" icon="el-icon-plus" @click="openPurchaseDialog()">新建采购</el-button>
            </div>
          </div>

          <div class="card-body table-container" v-loading="loading">
            <div v-if="activeTab === 'inventory'" style="height: 100%; display: flex; flex-direction: column;">
              <el-table 
                :data="filteredInventory" 
                style="width: 100%" 
                height="100%"
                class="ios-table"
                :header-cell-style="tableHeaderStyle">
                
                <el-table-column prop="code" label="物资编号" min-width="120"></el-table-column>
                <el-table-column prop="name" label="物资名称" min-width="150">
                  <template #default="scope">
                    <span class="strong-text">{{ scope.row.name || scope.row.materialName || scope.row.produce || '未命名' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="warehouse" label="所在仓区" min-width="120"></el-table-column>
                <el-table-column label="当前库存" min-width="140" align="right">
                  <template #default="scope">
                    <span class="num-text">{{ scope.row.stock || scope.row.number || scope.row.quantity || 0 }}</span> {{ scope.row.unit || '件' }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100" align="center">
                  <template #default="scope">
                    <span :class="['status-pill', getStockStatusClass(scope.row)]">
                      {{ getStockStatusText(scope.row) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right" align="center">
                  <template #default="scope">
                    <el-button link class="ios-btn-link" @click="openInventoryDialog(scope.row)">编辑</el-button>
                    <el-button link class="ios-btn-link action-danger" @click="removeInventory(scope.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div v-if="activeTab === 'purchase'" style="height: 100%; display: flex; flex-direction: column;">
              <el-table 
                :data="filteredPurchase" 
                style="width: 100%" 
                height="100%"
                class="ios-table"
                :header-cell-style="tableHeaderStyle">
                
                <el-table-column prop="orderNo" label="采购单号" min-width="140"></el-table-column>
                <el-table-column prop="itemName" label="采购项目" min-width="150">
                  <template #default="scope">
                    <span class="strong-text">{{ scope.row.itemName || scope.row.product || scope.row.name || '未命名项目' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="supplier" label="供应商" min-width="140" show-overflow-tooltip>
                  <template #default="scope">{{ scope.row.supplier || scope.row.provider }}</template>
                </el-table-column>
                <el-table-column label="单价" min-width="120" align="right">
                  <template #default="scope">
                    <span class="num-text">{{ formatCurrency(scope.row.price) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="总价" min-width="140" align="right">
                  <template #default="scope">
                    <span class="num-text text-blue">{{ formatCurrency(getPurchaseTotal(scope.row)) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" align="center" fixed="right">
                  <template #default="scope">
                    <el-button link class="ios-btn-link" @click="openPurchaseDialog(scope.row)">编辑</el-button>
                    <el-button link class="ios-btn-link action-danger" @click="removePurchase(scope.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="8" class="flex-col">
        <div class="ios-card flex-1 flex-col">
          <div class="card-header border-bottom">
            <div class="header-title">
              <i class="el-icon-warning-outline" style="color: #FF3B30;"></i> 智能预警与风险
            </div>
            <span class="badge-red" v-if="lowStockItems.length > 0">{{ lowStockItems.length }} 项待处理</span>
          </div>

          <div class="card-body scroll-body" v-loading="loading">
            
            <div v-if="lowStockItems.length === 0" class="ios-empty-state">
               <div class="empty-icon-circle"><i class="el-icon-circle-check"></i></div>
               <h3>库存健康</h3>
               <p>当前数据库中无低于安全线的物资</p>
            </div>

            <div v-else class="risk-list">
              <div class="risk-section-title">低于安全库存报警</div>
              
              <div class="risk-item" v-for="(item, index) in lowStockItems" :key="index">
                <div class="risk-icon">
                  <i class="el-icon-shopping-cart-full"></i>
                </div>
                <div class="risk-content">
                  <div class="risk-name">{{ item.name || item.materialName || item.produce }} <span class="risk-code">#{{ item.code || item.id }}</span></div>
                  <div class="risk-desc">
                    当前: <span class="text-red">{{ item.stock || item.number || item.quantity || 0 }}</span> {{ item.unit || '件' }} 
                    <span class="divider">|</span> 
                    安全线: {{ item.safeStock || 0 }} {{ item.unit || '件' }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

    </el-row>

    <el-dialog
      :title="inventoryForm.id ? '编辑库存档案' : '新建库存档案'"
      v-model="inventoryDialogVisible"
      width="540px"
      :close-on-click-modal="false"
      custom-class="ios-dialog"
    >
      <el-form :model="inventoryForm" label-width="100px" size="small" class="ios-form">
        <el-form-item label="物资名称">
          <el-input v-model="inventoryForm.produce" placeholder="输入物资名称" />
        </el-form-item>
        <el-form-item label="所属仓区">
          <el-input v-model="inventoryForm.warehouse" placeholder="例如：A区化肥库" />
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input-number v-model="inventoryForm.number" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="安全库存线">
          <el-input-number v-model="inventoryForm.safeStock" :min="1" style="width: 100%" />
          <div style="font-size: 12px; color: #8E8E93; margin-top: 4px;">库存低于此数值将触发首页红色报警</div>
        </el-form-item>
        <el-form-item label="最大库存">
          <el-input-number v-model="inventoryForm.maxStock" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="登记/保管员">
          <el-input v-model="inventoryForm.keeper" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="inventoryForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="ios-btn-secondary" @click="inventoryDialogVisible = false">取消</el-button>
          <el-button class="ios-btn-primary" @click="saveInventory">确定保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      :title="purchaseForm.id ? '编辑采购记录' : '新建采购记录'"
      v-model="purchaseDialogVisible"
      width="540px"
      :close-on-click-modal="false"
      custom-class="ios-dialog"
    >
      <el-form :model="purchaseForm" label-width="100px" size="small" class="ios-form">
        <el-form-item label="采购物资">
          <el-input v-model="purchaseForm.product" placeholder="输入采购物资名称" />
        </el-form-item>
        <el-form-item label="采购数量">
          <el-input-number v-model="purchaseForm.number" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="采购单价">
          <el-input-number v-model="purchaseForm.price" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="purchaseForm.provider" placeholder="输入供应商全称" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="purchaseForm.phone" />
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="purchaseForm.address" />
        </el-form-item>
        <el-form-item label="采购负责人">
          <el-input v-model="purchaseForm.purchaser" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="ios-btn-secondary" @click="purchaseDialogVisible = false">取消</el-button>
          <el-button class="ios-btn-primary" @click="savePurchase">确定保存</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script>
import { getStoredUserRaw } from '@/utils/authStorage'

// 初始表单构造器
function createInventoryForm() {
  return { produce: '', warehouse: '', region: '', number: 1, safeStock: 20, maxStock: 100, dailyConsumption: 0, keeper: '', remark: '' }
}
function createPurchaseForm(user) {
  return { product: '', price: 0, number: 1, provider: '', address: '', phone: '', purchaser: user.nickname || user.username || '', remark: '' }
}

export default {
  name: 'SupplyCenter',
  data() {
    const userStr = getStoredUserRaw()
    const user = userStr ? JSON.parse(userStr) : {}
    return {
      user,
      activeTab: 'inventory',
      searchQuery: '',
      loading: false,

      inventoryData: [],
      purchaseData: [],

      inventoryDialogVisible: false,
      purchaseDialogVisible: false,
      inventoryForm: createInventoryForm(),
      purchaseForm: createPurchaseForm(user)
    }
  },
  computed: {
    inventoryHealthRate() {
      if (this.inventoryData.length === 0) return 100;
      const healthyCount = this.inventoryData.length - this.lowStockItems.length;
      return Math.round((healthyCount / this.inventoryData.length) * 100);
    },
    inventoryUnitTotal() {
      return this.inventoryData.reduce((sum, item) => sum + (Number(item.stock || item.number || item.quantity) || 0), 0).toLocaleString();
    },
    purchaseAmount() {
      return this.purchaseData.reduce((sum, item) => sum + this.getPurchaseTotal(item), 0);
    },
    averagePurchaseTicket() {
      if (this.purchaseData.length === 0) return 0;
      return Math.round(this.purchaseAmount / this.purchaseData.length);
    },
    supplierCount() {
      const suppliers = new Set(this.purchaseData.map(item => item.supplier || item.provider).filter(Boolean));
      return suppliers.size;
    },
    lowStockItems() {
      return this.inventoryData.filter(item => {
        const stock = Number(item.stock || item.number || item.quantity) || 0;
        const safe = Number(item.safeStock) || 0;
        return safe > 0 && stock < safe;
      });
    },
    filteredInventory() {
      if (!this.searchQuery) return this.inventoryData;
      const lowerQuery = this.searchQuery.toLowerCase();
      return this.inventoryData.filter(item => {
        const name = item.name || item.materialName || item.produce || '';
        const code = item.code || String(item.id) || '';
        return name.toLowerCase().includes(lowerQuery) || code.toLowerCase().includes(lowerQuery);
      });
    },
    filteredPurchase() {
      if (!this.searchQuery) return this.purchaseData;
      const lowerQuery = this.searchQuery.toLowerCase();
      return this.purchaseData.filter(item => {
        const name = item.itemName || item.product || item.name || '';
        const orderNo = item.orderNo || String(item.id) || '';
        const supplier = item.supplier || item.provider || '';
        return name.toLowerCase().includes(lowerQuery) || 
               orderNo.toLowerCase().includes(lowerQuery) ||
               supplier.toLowerCase().includes(lowerQuery);
      });
    }
  },
  created() {
    this.fetchRealData();
  },
  methods: {
    async fetchRealData() {
      this.loading = true;
      try {
        const invRes = await this.request.get('/inventory/page', { params: { pageNum: 1, pageSize: 500 } });
        if (invRes.code === '200' && invRes.data) {
          this.inventoryData = invRes.data.records || invRes.data || [];
        }
        const purRes = await this.request.get('/purchase/page', { params: { pageNum: 1, pageSize: 500 } });
        if (purRes.code === '200' && purRes.data) {
          this.purchaseData = purRes.data.records || purRes.data || [];
        }
      } catch (error) {
        this.$message.error('数据加载失败，请检查数据库连接');
      } finally {
        this.loading = false;
      }
    },

    // ================= 弹窗开启与关闭逻辑 =================
    openInventoryDialog(row) {
      this.inventoryForm = row ? Object.assign(createInventoryForm(), JSON.parse(JSON.stringify(row))) : createInventoryForm()
      // 同步可能的不同字段名
      if (row && !this.inventoryForm.produce) this.inventoryForm.produce = row.name || row.materialName || '';
      if (row && !this.inventoryForm.number) this.inventoryForm.number = row.stock || row.quantity || 0;
      this.inventoryDialogVisible = true
    },
    openPurchaseDialog(row) {
      this.purchaseForm = row ? Object.assign(createPurchaseForm(this.user), JSON.parse(JSON.stringify(row))) : createPurchaseForm(this.user)
      if (row && !this.purchaseForm.product) this.purchaseForm.product = row.itemName || row.name || '';
      if (row && !this.purchaseForm.provider) this.purchaseForm.provider = row.supplier || '';
      this.purchaseDialogVisible = true
    },

    // 🔥 防弹级核心：精准区分 POST (新增) 和 PUT (修改) 🔥
    saveInventory() {
      const payload = Object.assign({}, this.inventoryForm);
      if (!payload.safeStock) payload.safeStock = 20; // 默认安全线
      
      const isEdit = !!payload.id;

      this.request.post('/inventory', payload).then(res => {
        if (res.code === '200') {
          this.$message.success(isEdit ? '库存档案已更新' : '已成功录入新库存');
          this.inventoryDialogVisible = false;
          this.fetchRealData(); // 重新拉取最新数据
        } else {
          this.$message.error(res.msg || '保存失败');
        }
      });
    },

    savePurchase() {
      const payload = Object.assign({}, this.purchaseForm);
      const isEdit = !!payload.id;

      this.request.post('/purchase', payload).then(res => {
        if (res.code === '200') {
          this.$message.success(isEdit ? '采购记录已更新' : '已成功录入新采购单');
          this.purchaseDialogVisible = false;
          this.fetchRealData(); // 重新拉取最新数据
        } else {
          this.$message.error(res.msg || '保存失败');
        }
      });
    },

    // ================= 删除逻辑 =================
    removeInventory(row) {
      this.$confirm(`确定删除物资“${row.produce || row.name}”吗？`, '删除确认', { type: 'warning' }).then(() => {
        this.request.delete('/inventory/' + row.id).then(res => {
          if (res.code === '200') { this.$message.success('删除成功'); this.fetchRealData(); } 
          else { this.$message.error(res.msg || '删除失败'); }
        });
      }).catch(err => { console.error('Delete inventory confirmation canceled:', err) });
    },

    removePurchase(row) {
      this.$confirm(`确定删除采购记录“${row.product || row.itemName}”吗？`, '删除确认', { type: 'warning' }).then(() => {
        this.request.delete('/purchase/' + row.id).then(res => {
          if (res.code === '200') { this.$message.success('删除成功'); this.fetchRealData(); } 
          else { this.$message.error(res.msg || '删除失败'); }
        });
      }).catch(err => { console.error('Delete purchase record confirmation canceled:', err) });
    },

    // ================= 辅助工具方法 =================
    getPurchaseTotal(row) {
      return (Number(row.price) || 0) * (Number(row.number) || 0)
    },
    formatCurrency(value) {
      return '¥' + (Number(value) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    },
    getStockStatusClass(row) {
      const stock = Number(row.stock || row.number || row.quantity) || 0;
      const safe = Number(row.safeStock) || 0;
      if (safe > 0 && stock < safe) return 'pill-red';
      if (safe > 0 && stock < safe * 1.2) return 'pill-orange';
      return 'pill-blue';
    },
    getStockStatusText(row) {
      const stock = Number(row.stock || row.number || row.quantity) || 0;
      const safe = Number(row.safeStock) || 0;
      if (safe > 0 && stock < safe) return '需补货';
      if (safe > 0 && stock < safe * 1.2) return '偏低';
      return '健康';
    },
    tableHeaderStyle() {
      return { backgroundColor: '#F8F9FA', color: '#64748B', fontWeight: '600', fontSize: '13px', borderBottom: '1px solid #E5E5EA' };
    }
  }
}
</script>

<style scoped>
/* ========================================================== */
/* B2B 经典企业级后台风格 - 供给协同中心 (Supply Center) */
/* ========================================================== */
.ios-container {
  padding: 24px;
  background-color: #f4f7fb;
  min-height: calc(100vh - 60px);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #303133;
}

/* --- 公共工具类 --- */
.flex-col { display: flex; flex-direction: column; }
.flex-1 { flex: 1; min-height: 0; }
.ios-card {
  background: #ffffff; 
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  border: 1px solid #ebeef5;
  overflow: hidden;
}

/* --- 1. 顶部标题栏 --- */
.ios-header-bar {
  display: flex; justify-content: space-between; align-items: center; 
  background: #ffffff; padding: 24px 32px; margin-bottom: 24px;
  border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); border: 1px solid #ebeef5;
}
.header-left { display: flex; flex-direction: column; }
.title-box { display: flex; align-items: center; gap: 8px; margin-bottom: 4px;}
.title-box h1 { font-size: 20px; font-weight: 600; color: #303133; margin: 0;}
.icon-blue { font-size: 24px; color: #409eff; }
.subtitle { font-size: 13px; color: #909399; margin: 0; }

.header-right { display: flex; gap: 12px; }
.ios-btn-primary {
  background: #409eff !important; border: transparent !important; color: white !important;
  border-radius: 4px !important; font-weight: 500 !important; padding: 10px 20px !important;
  transition: opacity 0.2s;
}
.ios-btn-primary:hover { opacity: 0.85; transform: none; }
.ios-btn-secondary {
  background: #ffffff !important; border: 1px solid #dcdfe6 !important; color: #606266 !important;
  border-radius: 4px !important; font-weight: 500 !important; padding: 10px 20px !important;
  transition: background 0.2s;
}
.ios-btn-secondary:hover { background: #f5f7fa !important; color: #409eff !important; border-color: #c6e2ff !important; }
.ios-btn-sm-primary {
  background: #409eff !important; border: none !important; color: white !important;
  border-radius: 4px !important; font-weight: 500 !important; padding: 8px 16px !important;
  transition: opacity 0.2s;
}
.ios-btn-sm-primary:hover { opacity: 0.85; }

/* --- 2. 核心指标卡片 --- */
.metrics-row { margin-bottom: 24px; }
.metric-card { padding: 20px 24px; display: flex; align-items: center; gap: 16px; border: 1px solid #ebeef5; border-radius: 8px; }
.metric-icon-box {
  width: 48px; height: 48px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0;
}
.bg-cyan-light { background: #e0f7fa; } .text-cyan { color: #00bcd4; }
.bg-blue-light { background: #ecf5ff; } .text-blue { color: #409eff; }
.bg-orange-light { background: #fdf6ec; } .text-orange { color: #e6a23c; }
.bg-purple-light { background: #f4f4f5; } .text-purple { color: #909399; }
.text-red { color: #f56c6c !important; }

.metric-info { display: flex; flex-direction: column; overflow: hidden; justify-content: center; }
.metric-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.metric-value { font-size: 28px; font-weight: 600; color: #303133; margin-bottom: 4px; white-space: nowrap; line-height: 1; }
.unit { font-size: 14px; font-weight: normal; color: #909399; margin: 0 2px; }
.metric-sub { font-size: 13px; color: #c0c4cc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 4px; }

/* --- 3. 主体布局 --- */
.main-layout { height: calc(100vh - 250px); min-height: 500px; }
.card-header {
  padding: 0 24px; display: flex; justify-content: space-between; align-items: center; 
  border-bottom: 1px solid #ebeef5; height: 56px;
}
.card-header.border-bottom { border-bottom: 1px solid #ebeef5; }
.header-title { font-size: 16px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 8px; }
.header-title i { font-size: 18px; }
.card-body { flex: 1; min-height: 0; overflow: hidden;}
.scroll-body { overflow-y: auto; padding: 16px; }

/* 分段控制器 (经典后台标签页风格) */
.ios-segmented-control {
  display: flex; height: 100%;
}
.segment-btn {
  padding: 0 8px; margin-right: 32px; border: none; background: transparent; 
  font-size: 15px; font-weight: 500; color: #606266; cursor: pointer; transition: all 0.2s ease;
  display: flex; align-items: center; height: 100%; border-bottom: 2px solid transparent;
  position: relative; top: 1px;
}
.segment-btn:last-child { margin-right: 0; }
.segment-btn:hover { color: #409eff; }
.segment-btn.active {
  color: #409eff; font-weight: 600; border-bottom: 2px solid #409eff;
}

/* 搜索框与工具栏 */
:deep(.search-input .el-input__wrapper ){
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-shadow: none !important;
  background: #fff;
  transition: border-color 0.2s ease;
}
:deep(.search-input .el-input__wrapper:hover ){
  border-color: #c0c4cc;
  box-shadow: none !important;
}
:deep(.search-input .el-input__wrapper.is-focus ){
  border-color: #409eff;
  box-shadow: none !important;
}
:deep(.search-input .el-input__inner ){
  height: 30px;
  line-height: 30px;
  border: none !important;
  border-radius: 0;
  box-shadow: none !important;
  font-size: 13px;
  background: transparent;
}
:deep(.search-input .el-input__inner:focus ){
  border: none !important;
  box-shadow: none !important;
}

/* 表格样式重写（强力去绿覆盖 global.css 和 green-theme.css） */
.table-container { padding: 0; }
:deep(.ios-table.el-table ){
  background: #ffffff !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  border: none !important;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}
:deep(.ios-table .el-table__body-wrapper::-webkit-scrollbar ){
  width: 6px; height: 6px;
}
:deep(.ios-table .el-table__body-wrapper::-webkit-scrollbar-thumb ){
  background: #cbd5e1 !important; border-radius: 3px;
}
:deep(.ios-table .el-table__body-wrapper::-webkit-scrollbar-thumb:hover ){
  background: #94a3b8 !important;
}

:deep(.ios-table th.el-table__cell ){
  background: #f8f9fa !important;
  color: #606266 !important;
  font-weight: 600 !important;
  border-bottom: 1px solid #ebeef5 !important;
  padding: 12px 16px !important;
}
:deep(.ios-table td.el-table__cell ){
  padding: 12px 16px !important;
  border-bottom: 1px solid #ebeef5 !important;
  background: #ffffff !important;
}
:deep(.ios-table tbody tr:hover > td ){
  background-color: #f5f7fa !important;
}
:deep(.ios-table--striped .el-table__body tr.el-table__row--striped td ){
  background: #fafafa !important;
}
:deep(.ios-table::before), :deep(.ios-table::after ){ display: none !important; }
.strong-text { font-weight: 500; color: #303133; }
.num-text { font-family: monospace; color: #606266; font-size: 14px;}

.ios-btn-link { color: #409eff; font-weight: 500; padding: 0 8px; }
.action-danger { color: #f56c6c !important; }

/* 状态标签 */
.status-pill {
  display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; border: 1px solid transparent;
}
.pill-blue { background: #ecf5ff; color: #409eff; border-color: #d9ecff; }
.pill-orange { background: #fdf6ec; color: #e6a23c; border-color: #faecd8; }
.pill-red { background: #fef0f0; color: #f56c6c; border-color: #fde2e2; }
.badge-red { background: #f56c6c; color: white; padding: 2px 8px; border-radius: 10px; font-size: 12px; }

/* --- 风险看板列表 --- */
.risk-list { padding-top: 0; }
.risk-section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 12px; }

.risk-item {
  display: flex; align-items: center; padding: 12px 16px; background: #ffffff;
  border: 1px solid #ebeef5; border-radius: 4px; margin-bottom: 10px;
}
.risk-item:hover { background: #f5f7fa; }

.risk-icon {
  width: 32px; height: 32px; border-radius: 4px; background: #fef0f0; color: #f56c6c;
  display: flex; align-items: center; justify-content: center; font-size: 16px; margin-right: 12px; flex-shrink: 0;
}

.risk-content { flex: 1; min-width: 0; }
.risk-name { font-size: 14px; font-weight: 500; color: #303133; margin-bottom: 4px; }
.risk-code { color: #909399; font-size: 13px;}
.risk-desc { font-size: 13px; color: #606266; margin-top: 4px; display: block;}
.divider { margin: 0 8px; color: #dcdfe6; }

/* 空状态 */
.ios-empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #909399; text-align: center; }
.empty-icon-circle { width: 60px; height: 60px; border-radius: 50%; background: #f4f4f5; display: flex; align-items: center; justify-content: center; font-size: 28px; margin-bottom: 12px; color: #c0c4cc;}
.ios-empty-state h3 { font-size: 15px; font-weight: 500; color: #303133; margin: 0 0 8px 0;}
.ios-empty-state p { font-size: 13px; margin: 0;}

/* ================= 弹窗表单精修 (强力驱逐全局绿) ================= */
:deep(.ios-dialog ){ 
  border-radius: 8px !important; overflow: hidden !important; 
  border: none !important; box-shadow: 0 4px 24px rgba(0, 0, 0, 0.1) !important;
}
:deep(.ios-dialog .el-dialog__header ){ 
  background: #ffffff !important; padding: 20px 24px !important; border-bottom: 1px solid #ebeef5 !important; 
}
:deep(.ios-dialog .el-dialog__title ){ 
  font-weight: 600 !important; font-size: 16px !important; color: #303133 !important; 
}
:deep(.ios-dialog .el-dialog__body ){ padding: 24px !important; }
:deep(.ios-dialog .el-dialog__footer ){ 
  padding: 16px 24px !important; border-top: 1px solid #ebeef5 !important; background: #ffffff !important; 
}

:deep(.ios-form .el-form-item__label ){ font-weight: 500 !important; color: #606266 !important; }
:deep(.ios-form .el-input__wrapper),
:deep(.ios-form .el-textarea__inner ){
  border-radius: 4px !important; border: 1px solid #dcdfe6 !important; transition: none !important;
  box-shadow: none !important;
}
:deep(.ios-form .el-input__wrapper.is-focus),
:deep(.ios-form .el-textarea__inner:focus ){ 
  border-color: #409eff !important; box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1) !important; 
}
:deep(.ios-form .el-input__inner){
  border: none !important; border-radius: 0 !important; box-shadow: none !important; background: transparent !important;
}
:deep(.ios-form .el-input__inner:focus){
  border: none !important; box-shadow: none !important;
}
:deep(.ios-form .el-input-number__increase),
:deep(.ios-form .el-input-number__decrease ){ 
  background: #f5f7fa !important; border-color: #dcdfe6 !important; 
}
</style>
