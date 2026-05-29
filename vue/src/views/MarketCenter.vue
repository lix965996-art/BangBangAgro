<template>
  <div class="ios-container" v-loading="salesLoading || onlineLoading">
    <div class="ios-header-bar">
      <div class="header-left">
        <div class="title-box">
          <i class="el-icon-shopping-cart-full icon-blue"></i>
          <h1>产销协同中心</h1>
        </div>
        <p class="subtitle">统一管理销售履约、联销上架与市场业绩转化</p>
      </div>
      <div class="header-right">
        <el-button class="ios-btn-primary" size="small" icon="el-icon-plus" @click="openSalesDialog()">录入订单</el-button>
        <el-button class="ios-btn-secondary" size="small" icon="el-icon-position" @click="openOnlineDialog()">新建上架</el-button>
        <el-button class="ios-btn-secondary" size="small" icon="el-icon-download" @click="exportSales">导出报表</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="metrics-row">
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-blue-light">
            <i class="el-icon-money text-blue"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">累计销售总额</div>
            <div class="metric-value"><span class="unit">¥ </span>{{ formatCurrency(totalSalesAmount).replace('¥','') }}</div>
            <div class="metric-sub">{{ salesList.length }} 笔订单已履约</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-orange-light">
            <i class="el-icon-user text-orange"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">合作客户覆盖</div>
            <div class="metric-value">{{ buyerCount }}<span class="unit"> 家</span></div>
            <div class="metric-sub">主销作物: {{ hotCropName || '暂无数据' }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-cyan-light">
            <i class="el-icon-sell text-cyan"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">在架联销品项</div>
            <div class="metric-value">{{ onShelfCount }}<span class="unit"> 项</span></div>
            <div class="metric-sub">预计总货值 {{ formatCurrency(onShelfValue) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="ios-card metric-card">
          <div class="metric-icon-box bg-purple-light">
            <i class="el-icon-shopping-bag-1 text-purple"></i>
          </div>
          <div class="metric-info">
            <div class="metric-label">待转上架库存</div>
            <div class="metric-value">{{ pendingListings.length }}<span class="unit"> 批</span></div>
            <div class="metric-sub">联销覆盖率 {{ listingCoverageRate }}%</div>
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
                :class="['segment-btn', { active: activeLedger === 'sales' }]" 
                @click="activeLedger = 'sales'">销售订单台账</button>
              <button 
                :class="['segment-btn', { active: activeLedger === 'online' }]" 
                @click="activeLedger = 'online'">联销上架管理</button>
            </div>
            
            <div class="toolbar-search">
              <el-input 
                :placeholder="activeLedger === 'sales' ? '搜索销售作物 / 客户' : '搜索上架作物'" 
                prefix-icon="el-icon-search" 
                v-model="searchKeyword"
                @clear="activeLedger === 'sales' ? loadSales() : loadOnline()"
                @keyup.enter="activeLedger === 'sales' ? loadSales() : loadOnline()"
                class="ios-input search-input"
                size="small"
                clearable>
              </el-input>
            </div>
          </div>

          <div class="card-body table-container">
            <el-table 
              v-if="activeLedger === 'sales'"
              :data="salesList" 
              style="width: 100%" 
              height="100%"
              class="ios-table"
              :header-cell-style="tableHeaderStyle">
              <el-table-column prop="product" label="作物名称" min-width="140">
                <template #default="scope">
                  <span class="strong-text">{{ scope.row.product }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="buyer" label="客户名称" min-width="160"></el-table-column>
              <el-table-column prop="number" label="数量" min-width="120" align="center">
                <template #default="{ row }">
                  <span class="num-text">{{ row.number }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单价" min-width="120" align="right">
                <template #default="{ row }">
                  <span class="num-text">{{ formatCurrency(row.price) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="订单总额" min-width="140" align="right">
                <template #default="{ row }">
                  <span class="strong-text text-blue">{{ formatCurrency(getSalesTotal(row)) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="shipper" label="出货人" min-width="120" align="center"></el-table-column>
              <el-table-column label="操作" width="140" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button link class="ios-btn-link" @click="openSalesDialog(row)">编辑</el-button>
                  <el-button link class="ios-btn-link action-danger" @click="removeSales(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-table 
              v-if="activeLedger === 'online'"
              :data="onlineList" 
              style="width: 100%" 
              height="100%"
              class="ios-table"
              :header-cell-style="tableHeaderStyle">
              <el-table-column prop="produce" label="上架作物" min-width="150">
                <template #default="scope">
                  <span class="strong-text">{{ scope.row.produce }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="warehouse" label="供货仓区" min-width="140"></el-table-column>
              <el-table-column prop="quantity" label="挂牌数量" min-width="120" align="center">
                <template #default="{ row }">
                  <span class="num-text">{{ row.quantity }}</span>
                </template>
              </el-table-column>
              <el-table-column label="预计总货值" min-width="140" align="right">
                <template #default="{ row }">
                  <span class="strong-text text-blue">{{ formatCurrency(row.totalPrice || row.price) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120" align="center">
                <template #default="{ row }">
                  <span :class="['status-pill', getStatusClass(row.status)]">
                    {{ row.status || '待处理' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link class="ios-btn-link" @click="toggleStatus(row)">
                    {{ row.status === '上架中' ? '下架' : '重新上架' }}
                  </el-button>
                  <el-button link class="ios-btn-link" @click="openOnlineDialog(row)">编辑</el-button>
                  <el-button link class="ios-btn-link action-danger" @click="removeOnline(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-col>

      <el-col :span="8" class="flex-col">
        <div class="ios-card flex-1 flex-col" style="margin-bottom: 16px;">
          <div class="card-header border-bottom">
            <div class="header-title">
              <i class="el-icon-medal" style="color: #FF9500;"></i> 优质大客户榜单
            </div>
          </div>
          <div class="card-body scroll-body">
            <div v-if="buyerRanking.length === 0" class="ios-empty-state">
               <div class="empty-icon-circle"><i class="el-icon-user"></i></div>
               <p>暂无客户交易数据</p>
            </div>
            <div v-else class="ranking-list">
              <div class="ranking-item" v-for="(item, index) in buyerRanking" :key="item.name">
                <div class="rank-num" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
                <div class="rank-content">
                  <div class="rank-name">{{ item.name }}</div>
                  <div class="rank-meta">{{ item.count }} 笔交易完成</div>
                </div>
                <div class="rank-amount">{{ formatCurrency(item.total) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="ios-card flex-1 flex-col">
          <div class="card-header border-bottom">
            <div class="header-title">
              <i class="el-icon-data-line" style="color: #FF2D55;"></i> 热销作物表现
            </div>
          </div>
          <div class="card-body scroll-body">
            <div v-if="cropRanking.length === 0" class="ios-empty-state">
               <div class="empty-icon-circle"><i class="el-icon-box"></i></div>
               <p>暂无产品销售数据</p>
            </div>
            <div v-else class="ranking-list">
              <div class="ranking-item" v-for="(item, index) in cropRanking" :key="item.name">
                <div class="rank-content" style="margin-left: 0;">
                  <div class="rank-name">{{ item.name }}</div>
                  <div class="rank-meta">共计售出: <span class="num-text">{{ item.number }}</span></div>
                </div>
                <div class="rank-amount text-blue">{{ formatCurrency(item.total) }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

    </el-row>

    <el-dialog
      :title="salesForm.id ? '编辑销售订单' : '录入销售订单'"
      v-model="salesDialogVisible"
      width="540px"
      :close-on-click-modal="false"
      custom-class="ios-dialog"
    >
      <el-form :model="salesForm" label-width="100px" size="small" class="ios-form">
        <el-form-item label="作物名称">
          <el-input v-model="salesForm.product" placeholder="如：优选番茄" />
        </el-form-item>
        <el-form-item label="销售数量">
          <el-input-number v-model="salesForm.number" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="销售单价">
          <el-input-number v-model="salesForm.price" :min="0" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="salesForm.buyer" placeholder="如：华联超市" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="salesForm.phone" />
        </el-form-item>
        <el-form-item label="交付地址">
          <el-input v-model="salesForm.address" />
        </el-form-item>
        <el-form-item label="出货人">
          <el-input v-model="salesForm.shipper" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="salesForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="ios-btn-secondary" @click="salesDialogVisible = false">取消</el-button>
          <el-button class="ios-btn-primary" @click="saveSales">确定保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      :title="onlineForm.id ? '编辑上架档案' : '新建上架档案'"
      v-model="onlineDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      custom-class="ios-dialog"
    >
      <el-form ref="onlineFormRef" :model="onlineForm" label-width="100px" size="small" class="ios-form">
        <el-form-item label="库存物资">
          <el-select
            v-model="onlineForm.inventoryId"
            placeholder="请选择仓库中已有的待售作物"
            style="width: 100%"
            :disabled="!!onlineForm.id"
            @change="onInventoryChange"
          >
            <el-option
              v-for="item in inventoryList"
              :key="item.id"
              :label="item.produce + '（当前库存：' + (item.number || item.stock || 0) + '）'"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="作物名称">
          <el-input v-model="onlineForm.produce" disabled />
        </el-form-item>
        <el-form-item label="所属仓区">
          <el-input v-model="onlineForm.warehouse" disabled />
        </el-form-item>
        <el-form-item label="上架数量">
          <el-input-number v-model="onlineForm.quantity" :min="1" :max="maxOnlineQuantity || 99999" style="width: 100%" />
          <div v-if="maxOnlineQuantity" style="font-size: 12px; color: #8E8E93; margin-top: 4px;">该批次最多可上架 {{ maxOnlineQuantity }} 件</div>
        </el-form-item>
        <el-form-item label="上架单价">
          <el-input-number v-model="onlineForm.price" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="登记人">
          <el-input v-model="onlineForm.seller" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="onlineForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="ios-btn-secondary" @click="onlineDialogVisible = false">取消</el-button>
          <el-button class="ios-btn-primary" @click="saveOnline">发布上架</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import apiConfig from '@/config/api.config';

// 初始化表单工厂函数
function createSalesForm(user) {
  return { product: '', price: 0, number: 1, buyer: '', address: '', phone: '', shipper: user.nickname || user.username || '', remark: '' }
}

function createOnlineForm(user) {
  return { inventoryId: '', produce: '', warehouse: '', quantity: 1, price: 0, totalPrice: 0, status: '上架中', seller: user.nickname || user.username || '', remark: '' }
}

export default {
  name: 'MarketCenter',
  data() {
    const user = localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')) : {}
    return {
      user, activeLedger: 'sales', salesKeyword: '', onlineKeyword: '',
      salesList: [], onlineList: [], inventoryList: [],
      salesLoading: false, onlineLoading: false,
      salesDialogVisible: false, onlineDialogVisible: false,
      salesForm: createSalesForm(user), onlineForm: createOnlineForm(user),
      maxOnlineQuantity: 0
    }
  },
  computed: {
    searchKeyword: {
      get() { return this.activeLedger === 'sales' ? this.salesKeyword : this.onlineKeyword },
      set(val) { if (this.activeLedger === 'sales') this.salesKeyword = val; else this.onlineKeyword = val }
    },
    // ===== 动态计算核心商业指标 =====
    totalSalesAmount() { return this.salesList.reduce((sum, item) => sum + this.getSalesTotal(item), 0) },
    buyerCount() { return new Set(this.salesList.map(item => (item.buyer || '').trim()).filter(Boolean)).size },
    
    // 优质客户排行榜
    buyerRanking() {
      const bucket = {}
      this.salesList.forEach(item => {
        const name = item.buyer && item.buyer.trim() ? item.buyer.trim() : '零散客户'
        if (!bucket[name]) bucket[name] = { name, count: 0, total: 0 }
        bucket[name].count += 1
        bucket[name].total += this.getSalesTotal(item)
      })
      return Object.values(bucket).sort((a, b) => b.total - a.total).slice(0, 5) // 取前5名
    },
    
    // 热销作物排行榜
    cropRanking() {
      const bucket = {}
      this.salesList.forEach(item => {
        const name = item.product && item.product.trim() ? item.product.trim() : '未分类作物'
        if (!bucket[name]) bucket[name] = { name, count: 0, number: 0, total: 0 }
        bucket[name].count += 1
        bucket[name].number += Number(item.number) || 0
        bucket[name].total += this.getSalesTotal(item)
      })
      return Object.values(bucket).sort((a, b) => b.total - a.total).slice(0, 4) // 取前4名
    },
    hotCropName() { return this.cropRanking.length ? this.cropRanking[0].name : '' },
    
    onShelfCount() { return this.onlineList.filter(item => item.status === '上架中').length },
    onShelfValue() { return this.onlineList.filter(item => item.status === '上架中').reduce((sum, item) => sum + (Number(item.totalPrice) || this.getOnlineTotal(item)), 0) },
    listingCoverageRate() {
      if (!this.inventoryList.length) return 0;
      return Math.round((this.onShelfCount / this.inventoryList.length) * 100)
    },
    pendingListings() {
      const activeIds = new Set(this.onlineList.filter(item => item.status === '上架中').map(item => item.inventoryId))
      return this.inventoryList.filter(item => !activeIds.has(item.id)).sort((a, b) => (Number(b.number) || 0) - (Number(a.number) || 0)).slice(0, 6)
    }
  },
  created() {
    this.refreshAll()
  },
  methods: {
    // ================= 接口请求与数据拉取 =================
    refreshAll() {
      this.loadSales()
      this.loadOnline()
      this.loadInventory()
    },
    async loadSales() {
      this.salesLoading = true
      try {
        const res = await this.request.get('/sales/page', { params: { pageNum: 1, pageSize: 500, product: this.salesKeyword } })
        this.salesList = res.code === '200' ? (res.data.records || res.data || []) : []
      } catch (e) {
        console.error(e)
      } finally { this.salesLoading = false }
    },
    async loadOnline() {
      this.onlineLoading = true
      try {
        const res = await this.request.get('/onlineSale/page', { params: { pageNum: 1, pageSize: 500, produce: this.onlineKeyword } })
        this.onlineList = res.code === '200' ? (res.data.records || res.data || []) : []
      } catch (e) {
        console.error(e)
      } finally { this.onlineLoading = false }
    },
    async loadInventory() {
      try {
        const res = await this.request.get('/inventory/page', { params: { pageNum: 1, pageSize: 1000, produce: '' } })
        this.inventoryList = res.code === '200' ? (res.data.records || res.data || []) : []
      } catch (e) {}
    },

    // ================= 弹窗交互控制 =================
    openSalesDialog(row) {
      this.salesForm = row ? Object.assign(createSalesForm(this.user), JSON.parse(JSON.stringify(row))) : createSalesForm(this.user)
      if (row && !this.salesForm.product) this.salesForm.product = row.itemName || row.name || '';
      if (row && !this.salesForm.buyer) this.salesForm.buyer = row.customer || '';
      this.salesDialogVisible = true
    },
    openOnlineDialog(row) {
      if (row) {
        this.onlineForm = Object.assign(createOnlineForm(this.user), JSON.parse(JSON.stringify(row)))
        this.maxOnlineQuantity = 0
      } else {
        this.onlineForm = createOnlineForm(this.user)
        this.maxOnlineQuantity = 0
      }
      this.onlineDialogVisible = true
    },
    onInventoryChange(inventoryId) {
      const current = this.inventoryList.find(item => item.id === inventoryId)
      if (!current) return
      this.onlineForm.produce = current.produce || current.name
      this.onlineForm.warehouse = current.warehouse
      this.onlineForm.quantity = 1
      this.maxOnlineQuantity = Number(current.number || current.stock || current.quantity) || 0
    },

    // ================= 🔥 防弹级：严格区分 POST 与 PUT =================
    saveSales() {
      const isEdit = !!this.salesForm.id;
      
      this.request.post('/sales', this.salesForm).then(res => {
        if (res.code === '200') {
          this.$message.success(isEdit ? '销售订单已更新' : '销售订单已成功录入')
          this.salesDialogVisible = false
          this.loadSales()
        } else {
          this.$message.error(res.msg || '保存失败')
        }
      })
    },

    saveOnline() {
      const payload = Object.assign({}, this.onlineForm)
      if (!payload.inventoryId && !payload.id) { this.$message.warning('请先选择需要上架的库存物资'); return }
      payload.totalPrice = this.getOnlineTotal(payload)
      if (!payload.status) payload.status = '上架中'
      
      const isEdit = !!payload.id;

      this.request.post('/onlineSale', payload).then(res => {
        if (res.code === '200' || res.code === 200) {
          this.$message.success(isEdit ? '上架档案已更新' : '已成功发布上架')
          this.onlineDialogVisible = false
          this.loadOnline()
        } else {
          this.$message.error(res.msg || '保存失败')
        }
      }).catch(err => {
        const d = err.response && err.response.data
        const msg =
          (d && (d.msg || d.message)) ||
          err.message ||
          '网络或服务异常，请打开浏览器控制台查看详情'
        this.$message.error('发布上架失败：' + msg)
        console.error('POST /onlineSale failed', err)
      })
    },

    // ================= 状态与删除 =================
    toggleStatus(row) {
      const newStatus = row.status === '上架中' ? '已下架' : '上架中'
      this.request.put('/onlineSale/status/' + row.id + '?status=' + newStatus).then(res => {
        if (res.code === '200') {
          this.$message.success(newStatus === '上架中' ? '已重新上架' : '已强制下架')
          this.loadOnline()
        } else {
          this.$message.error(res.msg || '状态更新失败')
        }
      })
    },
    removeSales(row) {
      this.$confirm(`确定删除该笔销售订单吗？`, '删除确认', { type: 'warning' }).then(() => {
        this.request.delete('/sales/' + row.id).then(res => {
          if (res.code === '200') { this.$message.success('删除成功'); this.loadSales() } 
          else { this.$message.error(res.msg || '删除失败') }
        })
      }).catch(err => { console.error('Delete sales order confirmation canceled:', err) })
    },
    removeOnline(row) {
      this.$confirm(`确定删除上架档案吗？`, '删除确认', { type: 'warning' }).then(() => {
        this.request.delete('/onlineSale/' + row.id).then(res => {
          if (res.code === '200') { this.$message.success('删除成功'); this.loadOnline() } 
          else { this.$message.error(res.msg || '删除失败') }
        })
      }).catch(err => { console.error('Delete online listing confirmation canceled:', err) })
    },

    // ================= 辅助工具方法 =================
    exportSales() { window.open(apiConfig.salesExport) },
    getSalesTotal(row) { return (Number(row.price) || 0) * (Number(row.number) || 0) },
    getOnlineTotal(row) { return (Number(row.price) || 0) * (Number(row.quantity) || 0) },
    getStatusClass(status) {
      if (status === '上架中') return 'pill-blue'
      if (status === '已售罄' || status === '已下架') return 'pill-red'
      return 'pill-neutral'
    },
    formatCurrency(value) { return '¥' + (Number(value) || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) },
    tableHeaderStyle() {
      return { backgroundColor: '#F8F9FA', color: '#64748B', fontWeight: '600', fontSize: '13px', borderBottom: '1px solid #E5E5EA' };
    }
  }
}
</script>

<style scoped>
/* ========================================================== */
/* B2B 经典企业级后台风格 - 产销协同中心 (Market Center) */
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
  margin-bottom: 24px;
  overflow: hidden;
}

.card-header {
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  height: 56px;
}
.card-header.border-bottom {
  border-bottom: 1px solid #ebeef5;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
}
.header-title i { font-size: 18px; }

/* 1. 顶部操作栏 */
.ios-header-bar {
  display: flex; justify-content: space-between; align-items: center; 
  background: #ffffff; padding: 24px 32px; margin-bottom: 24px;
  border-radius: 8px; box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); border: 1px solid #ebeef5;
}
.header-left { display: flex; align-items: center; gap: 16px; }
.title-box { display: flex; align-items: center; gap: 10px; }
.title-box h1 { font-size: 20px; font-weight: 600; color: #303133; margin: 0; }
.icon-blue { font-size: 24px; color: #409eff; }
.subtitle { display: none; } /* 顶栏化简 */

.header-right { display: flex; align-items: center; gap: 12px; }
.ios-btn-primary {
  background: #409eff !important; border: transparent !important; color: white !important;
  border-radius: 4px !important; font-weight: 500 !important; padding: 10px 20px !important;
  transition: opacity 0.2s;
}
.ios-btn-primary:active, .ios-btn-primary:hover { opacity: 0.85; transform: none; }
.ios-btn-secondary {
  background: #ffffff !important; border: 1px solid #dcdfe6 !important; color: #606266 !important;
  border-radius: 4px !important; font-weight: 500 !important; padding: 10px 20px !important;
  transition: background 0.2s;
}
.ios-btn-secondary:hover { background: #f5f7fa !important; color: #409eff !important; border-color: #c6e2ff !important; }

/* 2. KPI 卡片 */
.metrics-row { margin-bottom: 0px; }
.metric-card { padding: 20px 24px; display: flex; align-items: center; gap: 16px; border: 1px solid #ebeef5; border-radius: 8px; transition: none; cursor: default;}
.metric-card:hover { transform: none; box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); border-color: #c6e2ff; }
.metric-icon-box {
  width: 48px; height: 48px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center; font-size: 24px; flex-shrink: 0;
}
.bg-blue-light { background: #ecf5ff; } .text-blue { color: #409eff; }
.bg-cyan-light { background: #e0f7fa; } .text-cyan { color: #00bcd4; }
.bg-orange-light { background: #fdf6ec; } .text-orange { color: #e6a23c; }
.bg-purple-light { background: #f4f4f5; } .text-purple { color: #909399; }

.metric-info { display: flex; flex-direction: column; overflow: hidden; justify-content: center; }
.metric-label { font-size: 14px; color: #909399; margin-bottom: 8px;}
.metric-value { font-size: 28px; font-weight: 600; color: #303133; line-height: 1; margin-bottom: 4px; white-space: nowrap;}
.unit { font-size: 14px; font-weight: normal; color: #909399; margin: 0 2px;}
.metric-sub { font-size: 13px; color: #c0c4cc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 4px; }

/* 3. 主体布局 */
.main-layout { height: calc(100vh - 270px); min-height: 550px; }
.card-body { flex: 1; min-height: 0; overflow: hidden;}
.scroll-body { overflow-y: auto; padding: 16px;}

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

/* 搜索框 */
.toolbar-search { width: 260px; }
:deep(.search-input .el-input__inner ){
  border: 1px solid #dcdfe6; border-radius: 4px; height: 32px; line-height: 32px; font-size: 13px; background: #fff; transition: none;
}
:deep(.search-input .el-input__inner:focus ){ border-color: #409eff; box-shadow: none; }

/* 表格样式重写（强力去绿覆盖 global.css） */
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
.pill-red { background: #fef0f0; color: #f56c6c; border-color: #fde2e2; }
.pill-neutral { background: #f4f4f5; color: #909399; border-color: #e9e9eb; }

/* --- 商业排行榜单 --- */
.ranking-list { display: flex; flex-direction: column; gap: 12px; }
.ranking-item {
  display: flex; align-items: center; padding: 12px 16px; background: #ffffff;
  border: 1px solid #ebeef5; border-radius: 4px;
}
.ranking-item:hover { background: #f5f7fa; }

.rank-num {
  width: 28px; height: 28px; border-radius: 4px; background: #f4f4f5; color: #909399;
  display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; margin-right: 14px; flex-shrink: 0;
}
/* 前三名专属样式 */
.rank-1 { background: #fdf6ec; color: #e6a23c; }
.rank-2 { background: #f4f4f5; color: #909399; }
.rank-3 { background: #ecf5ff; color: #409eff; }

.rank-content { flex: 1; min-width: 0; }
.rank-name { font-size: 14px; font-weight: 500; color: #303133; margin-bottom: 4px; }
.rank-meta { font-size: 13px; color: #909399; }

.rank-amount { font-size: 14px; font-weight: 600; color: #303133; font-family: monospace; flex-shrink: 0; text-align: right;}
.text-blue { color: #409eff !important; }

/* 空状态 */
.ios-empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #909399; font-size: 14px; margin-top: 40px;}
.empty-icon-circle { width: 60px; height: 60px; border-radius: 50%; background: #f4f4f5; display: flex; align-items: center; justify-content: center; font-size: 28px; margin-bottom: 12px; color: #c0c4cc;}

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
:deep(.ios-form .el-input__inner),
:deep(.ios-form .el-textarea__inner ){
  border-radius: 4px !important; border: 1px solid #dcdfe6 !important; transition: none !important;
  box-shadow: none !important;
}
:deep(.ios-form .el-input__inner:focus),
:deep(.ios-form .el-textarea__inner:focus ){ 
  border-color: #409eff !important; box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1) !important; 
}
:deep(.ios-form .el-input-number__increase),
:deep(.ios-form .el-input-number__decrease ){ 
  background: #f5f7fa !important; border-color: #dcdfe6 !important; 
}
</style>