<template>
  <div class="fragment-root">
    <!-- 碎片拼接背景 -->
    <div class="fragments-background">
      <div class="fragments-container">
        <div
          v-for="(fragment, index) in fragments"
          :key="index"
          class="fragment-tile"
          :style="getFragmentStyle(fragment, index)"
        >
        </div>
      </div>
    </div>
    <!-- 渐变融合层 -->
    <div class="gradient-blend"></div>
  </div>
</template>

<script>
import { assetUrl } from '@/utils/assetUrl'

const bgImages = {
  'background2.jpg': assetUrl('background2.jpg')
}

export default {
  name: 'FragmentBackground',
  props: {
    backgroundImage: {
      type: String,
      default: 'background2.jpg'
    }
  },
  data() {
    return {
      gridSize: 16,
      fragments: []
    }
  },
  mounted() {
    this.initFragments()
  },
  methods: {
    initFragments() {
      const fragments = []
      const gridSize = this.gridSize

      for (let row = 0; row < gridSize; row++) {
        for (let col = 0; col < gridSize; col++) {
          const xPercent = gridSize > 1 ? (col / (gridSize - 1)) * 100 : 0
          const yPercent = gridSize > 1 ? (row / (gridSize - 1)) * 100 : 0

          const randomX = (Math.random() - 0.5) * 2000
          const randomY = (Math.random() - 0.5) * 2000
          const randomRotate = (Math.random() - 0.5) * 360
          const randomScale = 0.2 + Math.random() * 0.3

          fragments.push({
            row, col, xPercent, yPercent,
            randomX, randomY, randomRotate, randomScale,
            delay: (row * gridSize + col) * 0.005
          })
        }
      }

      this.fragments = fragments
    },
    getFragmentStyle(fragment, index) {
      const gridSize = this.gridSize
      const size = 100 / gridSize
      const bgSize = gridSize * 100
      const img = bgImages[this.backgroundImage] || bgImages['background2.jpg']

      return {
        backgroundImage: `url(${img})`,
        backgroundSize: `${bgSize}% ${bgSize}%`,
        backgroundPosition: `${fragment.xPercent}% ${fragment.yPercent}%`,
        backgroundRepeat: 'no-repeat',
        left: `${fragment.col * size}%`,
        top: `${fragment.row * size}%`,
        width: `${size}%`,
        height: `${size}%`,
        '--fragment-width': `${size}%`,
        '--fragment-height': `${size}%`,
        '--initial-x': `${fragment.randomX}px`,
        '--initial-y': `${fragment.randomY}px`,
        '--initial-rotate': `${fragment.randomRotate}deg`,
        '--initial-scale': fragment.randomScale,
        '--animation-delay': `${fragment.delay}s`
      }
    }
  }
}
</script>

<style>
/* 碎片背景容器 */
.fragments-background {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 55%;
  left: 0;
  height: 100%;
  z-index: 0;
  overflow: hidden;
  transform: translateZ(0);
}

.fragments-container {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  overflow: hidden;
  transform: translateZ(0);
}

/* 碎片基础样式 */
.fragment-tile {
  position: absolute;
  opacity: 0;
  transform: translate(
    var(--initial-x, 0),
    var(--initial-y, 0)
  ) scale(var(--initial-scale, 0.3)) rotate(var(--initial-rotate, 0deg));
  animation: fragmentAssemble 0.8s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: var(--animation-delay, 0s);
  transform-origin: center center;
  backface-visibility: hidden;
  will-change: transform, opacity;
  margin: -1px;
  padding: 0;
  border: none;
  box-sizing: border-box;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  width: calc(var(--fragment-width) + 2px) !important;
  height: calc(var(--fragment-height) + 2px) !important;
}

@keyframes fragmentAssemble {
  0% {
    opacity: 0;
    transform: translate(
      var(--initial-x, 0),
      var(--initial-y, 0)
    ) scale(var(--initial-scale, 0.2)) rotate(var(--initial-rotate, 0deg));
  }
  40% {
    opacity: 0.7;
    transform: translate(0, 0) scale(1.02) rotate(0deg);
  }
  70% {
    opacity: 0.95;
    transform: translate(0, 0) scale(1.01) rotate(0deg);
  }
  100% {
    opacity: 1;
    transform: translate(0, 0) scale(1) rotate(0deg);
  }
}

/* 渐变融合层 */
.gradient-blend {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 55%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
  background:
    radial-gradient(circle at 20% 30%, rgba(34, 197, 94, 0.03), transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(16, 185, 129, 0.02), transparent 50%),
    linear-gradient(to right, transparent 0%, rgba(255, 255, 255, 0.05) 95%, rgba(255, 255, 255, 0.1) 100%);
  animation: fadeInGradient 0.5s ease-in 1.5s forwards;
  opacity: 0;
}

.fragment-root {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

@keyframes fadeInGradient {
  to { opacity: 1; }
}
</style>
