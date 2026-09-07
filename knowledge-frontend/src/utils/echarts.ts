/**
 * @since: 2026-08-02
 * @author: lxcechoo@gmail.com
 */

/**
 * ECharts 模块化注册（vue-echarts v8 + echarts v6）。
 * <p>按需注册渲染器 / 图表 / 组件，避免引入全量包；各运维看板页面统一从此处导入 VChart。
 */
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent
])

export default VChart
