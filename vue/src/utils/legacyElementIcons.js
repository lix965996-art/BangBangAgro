import { h, render } from 'vue'
import {
  Aim,
  ArrowDown,
  ArrowRight,
  Bell,
  Box,
  CameraFilled,
  ChatDotRound,
  ChatDotSquare,
  ChatLineRound,
  Check,
  CircleCheck,
  CircleClose,
  CirclePlus,
  Clock,
  Close,
  Cloudy,
  CollectionTag,
  Connection,
  Cpu,
  Coin,
  Crop,
  DataAnalysis,
  DataBoard,
  DataLine,
  Delete,
  Document,
  DocumentChecked,
  DocumentCopy,
  Download,
  Drizzling,
  Edit,
  EditPen,
  Expand,
  Finished,
  Flag,
  Fold,
  FullScreen,
  Goblet,
  Guide,
  HomeFilled,
  Histogram,
  Key,
  Loading,
  Location,
  LocationInformation,
  Lock,
  MagicStick,
  MapLocation,
  Medal,
  Menu,
  Message,
  MessageBox,
  Microphone,
  Money,
  Monitor,
  More,
  Odometer,
  OfficeBuilding,
  Operation,
  Opportunity,
  Phone,
  Picture,
  PictureRounded,
  PieChart,
  Plus,
  Position,
  Pouring,
  Promotion,
  QuestionFilled,
  Reading,
  Refresh,
  RefreshLeft,
  ScaleToOriginal,
  School,
  Search,
  Sell,
  SetUp,
  Setting,
  Share,
  ShoppingBag,
  ShoppingCartFull,
  Stopwatch,
  SuccessFilled,
  Sunny,
  SwitchButton,
  Ticket,
  Tickets,
  Timer,
  Top,
  Tools,
  TrendCharts,
  Trophy,
  Umbrella,
  Unlock,
  Upload,
  UploadFilled,
  User,
  UserFilled,
  Van,
  VideoCamera,
  VideoCameraFilled,
  VideoPause,
  VideoPlay,
  View,
  Wallet,
  Warning,
  WindPower
} from '@element-plus/icons-vue'

const iconAliases = {
  'el-icon-aim': Aim,
  'el-icon-airplane': Position,
  'el-icon-arrow-down': ArrowDown,
  'el-icon-arrow-right': ArrowRight,
  'el-icon-bell': Bell,
  'el-icon-box': Box,
  'el-icon-camera-solid': CameraFilled,
  'el-icon-chat-dot-round': ChatDotRound,
  'el-icon-chat-dot-square': ChatDotSquare,
  'el-icon-chat-line-round': ChatLineRound,
  'el-icon-check': Check,
  'el-icon-circle-check': CircleCheck,
  'el-icon-circle-plus-outline': CirclePlus,
  'el-icon-close': Close,
  'el-icon-cloudy': Cloudy,
  'el-icon-collection-tag': CollectionTag,
  'el-icon-connection': Connection,
  'el-icon-coin': Coin,
  'el-icon-cpu': Cpu,
  'el-icon-crop': Crop,
  'el-icon-data-analysis': DataAnalysis,
  'el-icon-data-board': DataBoard,
  'el-icon-data-line': DataLine,
  'el-icon-delete': Delete,
  'el-icon-document': Document,
  'el-icon-document-checked': DocumentChecked,
  'el-icon-document-copy': DocumentCopy,
  'el-icon-download': Download,
  'el-icon-edit': Edit,
  'el-icon-edit-outline': EditPen,
  'el-icon-error': CircleClose,
  'el-icon-finished': Finished,
  'el-icon-s-flag': Flag,
  'el-icon-full-screen': FullScreen,
  'el-icon-guide': Guide,
  'el-icon-heavy-rain': Pouring,
  'el-icon-histogram': Histogram,
  'el-icon-inbox': MessageBox,
  'el-icon-key': Key,
  'el-icon-lightbulb': Opportunity,
  'el-icon-light-rain': Drizzling,
  'el-icon-loading': Loading,
  'el-icon-location': Location,
  'el-icon-location-outline': LocationInformation,
  'el-icon-lock': Lock,
  'el-icon-magic-stick': MagicStick,
  'el-icon-map-location': MapLocation,
  'el-icon-medal': Medal,
  'el-icon-menu': Menu,
  'el-icon-message-solid': Message,
  'el-icon-microphone': Microphone,
  'el-icon-monitor': Monitor,
  'el-icon-money': Money,
  'el-icon-more': More,
  'el-icon-more-outline': More,
  'el-icon-odometer': Odometer,
  'el-icon-office-building': OfficeBuilding,
  'el-icon-phone-outline': Phone,
  'el-icon-picture-outline': Picture,
  'el-icon-picture-outline-round': PictureRounded,
  'el-icon-pie-chart': PieChart,
  'el-icon-plus': Plus,
  'el-icon-position': Position,
  'el-icon-question': QuestionFilled,
  'el-icon-reading': Reading,
  'el-icon-refresh': Refresh,
  'el-icon-refresh-left': RefreshLeft,
  'el-icon-robot': Monitor,
  'el-icon-s-battery': ScaleToOriginal,
  'el-icon-school': School,
  'el-icon-s-claim': DocumentChecked,
  'el-icon-s-cooperation': Connection,
  'el-icon-s-data': DataBoard,
  'el-icon-search': Search,
  'el-icon-sell': Sell,
  'el-icon-setting': Setting,
  'el-icon-set-up': SetUp,
  'el-icon-s-fold': Fold,
  'el-icon-share': Share,
  'el-icon-s-home': HomeFilled,
  'el-icon-shopping-bag-1': ShoppingBag,
  'el-icon-shopping-cart-full': ShoppingCartFull,
  'el-icon-s-marketing': TrendCharts,
  'el-icon-s-opportunity': Opportunity,
  'el-icon-s-operation': Operation,
  'el-icon-s-order': Tickets,
  'el-icon-s-promotion': Promotion,
  'el-icon-s-ticket': Ticket,
  'el-icon-s-tools': Tools,
  'el-icon-stopwatch': Stopwatch,
  'el-icon-success': SuccessFilled,
  'el-icon-s-unfold': Expand,
  'el-icon-sunny': Sunny,
  'el-icon-switch-button': SwitchButton,
  'el-icon-thermometer': Odometer,
  'el-icon-tickets': Tickets,
  'el-icon-time': Clock,
  'el-icon-timer': Timer,
  'el-icon-top': Top,
  'el-icon-trophy': Trophy,
  'el-icon-truck': Van,
  'el-icon-umbrella': Umbrella,
  'el-icon-unlock': Unlock,
  'el-icon-upload': Upload,
  'el-icon-upload2': UploadFilled,
  'el-icon-user': User,
  'el-icon-user-solid': UserFilled,
  'el-icon-video-camera': VideoCamera,
  'el-icon-video-camera-solid': VideoCameraFilled,
  'el-icon-video-pause': VideoPause,
  'el-icon-video-play': VideoPlay,
  'el-icon-view': View,
  'el-icon-wallet': Wallet,
  'el-icon-warning': Warning,
  'el-icon-warning-outline': Warning,
  'el-icon-water-cup': Goblet,
  'el-icon-wind-power': WindPower
}

export function installLegacyElementIcons(app) {
  Object.entries(iconAliases).forEach(([legacyName, icon]) => {
    app.component(legacyName, icon)
  })
}

const legacyIconSelector = 'i[class^="el-icon-"], i[class*=" el-icon-"]'
const legacyIconMountedAttr = 'data-legacy-icon-mounted'

function getLegacyIcon(el) {
  return Array.from(el.classList).find((name) => iconAliases[name])
}

function mountLegacyIcon(el) {
  if (!(el instanceof HTMLElement) || el.getAttribute(legacyIconMountedAttr) === 'true') {
    return
  }

  const legacyName = getLegacyIcon(el)
  const Icon = legacyName && iconAliases[legacyName]
  if (!Icon) return

  el.textContent = ''
  render(h(Icon), el)
  el.setAttribute(legacyIconMountedAttr, 'true')
  el.setAttribute('aria-hidden', 'true')
}

function scanLegacyIcons(root = document.body) {
  if (!root) return

  if (root instanceof HTMLElement && root.matches(legacyIconSelector)) {
    mountLegacyIcon(root)
  }

  root.querySelectorAll?.(legacyIconSelector).forEach(mountLegacyIcon)
}

export function mountLegacyElementIconFallbacks() {
  if (typeof window === 'undefined' || !document.body) return

  queueMicrotask(() => scanLegacyIcons())

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === 'attributes') {
        mutation.target.removeAttribute?.(legacyIconMountedAttr)
        scanLegacyIcons(mutation.target)
        return
      }

      mutation.addedNodes.forEach((node) => {
        if (node.nodeType === Node.ELEMENT_NODE) {
          scanLegacyIcons(node)
        }
      })
    })
  })

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['class']
  })
}
