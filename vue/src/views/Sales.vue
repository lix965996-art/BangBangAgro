<template>
  <div class="showcase-page sales-flow-page">
    <section class="sales-flow-hero">
      <div class="sales-flow-hero__intro">
        <div class="sales-flow-hero__badge">
          <i class="el-icon-s-order"></i>
          订单流向
        </div>
        <h2>销售页应该展示订单流向和价值兑现，而不是又一张录入表。</h2>
        <p>
          当前页面围绕“卖给了谁、哪类作物最能打、订单价值是否稳定”来组织。
          订单录入仍然保留，但主舞台先讲流向，再讲台账。
        </p>
        <div class="sales-flow-hero__tags">
          <span class="sales-flow-hero__tag">本期销额 {{ formatCurrency(monthlyTotal) }}</span>
          <span class="sales-flow-hero__tag">客户覆盖 {{ buyerCount }} 个</span>
          <span class="sales-flow-hero__tag">热销作物 {{ hotSalesCrop || '待形成样本' }}</span>
        </div>
      </div>

      <div class="sales-flow-hero__summary">
        <div class="sales-flow-hero__summary-label">流向稳定度</div>
        <div class="sales-flow-hero__summary-value">{{ customerBalanceRate }}%</div>
        <div class="sales-flow-hero__summary-note">
          头部客户占销售总额的比重越低，说明去向结构越均衡。
        </div>
      </div>
    </section>

    <section class="sales-flow-grid">
      <div class="sales-flow-panel sales-flow-panel--channels">
        <div class="sales-flow-panel__head">
          <div class="sales-flow-panel__title">客户去向带</div>
          <div class="sales-flow-panel__desc">这部分回答“卖给了谁”，让页面更像订单流向图。</div>
        </div>

        <div v-if="buyerRanking.length" class="sales-channel-list">
          <article
            v-for="item in buyerRanking"
            :key="item.name"
            class="sales-channel-strip"
          >
            <div class="sales-channel-strip__header">
              <div>
                <div class="sales-channel-strip__name">{{ item.name }}</div>
                <div class="sales-channel-strip__meta">{{ item.count }} 笔订单</div>
              </div>
              <div class="sales-channel-strip__amount">{{ formatCurrency(item.total) }}</div>
            </div>
            <div class="sales-channel-strip__bar">
              <div class="sales-channel-strip__fill" :style="{ width: getBuyerShare(item) + '%' }"></div>
            </div>
          </article>
        </div>
        <div v-else class="sales-flow-empty">暂无销售数据，当前还无法形成订单流向。</div>
      </div>

      <div class="sales-flow-panel sales-flow-panel--crops">
        <div class="sales-flow-panel__head">
          <div class="sales-flow-panel__title">作物收益榜</div>
          <div class="sales-flow-panel__desc">这一块回答“什么最能卖”，与客户流向形成互补。</div>
        </div>

        <div v-if="cropRanking.length" class="sales-crop-grid">
          <article
            v-for="item in cropRanking"
            :key="item.name"
            class="sales-crop-card"
          >
            <div class="sales-crop-card__name">{{ item.name }}</div>
            <div class="sales-crop-card__amount">{{ formatCurrency(item.total) }}</div>
            <div class="sales-crop-card__meta">销量 {{ item.number }} · 订单 {{ item.count }}</div>
          </article>
        </div>
        <div v-else class="sales-flow-empty">暂无作物销售样本。</div>

        <div class="sales-flow-note">
          <div class="sales-flow-note__label">当前研判</div>
          <p>{{ heroInsight }}</p>
        </div>
      </div>
    </section>

    <section class="showcase-panel sales-ledger-panel">
      <div class="showcase-panel__header">
        <div>
          <div class="showcase-panel__title">订单台账</div>
          <div class="showcase-panel__desc">订单台账用于执行和追溯，不再承担整页的展示任务。</div>
        </div>
      </div>
      <div class="showcase-panel__body">
        <div class="showcase-toolbar">
          <div class="showcase-toolbar__filters">
            <el-input
              v-model="product"
              clearable
              prefix-icon="el-icon-search"
              placeholder="搜索销售作物"
              @clear="load"
              @keyup.enter="load"
            />
          </div>
          <div class="showcase-toolbar__actions">
            <el-button type="primary" icon="el-icon-plus" @click="handleAdd">录入订单</el-button>
            <el-button plain icon="el-icon-delete" :disabled="!multipleSelection.length" @click="delBatch">批量删除</el-button>
            <el-button plain icon="el-icon-download" @click="exp">导出月报</el-button>
          </div>
        </div>

        <el-table
          :data="tableData"
          v-loading="loading"
          style="width: 100%"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55"></el-table-column>
          <el-table-column label="销售作物" min-width="200">
            <template #default="scope">
              <div class="sales-product">
                <div class="sales-product__name">{{ scope.row.product }}</div>
                <div class="sales-product__meta">{{ scope.row.remark || '已进入销售履约链路' }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="客户信息" min-width="170">
            <template #default="scope">
              <div class="sales-buyer">{{ scope.row.buyer || '待补充客户' }}</div>
              <div class="sales-buyer__meta">{{ scope.row.phone || '未填写联系电话' }}</div>
            </template>
          </el-table-column>

          <el-table-column prop="number" label="数量" width="90" align="center"></el-table-column>

          <el-table-column label="单价" width="110" align="center">
            <template #default="scope">
              {{ formatCurrency(scope.row.price) }}
            </template>
          </el-table-column>

          <el-table-column label="订单金额" width="120" align="center">
            <template #default="scope">
              <span class="sales-amount">{{ formatCurrency(getRowTotal(scope.row)) }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="shipper" label="出货人" width="100" align="center"></el-table-column>

          <el-table-column label="时间" width="170" align="center">
            <template #default="scope">
              {{ scope.row.time || scope.row.createTime || '--' }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="scope">
              <el-button link @click="handleEdit(scope.row)">编辑</el-button>
              <el-button link class="sales-delete" @click="del(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="showcase-pagination">
          <el-pagination
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            :current-page="pageNum"
            :page-sizes="[10, 20, 50]"
            :page-size="pageSize"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
          >
          </el-pagination>
        </div>
      </div>
    </section>

    <el-dialog title="销售建档" v-model="dialogFormVisible" width="520px" :close-on-click-modal="false">
      <el-form label-width="96px" size="small" :model="form" class="custom-form">
        <el-form-item label="销售作物">
          <el-input v-model="form.product" autocomplete="off" placeholder="请输入销售作物"></el-input>
        </el-form-item>
        <el-form-item label="销售数量">
          <el-input-number v-model="form.number" :min="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="销售单价">
          <el-input-number v-model="form.price" :precision="2" :step="0.1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="form.buyer"></el-input>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="交付地址">
          <el-input v-model="form.address"></el-input>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogFormVisible = false">取消</el-button>
          <el-button type="primary" @click="save">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import apiConfig from '@/config/api.config'

export default {
  name: 'Sales',
  data() {
    return {
      apiConfig,
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      product: '',
      form: {},
      dialogFormVisible: false,
      multipleSelection: [],
      user: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')) : {},
      loading: false
    }
  },
  computed: {
    monthlyTotal() {
      return this.tableData.reduce((sum, item) => sum + this.getRowTotal(item), 0)
    },
    hotSalesCrop() {
      return this.cropRanking.length ? this.cropRanking[0].name : ''
    },
    buyerCount() {
      const buyers = new Set()
      this.tableData.forEach(item => {
        if (item.buyer && item.buyer.trim()) {
          buyers.add(item.buyer.trim())
        }
      })
      return buyers.size
    },
    buyerRanking() {
      const bucket = {}
      this.tableData.forEach(item => {
        const name = item.buyer && item.buyer.trim() ? item.buyer.trim() : '待补充客户'
        if (!bucket[name]) {
          bucket[name] = { name, count: 0, total: 0 }
        }
        bucket[name].count += 1
        bucket[name].total += this.getRowTotal(item)
      })
      return Object.values(bucket)
        .sort((a, b) => b.total - a.total)
        .slice(0, 5)
    },
    cropRanking() {
      const bucket = {}
      this.tableData.forEach(item => {
        const name = item.product && item.product.trim() ? item.product.trim() : '未命名作物'
        if (!bucket[name]) {
          bucket[name] = { name, count: 0, total: 0, number: 0 }
        }
        bucket[name].count += 1
        bucket[name].total += this.getRowTotal(item)
        bucket[name].number += Number(item.number) || 0
      })
      return Object.values(bucket)
        .sort((a, b) => b.total - a.total)
        .slice(0, 4)
    },
    customerBalanceRate() {
      if (!this.buyerRanking.length || !this.monthlyTotal) {
        return 100
      }
      const headShare = this.buyerRanking[0].total / this.monthlyTotal
      return Math.max(0, Math.round((1 - headShare) * 100))
    },
    heroInsight() {
      if (!this.total) {
        return '当前没有销售样本，建议录入至少两组订单，避免这页只剩空壳框架。'
      }
      if (this.buyerRanking.length && this.getBuyerShare(this.buyerRanking[0]) >= 60) {
        return '订单明显集中在头部客户，答辩时更适合强调示范推广，而不是包装成成熟渠道网络。'
      }
      return '当前订单去向相对均衡，可以重点强调从地块产出到订单履约的闭环能力。'
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      this.request.get('/sales/page', {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          product: this.product
        }
      }).then(res => {
        if (res && res.data) {
          this.tableData = res.data.records || []
          this.total = res.data.total || 0
        }
      }).catch(err => { console.error('Failed to load sales:', err) }).finally(() => {
        this.loading = false
      })
    },
    save() {
      this.request.post('/sales', this.form).then(res => {
        if (res.code === '200') {
          this.$message.success('保存成功')
          this.dialogFormVisible = false
          this.load()
        } else {
          this.$message.error('保存失败')
        }
      })
    },
    handleAdd() {
      this.dialogFormVisible = true
      this.form = {}
    },
    handleEdit(row) {
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogFormVisible = true
    },
    del(id) {
      this.request.delete('/sales/' + id).then(res => {
        if (res.code === '200') {
          this.$message.success('删除成功')
          this.load()
        } else {
          this.$message.error('删除失败')
        }
      })
    },
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    delBatch() {
      const ids = this.multipleSelection.map(v => v.id)
      this.request.post('/sales/del/batch', ids).then(res => {
        if (res.code === '200') {
          this.$message.success('批量删除成功')
          this.load()
        } else {
          this.$message.error('批量删除失败')
        }
      })
    },
    handleSizeChange(pageSize) {
      this.pageSize = pageSize
      this.load()
    },
    handleCurrentChange(pageNum) {
      this.pageNum = pageNum
      this.load()
    },
    exp() {
      window.open(this.apiConfig.salesExport)
    },
    getRowTotal(row) {
      const price = Number(row.price) || 0
      const number = Number(row.number) || 0
      return price * number
    },
    getBuyerShare(item) {
      if (!this.monthlyTotal) {
        return 0
      }
      return Math.round((item.total / this.monthlyTotal) * 100)
    },
    formatCurrency(value) {
      return '￥' + (Number(value) || 0).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })
    }
  }
}
</script>

<style scoped>
.sales-flow-page {
  background:
    radial-gradient(circle at 10% 18%, rgba(78, 126, 168, 0.14), transparent 18%),
    linear-gradient(180deg, #f6f8fb 0%, #eef3f7 100%);
}

.sales-flow-hero,
.sales-flow-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 18px;
}

.sales-flow-hero {
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.7fr);
}

.sales-flow-grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
}

.sales-flow-hero__intro,
.sales-flow-hero__summary,
.sales-flow-panel {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(78, 126, 168, 0.14);
  border-radius: 30px;
  box-shadow: 0 18px 48px rgba(56, 86, 118, 0.08);
  backdrop-filter: blur(16px);
}

.sales-flow-hero__intro {
  padding: 30px 32px;
}

.sales-flow-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(78, 126, 168, 0.12);
  color: #4e7ea8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.sales-flow-hero__intro h2 {
  margin: 18px 0 12px;
  color: #213245;
  font-size: 34px;
  line-height: 1.2;
}

.sales-flow-hero__intro p,
.sales-flow-hero__summary-note,
.sales-flow-panel__desc,
.sales-flow-empty,
.sales-flow-note p,
.sales-channel-strip__meta,
.sales-product__meta,
.sales-buyer__meta {
  color: #6c7b89;
  font-size: 13px;
  line-height: 1.8;
}

.sales-flow-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.sales-flow-hero__tag {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(244, 247, 251, 0.96);
  color: #375878;
  font-size: 13px;
}

.sales-flow-hero__summary {
  padding: 26px 24px;
}

.sales-flow-hero__summary-label,
.sales-flow-note__label {
  color: #7b8c9b;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sales-flow-hero__summary-value {
  margin-top: 12px;
  color: #4e7ea8;
  font-size: 38px;
  font-weight: 700;
}

.sales-flow-hero__summary-note {
  margin-top: 12px;
}

.sales-flow-panel {
  padding: 24px;
}

.sales-flow-panel__head {
  margin-bottom: 18px;
}

.sales-flow-panel__title {
  color: #213245;
  font-size: 22px;
  font-weight: 700;
}

.sales-channel-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sales-channel-strip {
  padding: 16px;
  border-radius: 22px;
  background: rgba(243, 247, 250, 0.92);
}

.sales-channel-strip__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sales-channel-strip__name,
.sales-crop-card__name,
.sales-product__name,
.sales-buyer {
  color: #213245;
  font-size: 15px;
  font-weight: 600;
}

.sales-channel-strip__amount,
.sales-crop-card__amount {
  color: #4e7ea8;
  font-size: 18px;
  font-weight: 700;
}

.sales-channel-strip__bar {
  height: 10px;
  margin-top: 14px;
  border-radius: 999px;
  background: rgba(78, 126, 168, 0.12);
  overflow: hidden;
}

.sales-channel-strip__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #4e7ea8, #7da7cc);
}

.sales-crop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.sales-crop-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(244, 248, 252, 0.96), rgba(237, 243, 248, 0.9));
}

.sales-crop-card__meta {
  margin-top: 8px;
  color: #6c7b89;
  font-size: 12px;
}

.sales-flow-note {
  margin-top: 16px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
}

.sales-ledger-panel {
  border-radius: 30px;
}

.sales-amount {
  color: #4e7ea8;
  font-weight: 700;
}

.sales-delete {
  color: #c86e52;
}

.sales-delete:hover {
  color: #d17b62;
}

@media (max-width: 1280px) {
  .sales-flow-hero,
  .sales-flow-grid,
  .sales-crop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
