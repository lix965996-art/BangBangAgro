<template>
  <div class="report-page" v-loading="loading">
    <section class="report-header">
      <div>
        <div class="eyebrow">Data Report Center</div>
        <h1>数据报表中心</h1>
        <p>汇总地块环境、经营收益、库存和作物利润，形成一屏式经营看板。</p>
      </div>
      <div class="header-tools">
        <el-select v-model="selectedFarmId" class="farm-select" size="small" @change="renderCharts">
          <el-option label="全部地块" :value="0" />
          <el-option
            v-for="farm in farmlandData"
            :key="farm.id"
            :label="farm.farm || `地块 ${farm.id}`"
            :value="farm.id"
          />
        </el-select>
        <el-button type="primary" icon="el-icon-refresh" :loading="loading" @click="loadAll">
          刷新数据
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <div v-for="card in metricCards" :key="card.label" class="metric-card">
        <div class="metric-icon" :class="card.tone">
          <i :class="card.icon"></i>
        </div>
        <div>
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">
            {{ card.value }}<span v-if="card.unit">{{ card.unit }}</span>
          </div>
          <div class="metric-note">{{ card.note }}</div>
        </div>
      </div>
    </section>

    <section class="dashboard-grid">
      <div class="panel chart-panel wide-panel">
        <div class="panel-header">
          <div>
            <h2>收益与利润对比</h2>
            <p>{{ selectedFarmName }}经营预测</p>
          </div>
          <el-tag effect="plain" type="success">经营分析</el-tag>
        </div>
        <div ref="revenueChart" class="chart-box"></div>
      </div>

      <div class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2>作物利润占比</h2>
            <p>按作物聚合利润贡献</p>
          </div>
        </div>
        <div ref="cropChart" class="chart-box"></div>
      </div>

      <div class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2>环境健康雷达</h2>
            <p>温湿度、光照与酸碱度评分</p>
          </div>
        </div>
        <div ref="healthChart" class="chart-box"></div>
      </div>

      <div class="panel rank-panel">
        <div class="panel-header">
          <div>
            <h2>利润排行</h2>
            <p>按预计净利润排序</p>
          </div>
        </div>
        <div class="rank-list">
          <div v-for="(row, index) in topProfitRows" :key="row.farmlandId || index" class="rank-item">
            <span class="rank-index">{{ index + 1 }}</span>
            <div class="rank-main">
              <strong>{{ row.farmlandName || '未命名地块' }}</strong>
              <span>{{ row.crop || '未配置作物' }} · {{ row.area || 0 }}亩</span>
            </div>
            <span class="rank-value">{{ formatMoney(row.profit) }}</span>
          </div>
          <el-empty v-if="!topProfitRows.length" description="暂无经营分析数据" :image-size="90" />
        </div>
      </div>

      <div class="panel table-panel wide-panel">
        <div class="panel-header">
          <div>
            <h2>地块经营报表</h2>
            <p>产量、收益、成本和利润明细</p>
          </div>
          <div class="status-line">
            <span>地块 {{ filteredBusinessRows.length }}</span>
            <span>库存预警 {{ inventoryWarningCount }}</span>
          </div>
        </div>
        <el-table :data="filteredBusinessRows" height="360" class="report-table" stripe>
          <el-table-column prop="farmlandName" label="地块" min-width="140" />
          <el-table-column prop="crop" label="作物" width="120" />
          <el-table-column prop="area" label="面积(亩)" width="110" align="right" />
          <el-table-column label="预计产量(kg)" width="140" align="right">
            <template #default="{ row }">{{ formatNumber(row.expectedYield) }}</template>
          </el-table-column>
          <el-table-column label="预计收益" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.expectedRevenue) }}</template>
          </el-table-column>
          <el-table-column label="预计成本" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.expectedCost) }}</template>
          </el-table-column>
          <el-table-column label="预计利润" width="140" align="right">
            <template #default="{ row }">
              <span :class="['profit-text', Number(row.profit || 0) >= 0 ? 'positive' : 'negative']">
                {{ formatMoney(row.profit) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script>
import * as echarts from 'echarts'

const success = (res) => res && (res.code === '200' || res.code === 200)

export default {
  name: 'Role',
  data() {
    return {
      loading: false,
      selectedFarmId: 0,
      summary: {
        totalArea: 0,
        totalStock: 0,
        farmCount: 0,
        normalCount: 0
      },
      farmlandData: [],
      businessRows: [],
      cropProfit: {},
      inventoryRows: [],
      revenueChart: null,
      cropChart: null,
      healthChart: null,
      resizeHandler: null
    }
  },
  computed: {
    filteredBusinessRows() {
      if (!this.selectedFarmId) return this.businessRows
      return this.businessRows.filter(row => Number(row.farmlandId) === Number(this.selectedFarmId))
    },
    selectedFarmName() {
      if (!this.selectedFarmId) return '全部地块'
      const farm = this.farmlandData.find(item => Number(item.id) === Number(this.selectedFarmId))
      return farm ? (farm.farm || `地块 ${farm.id}`) : '当前地块'
    },
    totalArea() {
      return this.summary.totalArea || this.farmlandData.reduce((sum, item) => sum + this.toNumber(item.area), 0)
    },
    expectedRevenue() {
      return this.filteredBusinessRows.reduce((sum, item) => sum + this.toNumber(item.expectedRevenue), 0)
    },
    expectedProfit() {
      return this.filteredBusinessRows.reduce((sum, item) => sum + this.toNumber(item.profit), 0)
    },
    inventoryTotal() {
      const inventoryStock = this.inventoryRows.reduce((sum, item) => sum + this.toNumber(item.number), 0)
      return inventoryStock || this.summary.totalStock || 0
    },
    inventoryWarningCount() {
      return this.inventoryRows.filter(item => {
        const safe = this.toNumber(item.safeStock)
        return safe > 0 && this.toNumber(item.number) < safe
      }).length
    },
    metricCards() {
      const farmCount = this.summary.farmCount || this.farmlandData.length
      const normalCount = this.summary.normalCount || this.farmlandData.filter(item => item.state === '正常').length
      return [
        {
          label: '地块总数',
          value: this.formatNumber(farmCount),
          unit: '块',
          note: `正常 ${normalCount} 块`,
          icon: 'el-icon-map-location',
          tone: 'green'
        },
        {
          label: '总种植面积',
          value: this.formatNumber(this.totalArea),
          unit: '亩',
          note: '来自地块基础档案',
          icon: 'el-icon-crop',
          tone: 'blue'
        },
        {
          label: '预计总收益',
          value: this.formatMoney(this.expectedRevenue),
          unit: '',
          note: '按作物产量模型估算',
          icon: 'el-icon-coin',
          tone: 'amber'
        },
        {
          label: '预计净利润',
          value: this.formatMoney(this.expectedProfit),
          unit: '',
          note: `库存合计 ${this.formatNumber(this.inventoryTotal)}`,
          icon: 'el-icon-data-line',
          tone: 'purple'
        }
      ]
    },
    topProfitRows() {
      return [...this.filteredBusinessRows]
        .sort((a, b) => this.toNumber(b.profit) - this.toNumber(a.profit))
        .slice(0, 5)
    }
  },
  mounted() {
    this.loadAll()
    this.resizeHandler = () => {
      if (this.revenueChart) this.revenueChart.resize()
      if (this.cropChart) this.cropChart.resize()
      if (this.healthChart) this.healthChart.resize()
    }
    window.addEventListener('resize', this.resizeHandler)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.resizeHandler)
    this.disposeCharts()
  },
  methods: {
    async loadAll() {
      this.loading = true
      try {
        const [summaryRes, farmlandRes, businessRes, inventoryRes] = await Promise.allSettled([
          this.request.get('/statistic/dashboard/summary'),
          this.request.get('/statistic/dashboard'),
          this.request.get('/business-analysis/all'),
          this.request.get('/inventory')
        ])

        const summary = summaryRes.status === 'fulfilled' ? summaryRes.value : null
        const farmland = farmlandRes.status === 'fulfilled' ? farmlandRes.value : null
        const business = businessRes.status === 'fulfilled' ? businessRes.value : null
        const inventory = inventoryRes.status === 'fulfilled' ? inventoryRes.value : null

        if (success(summary) && summary.data) {
          this.summary = { ...this.summary, ...summary.data }
        }
        if (success(farmland) && Array.isArray(farmland.data)) {
          this.farmlandData = farmland.data
        }
        if (success(business) && business.data) {
          this.businessRows = business.data.farmlandData || []
          this.cropProfit = business.data.cropProfit || {}
        }
        if (success(inventory) && Array.isArray(inventory.data)) {
          this.inventoryRows = inventory.data
        }

        this.$nextTick(this.renderCharts)
      } catch (error) {
        this.$message.error(error.message || '数据报表加载失败')
      } finally {
        this.loading = false
      }
    },
    renderCharts() {
      this.renderRevenueChart()
      this.renderCropChart()
      this.renderHealthChart()
    },
    renderRevenueChart() {
      const rows = this.filteredBusinessRows
      this.revenueChart = this.ensureChart('revenueChart', this.$refs.revenueChart)
      if (!this.revenueChart) return
      this.revenueChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { top: 0, right: 10, data: ['预计收益', '预计利润'] },
        grid: { top: 46, left: 48, right: 18, bottom: 42 },
        xAxis: {
          type: 'category',
          data: rows.map(item => item.farmlandName || '未命名'),
          axisLabel: { color: '#64748b', interval: 0, rotate: rows.length > 5 ? 25 : 0 }
        },
        yAxis: {
          type: 'value',
          axisLabel: { color: '#64748b', formatter: value => `${value}万` },
          splitLine: { lineStyle: { color: '#e8f0ec' } }
        },
        series: [
          {
            name: '预计收益',
            type: 'bar',
            data: rows.map(item => Number((this.toNumber(item.expectedRevenue) / 10000).toFixed(2))),
            itemStyle: { color: '#16a34a', borderRadius: [6, 6, 0, 0] },
            barMaxWidth: 28
          },
          {
            name: '预计利润',
            type: 'line',
            smooth: true,
            data: rows.map(item => Number((this.toNumber(item.profit) / 10000).toFixed(2))),
            symbolSize: 8,
            lineStyle: { color: '#2563eb', width: 3 },
            itemStyle: { color: '#2563eb' },
            areaStyle: { color: 'rgba(37, 99, 235, 0.08)' }
          }
        ]
      })
    },
    renderCropChart() {
      const data = Object.entries(this.cropProfit || {})
        .map(([name, value]) => ({ name, value: this.toNumber(value) }))
        .filter(item => item.value !== 0)
      this.cropChart = this.ensureChart('cropChart', this.$refs.cropChart)
      if (!this.cropChart) return
      this.cropChart.setOption({
        tooltip: { trigger: 'item', formatter: params => `${params.name}<br/>${this.formatMoney(params.value)} (${params.percent}%)` },
        legend: { bottom: 0, type: 'scroll' },
        series: [
          {
            type: 'pie',
            radius: ['46%', '70%'],
            center: ['50%', '44%'],
            avoidLabelOverlap: true,
            label: { formatter: '{b}' },
            itemStyle: { borderColor: '#fff', borderWidth: 3 },
            data
          }
        ],
        color: ['#16a34a', '#2563eb', '#f59e0b', '#8b5cf6', '#14b8a6', '#ef4444']
      })
    },
    renderHealthChart() {
      const rows = this.selectedFarmId
        ? this.farmlandData.filter(item => Number(item.id) === Number(this.selectedFarmId))
        : this.farmlandData
      const scores = this.getEnvironmentScores(rows)
      this.healthChart = this.ensureChart('healthChart', this.$refs.healthChart)
      if (!this.healthChart) return
      this.healthChart.setOption({
        tooltip: {},
        radar: {
          radius: '66%',
          indicator: [
            { name: '温度', max: 100 },
            { name: '空气湿度', max: 100 },
            { name: '土壤湿度', max: 100 },
            { name: '光照', max: 100 },
            { name: 'PH', max: 100 }
          ],
          splitArea: { areaStyle: { color: ['#f8fafc', '#eef8f1'] } },
          axisName: { color: '#475569' }
        },
        series: [
          {
            type: 'radar',
            data: [{ value: scores, name: '环境评分' }],
            areaStyle: { color: 'rgba(22, 163, 74, 0.18)' },
            lineStyle: { color: '#16a34a', width: 3 },
            itemStyle: { color: '#16a34a' }
          }
        ]
      })
    },
    getEnvironmentScores(rows) {
      const avg = (field, fallback = 0) => {
        const values = rows.map(item => this.toNumber(item[field])).filter(value => value > 0)
        if (!values.length) return fallback
        return values.reduce((sum, value) => sum + value, 0) / values.length
      }
      const temp = avg('temperature', 25)
      const air = avg('airhumidity', 60)
      const soil = avg('soilhumidity', 55)
      const light = avg('light', 600)
      const ph = avg('ph', 6.8)
      return [
        this.clamp(100 - Math.abs(temp - 25) * 5),
        this.clamp(air),
        this.clamp(soil),
        this.clamp(light / 10),
        this.clamp(100 - Math.abs(ph - 6.8) * 18)
      ].map(value => Number(value.toFixed(1)))
    },
    ensureChart(key, el) {
      if (!el) return null
      if (!this[key]) {
        this[key] = echarts.init(el)
      }
      return this[key]
    },
    disposeCharts() {
      ;['revenueChart', 'cropChart', 'healthChart'].forEach(key => {
        if (this[key]) {
          this[key].dispose()
          this[key] = null
        }
      })
    },
    toNumber(value) {
      const num = Number(value)
      return Number.isFinite(num) ? num : 0
    },
    clamp(value) {
      return Math.max(0, Math.min(100, value))
    },
    formatNumber(value) {
      return this.toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    },
    formatMoney(value) {
      return `¥${this.toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
    }
  }
}
</script>

<style scoped>
.report-page {
  min-height: calc(100vh - 90px);
  padding: 24px;
  background: #f4f8f6;
  color: #0f172a;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  border: 1px solid #dbeae2;
  border-radius: 18px;
  background: linear-gradient(135deg, #ffffff 0%, #eef8f2 100%);
  box-shadow: 0 18px 45px rgba(15, 118, 110, 0.08);
}

.eyebrow {
  color: #059669;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.report-header h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #102a1f;
}

.report-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.farm-select {
  width: 180px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 18px 0;
}

.metric-card,
.panel {
  background: #ffffff;
  border: 1px solid #dfece6;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 118px;
  padding: 20px;
  border-radius: 16px;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.metric-icon.green { background: #dcfce7; color: #15803d; }
.metric-icon.blue { background: #dbeafe; color: #1d4ed8; }
.metric-icon.amber { background: #fef3c7; color: #b45309; }
.metric-icon.purple { background: #ede9fe; color: #6d28d9; }

.metric-label,
.metric-note {
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin: 5px 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: #0f172a;
}

.metric-value span {
  margin-left: 4px;
  font-size: 13px;
  color: #64748b;
  font-weight: 700;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(360px, 0.7fr);
  gap: 18px;
}

.panel {
  border-radius: 16px;
  padding: 18px;
  min-width: 0;
}

.wide-panel {
  grid-column: span 1;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  color: #102a1f;
  font-size: 17px;
  font-weight: 800;
}

.panel-header p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 12px;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.rank-panel {
  min-height: 330px;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f8faf9;
  border: 1px solid #e5efe9;
}

.rank-index {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #16a34a;
  color: #fff;
  font-weight: 800;
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-main strong,
.rank-main span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-main strong {
  color: #0f172a;
  font-size: 14px;
}

.rank-main span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.rank-value {
  color: #15803d;
  font-weight: 800;
}

.table-panel {
  grid-column: 1 / -1;
}

.status-line {
  display: flex;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.status-line span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f1f5f9;
}

.report-table {
  border-radius: 12px;
  overflow: hidden;
}

.profit-text {
  font-weight: 800;
}

.profit-text.positive {
  color: #15803d;
}

.profit-text.negative {
  color: #dc2626;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .report-page {
    padding: 14px;
  }

  .report-header,
  .header-tools {
    flex-direction: column;
    align-items: stretch;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .farm-select {
    width: 100%;
  }
}
</style>
