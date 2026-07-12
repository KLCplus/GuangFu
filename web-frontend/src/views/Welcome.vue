<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

const HERO_VIDEO = 'https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260613_180732_a54afbf6-b30d-470e-861f-669871f09f67.mp4'
const RAINBOW_IMAGE = 'https://soft-zoom-63098134.figma.site/_assets/v11/8d520a7515d06cbfc403d0125e3d05b1a7ccd29c.png'
const CLOUD_IMAGE = 'https://soft-zoom-63098134.figma.site/_assets/v11/0d6dfd3f90b930f21726f2ed56a3320d79b7a797.png'

const quoteRef = ref<HTMLElement>()
const menuOpen = ref(false)
const rafRef = ref<number>()

const current = reactive({
  rainbowY: 120,
  leftX: -200,
  rightX: 200,
  cloudY: 0,
  cloudOpacity: 0
})

const target = reactive({
  rainbowY: 120,
  leftX: -200,
  rightX: 200,
  cloudY: 0,
  cloudOpacity: 0
})

const navLinks = [
  { label: '平台介绍', to: '/welcome' },
  { label: '电站服务', to: '/pvoutput' },
  { label: '联系我们', to: '/api' }
]

function clamp(min: number, max: number, value: number) {
  return Math.min(max, Math.max(min, value))
}

function lerp(currentValue: number, targetValue: number, factor: number) {
  return currentValue + (targetValue - currentValue) * factor
}

function updateTargets() {
  const section = quoteRef.value
  if (!section) return
  const rect = section.getBoundingClientRect()
  const windowHeight = window.innerHeight
  const progress = clamp(0, 1, (windowHeight - rect.top) / (windowHeight + rect.height))
  const enter = clamp(0, 1, (progress - 0.12) / 0.18)
  const exit = clamp(0, 1, (progress - 0.92) / 0.08)
  const cloudIn = enter * (1 - exit)

  target.rainbowY = 120 + (-280 * progress)
  target.leftX = -200 * (1 - cloudIn)
  target.rightX = 200 * (1 - cloudIn)
  target.cloudY = progress * -50
  target.cloudOpacity = cloudIn
}

function animateParallax() {
  updateTargets()
  current.rainbowY = lerp(current.rainbowY, target.rainbowY, 0.06)
  current.leftX = lerp(current.leftX, target.leftX, 0.04)
  current.rightX = lerp(current.rightX, target.rightX, 0.04)
  current.cloudY = lerp(current.cloudY, target.cloudY, 0.04)
  current.cloudOpacity = lerp(current.cloudOpacity, target.cloudOpacity, 0.04)
  rafRef.value = window.requestAnimationFrame(animateParallax)
}

const rainbowStyle = computed(() => ({
  transform: `translate3d(0, ${current.rainbowY}px, 0)`
}))

const leftCloudStyle = computed(() => ({
  marginLeft: '-50%',
  opacity: current.cloudOpacity,
  transform: `translate3d(${current.leftX}px, ${current.cloudY}px, 0)`
}))

const rightCloudStyle = computed(() => ({
  marginRight: '-75%',
  opacity: current.cloudOpacity,
  transform: `translate3d(${current.rightX}px, ${current.cloudY}px, 0) scaleX(-1)`
}))

onMounted(() => {
  rafRef.value = window.requestAnimationFrame(animateParallax)
})

onBeforeUnmount(() => {
  if (rafRef.value) window.cancelAnimationFrame(rafRef.value)
})
</script>

<template>
  <main class="serene-page">
    <nav class="serene-nav">
      <router-link class="serene-logo serene-logo-minimal" to="/welcome" aria-label="光伏智云首页">光伏智云</router-link>

      <div class="serene-nav-center" aria-label="主导航">
        <router-link v-for="link in navLinks" :key="link.label" :to="link.to">{{ link.label }}</router-link>
      </div>

      <router-link class="serene-consult" to="/login">登录平台</router-link>
      <button class="serene-menu-button" :class="{ open: menuOpen }" type="button" aria-label="打开导航" @click="menuOpen = !menuOpen">
        <span></span>
        <span></span>
        <span></span>
      </button>
    </nav>

    <aside class="serene-mobile-panel" :class="{ open: menuOpen }" aria-label="移动端导航">
      <router-link
        v-for="(link, index) in navLinks"
        :key="link.label"
        :to="link.to"
        :style="{ transitionDelay: menuOpen ? `${150 + index * 75}ms` : '0ms' }"
        @click="menuOpen = false"
      >
        {{ link.label }}
      </router-link>
      <router-link class="mobile-consult" to="/login" :style="{ transitionDelay: menuOpen ? '450ms' : '0ms' }" @click="menuOpen = false">
        登录平台
      </router-link>
    </aside>

    <section class="serene-hero">
      <video class="serene-video" :src="HERO_VIDEO" autoplay muted loop playsinline></video>
      <div class="serene-overlay"></div>
      <div class="serene-hero-content">
        <div class="hero-brand-title text-glow" aria-label="GuangFu 光伏智云">
          <span class="font-dancing">GuangFu</span>
          <strong>光伏智云</strong>
        </div>
        <p>以电站数据、天气变化与多模型算法，为光伏运维提供更从容的判断。</p>
        <router-link class="serene-button button-glow" to="/login">登录平台</router-link>
      </div>

      <div class="sound-indicator" aria-hidden="true">
        <div class="sound-circle"><span></span></div>
        <div>
          <span>Power</span>
          <span>with insight</span>
        </div>
      </div>
    </section>

    <section ref="quoteRef" class="quote-section">
      <img class="rainbow-layer" :src="RAINBOW_IMAGE" alt="" :style="rainbowStyle" />
      <img class="cloud-layer left-cloud" :src="CLOUD_IMAGE" alt="" :style="leftCloudStyle" />
      <img class="cloud-layer right-cloud" :src="CLOUD_IMAGE" alt="" :style="rightCloudStyle" />

      <div class="quote-content">
        <p class="quote-main font-instrument">
          “可靠的能源预测，来自对每一处细节的尊重。”
        </p>
        <p class="quote-detail">
          光伏智云把电站功率、气象环境、云图变化和模型表现放在同一个视野里，先理解现场，再给出判断。不追求堆叠功能，只提供能帮助团队看清趋势、安排运维、沉淀报告的长期能力。
        </p>
      </div>

      <div class="quote-signature">
        <span>第10小组</span>
        <strong>来自光伏智云项目组</strong>
      </div>
    </section>
  </main>
</template>

<style scoped>
.serene-page {
  min-height: 100vh;
  overflow-x: hidden;
  background: #0a0608;
  color: #ffffff;
  font-family: 'Inter', sans-serif;
}

.serene-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
}

.serene-logo {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  color: #ffffff;
}

.serene-logo-minimal {
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.14em;
}

.serene-nav-center {
  display: none;
  align-items: center;
  gap: 48px;
}

.serene-nav-center a {
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  letter-spacing: 0.025em;
  transition: color 0.3s ease;
}

.serene-nav-center a:hover {
  color: #ffffff;
}

.serene-consult,
.serene-button,
.mobile-consult {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #ffffff;
  color: #000000;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.025em;
  transition: all 0.3s ease;
}

.serene-consult {
  display: none;
  padding: 12px 26px;
}

.serene-consult:hover,
.serene-button:hover,
.mobile-consult:hover {
  background: rgba(255, 255, 255, 0.9);
}

.serene-menu-button {
  position: relative;
  z-index: 70;
  display: grid;
  gap: 7px;
  width: 42px;
  height: 42px;
  place-content: center;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  cursor: pointer;
}

.serene-menu-button span {
  display: block;
  width: 22px;
  height: 2px;
  border-radius: 999px;
  background: #ffffff;
  transition: transform 0.45s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.3s ease;
}

.serene-menu-button.open span:nth-child(1) {
  transform: translateY(9px) rotate(45deg);
}

.serene-menu-button.open span:nth-child(2) {
  opacity: 0;
  transform: scaleX(0);
}

.serene-menu-button.open span:nth-child(3) {
  transform: translateY(-9px) rotate(-45deg);
}

.serene-mobile-panel {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 60;
  display: flex;
  width: 85%;
  max-width: 340px;
  height: 100vh;
  flex-direction: column;
  gap: 22px;
  padding: 96px 30px 30px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(10, 6, 8, 0.95);
  backdrop-filter: blur(24px);
  transform: translateX(100%);
  transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}

.serene-mobile-panel.open {
  transform: translateX(0);
}

.serene-mobile-panel a {
  color: rgba(255, 255, 255, 0.86);
  font-size: 22px;
  opacity: 0;
  transform: translateX(24px);
  transition: opacity 0.4s ease, transform 0.4s cubic-bezier(0.22, 1, 0.36, 1), background 0.3s ease;
}

.serene-mobile-panel.open a {
  opacity: 1;
  transform: translateX(0);
}

.mobile-consult {
  margin-top: auto;
  padding: 14px 24px;
  color: #000000 !important;
  font-size: 14px !important;
}

.serene-hero,
.quote-section {
  position: relative;
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
}

.serene-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.serene-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
}

.serene-hero-content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  margin-top: -120px;
  text-align: center;
}

.hero-brand-title {
  display: grid;
  justify-items: center;
  gap: 4px;
  color: #ffffff;
}

.hero-brand-title span {
  font-size: clamp(72px, 14vw, 168px);
  font-weight: 700;
  line-height: 0.82;
}

.hero-brand-title strong {
  font-size: clamp(34px, 6vw, 86px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.16em;
  text-indent: 0.16em;
}

.serene-hero-content p {
  max-width: 576px;
  margin-top: 20px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  line-height: 1.7;
}

.serene-button {
  margin-top: 24px;
  padding: 14px 32px;
}

.sound-indicator {
  position: absolute;
  bottom: 32px;
  left: 32px;
  display: none;
  align-items: center;
  gap: 12px;
}

.sound-circle {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
}

.sound-circle span {
  width: 14px;
  height: 2px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
}

.sound-indicator div:last-child {
  display: grid;
  gap: 2px;
}

.sound-indicator div:last-child span {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
}

.quote-section {
  display: grid;
  place-items: center;
  padding: 0 22px;
  background: linear-gradient(to bottom, #010A17 0%, #0A4267 30%, #20658E 60%, #6BADC4 100%);
}

.rainbow-layer {
  position: absolute;
  inset-inline: 0;
  top: 0;
  z-index: 30;
  width: 100%;
  pointer-events: none;
  will-change: transform;
}

.cloud-layer {
  position: absolute;
  z-index: 10;
  display: none;
  width: 500px;
  pointer-events: none;
  will-change: transform, opacity;
}

.left-cloud {
  left: 0;
  bottom: 10%;
}

.right-cloud {
  right: 0;
  bottom: 15%;
}

.quote-content {
  position: relative;
  z-index: 20;
  max-width: 896px;
  text-align: center;
}

.quote-main {
  margin: 0;
  color: #ffffff;
  font-size: 30px;
  line-height: 1.18;
}

.quote-detail {
  max-width: 720px;
  margin: 22px auto 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  line-height: 1.9;
  letter-spacing: 0.02em;
}

.quote-signature {
  position: absolute;
  right: 24px;
  bottom: 28px;
  z-index: 25;
  display: grid;
  gap: 6px;
  text-align: right;
}

.quote-signature span {
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.quote-signature strong {
  color: rgba(255, 255, 255, 0.68);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
}

@media (min-width: 640px) {
  .cloud-layer {
    display: block;
  }

  .quote-main {
    font-size: 40px;
  }
}

@media (min-width: 768px) {
  .serene-nav {
    padding: 20px 48px;
  }

  .serene-nav-center {
    display: flex;
  }

  .serene-consult {
    display: inline-flex;
  }

  .serene-menu-button {
    display: none;
  }

  .serene-hero-content p {
    margin-top: 28px;
    font-size: 16px;
  }

  .serene-button {
    margin-top: 36px;
  }

  .sound-indicator {
    display: flex;
  }

  .cloud-layer {
    width: 650px;
  }

  .quote-main {
    font-size: 52px;
    line-height: 1.22;
  }

  .quote-detail {
    margin-top: 26px;
    font-size: 16px;
  }

  .quote-signature {
    right: 56px;
    bottom: 48px;
  }
}

@media (min-width: 1024px) {
  .quote-main {
    font-size: 64px;
  }
}
</style>
