<template>
  <div class="showcase-page purchase-network-page">
    <section class="purchase-network-hero">
      <div class="purchase-network-hero__intro">
        <div class="purchase-network-hero__badge">
          <i class="el-icon-shopping-cart-full"></i>
          供应协作网
        </div>
        <h2>采购页讲的不是“填单”，而是“谁在支撑生产补给”。</h2>
        <p>
          这页的主舞台改成供应协作结构。先看头部供应商和补给集中度，再看采购台账。
          这样它更像真实的供应网络，而不是一个普通的进货后台。
        </p>
        <div class="purchase-network-tags">
          <span class="purchase-network-tag">累计采购 {{ formatCurrency(monthlyTotal) }}</span>
          <span class="purchase-network-tag">合作供应商 {{ supplierCount }} 家</span>
          <span class="purchase-network-tag">平均单笔 {{ formatCurrency(averageTicket) }}</span>
        </div>
      </div>

      <div class="purchase-network-hero__focus">
        <div class="purchase-network-hero__label">供应集中度</div>
        <div class="purchase-network-hero__value">{{ dependencyRate }}%</div>
        <div class="purchase-network-hero__note">
          头部供应商 {{ topProviderName }} 占当前采购总额的比重。
        </div>
        <el-progress :percentage="dependencyRate" :stroke-width="10" :show-text="false" color="#c08b43"></el-progress>
      </div>
    </section>

    <section class="purchase-network-grid">
      <div class="purchase-network-panel purchase-network-panel--providers">
        <div class="purchase-network-panel__head">
          <div class="purchase-network-panel__title">供应商梯队</div>
          <div class="purchase-network-panel__desc">谁承接了大部分补给，这一眼就要看出来。</div>
        </div>

        <div v-if="providerRanking.length" class="purchase-provider-ladder">
          <div
            v-for="item in providerRanking"
            :key="item.name"
            class="purchase-provider-ladder__item"
          >
            <div class="purchase-provider-ladder__top">
              <div>
                <div class="purchase-provider-ladder__name">{{ item.name }}</div>
                <div class="purchase-provider-ladder__meta">{{ item.count }} 笔采购</div>
              </div>
              <div class="purchase-provider-ladder__amount">{{ formatCurrency(item.total) }}</div>
            </div>
            <el-progress
              :percentage="getProviderShare(item)"
              :stroke-width="8"
              :show-text="false"
              color="#1f8a63"
            ></el-progress>
          </div>
        </div>
        <div v-else class="purchase-network-empty">暂无采购数据，当前无法形成供应梯队。</div>
      </div>

      <div class="purchase-network-panel purchase-network-panel--materials">
        <div class="purchase-network-panel__head">
          <div class="purchase-network-panel__title">补给重点</div>
          <div class="purchase-network-panel__desc">采购页更适合讲“先保什么”，而不是讲工具栏按钮。</div>
        </div>

        <div v-if="materialRanking.length" class="purchase-material-list">
          <article
            v-for="(item, index) in materialRanking"
            :key="item.name"
            class="purchase-material-card"
          >
            <div class="purchase-material-card__rank">0{{ index + 1 }}</div>
            <div class="purchase-material-card__body">
              <div class="purchase-material-card__name">{{ item.name }}</div>
              <div class="purchase-material-card__meta">采购 {{ item.count }} 次 · 数量 {{ item.number }}</div>
            </div>
            <div class="purchase-material-card__amount">{{ formatCurrency(item.total) }}</div>
          </article>
        </div>
        <div v-else class="purchase-network-empty">暂无物资采购记录，无法形成补给重点排序。</div>

        <div class="purchase-network-note">
          <div class="purchase-network-note__label">当前研判</div>
          <p>{{ heroInsight }}</p>
        </div>
      </div>
    </section>

    <section class="showcase-panel purchase-ledger-panel">
      <div class="showcase-panel__header">
        <div>
          <div class="showcase-panel__title">采购台账</div>
          <div class="showcase-panel__desc">台账继续保留，但它是执行面，不再占据页面叙事中心。</div>
        </div>
      </div>
      <div class="showcase-panel__body">
        <div class="showcase-toolbar">
          <div class="showcase-toolbar__filters">
            <el-input
              v-model="product"
              clearable
              prefix-icon="el-icon-search"
              placeholder="搜索采购物资"
              @clear="load"
              @keyup.enter="load"
            />
          </div>
          <div class="showcase-toolbar__actions">
            <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新建采购单</el-button>
            <el-button plain icon="el-icon-delete" :disabled="!multipleSelection.length" @click="delBatch">批量删除</el-button>
            <el-button plain icon="el-icon-download" @click="exp">导出报表</el-button>
          </div>
        </div>

        <el-table
          :data="tableData"
          v-loading="loading"
          style="width: 100%"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55"></el-table-column>
          <el-table-column label="采购物资" min-width="200">
            <template #default="scope">
              <div class="purchase-product">
                <div class="purchase-product__name">{{ scope.row.product }}</div>
                <div class="purchase-product__meta">{{ scope.row.remark || '已纳入补给链路' }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="供应商" min-width="170">
            <template #default="scope">
              <div class="purchase-provider">{{ scope.row.provider || '待补充' }}</div>
              <div class="purchase-provider__meta">{{ scope.row.phone || '未填写联系电话' }}</div>
            </template>
          </el-table-column>

          <el-table-column prop="number" label="数量" width="90" align="center"></el-table-column>

          <el-table-column label="单价" width="110" align="center">
            <template #default="scope">
              {{ formatCurrency(scope.row.price) }}
            </template>
          </el-table-column>

          <el-table-column label="采购额" width="120" align="center">
            <template #default="scope">
              <span class="purchase-amount">{{ formatCurrency(getRowTotal(scope.row)) }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="purchaser" label="采购人" width="100" align="center"></el-table-column>

          <el-table-column label="时间" width="170" align="center">
            <template #default="scope">
              {{ scope.row.time || scope.row.createTime || '--' }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="scope">
              <el-button link @click="handleEdit(scope.row)">编辑</el-button>
              <el-button link class="purchase-delete" @click="del(scope.row.id)">删除</el-button>
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

    <el-dialog title="采购建档" v-model="dialogFormVisible" width="520px" :close-on-click-modal="false">
      <el-form label-width="96px" size="small" :model="form" class="custom-form">
        <el-form-item label="物资名称">
          <el-input v-model="form.product" autocomplete="off" placeholder="请输入采购物资"></el-input>
        </el-form-item>
        <el-form-item label="采购数量">
          <el-input-number v-model="form.number" :min="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="form.price" :precision="2" :step="0.1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="form.provider"></el-input>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone"></el-input>
        </el-form-item>
        <el-form-item label="联系地址">
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
  name: 'Purchase',
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
    supplierCount() {
      const suppliers = new Set()
      this.tableData.forEach(item => {
        if (item.provider && item.provider.trim()) {
          suppliers.add(item.provider.trim())
        }
      })
      return suppliers.size
    },
    averageTicket() {
      if (!this.tableData.length) {
        return 0
      }
      return this.monthlyTotal / this.tableData.length
    },
    highestOrder() {
      return this.tableData.slice().sort((a, b) => this.getRowTotal(b) - this.getRowTotal(a))[0] || null
    },
    providerRanking() {
      const bucket = {}
      this.tableData.forEach(item => {
        const name = item.provider && item.provider.trim() ? item.provider.trim() : '待补充供应商'
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
    materialRanking() {
      const bucket = {}
      this.tableData.forEach(item => {
        const name = item.product && item.product.trim() ? item.product.trim() : '未命名物资'
        if (!bucket[name]) {
          bucket[name] = { name, count: 0, total: 0, number: 0 }
        }
        bucket[name].count += 1
        bucket[name].total += this.getRowTotal(item)
        bucket[name].number += Number(item.number) || 0
      })
      return Object.values(bucket)
        .sort((a, b) => b.total - a.total)
        .slice(0, 5)
    },
    topProviderName() {
      return this.providerRanking.length ? this.providerRanking[0].name : '暂无'
    },
    dependencyRate() {
      if (!this.providerRanking.length || !this.monthlyTotal) {
        return 0
      }
      return Math.round((this.providerRanking[0].total / this.monthlyTotal) * 100)
    },
    heroInsight() {
      if (!this.total) {
        return '当前还没有采购台账，建议至少录入一组真实或演示数据，让供应网络能被看见。'
      }
      if (this.dependencyRate >= 60) {
        return '当前采购明显依赖头部供应商，适合补充第二供给链，降低到货和价格波动风险。'
      }
      return '当前供应协作相对均衡，可以进一步比较价格、到货稳定性和账期。'
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      this.request.get('/purchase/page', {
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
      }).catch(err => { console.error('Failed to load purchases:', err) }).finally(() => {
        this.loading = false
      })
    },
    save() {
      this.request.post('/purchase', this.form).then(res => {
        if (res.code === '200') {
          this.$message.success('操作成功')
          this.dialogFormVisible = false
          this.load()
        } else {
          this.$message.error('操作失败')
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
      this.request.delete('/purchase/' + id).then(res => {
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
      this.request.post('/purchase/del/batch', ids).then(res => {
        if (res.code === '200') {
          this.$message.success('批量删除成功')
          this.load()
        } else {
          this.$message.error('操作失败')
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
      window.open(this.apiConfig.purchaseExport)
    },
    getRowTotal(row) {
      const price = Number(row.price) || 0
      const number = Number(row.number) || 0
      return price * number
    },
    getProviderShare(item) {
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
.purchase-network-page {
  background:
    radial-gradient(circle at 92% 10%, rgba(192, 139, 67, 0.14), transparent 16%),
    linear-gradient(180deg, #fbfaf6 0%, #f5f1ea 100%);
}

.purchase-network-hero,
.purchase-network-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 18px;
}

.purchase-network-hero {
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.7fr);
}

.purchase-network-grid {
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
}

.purchase-network-hero__intro,
.purchase-network-hero__focus,
.purchase-network-panel {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(192, 139, 67, 0.14);
  border-radius: 30px;
  box-shadow: 0 18px 48px rgba(92, 73, 39, 0.08);
  backdrop-filter: blur(16px);
}

.purchase-network-hero__intro {
  padding: 30px 32px;
}

.purchase-network-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(192, 139, 67, 0.1);
  color: #9f6d2c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.purchase-network-hero__intro h2 {
  margin: 18px 0 12px;
  color: #34281d;
  font-size: 34px;
  line-height: 1.2;
}

.purchase-network-hero__intro p,
.purchase-network-hero__note,
.purchase-network-panel__desc,
.purchase-network-empty,
.purchase-network-note p,
.purchase-provider-ladder__meta,
.purchase-product__meta,
.purchase-provider__meta {
  color: #7d705f;
  font-size: 13px;
  line-height: 1.8;
}

.purchase-network-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.purchase-network-tag {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(247, 244, 238, 0.96);
  color: #5e4a2b;
  font-size: 13px;
}

.purchase-network-hero__focus {
  padding: 26px 24px;
}

.purchase-network-hero__label {
  color: #8b7f6f;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.purchase-network-hero__value {
  margin-top: 12px;
  color: #9f6d2c;
  font-size: 38px;
  font-weight: 700;
}

.purchase-network-hero__note {
  margin: 12px 0 16px;
}

.purchase-network-panel {
  padding: 24px;
}

.purchase-network-panel__head {
  margin-bottom: 18px;
}

.purchase-network-panel__title {
  color: #34281d;
  font-size: 22px;
  font-weight: 700;
}

.purchase-provider-ladder,
.purchase-material-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.purchase-provider-ladder__item,
.purchase-material-card {
  padding: 16px;
  border-radius: 22px;
  background: rgba(248, 244, 237, 0.92);
}

.purchase-provider-ladder__top,
.purchase-material-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.purchase-provider-ladder__name,
.purchase-material-card__name,
.purchase-product__name,
.purchase-provider {
  color: #2f251b;
  font-size: 15px;
  font-weight: 600;
}

.purchase-provider-ladder__amount,
.purchase-material-card__amount {
  color: #9f6d2c;
  font-size: 18px;
  font-weight: 700;
}

.purchase-material-card__rank {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(192, 139, 67, 0.12);
  color: #9f6d2c;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.purchase-material-card__body {
  flex: 1;
}

.purchase-material-card__meta {
  margin-top: 4px;
  color: #7d705f;
  font-size: 12px;
}

.purchase-network-note {
  margin-top: 16px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.68);
}

.purchase-network-note__label {
  color: #8b7f6f;
  font-size: 12px;
}

.purchase-ledger-panel {
  border-radius: 30px;
}

.purchase-amount {
  color: #9f6d2c;
  font-weight: 700;
}

.purchase-delete {
  color: #c86e52;
}

.purchase-delete:hover {
  color: #d17b62;
}

@media (max-width: 1280px) {
  .purchase-network-hero,
  .purchase-network-grid {
    grid-template-columns: 1fr;
  }
}
</style>
