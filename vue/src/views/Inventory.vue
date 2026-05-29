<template>
  <div class="showcase-page inventory-radar-page">
    <section class="inventory-radar-hero">
      <div class="inventory-radar-intro">
        <div class="inventory-radar-badge">
          <i class="el-icon-box"></i>
          仓储雷达
        </div>
        <h2>把库存页改成“仓区健康监测”，而不是一张静态仓库表。</h2>
        <p>
          当前仓储页面重点讲三件事：哪个仓区压力最大、哪些物资需要立即补货、温湿度是否会影响保存质量。
          录入、编辑、报废和导出能力继续保留，但主画面先展示仓储判断，再展示台账。
        </p>
        <div class="inventory-radar-tags">
          <span class="inventory-radar-tag">安全达标率 {{ inventoryHealthRate }}%</span>
          <span class="inventory-radar-tag">紧急补货 {{ lowStockCount }} 项</span>
          <span class="inventory-radar-tag">覆盖仓区 {{ warehouseCount }} 处</span>
        </div>
      </div>

      <div class="inventory-radar-climate">
        <div class="inventory-radar-climate__label">仓储气候</div>
        <div class="inventory-radar-climate__state">{{ environmentStatus }}</div>
        <div class="inventory-radar-climate__metrics">
          <div class="inventory-radar-climate__metric">
            <span>仓温</span>
            <strong>{{ warehouseTemp.toFixed(1) }}°C</strong>
          </div>
          <div class="inventory-radar-climate__metric">
            <span>湿度</span>
            <strong>{{ warehouseHumidity.toFixed(0) }}%</strong>
          </div>
        </div>
        <p>{{ heroInsight }}</p>
      </div>
    </section>

    <section class="inventory-radar-grid">
      <div class="inventory-radar-board">
        <div class="inventory-radar-board__head">
          <div>
            <div class="inventory-radar-board__title">仓区热区图</div>
            <div class="inventory-radar-board__desc">先看仓区状态，再看具体物资。这样这页的角色才像调度中枢。</div>
          </div>
          <div class="inventory-radar-summary">
            <div class="inventory-radar-summary__label">当前判断</div>
            <div class="inventory-radar-summary__value">{{ heroHeadline }}</div>
          </div>
        </div>

        <div class="inventory-zone-list">
          <article
            v-for="zone in warehouseSnapshots"
            :key="zone.name"
            class="inventory-zone-card"
          >
            <div class="inventory-zone-card__top">
              <div>
                <div class="inventory-zone-card__name">{{ zone.name }}</div>
                <div class="inventory-zone-card__meta">{{ zone.itemCount }} 类物资 · 当前库存 {{ zone.quantity }}</div>
              </div>
              <span :class="['inventory-zone-card__state', getZoneStateClass(zone)]">
                {{ getZoneStateText(zone) }}
              </span>
            </div>

            <div class="inventory-zone-card__progress">
              <div class="inventory-zone-card__progress-row">
                <span>安全达标率</span>
                <strong>{{ zone.safeRate }}%</strong>
              </div>
              <el-progress :percentage="zone.safeRate" :stroke-width="8" :show-text="false" color="#1f8a63"></el-progress>
            </div>

            <div class="inventory-zone-card__footer">
              <div class="inventory-zone-card__metric">
                <span>预警项</span>
                <strong>{{ zone.riskCount }}</strong>
              </div>
              <div class="inventory-zone-card__metric">
                <span>主保管人</span>
                <strong>{{ zone.keeper }}</strong>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="inventory-priority-panel">
        <div class="inventory-priority-panel__title">补货序列</div>
        <div class="inventory-priority-panel__desc">右侧只保留最值得处理的事项，不再堆一排同质化 KPI。</div>

        <div v-if="restockCandidates.length" class="inventory-priority-list">
          <div
            v-for="(item, index) in restockCandidates"
            :key="item.id"
            class="inventory-priority-item"
          >
            <div class="inventory-priority-item__rank">0{{ index + 1 }}</div>
            <div class="inventory-priority-item__content">
              <div class="inventory-priority-item__title">{{ item.produce }}</div>
              <div class="inventory-priority-item__meta">
                {{ item.warehouse || '默认仓区' }} · 当前 {{ item.number || 0 }} / 安全线 {{ getSafeStock(item) }}
              </div>
            </div>
            <div class="inventory-priority-item__gap">补 {{ getRestockGap(item) }}</div>
          </div>
        </div>
        <div v-else class="inventory-priority-empty">全部物资高于安全线，当前可转入成本优化观察。</div>

        <div class="inventory-priority-note">
          <div class="inventory-priority-note__label">周转观察</div>
          <p>{{ turnoverHint }}</p>
        </div>
      </div>
    </section>

    <section class="showcase-panel inventory-ledger-panel">
      <div class="showcase-panel__header">
        <div>
          <div class="showcase-panel__title">仓储台账</div>
          <div class="showcase-panel__desc">台账退到第二层，用于核验细节和执行动作，不再占整个页面主舞台。</div>
        </div>
      </div>
      <div class="showcase-panel__body">
        <div class="showcase-toolbar">
          <div class="showcase-toolbar__filters">
            <el-input
              v-model="produce"
              clearable
              prefix-icon="el-icon-search"
              placeholder="搜索物资名称"
              @clear="load"
              @keyup.enter="load"
            />
          </div>
          <div class="showcase-toolbar__actions">
            <el-button type="primary" icon="el-icon-plus" @click="handleAdd">入库登记</el-button>
            <el-button plain icon="el-icon-refresh" @click="load">刷新</el-button>
            <el-button plain icon="el-icon-download" @click="exp">导出报表</el-button>
          </div>
        </div>

        <el-table
          :data="tableData"
          v-loading="loading"
          style="width: 100%"
          :empty-text="produce ? '未找到匹配物资' : '暂无库存数据'"
        >
          <el-table-column label="物资信息" min-width="240">
            <template #default="scope">
              <div class="inventory-material">
                <img :src="getItemImage(scope.row.produce)" :alt="scope.row.produce" class="inventory-material__thumb">
                <div>
                  <div class="inventory-material__name">{{ scope.row.produce }}</div>
                  <div class="inventory-material__meta">
                    {{ scope.row.remark || '已完成仓储建档，可联动补货与出库。' }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="仓区" width="120" align="center">
            <template #default="scope">
              {{ scope.row.warehouse || '默认仓区' }}
            </template>
          </el-table-column>

          <el-table-column label="库位" width="130" align="center">
            <template #default="scope">
              {{ scope.row.region || '待补充' }}
            </template>
          </el-table-column>

          <el-table-column label="状态" width="120" align="center">
            <template #default="scope">
              <span :class="['showcase-status-tag', getStockTagClass(scope.row)]">
                {{ getStockText(scope.row) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="当前库存" width="100" align="center">
            <template #default="scope">
              {{ scope.row.number || 0 }}
            </template>
          </el-table-column>

          <el-table-column label="安全线" width="100" align="center">
            <template #default="scope">
              {{ getSafeStock(scope.row) }}
            </template>
          </el-table-column>

          <el-table-column label="覆盖天数" width="110" align="center">
            <template #default="scope">
              {{ getCoverageDays(scope.row) }}
            </template>
          </el-table-column>

          <el-table-column prop="keeper" label="保管员" width="100" align="center"></el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="scope">
              <el-button link @click="handleEdit(scope.row)">编辑</el-button>
              <el-button link class="inventory-delete" @click="del(scope.row.id)">报废</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog
      title="仓储建档"
      v-model="dialogFormVisible"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-width="96px" size="small" :model="form">
        <el-form-item label="物资名称">
          <el-input v-model="form.produce" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item label="所属仓库">
          <el-select v-model="form.warehouse" placeholder="请选择" style="width: 100%">
            <el-option label="1号综合农资库" value="1号仓"></el-option>
            <el-option label="2号设备仓" value="2号仓"></el-option>
            <el-option label="冷链保鲜仓" value="冷链仓"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="存放区域">
          <el-input v-model="form.region" placeholder="例如 A-01 货架"></el-input>
        </el-form-item>
        <el-form-item label="当前库存">
          <el-input-number v-model="form.number" :min="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="form.safeStock" :min="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="最大库存">
          <el-input-number v-model="form.maxStock" :min="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="日均消耗">
          <el-input-number v-model="form.dailyConsumption" :min="0" :precision="1" style="width: 100%"></el-input-number>
        </el-form-item>
        <el-form-item label="保管员">
          <el-input v-model="form.keeper"></el-input>
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
import { assetUrl } from '@/utils/assetUrl'

export default {
  name: 'Inventory',
  data() {
    return {
      apiConfig,
      tableData: [],
      total: 0,
      pageNum: 1,
      pageSize: 12,
      produce: '',
      form: {},
      dialogFormVisible: false,
      user: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')) : {},
      loading: false,
      warehouseTemp: 24,
      warehouseHumidity: 45,
      envTimer: null
    }
  },
  computed: {
    lowStockCount() {
      return this.tableData.filter(item => (item.number || 0) < this.getSafeStock(item)).length
    },
    totalUnits() {
      return this.tableData.reduce((sum, item) => sum + (Number(item.number) || 0), 0)
    },
    warehouseCount() {
      const warehouses = new Set()
      this.tableData.forEach(item => {
        if (item.warehouse) {
          warehouses.add(item.warehouse)
        }
      })
      return warehouses.size || 1
    },
    inventoryHealthRate() {
      if (!this.tableData.length) {
        return 100
      }
      const safeCount = this.tableData.filter(item => (item.number || 0) >= this.getSafeStock(item)).length
      return Math.round((safeCount / this.tableData.length) * 100)
    },
    heroHeadline() {
      if (this.lowStockCount >= 4) {
        return '补货窗口已开启'
      }
      if (this.lowStockCount > 0) {
        return '库存总体可控'
      }
      return '仓储状态稳定'
    },
    heroInsight() {
      if (this.lowStockCount >= 4) {
        return '当前低库存物资较多，建议先保障肥料、药剂和高频器材的补给，避免春耕高峰出现断档。'
      }
      if (this.lowStockCount > 0) {
        return '大部分物资仍处于安全区间，但个别品类接近阈值，适合按缺口大小逐步补齐。'
      }
      return '当前仓储状态较稳，可以把精力从应急补货转到损耗控制与周转优化。'
    },
    environmentStatus() {
      const tempNormal = this.warehouseTemp >= 20 && this.warehouseTemp <= 26
      const humidityNormal = this.warehouseHumidity >= 40 && this.warehouseHumidity <= 60
      if (tempNormal && humidityNormal) {
        return '稳定'
      }
      if (this.warehouseTemp > 30 || this.warehouseHumidity > 70 || this.warehouseHumidity < 35) {
        return '关注'
      }
      return '轻微波动'
    },
    restockCandidates() {
      return this.tableData
        .filter(item => this.getRestockGap(item) > 0)
        .sort((a, b) => this.getRestockGap(b) - this.getRestockGap(a))
        .slice(0, 5)
    },
    turnoverHint() {
      const topItem = this.tableData
        .slice()
        .sort((a, b) => (Number(b.number) || 0) - (Number(a.number) || 0))[0]
      if (!topItem) {
        return '暂无物资周转数据，建议完成首轮仓储建档后再做补给节奏分析。'
      }
      return `${topItem.produce} 当前库存最高，建议复核是否存在积压或采购过量。`
    },
    warehouseSnapshots() {
      const map = {}
      this.tableData.forEach(item => {
        const name = item.warehouse || '默认仓区'
        if (!map[name]) {
          map[name] = {
            name,
            itemCount: 0,
            quantity: 0,
            riskCount: 0,
            safeCount: 0,
            keeper: item.keeper || '待指定'
          }
        }
        map[name].itemCount += 1
        map[name].quantity += Number(item.number) || 0
        if ((item.number || 0) < this.getSafeStock(item)) {
          map[name].riskCount += 1
        } else {
          map[name].safeCount += 1
        }
        if (!map[name].keeper && item.keeper) {
          map[name].keeper = item.keeper
        }
      })
      return Object.values(map).map(zone => ({
        ...zone,
        safeRate: zone.itemCount ? Math.round((zone.safeCount / zone.itemCount) * 100) : 0
      }))
    }
  },
  created() {
    this.load()
    this.startEnvironmentMonitor()
  },
  beforeUnmount() {
    if (this.envTimer) {
      clearInterval(this.envTimer)
    }
  },
  methods: {
    generateMockData() {
      const materials = [
        { name: '复合肥（NPK 15-15-15）', maxStock: 500, current: 450, dailyConsumption: 8.5 },
        { name: '尿素（含氮 46%）', maxStock: 300, current: 180, dailyConsumption: 5.6 },
        { name: '草甘膦除草剂', maxStock: 200, current: 35, dailyConsumption: 2.3 },
        { name: '吡虫啉杀虫剂', maxStock: 150, current: 120, dailyConsumption: 1.2 },
        { name: '锄头（加厚型）', maxStock: 50, current: 8, dailyConsumption: 0.4 },
        { name: '喷雾器（电动）', maxStock: 30, current: 25, dailyConsumption: 0.2 },
        { name: '有机肥（腐熟鸡粪）', maxStock: 400, current: 380, dailyConsumption: 6.5 },
        { name: '水溶肥（速效型）', maxStock: 100, current: 12, dailyConsumption: 3.8 }
      ]

      const warehouses = ['1号仓', '2号仓', '冷链仓']
      const regions = ['A-01', 'A-02', 'B-01', 'B-02', 'C-01']
      const keepers = ['张三', '李四', '王五', '赵六']

      return materials.map((item, index) => ({
        id: index + 1,
        produce: item.name,
        warehouse: warehouses[index % warehouses.length],
        region: regions[index % regions.length] + '货架',
        number: item.current,
        safeStock: Math.round(item.maxStock * 0.2),
        maxStock: item.maxStock,
        dailyConsumption: item.dailyConsumption,
        keeper: keepers[index % keepers.length],
        remark: item.current < item.maxStock * 0.2 ? '建议优先补给' : '库存处于安全区间'
      }))
    },
    load() {
      this.loading = true
      this.request.get('/inventory/page', {
        params: {
          pageNum: this.pageNum,
          pageSize: this.pageSize,
          produce: this.produce
        }
      }).then(res => {
        if (res && res.data && res.data.records) {
          this.tableData = res.data.records.map(item => {
            const maxStock = Number(item.maxStock) || 100
            const safeStock = Number(item.safeStock) || Math.max(10, Math.round(maxStock * 0.2))
            return Object.assign({}, item, {
              maxStock,
              safeStock,
              dailyConsumption: Number(item.dailyConsumption) || 0
            })
          })
          this.total = res.data.total || this.tableData.length
        } else {
          this.loadMockData()
        }
      }).catch(() => {
        this.loadMockData()
      }).finally(() => {
        this.loading = false
      })
    },
    loadMockData() {
      const mockData = this.generateMockData()
      this.tableData = this.produce
        ? mockData.filter(item => item.produce.indexOf(this.produce) !== -1)
        : mockData
      this.total = this.tableData.length
    },
    save() {
      const payload = Object.assign({}, this.form, {
        safeStock: this.form.safeStock || Math.max(10, Math.round((Number(this.form.maxStock) || 100) * 0.2))
      })
      this.request.post('/inventory', payload).then(res => {
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
      this.form = {
        maxStock: 100,
        safeStock: 20,
        dailyConsumption: 0
      }
    },
    handleEdit(row) {
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogFormVisible = true
    },
    del(id) {
      this.$confirm('确认报废此物资？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.request.delete('/inventory/' + id).then(res => {
          if (res.code === '200') {
            this.$message.success('已报废出库')
            this.load()
          } else {
            this.$message.error('操作失败')
          }
        })
      }).catch(err => { console.error('Scrap confirmation canceled:', err) })
    },
    exp() {
      window.open(this.apiConfig.inventoryExport)
    },
    getSafeStock(item) {
      const maxStock = Number(item.maxStock) || 100
      return Number(item.safeStock) || Math.max(10, Math.round(maxStock * 0.2))
    },
    getRestockGap(item) {
      return Math.max(0, this.getSafeStock(item) - (Number(item.number) || 0))
    },
    getStockText(item) {
      const current = Number(item.number) || 0
      const safeStock = this.getSafeStock(item)
      if (current < safeStock) {
        return '需要补货'
      }
      if ((Number(item.number) || 0) / (Number(item.maxStock) || 100) > 0.8) {
        return '储备充足'
      }
      return '状态平稳'
    },
    getStockTagClass(item) {
      const current = Number(item.number) || 0
      const safeStock = this.getSafeStock(item)
      if (current < safeStock) {
        return 'showcase-status-tag--risk'
      }
      if ((Number(item.number) || 0) / (Number(item.maxStock) || 100) > 0.8) {
        return 'showcase-status-tag--safe'
      }
      return 'showcase-status-tag--warn'
    },
    getCoverageDays(item) {
      const dailyConsumption = Number(item.dailyConsumption) || 0
      if (!dailyConsumption) {
        return '待计算'
      }
      return Math.floor((Number(item.number) || 0) / dailyConsumption) + ' 天'
    },
    getZoneStateText(zone) {
      if (zone.riskCount >= 2) {
        return '高压'
      }
      if (zone.riskCount === 1) {
        return '关注'
      }
      return '稳定'
    },
    getZoneStateClass(zone) {
      if (zone.riskCount >= 2) {
        return 'inventory-zone-card__state--danger'
      }
      if (zone.riskCount === 1) {
        return 'inventory-zone-card__state--warn'
      }
      return 'inventory-zone-card__state--safe'
    },
    getItemImage(name) {
      const imageMap = {
        复合肥: assetUrl('moreng.png'),
        尿素: assetUrl('yumi.png'),
        草甘膦: assetUrl('xiaomai.png'),
        吡虫啉: assetUrl('aiye.png'),
        锄头: assetUrl('moreng.png'),
        喷雾器: assetUrl('yumi.png'),
        有机肥: assetUrl('moreng.png'),
        水溶肥: assetUrl('xiaomai.png'),
        地膜: assetUrl('aiye.png'),
        滴灌管: assetUrl('yumi.png'),
        种子: assetUrl('moreng.png'),
        防虫网: assetUrl('aiye.png')
      }

      for (const key in imageMap) {
        if (name && name.indexOf(key) !== -1) {
          return imageMap[key]
        }
      }
      return assetUrl('moreng.png')
    },
    startEnvironmentMonitor() {
      this.envTimer = setInterval(() => {
        this.warehouseTemp = 22 + Math.random() * 6
        this.warehouseHumidity = 40 + Math.random() * 25
      }, 10000)
    }
  }
}
</script>

<style scoped>
.inventory-radar-page {
  background:
    radial-gradient(circle at 8% 10%, rgba(31, 138, 99, 0.12), transparent 18%),
    linear-gradient(180deg, #f6faf5 0%, #eef5ef 100%);
}

.inventory-radar-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr);
  gap: 18px;
  margin-bottom: 18px;
}

.inventory-radar-intro,
.inventory-radar-climate,
.inventory-radar-board,
.inventory-priority-panel {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(31, 138, 99, 0.12);
  box-shadow: 0 18px 48px rgba(38, 77, 63, 0.08);
  backdrop-filter: blur(16px);
}

.inventory-radar-intro {
  padding: 30px 32px;
  border-radius: 34px;
}

.inventory-radar-intro h2 {
  margin: 18px 0 12px;
  color: #1f342c;
  font-size: 34px;
  line-height: 1.2;
}

.inventory-radar-intro p,
.inventory-radar-climate p {
  color: #687b72;
  font-size: 14px;
  line-height: 1.8;
}

.inventory-radar-badge {
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

.inventory-radar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.inventory-radar-tag {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(244, 248, 244, 0.92);
  color: #345648;
  font-size: 13px;
  font-weight: 500;
}

.inventory-radar-climate {
  padding: 26px 24px;
  border-radius: 30px;
}

.inventory-radar-climate__label {
  color: #7b8f86;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.inventory-radar-climate__state {
  margin-top: 12px;
  color: #1f342c;
  font-size: 30px;
  font-weight: 700;
}

.inventory-radar-climate__metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin: 18px 0;
}

.inventory-radar-climate__metric {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(242, 246, 241, 0.92);
}

.inventory-radar-climate__metric span {
  display: block;
  color: #6c7f76;
  font-size: 12px;
}

.inventory-radar-climate__metric strong {
  display: block;
  margin-top: 8px;
  color: #23372f;
  font-size: 24px;
}

.inventory-radar-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.75fr);
  gap: 18px;
  margin-bottom: 18px;
}

.inventory-radar-board {
  padding: 24px;
  border-radius: 30px;
}

.inventory-radar-board__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.inventory-radar-board__title,
.inventory-priority-panel__title {
  color: #1f342c;
  font-size: 22px;
  font-weight: 700;
}

.inventory-radar-board__desc,
.inventory-priority-panel__desc,
.inventory-radar-summary__label {
  color: #6f8078;
  font-size: 13px;
  line-height: 1.7;
}

.inventory-radar-summary {
  min-width: 150px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(244, 248, 244, 0.92);
}

.inventory-radar-summary__value {
  margin-top: 8px;
  color: #1f8a63;
  font-size: 22px;
  font-weight: 700;
}

.inventory-zone-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.inventory-zone-card {
  padding: 18px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(247, 250, 247, 0.96), rgba(239, 245, 240, 0.9));
  border: 1px solid rgba(31, 138, 99, 0.08);
}

.inventory-zone-card__top,
.inventory-zone-card__footer,
.inventory-zone-card__progress-row,
.inventory-priority-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.inventory-zone-card__name {
  color: #20352d;
  font-size: 16px;
  font-weight: 600;
}

.inventory-zone-card__meta {
  margin-top: 4px;
  color: #71847b;
  font-size: 12px;
}

.inventory-zone-card__state {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.inventory-zone-card__state--safe {
  background: rgba(31, 138, 99, 0.12);
  color: #1f8a63;
}

.inventory-zone-card__state--warn {
  background: rgba(192, 139, 67, 0.16);
  color: #9f6d2c;
}

.inventory-zone-card__state--danger {
  background: rgba(200, 110, 82, 0.16);
  color: #c86e52;
}

.inventory-zone-card__progress {
  margin-top: 18px;
}

.inventory-zone-card__progress-row span,
.inventory-zone-card__metric span,
.inventory-priority-note__label {
  color: #71847b;
  font-size: 12px;
}

.inventory-zone-card__progress-row strong,
.inventory-zone-card__metric strong {
  color: #21362e;
  font-size: 14px;
}

.inventory-zone-card__footer {
  margin-top: 18px;
}

.inventory-zone-card__metric {
  flex: 1;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
}

.inventory-priority-panel {
  padding: 24px 22px;
  border-radius: 30px;
}

.inventory-priority-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.inventory-priority-item {
  align-items: center;
  padding: 14px;
  border-radius: 20px;
  background: rgba(244, 248, 244, 0.94);
}

.inventory-priority-item__rank {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(200, 110, 82, 0.12);
  color: #c86e52;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.inventory-priority-item__content {
  flex: 1;
}

.inventory-priority-item__title,
.inventory-material__name {
  color: #20352d;
  font-size: 15px;
  font-weight: 600;
}

.inventory-priority-item__meta,
.inventory-priority-empty,
.inventory-priority-note p,
.inventory-material__meta {
  color: #6d8077;
  font-size: 12px;
  line-height: 1.7;
}

.inventory-priority-item__gap {
  color: #c86e52;
  font-size: 18px;
  font-weight: 700;
}

.inventory-priority-note {
  margin-top: 18px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.7);
}

.inventory-ledger-panel {
  border-radius: 30px;
}

.inventory-material {
  display: flex;
  align-items: center;
  gap: 12px;
}

.inventory-material__thumb {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  object-fit: cover;
  box-shadow: 0 10px 18px rgba(31, 138, 99, 0.12);
  background: rgba(31, 138, 99, 0.08);
}

.inventory-delete {
  color: #c86e52;
}

.inventory-delete:hover {
  color: #d17b62;
}

@media (max-width: 1280px) {
  .inventory-radar-hero,
  .inventory-radar-grid {
    grid-template-columns: 1fr;
  }

  .inventory-zone-list {
    grid-template-columns: 1fr;
  }
}
</style>
