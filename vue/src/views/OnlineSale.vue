<template>
  <div class="showcase-page online-board-page">
    <section class="online-board-hero">
      <div class="online-board-hero__intro">
        <div class="online-board-hero__badge">
          <i class="el-icon-sell"></i>
          现货联销
        </div>
        <h2>这页应该像“状态看板”，而不是伪商城首页。</h2>
        <p>
          主舞台改成上架状态板：哪些正在联销、哪些已下架、哪些已经售罄，一眼看清。
          台账和弹窗继续保留，但先讲状态流转，再讲录入动作。
        </p>
        <div class="online-board-hero__tags">
          <span class="online-board-hero__tag">在架 {{ onSaleCount }} 项</span>
          <span class="online-board-hero__tag">预计收入 {{ formatCurrency(totalIncome) }}</span>
          <span class="online-board-hero__tag">可上架库存 {{ inventoryList.length }} 项</span>
        </div>
      </div>

      <div class="online-board-hero__signal">
        <div class="online-board-hero__signal-label">联销覆盖率</div>
        <div class="online-board-hero__signal-value">{{ listingCoverageRate }}%</div>
        <div class="online-board-hero__signal-note">
          在架商品数量占可上架库存样本的比例。
        </div>
      </div>
    </section>

    <section class="online-board-columns">
      <article
        v-for="column in statusColumns"
        :key="column.key"
        class="online-board-column"
      >
        <div class="online-board-column__head">
          <div>
            <div class="online-board-column__title">{{ column.title }}</div>
            <div class="online-board-column__desc">{{ column.desc }}</div>
          </div>
          <span class="online-board-column__count">{{ column.items.length }}</span>
        </div>

        <div class="online-board-card-list" v-if="column.items.length">
          <div
            v-for="item in column.items"
            :key="item.id"
            class="online-board-card"
          >
            <div class="online-board-card__title">{{ item.produce }}</div>
            <div class="online-board-card__meta">{{ item.warehouse || '默认仓区' }} · 数量 {{ item.quantity }}</div>
            <div class="online-board-card__price">{{ formatCurrency(item.totalPrice || item.price) }}</div>
            <div class="online-board-card__actions">
              <el-button link @click="handleEdit(item)">编辑</el-button>
              <el-button link class="online-toggle" @click="toggleStatus(item)">
                {{ item.status === '上架中' ? '下架' : '上架' }}
              </el-button>
            </div>
          </div>
        </div>
        <div v-else class="online-board-empty">当前无记录</div>
      </article>
    </section>

    <section class="online-board-grid">
      <div class="online-board-side">
        <div class="online-board-side__title">待联动物资</div>
        <div class="online-board-side__desc">库存充足但还未进入联销池的物资，适合作为下一步演示样本。</div>

        <div v-if="recommendedListings.length" class="online-board-side__list">
          <div
            v-for="item in recommendedListings"
            :key="item.id"
            class="online-board-side__item"
          >
            <div>
              <div class="online-board-side__item-name">{{ item.produce }}</div>
              <div class="online-board-side__item-meta">{{ item.warehouse || '默认仓区' }} · 库存 {{ item.number }}</div>
            </div>
            <div class="online-board-side__item-flag">待挂接</div>
          </div>
        </div>
        <div v-else class="online-board-empty">当前库存已基本接入联销链路。</div>
      </div>

      <div class="showcase-panel online-board-ledger">
        <div class="showcase-panel__header">
          <div>
            <div class="showcase-panel__title">联销台账</div>
            <div class="showcase-panel__desc">台账保留给管理动作，状态板保留给展示逻辑。</div>
          </div>
        </div>
        <div class="showcase-panel__body">
          <div class="showcase-toolbar">
            <div class="showcase-toolbar__filters">
              <el-input
                v-model="searchProduce"
                clearable
                prefix-icon="el-icon-search"
                placeholder="搜索联销商品"
                @clear="load"
                @keyup.enter="load"
              />
            </div>
            <div class="showcase-toolbar__actions">
              <el-button type="primary" icon="el-icon-plus" @click="handleAdd">新建上架</el-button>
              <el-button plain icon="el-icon-delete" :disabled="!multipleSelection.length" @click="delBatch">批量下架</el-button>
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
            <el-table-column label="联销商品" min-width="180">
              <template #default="scope">
                <div class="online-product">
                  <div class="online-product__name">{{ scope.row.produce }}</div>
                  <div class="online-product__meta">{{ scope.row.remark || '已接入联销状态流' }}</div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="仓区" width="120" align="center">
              <template #default="scope">
                {{ scope.row.warehouse || '默认仓区' }}
              </template>
            </el-table-column>

            <el-table-column prop="quantity" label="数量" width="90" align="center"></el-table-column>

            <el-table-column label="总额" width="110" align="center">
              <template #default="scope">
                <span class="online-amount">{{ formatCurrency(scope.row.totalPrice) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="110" align="center">
              <template #default="scope">
                <span :class="['showcase-status-tag', getStatusClass(scope.row.status)]">
                  {{ scope.row.status || '待处理' }}
                </span>
              </template>
            </el-table-column>

            <el-table-column prop="seller" label="登记人" width="100" align="center"></el-table-column>

            <el-table-column label="操作" width="200" align="center" fixed="right">
              <template #default="scope">
                <el-button link @click="handleEdit(scope.row)">编辑</el-button>
                <el-button link class="online-toggle" @click="toggleStatus(scope.row)">
                  {{ scope.row.status === '上架中' ? '下架' : '上架' }}
                </el-button>
                <el-button link class="online-delete" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </section>

    <el-dialog
      :title="form.id ? '编辑上架档案' : '新建上架档案'"
      v-model="dialogFormVisible"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" label-width="96px" size="small" :model="form" :rules="rules">
        <el-form-item label="库存物资" prop="inventoryId">
          <el-select
            v-model="form.inventoryId"
            placeholder="请选择库存物资"
            style="width: 100%"
            :disabled="!!form.id"
            @change="onInventoryChange"
          >
            <el-option
              v-for="item in inventoryList"
              :key="item.id"
              :label="item.produce + '（库存：' + item.number + '）'"
              :value="item.id"
            >
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="form.produce" disabled></el-input>
        </el-form-item>
        <el-form-item label="所属仓区">
          <el-input v-model="form.warehouse" disabled></el-input>
        </el-form-item>
        <el-form-item label="上架数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" :max="maxQuantity" style="width: 100%"></el-input-number>
          <div class="online-tip" v-if="maxQuantity">当前最多可上架 {{ maxQuantity }}</div>
        </el-form-item>
        <el-form-item label="单价" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="form.remark" placeholder="可填写售卖说明或联动策略"></el-input>
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
  name: 'OnlineSale',
  data() {
    return {
      apiConfig,
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
      searchProduce: '',
      form: {},
      dialogFormVisible: false,
      multipleSelection: [],
      loading: false,
      inventoryList: [],
      maxQuantity: 0,
      rules: {
        inventoryId: [{ required: true, message: '请选择库存物资', trigger: 'change' }],
        quantity: [{ required: true, message: '请输入上架数量', trigger: 'blur' }],
        price: [{ required: true, message: '请输入单价', trigger: 'blur' }]
      }
    }
  },
  computed: {
    onSaleCount() {
      return this.tableData.filter(item => item.status === '上架中').length
    },
    soldOutCount() {
      return this.tableData.filter(item => item.status === '已售罄').length
    },
    offShelfCount() {
      return this.tableData.filter(item => item.status === '已下架').length
    },
    totalIncome() {
      return this.tableData
        .filter(item => item.status === '上架中')
        .reduce((sum, item) => sum + (Number(item.totalPrice) || 0), 0)
    },
    recommendedListings() {
      const activeInventoryIds = new Set(
        this.tableData
          .filter(item => item.status === '上架中')
          .map(item => item.inventoryId)
      )
      return this.inventoryList
        .filter(item => !activeInventoryIds.has(item.id))
        .sort((a, b) => (Number(b.number) || 0) - (Number(a.number) || 0))
        .slice(0, 4)
    },
    listingCoverageRate() {
      if (!this.inventoryList.length) {
        return 0
      }
      return Math.round((this.onSaleCount / this.inventoryList.length) * 100)
    },
    statusColumns() {
      return [
        {
          key: 'on',
          title: '在架池',
          desc: '当前可承接销售的现货',
          items: this.tableData.filter(item => item.status === '上架中')
        },
        {
          key: 'off',
          title: '待复位',
          desc: '已下架，可根据库存再次挂接',
          items: this.tableData.filter(item => item.status === '已下架')
        },
        {
          key: 'sold',
          title: '已清货',
          desc: '已售罄，可转为复盘样本',
          items: this.tableData.filter(item => item.status === '已售罄')
        }
      ]
    }
  },
  created() {
    this.load()
    this.loadInventory()
  },
  methods: {
    load() {
      this.loading = true
      this.request.get('/onlineSale/page', {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          produce: this.searchProduce
        }
      }).then(res => {
        if (res && res.data) {
          this.tableData = res.data.records || []
          this.total = res.data.total || 0
        }
      }).catch(err => { console.error('Failed to load online sales:', err) }).finally(() => {
        this.loading = false
      })
    },
    loadInventory() {
      this.request.get('/inventory/page', {
        params: {
          pageNum: 1,
          pageSize: 1000,
          produce: ''
        }
      }).then(res => {
        if (res && res.data && res.data.records) {
          this.inventoryList = res.data.records.filter(item => Number(item.number) > 0)
        }
      }).catch(err => { console.error('Failed to load inventory:', err) })
    },
    onInventoryChange(inventoryId) {
      const item = this.inventoryList.find(data => data.id === inventoryId)
      if (item) {
        this.form.produce = item.produce
        this.form.warehouse = item.warehouse
        this.maxQuantity = Number(item.number) || 0
        this.form.quantity = 1
      }
    },
    save() {
      this.$refs.formRef.validate(valid => {
        if (!valid) {
          return
        }
        const payload = Object.assign({}, this.form)
        if (!payload.status) {
          payload.status = '上架中'
        }
        this.request.post('/onlineSale', payload).then(res => {
          if (res.code === '200') {
            this.$message.success('操作成功')
            this.dialogFormVisible = false
            this.load()
            this.loadInventory()
          } else {
            this.$message.error(res.msg || '操作失败')
          }
        })
      })
    },
    handleAdd() {
      this.loadInventory()
      this.dialogFormVisible = true
      this.form = {
        status: '上架中'
      }
      this.maxQuantity = 0
    },
    handleEdit(row) {
      this.form = JSON.parse(JSON.stringify(row))
      const item = this.inventoryList.find(data => data.id === row.inventoryId)
      this.maxQuantity = item ? (Number(item.number) || 0) + (Number(row.quantity) || 0) : Number(row.quantity) || 0
      this.dialogFormVisible = true
    },
    toggleStatus(row) {
      const newStatus = row.status === '上架中' ? '已下架' : '上架中'
      this.request.put('/onlineSale/status/' + row.id + '?status=' + newStatus).then(res => {
        if (res.code === '200') {
          this.$message.success(newStatus === '上架中' ? '已上架' : '已下架')
          this.load()
        }
      })
    },
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    delBatch() {
      this.$confirm('确定批量下架选中的商品吗？', '提示', { type: 'warning' }).then(() => {
        const ids = this.multipleSelection.map(v => v.id)
        this.request.post('/onlineSale/del/batch', ids).then(res => {
          if (res.code === '200') {
            this.$message.success('批量下架成功')
            this.load()
          }
        })
      }).catch(err => { console.error('Batch delist confirmation canceled:', err) })
    },
    handleDelete(row) {
      this.$confirm('确定删除商品“' + row.produce + '”吗？删除后将无法恢复。', '删除确认', {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }).then(() => {
        this.request.delete('/onlineSale/' + row.id).then(res => {
          if (res.code === '200') {
            this.$message.success('删除成功')
            this.load()
            this.loadInventory()
          } else {
            this.$message.error(res.msg || '删除失败')
          }
        })
      }).catch(err => { console.error('Delete product confirmation canceled:', err) })
    },
    handleSizeChange(pageSize) {
      this.pageSize = pageSize
      this.load()
    },
    handleCurrentChange(pageNum) {
      this.pageNum = pageNum
      this.load()
    },
    getStatusClass(status) {
      if (status === '上架中') {
        return 'showcase-status-tag--safe'
      }
      if (status === '已售罄') {
        return 'showcase-status-tag--risk'
      }
      return 'showcase-status-tag--warn'
    },
    exp() {
      window.open(this.apiConfig.onlineSaleExport)
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
.online-board-page {
  background:
    radial-gradient(circle at 90% 16%, rgba(31, 138, 99, 0.12), transparent 18%),
    linear-gradient(180deg, #f7fbf8 0%, #edf4ef 100%);
}

.online-board-hero,
.online-board-columns,
.online-board-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 18px;
}

.online-board-hero {
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.7fr);
}

.online-board-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.online-board-grid {
  grid-template-columns: minmax(280px, 0.7fr) minmax(0, 1.3fr);
}

.online-board-hero__intro,
.online-board-hero__signal,
.online-board-column,
.online-board-side {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(31, 138, 99, 0.12);
  border-radius: 30px;
  box-shadow: 0 18px 48px rgba(38, 77, 63, 0.08);
  backdrop-filter: blur(16px);
}

.online-board-hero__intro {
  padding: 30px 32px;
}

.online-board-hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border-radius: 999px;
  background: rgba(31, 138, 99, 0.1);
  color: #1f8a63;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.online-board-hero__intro h2 {
  margin: 18px 0 12px;
  color: #20352d;
  font-size: 34px;
  line-height: 1.2;
}

.online-board-hero__intro p,
.online-board-hero__signal-note,
.online-board-column__desc,
.online-board-empty,
.online-board-side__desc,
.online-board-side__item-meta,
.online-product__meta,
.online-tip {
  color: #6d8077;
  font-size: 13px;
  line-height: 1.8;
}

.online-board-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.online-board-hero__tag {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(244, 248, 244, 0.96);
  color: #345648;
  font-size: 13px;
}

.online-board-hero__signal {
  padding: 26px 24px;
}

.online-board-hero__signal-label {
  color: #7b8f86;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.online-board-hero__signal-value {
  margin-top: 12px;
  color: #1f8a63;
  font-size: 38px;
  font-weight: 700;
}

.online-board-hero__signal-note {
  margin-top: 12px;
}

.online-board-column {
  padding: 20px;
}

.online-board-column__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.online-board-column__title,
.online-board-side__title {
  color: #20352d;
  font-size: 20px;
  font-weight: 700;
}

.online-board-column__count {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: rgba(31, 138, 99, 0.12);
  color: #1f8a63;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.online-board-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.online-board-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(244, 248, 244, 0.94);
}

.online-board-card__title,
.online-board-side__item-name,
.online-product__name {
  color: #20352d;
  font-size: 15px;
  font-weight: 600;
}

.online-board-card__meta {
  margin-top: 4px;
  color: #6d8077;
  font-size: 12px;
}

.online-board-card__price {
  margin-top: 10px;
  color: #1f8a63;
  font-size: 18px;
  font-weight: 700;
}

.online-board-card__actions {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
}

.online-board-side {
  padding: 24px;
}

.online-board-side__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.online-board-side__item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(244, 248, 244, 0.94);
}

.online-board-side__item-flag {
  color: #9f6d2c;
  font-size: 12px;
  font-weight: 600;
}

.online-board-ledger {
  border-radius: 30px;
}

.online-amount {
  color: #1f8a63;
  font-weight: 700;
}

.online-toggle {
  color: #9f6d2c;
}

.online-toggle:hover {
  color: #b07e38;
}

.online-delete {
  color: #c86e52;
}

.online-delete:hover {
  color: #d17b62;
}

@media (max-width: 1280px) {
  .online-board-hero,
  .online-board-columns,
  .online-board-grid {
    grid-template-columns: 1fr;
  }
}
</style>
