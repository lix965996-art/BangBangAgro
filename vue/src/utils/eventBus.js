import mitt from 'mitt'

// 创建事件总线用于跨组件通信
export const eventBus = mitt()

// 事件类型常量
export const EVENTS = {
  IRRIGATION_ON: 'irrigation:on',
  IRRIGATION_OFF: 'irrigation:off',
  LED_ON: 'led:on',
  LED_OFF: 'led:off',
  DRONE_SPRAY_ON: 'drone:spray:on'
}
