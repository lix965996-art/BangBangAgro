<template>
  <div class="particle-background">
    <canvas ref="particleCanvas"></canvas>
  </div>
</template>

<script>
export default {
  name: 'ParticleBackground',
  data() {
    return {
      canvas: null,
      ctx: null,
      particles: [],
      animationId: null
    };
  },
  mounted() {
    this.initParticles();
  },
  beforeUnmount() {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId);
    }
    window.removeEventListener('resize', this._boundResizeCanvas);
  },
  methods: {
    initParticles() {
      const canvas = this.$refs.particleCanvas;
      if (!canvas) return;
      
      this.canvas = canvas;
      this.ctx = canvas.getContext('2d');
      
      // 设置canvas尺寸为全屏
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      
      // 创建粒子
      const particleCount = 150;
      this.particles = [];
      
      for (let i = 0; i < particleCount; i++) {
        this.particles.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          vx: (Math.random() - 0.5) * 0.8,
          vy: (Math.random() - 0.5) * 0.8,
          radius: Math.random() * 2.5 + 0.5,
          opacity: Math.random() * 0.6 + 0.2,
          hue: Math.random() * 60 + 180 // 蓝色到青色范围
        });
      }
      
      // 开始动画
      this.animate();
      
      // 窗口大小改变时调整canvas
      window.addEventListener('resize', this._boundResizeCanvas = () => this.resizeCanvas());
    },
    
    resizeCanvas() {
      if (!this.canvas) return;
      this.canvas.width = window.innerWidth;
      this.canvas.height = window.innerHeight;
    },
    
    animate() {
      if (!this.ctx || !this.canvas) return;
      
      const ctx = this.ctx;
      const canvas = this.canvas;
      
      // 创建渐变背景
      ctx.fillStyle = 'rgba(15, 32, 39, 0.05)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      
      // 绘制粒子
      this.particles.forEach(particle => {
        // 更新位置
        particle.x += particle.vx;
        particle.y += particle.vy;
        
        // 边界检测（反弹）
        if (particle.x < 0 || particle.x > canvas.width) particle.vx *= -1;
        if (particle.y < 0 || particle.y > canvas.height) particle.vy *= -1;
        
        // 绘制发光效果
        const gradient = ctx.createRadialGradient(
          particle.x, particle.y, 0,
          particle.x, particle.y, particle.radius * 3
        );
        gradient.addColorStop(0, `hsla(${particle.hue}, 100%, 60%, ${particle.opacity})`);
        gradient.addColorStop(0.5, `hsla(${particle.hue}, 100%, 50%, ${particle.opacity * 0.5})`);
        gradient.addColorStop(1, `hsla(${particle.hue}, 100%, 40%, 0)`);
        
        ctx.beginPath();
        ctx.arc(particle.x, particle.y, particle.radius * 3, 0, Math.PI * 2);
        ctx.fillStyle = gradient;
        ctx.fill();
        
        // 绘制粒子核心
        ctx.beginPath();
        ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
        ctx.fillStyle = `hsla(${particle.hue}, 100%, 70%, ${particle.opacity})`;
        ctx.shadowBlur = 10;
        ctx.shadowColor = `hsla(${particle.hue}, 100%, 60%, 0.8)`;
        ctx.fill();
        ctx.shadowBlur = 0;
      });
      
      // 绘制连线（更炫酷的渐变连线）
      for (let i = 0; i < this.particles.length; i++) {
        for (let j = i + 1; j < this.particles.length; j++) {
          const dx = this.particles[i].x - this.particles[j].x;
          const dy = this.particles[i].y - this.particles[j].y;
          const distance = Math.sqrt(dx * dx + dy * dy);
          
          if (distance < 150) {
            const opacity = 0.3 * (1 - distance / 150);
            const gradient = ctx.createLinearGradient(
              this.particles[i].x, this.particles[i].y,
              this.particles[j].x, this.particles[j].y
            );
            gradient.addColorStop(0, `hsla(${this.particles[i].hue}, 100%, 60%, ${opacity})`);
            gradient.addColorStop(1, `hsla(${this.particles[j].hue}, 100%, 60%, ${opacity})`);
            
            ctx.beginPath();
            ctx.moveTo(this.particles[i].x, this.particles[i].y);
            ctx.lineTo(this.particles[j].x, this.particles[j].y);
            ctx.strokeStyle = gradient;
            ctx.lineWidth = 1;
            ctx.stroke();
          }
        }
      }
      
      this.animationId = requestAnimationFrame(() => this.animate());
    }
  }
};
</script>

<style scoped>
.particle-background {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
  background: radial-gradient(ellipse at top, rgba(64, 158, 255, 0.1) 0%, transparent 50%),
              radial-gradient(ellipse at bottom, rgba(100, 181, 246, 0.05) 0%, transparent 50%);
}

.particle-background canvas {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
