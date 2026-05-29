/**
 * 内存泄漏检测和修复工具
 */

export class MemoryLeakChecker {
  constructor(componentName) {
    this.componentName = componentName;
    this.timers = new Set();
    this.eventListeners = new Map();
    this.observables = new Set();
    this.intervals = new Set();
    this.timeouts = new Set();
  }

  /**
   * 记录定时器
   */
  registerTimer(timerId, type = 'interval') {
    this.timers.add(timerId);
    if (type === 'interval') {
      this.intervals.add(timerId);
    } else if (type === 'timeout') {
      this.timeouts.add(timerId);
    }
    console.log(`[${this.componentName}] Registered ${type}:`, timerId);
  }

  /**
   * 清理定时器
   */
  clearTimers() {
    let clearedCount = 0;

    this.intervals.forEach(id => {
      if (typeof id === 'number' && !isNaN(id)) {
        clearInterval(id);
        clearedCount++;
      }
    });

    this.timeouts.forEach(id => {
      if (typeof id === 'number' && !isNaN(id)) {
        clearTimeout(id);
        clearedCount++;
      }
    });

    this.timers.clear();
    this.intervals.clear();
    this.timeouts.clear();

    if (clearedCount > 0) {
      console.log(`[${this.componentName}] Cleared ${clearedCount} timers`);
    }
  }

  /**
   * 记录事件监听器
   */
  registerEventListener(target, event, handler) {
    const key = `${event}-${handler.name || 'anonymous'}`;
    if (!this.eventListeners.has(key)) {
      this.eventListeners.set(key, { target, event, handler });
    }
    console.log(`[${this.componentName}] Registered event:`, key);
  }

  /**
   * 清理事件监听器
   */
  clearEventListeners() {
    let clearedCount = 0;

    this.eventListeners.forEach(({ target, event, handler }) => {
      if (target && typeof target.removeEventListener === 'function') {
        try {
          target.removeEventListener(event, handler);
          clearedCount++;
        } catch (e) {
          console.warn(`[${this.componentName}] Failed to remove event listener:`, e);
        }
      }
    });

    this.eventListeners.clear();

    if (clearedCount > 0) {
      console.log(`[${this.componentName}] Cleared ${clearedCount} event listeners`);
    }
  }

  /**
   * 记录可观察对象
   */
  registerObservable(observable) {
    this.observables.add(observable);
    console.log(`[${this.componentName}] Registered observable`);
  }

  /**
   * 清理可观察对象
   */
  clearObservables() {
    let clearedCount = 0;

    this.observables.forEach(observable => {
      if (observable && typeof observable.unsubscribe === 'function') {
        try {
          observable.unsubscribe();
          clearedCount++;
        } catch (e) {
          console.warn(`[${this.componentName}] Failed to unsubscribe:`, e);
        }
      } else if (observable && typeof observable.disconnect === 'function') {
        try {
          observable.disconnect();
          clearedCount++;
        } catch (e) {
          console.warn(`[${this.componentName}] Failed to disconnect:`, e);
        }
      }
    });

    this.observables.clear();

    if (clearedCount > 0) {
      console.log(`[${this.componentName}] Cleared ${clearedCount} observables`);
    }
  }

  /**
   * 清理所有资源
   */
  cleanup() {
    console.log(`[${this.componentName}] Starting cleanup...`);

    this.clearTimers();
    this.clearEventListeners();
    this.clearObservables();

    console.log(`[${this.componentName}] Cleanup completed`);
  }

  /**
   * 检查是否有未清理的资源
   */
  checkLeaks() {
    const leaks = {
      timers: this.timers.size,
      eventListeners: this.eventListeners.size,
      observables: this.observables.size
    };

    if (leaks.timers > 0 || leaks.eventListeners > 0 || leaks.observables > 0) {
      console.warn(`[${this.componentName}] Memory leaks detected:`, leaks);
      return true;
    }

    console.log(`[${this.componentName}] No memory leaks detected`);
    return false;
  }
}

/**
 * 创建内存泄漏检查混入
 */
export const memoryLeakMixin = {
  data() {
    return {
      memoryChecker: null
    };
  },
  created() {
    this.memoryChecker = new MemoryLeakChecker(this.$options.name || 'Unknown');
  },
  beforeUnmount() {
    this.memoryChecker.cleanup();
    this.memoryChecker.checkLeaks();
  }
};

/**
 * Vue 指令：v-memory-leak - 自动清理事件监听器
 */
export const memoryLeakDirective = {
  mounted(el, binding, vnode) {
    const { event, handler } = binding.value;
    const component = vnode.componentInstance;

    if (component && component.memoryChecker) {
      component.memoryChecker.registerEventListener(el, event, handler);
    }

    // 添加清理方法
    el._cleanupMemory = () => {
      if (component && component.memoryChecker) {
        component.memoryChecker.clearEventListeners();
      }
    };
  },
  unmounted(el) {
    if (el._cleanupMemory) {
      el._cleanupMemory();
    }
  }
};