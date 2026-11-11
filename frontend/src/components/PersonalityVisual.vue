<script setup lang="ts">
import { computed, ref } from 'vue'

defineProps<{ compact?: boolean }>()
const selected = ref('INTJ')
const types = [
  {
    code: 'INTJ',
    title: '建筑师',
    group: 'NT',
    traits: '独立思考 · 系统规划 · 追问原因',
    prompt: '先看清全局，再找到自己的路径。',
  },
  {
    code: 'INTP',
    title: '逻辑学家',
    group: 'NT',
    traits: '分析问题 · 探索原理 · 保持好奇',
    prompt: '每一个问题，都可以再往深处想一步。',
  },
  {
    code: 'ENTJ',
    title: '指挥官',
    group: 'NT',
    traits: '组织资源 · 明确目标 · 推动行动',
    prompt: '把想法组织起来，让下一步更清晰。',
  },
  {
    code: 'ENTP',
    title: '辩论家',
    group: 'NT',
    traits: '联想创新 · 讨论观点 · 挑战假设',
    prompt: '换一种问法，也许就有新的可能。',
  },
  {
    code: 'INFJ',
    title: '提倡者',
    group: 'NF',
    traits: '理解意义 · 洞察他人 · 长远思考',
    prompt: '在真实的连接中，找到值得投入的方向。',
  },
  {
    code: 'INFP',
    title: '调停者',
    group: 'NF',
    traits: '重视价值 · 体察情绪 · 自由表达',
    prompt: '保留内心的声音，也给世界一个回应。',
  },
  {
    code: 'ENFJ',
    title: '主人公',
    group: 'NF',
    traits: '关注他人 · 建立共识 · 鼓励成长',
    prompt: '看见每个人，也看见一起前进的可能。',
  },
  {
    code: 'ENFP',
    title: '竞选者',
    group: 'NF',
    traits: '连接想法 · 热情探索 · 发现可能',
    prompt: '让好奇发生，让新的连接自然生长。',
  },
  {
    code: 'ISTJ',
    title: '物流师',
    group: 'SJ',
    traits: '认真负责 · 尊重事实 · 有序推进',
    prompt: '用扎实的每一步，让承诺落到实处。',
  },
  {
    code: 'ISFJ',
    title: '守卫者',
    group: 'SJ',
    traits: '细致观察 · 体贴支持 · 稳定投入',
    prompt: '那些被照顾到的细节，同样有分量。',
  },
  {
    code: 'ESTJ',
    title: '总经理',
    group: 'SJ',
    traits: '执行计划 · 清晰沟通 · 讲求秩序',
    prompt: '把事情理清楚，让团队走得更稳。',
  },
  {
    code: 'ESFJ',
    title: '执政官',
    group: 'SJ',
    traits: '协调关系 · 热心参与 · 务实合作',
    prompt: '在彼此支持的日常里，让协作更有温度。',
  },
  {
    code: 'ISTP',
    title: '鉴赏家',
    group: 'SP',
    traits: '动手实践 · 冷静观察 · 灵活应变',
    prompt: '亲手试一试，让问题有具体的答案。',
  },
  {
    code: 'ISFP',
    title: '探险家',
    group: 'SP',
    traits: '感受当下 · 保持真诚 · 自在表达',
    prompt: '用自己的方式，回应眼前的生活。',
  },
  {
    code: 'ESTP',
    title: '企业家',
    group: 'SP',
    traits: '敏锐观察 · 及时行动 · 实际体验',
    prompt: '走进真实情境，在行动中找到方向。',
  },
  {
    code: 'ESFP',
    title: '表演者',
    group: 'SP',
    traits: '乐于互动 · 体验生活 · 即兴表达',
    prompt: '感受此刻，也把活力带给身边的人。',
  },
]
const personalityImages = import.meta.glob<string>('../../MBTI/*.jpg', {
  eager: true,
  query: '?url',
  import: 'default',
})
const active = computed(() => types.find((type) => type.code === selected.value) || types[0]!)
const activeImage = computed(() => personalityImages[`../../MBTI/${active.value.code}.jpg`])
const axes = [
  { title: '能量方向', first: 'E', second: 'I', firstLabel: '外向', secondLabel: '内向' },
  { title: '信息获取', first: 'S', second: 'N', firstLabel: '实感', secondLabel: '直觉' },
  { title: '判断方式', first: 'T', second: 'F', firstLabel: '思考', secondLabel: '情感' },
  { title: '生活态度', first: 'J', second: 'P', firstLabel: '判断', secondLabel: '知觉' },
]
function toggleAxis(index: number) {
  const axis = axes[index]
  if (!axis) return
  const code = selected.value.split('')
  code[index] = code[index] === axis.first ? axis.second : axis.first
  selected.value = code.join('')
}
</script>

<template>
  <section
    class="personality-visual"
    :class="[{ compact }, 'family-' + active.group]"
    aria-label="16型人格概念导览，非个人测评结果"
  >
    <header class="atlas-topline">
      <span>人格图谱导览</span>
      <span>16 种视角 · 不止一种可能</span>
    </header>
    <div class="atlas-stage">
      <div
        class="atlas-profile"
        aria-live="polite"
      >
        <p class="atlas-kicker">16 种人格 / 概念导览</p>
        <h2 class="atlas-code">{{ active.code }}</h2>
        <h3>{{ active.title }}</h3>
        <p class="atlas-traits">{{ active.traits }}</p>
        <span class="atlas-family">
          {{ active.group }} /
          {{
            active.group === 'NT'
              ? '分析与探索'
              : active.group === 'NF'
                ? '理想与共情'
                : active.group === 'SJ'
                  ? '秩序与支持'
                  : '体验与行动'
          }}
        </span>
        <div class="atlas-insight">
          <p class="atlas-kicker">另一种视角</p>
          <p>{{ active.prompt }}</p>
        </div>
        <p class="atlas-disclaimer">
          类型名称与描述仅作概念介绍。
          <br />
          真实的你，远比四个字母丰富。
        </p>
      </div>
      <figure class="atlas-character">
        <img
          :src="activeImage"
          :alt="active.code + '（' + active.title + '）人格插画'"
          class="personality-image"
          width="1200"
          height="1200"
          decoding="async"
        />
        <figcaption>{{ active.prompt }}</figcaption>
      </figure>
      <div class="atlas-preferences">
        <p class="atlas-kicker">偏好切换</p>
        <div
          v-for="(axis, index) in axes"
          :key="axis.title"
          class="atlas-axis"
        >
          <span class="axis-label">{{ axis.title }}</span>
          <div class="axis-control">
            <span :class="{ chosen: selected[index] === axis.first }">
              {{ axis.first }} {{ axis.firstLabel }}
            </span>
            <button
              type="button"
              role="switch"
              :aria-checked="selected[index] === axis.second"
              :aria-label="
                axis.title +
                '，当前' +
                (selected[index] === axis.first ? axis.firstLabel : axis.secondLabel)
              "
              @click="toggleAxis(index)"
            >
              <span></span>
            </button>
            <span :class="{ chosen: selected[index] === axis.second }">
              {{ axis.second }} {{ axis.secondLabel }}
            </span>
          </div>
        </div>
        <p class="atlas-switch-note">
          切换字母，探索另一种视角。
          <br />
          这不是测评，也不会修改你的报告。
        </p>
        <RouterLink
          to="/assessment"
          class="atlas-assessment-link"
        >
          测试我的MBTI人格
        </RouterLink>
      </div>
    </div>
    <nav
      class="atlas-type-selector"
      aria-label="选择人格概念类型"
    >
      <button
        v-for="type in types"
        :key="type.code"
        type="button"
        :aria-pressed="selected === type.code"
        @click="selected = type.code"
      >
        {{ type.code }}
      </button>
    </nav>
    <footer class="atlas-bottomline">
      <span>偏好不定义能力，类型不限制成长。</span>
      <span>探索你自己的视角</span>
    </footer>
  </section>
</template>

<style scoped lang="scss">
.personality-visual {
  --figure-main: #8d79a7;
  --figure-mid: #74618e;
  --figure-dark: #514563;
  --figure-light: #c5b9d6;
  --halo: #eee9f4;
  width: 100%;
  min-width: 0;
  padding: 0;
  background: #fff;
  color: #202124;
}
.family-NF {
  --figure-main: #74a08a;
  --figure-mid: #52846b;
  --figure-dark: #335747;
  --figure-light: #afcdb7;
  --halo: #edf4ee;
}
.family-SJ {
  --figure-main: #679baf;
  --figure-mid: #467d96;
  --figure-dark: #345666;
  --figure-light: #b5d3de;
  --halo: #edf3f6;
}
.family-SP {
  --figure-main: #c4a35e;
  --figure-mid: #a98642;
  --figure-dark: #7c6639;
  --figure-light: #e2cf9d;
  --halo: #f7f1e5;
}
.atlas-topline,
.atlas-bottomline {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 12px;
  line-height: 16px;
  color: #94959a;
  letter-spacing: 1px;
  padding: 16px 0;
}
.atlas-topline {
  border-bottom: 1px solid #ececee;
}
.atlas-bottomline {
  border-top: 1px solid #ececee;
}
.atlas-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.8fr) minmax(0, 1fr);
  gap: 32px;
  align-items: center;
  padding: 32px 0 24px;
  min-height: 480px;
}
.atlas-kicker {
  font-size: 12px;
  line-height: 16px;
  color: var(--text-secondary);
  letter-spacing: 0;
  margin: 0 0 16px;
}
.atlas-profile .atlas-code {
  font-size: 72px;
  line-height: 1;
  font-weight: 600;
  letter-spacing: -4px;
  margin: 0 0 8px;
  color: #17181a;
}
.atlas-profile h3 {
  font-size: 24px;
  line-height: 32px;
  margin: 0 0 12px;
  font-weight: 500;
  color: #202124;
}
.atlas-traits {
  font-size: 12px;
  line-height: 20px;
  color: #85868a;
  margin: 0 0 16px;
}
.atlas-family {
  font-size: 12px;
  line-height: 16px;
  color: var(--figure-dark);
}
.atlas-insight {
  margin-top: 40px;
  padding-top: 16px;
  border-top: 1px solid #ececee;
}
.atlas-insight > p:last-child {
  font-size: 14px;
  line-height: 20px;
  color: #68696f;
}
.atlas-disclaimer {
  font-size: 12px;
  line-height: 20px;
  color: #a0a0a5;
  margin: 24px 0 0;
}
.atlas-character {
  position: relative;
  isolation: isolate;
  margin: 0;
  text-align: center;
}

.atlas-character .personality-image {
  display: block;
  width: 100%;
  height: 400px;
  max-width: 400px;
  margin: auto;
  object-fit: contain;
}
.atlas-character figcaption {
  font-size: 12px;
  line-height: 16px;
  color: #9c9ca2;
  margin-top: 8px;
}
.atlas-preferences {
  align-self: center;
}
.atlas-axis {
  margin-bottom: 24px;
}
.axis-label {
  display: block;
  font-size: 12px;
  line-height: 16px;
  color: #a2a2a7;
  margin-bottom: 8px;
}
.axis-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  line-height: 16px;
  color: #b0b0b5;
}
.axis-control .chosen {
  color: #37383c;
}
.axis-control button {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 24px;
  border: 0;
  border-radius: 9999px;
  padding: 4px;
  background: #242033;
  box-shadow:
    inset 0 1px 2px rgb(255 255 255 / 10%),
    0 5px 12px rgb(36 32 51 / 14%);
  cursor: pointer;
  transition:
    box-shadow 350ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1);
}
.axis-control button span {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 5px rgb(38 35 54 / 18%);
  transform: translate3d(0, 0, 0);
  will-change: transform;
}
.axis-control button:hover {
  box-shadow:
    inset 0 1px 2px rgb(255 255 255 / 14%),
    0 7px 14px rgb(36 32 51 / 18%);
}
.axis-control button:active {
  transform: scale(0.96);
}
.axis-control button[aria-checked='true'] span {
  transform: translate3d(16px, 0, 0);
  transform-origin: right center;
  animation: jelly-toggle-on 350ms cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.axis-control button[aria-checked='false'] span {
  transform-origin: left center;
  animation: jelly-toggle-off 350ms cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
@keyframes jelly-toggle-on {
  0% {
    transform: translate3d(0, 0, 0) scaleX(1);
  }
  45% {
    transform: translate3d(10px, 0, 0) scaleX(1.42) scaleY(0.9);
  }
  76% {
    transform: translate3d(17px, 0, 0) scaleX(0.94) scaleY(1.04);
  }
  100% {
    transform: translate3d(16px, 0, 0) scale(1);
  }
}
@keyframes jelly-toggle-off {
  0% {
    transform: translate3d(16px, 0, 0) scaleX(1);
  }
  45% {
    transform: translate3d(6px, 0, 0) scaleX(1.42) scaleY(0.9);
  }
  76% {
    transform: translate3d(-1px, 0, 0) scaleX(0.94) scaleY(1.04);
  }
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
}
@media (prefers-reduced-motion: reduce) {
  .axis-control button,
  .axis-control button span {
    transition: none;
    animation: none;
  }
}
.atlas-switch-note {
  margin: 24px 0;
  font-size: 12px;
  line-height: 20px;
  color: #a0a0a5;
  border-top: 1px solid #ececee;
  padding-top: 16px;
}
.atlas-assessment-link {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  line-height: 16px;
  color: #45464b;
  text-decoration: none;
}
.atlas-type-selector {
  display: grid;
  grid-template-columns: repeat(16, minmax(0, 1fr));
  gap: 4px;
  padding: 12px 0 24px;
}
.atlas-type-selector button {
  border: 0;
  border-bottom: 1px solid transparent;
  padding: 8px 4px;
  color: #a0a0a5;
  background: none;
  font: inherit;
  font-size: 12px;
  line-height: 16px;
  cursor: pointer;
  transition:
    color 300ms cubic-bezier(0.22, 1, 0.36, 1),
    background 300ms cubic-bezier(0.22, 1, 0.36, 1);
}
.atlas-type-selector button:hover {
  color: #202124;
  background: #f8f8f9;
}
.atlas-type-selector button[aria-pressed='true'] {
  color: #202124;
  border-bottom-color: #202124;
  font-weight: 600;
}
.compact .atlas-topline,
.compact .atlas-profile,
.compact .atlas-preferences,
.compact .atlas-type-selector,
.compact .atlas-bottomline {
  display: none;
}
.compact .atlas-stage {
  display: block;
  min-height: 0;
  padding: 0;
}
.compact .atlas-character .personality-image {
  height: 320px;
}
.compact .atlas-character figcaption {
  display: none;
}
@media (max-width: 768px) {
  .atlas-stage {
    grid-template-columns: 1fr 1.5fr;
    gap: 16px;
    min-height: 0;
    padding: 24px 0;
  }
  .atlas-profile .atlas-code {
    font-size: 48px;
    letter-spacing: -2px;
  }
  .atlas-profile h3 {
    font-size: 20px;
    line-height: 28px;
  }
  .atlas-character .personality-image {
    height: 280px;
  }
  .atlas-insight,
  .atlas-disclaimer {
    display: none;
  }
  .atlas-character figcaption {
    display: none;
  }
  .atlas-preferences {
    grid-column: 1/-1;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px 24px;
    border-top: 1px solid #ececee;
    padding-top: 16px;
  }
  .atlas-preferences > .atlas-kicker {
    grid-column: 1/-1;
    margin-bottom: 0;
  }
  .atlas-axis {
    margin: 0;
  }
  .atlas-switch-note {
    display: none;
  }
  .atlas-assessment-link {
    grid-column: 1/-1;
    margin-top: 8px;
  }
  .atlas-type-selector {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }
  .atlas-topline span:last-child,
  .atlas-bottomline span:last-child {
    display: none;
  }
  .atlas-kicker {
    letter-spacing: 1px;
  }
}
</style>
