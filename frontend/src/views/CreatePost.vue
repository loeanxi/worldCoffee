<template>
  <div class="min-h-screen pb-28 bg-surface">
    <!-- 顶部栏 -->
    <header class="sticky top-0 z-30 bg-surface-elevated/90 backdrop-blur-xl border-b border-line/60">
      <div class="max-w-2xl mx-auto px-4 h-14 flex items-center justify-between">
        <button
          class="p-2 -ml-2 rounded-xl hover:bg-surface-soft transition-colors tap-scale"
          @click="router.back()"
          aria-label="返回"
        >
          <Icon icon="material-symbols:arrow-back" class="w-5 h-5 text-ink-soft" />
        </button>
        <h1 class="text-[15px] font-semibold text-ink">发布帖子</h1>
        <div class="w-9" />
      </div>
    </header>

    <main class="max-w-2xl mx-auto px-4 pt-6 animate-fade-up">
      <div class="rounded-3xl p-6 bg-surface-elevated shadow-[0_1px_2px_rgba(62,39,35,0.04),0_2px_12px_rgba(62,39,35,0.06)] space-y-6 border border-line/40">
        <!-- 标题 -->
        <AppInput
          v-model="form.title"
          label="标题"
          placeholder="给帖子取个好名字"
          :maxlength="50"
          showCounter
          :error="errors.title"
        />

        <!-- 内容（textarea） -->
        <div class="space-y-1.5">
          <label class="block text-sm font-medium text-ink-soft pl-1">
            内容
          </label>
          <textarea
            v-model="form.content"
            rows="5"
            maxlength="1000"
            placeholder="分享你的咖啡故事…"
            class="input-coffee resize-none min-h-[140px]"
          />
          <div class="flex items-center justify-between px-1">
            <span class="text-[11px] text-ink-muted">支持换行和纯文本</span>
            <span
              class="text-[11px] shrink-0"
              :class="[
                form.content.length >= 900 ? 'text-rose' :
                form.content.length >= 750 ? 'text-amber' : 'text-ink-muted'
              ]"
            >
              {{ form.content.length }} / 1000
            </span>
          </div>
        </div>

        <!-- 图片上传 -->
        <div class="space-y-2">
          <label class="block text-sm font-medium text-ink-soft pl-1">
            图片（最多 9 张）
          </label>
          <div class="flex flex-wrap gap-3">
            <div
              v-for="(img, i) in uploadedImages"
              :key="i"
              class="relative w-24 h-24 rounded-2xl overflow-hidden border border-line/60 shadow-[0_1px_3px_rgba(62,39,35,0.04),0_2px_6px_rgba(62,39,35,0.04)] group animate-fade-up"
            >
              <img :src="img" class="w-full h-full object-cover" @click="previewImage(img)" />
              <button
                type="button"
                class="absolute top-1 right-1 w-6 h-6 rounded-full bg-ink/70 backdrop-blur-sm opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"
                @click="removeImage(i)"
                aria-label="删除图片"
              >
                <Icon icon="material-symbols:close" class="w-4 h-4 text-white" />
              </button>
            </div>
            <button
              v-if="uploadedImages.length < 9"
              type="button"
              class="w-24 h-24 rounded-2xl border-2 border-dashed border-line hover:border-brand/40 hover:bg-surface-soft flex flex-col items-center justify-center gap-1 transition-all tap-scale text-ink-muted hover:text-brand/60"
              @click="triggerUpload"
              aria-label="添加图片"
            >
              <Icon icon="material-symbols:add" class="w-7 h-7" />
              <span class="text-[11px]">添加图片</span>
            </button>
          </div>
          <p v-if="uploading" class="text-xs text-ink-muted pl-1 animate-pulse-soft">
            正在上传 {{ uploadProgress }}%…
          </p>
        </div>

        <!-- 咖啡信息 -->
        <div class="grid grid-cols-2 gap-4">
          <AppInput v-model="form.coffeeName" label="咖啡名称" placeholder="如：耶加雪菲" :maxlength="30" />
          <AppInput v-model="form.coffeeBrand" label="品牌 / 门店" placeholder="如：星巴克" :maxlength="30" />
        </div>

        <!-- 地点 -->
        <AppInput v-model="form.location" label="地点" placeholder="你在哪里喝的？" :maxlength="50" />

        <!-- 草稿指示 -->
        <div v-if="hasDraft" class="flex items-center gap-2 text-xs text-amber bg-amber-soft border border-line/40 px-3 py-2 rounded-2xl animate-fade-in">
          <Icon icon="material-symbols:save" class="w-4 h-4 shrink-0" />
          <span>已自动保存草稿 · 刷新页面内容会保留</span>
          <button class="ml-auto underline hover:text-brand" @click="clearDraft">清空</button>
        </div>

        <!-- 错误提示 -->
        <div v-if="serverError" class="flex items-start gap-2 text-sm text-rose bg-rose-soft border border-line/40 px-3.5 py-2.5 rounded-2xl animate-fade-in">
          <Icon icon="material-symbols:error-outline" class="w-4 h-4 mt-0.5 shrink-0" />
          <span>{{ serverError }}</span>
        </div>

        <!-- 提交按钮 -->
        <button
          :disabled="submitting"
          @click="handleSubmit"
          class="w-full min-h-[48px] rounded-2xl brand-gradient-btn font-semibold text-[15px] shadow-[0_4px_14px_rgba(109,76,65,0.25)] hover:shadow-[0_6px_20px_rgba(109,76,65,0.35)] hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:hover:translate-y-0 disabled:cursor-not-allowed flex items-center justify-center gap-2"
        >
          <svg v-if="submitting" class="animate-spin-slow w-5 h-5" viewBox="0 0 24 24" fill="none">
            <path d="M12 2 A10 10 0 0 1 22 12" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
          </svg>
          <span>{{ submitting ? '发布中…' : '发布帖子' }}</span>
        </button>
      </div>
    </main>

    <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="handleFileSelect" />

    <!-- 图片预览模态 -->
    <Teleport to="body">
      <Transition name="modal">
        <div
          v-if="previewSrc"
          class="fixed inset-0 z-[100] bg-ink/85 backdrop-blur-sm flex items-center justify-center p-6 animate-fade-in"
          @click.self="previewSrc = ''"
        >
          <button
            class="absolute top-4 right-4 w-10 h-10 rounded-full bg-white/10 backdrop-blur flex items-center justify-center text-white hover:bg-white/20 transition-colors"
            @click="previewSrc = ''"
            aria-label="关闭预览"
          >
            <Icon icon="material-symbols:close" class="w-6 h-6" />
          </button>
          <img :src="previewSrc" class="max-w-full max-h-[85vh] rounded-2xl shadow-[0_16px_48px_rgba(0,0,0,0.4)]" />
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { coffeeApi, getApiError } from '../api'
import AppInput from '../components/AppInput.vue'

const router = useRouter()
const toast = inject('toast')
const fileInput = ref(null)
const previewSrc = ref('')
const uploadProgress = ref(0)

const DRAFT_KEY = 'worldcoffee:post-draft'

const form = reactive({
  title: '',
  content: '',
  coffeeName: '',
  coffeeBrand: '',
  location: ''
})

const errors = reactive({ title: '' })
const uploadedImages = ref([])
const uploading = ref(false)
const submitting = ref(false)
const serverError = ref('')

const hasDraft = computed(() =>
  !!form.title || !!form.content || !!form.coffeeName || !!form.coffeeBrand ||
  !!form.location || uploadedImages.value.length > 0
)

/* -------- 草稿持久化 -------- */
onMounted(() => {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (raw) {
      const saved = JSON.parse(raw)
      if (saved && typeof saved === 'object') {
        Object.assign(form, {
          title: saved.title || '',
          content: saved.content || '',
          coffeeName: saved.coffeeName || '',
          coffeeBrand: saved.coffeeBrand || '',
          location: saved.location || ''
        })
        if (Array.isArray(saved.images)) uploadedImages.value = saved.images
      }
    }
  } catch (e) { /* ignore */ }
})

watch(form, saveDraft, { deep: true })
watch(uploadedImages, saveDraft, { deep: true })

function saveDraft() {
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify({
      ...form,
      images: uploadedImages.value
    }))
  } catch (e) { /* ignore */ }
}

function clearDraft() {
  form.title = ''
  form.content = ''
  form.coffeeName = ''
  form.coffeeBrand = ''
  form.location = ''
  uploadedImages.value = []
  try { localStorage.removeItem(DRAFT_KEY) } catch (e) { /* ignore */ }
}

/* -------- 图片上传 -------- */
function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileSelect(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.show('请选择图片文件', 'error')
    e.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    toast.show('图片不能超过 5MB', 'error')
    e.target.value = ''
    return
  }

  uploading.value = true
  uploadProgress.value = 20
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await coffeeApi.upload(fd)
    uploadProgress.value = 80
    if (res && res.code === 200) {
      uploadedImages.value.push(res.data)
      toast.show('图片上传成功')
    } else {
      toast.show(res?.msg || '上传失败', 'error')
    }
  } catch (err) {
    toast.show(getApiError(err), 'error')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
    e.target.value = ''
  }
}

function removeImage(i) {
  uploadedImages.value.splice(i, 1)
}

function previewImage(src) {
  previewSrc.value = src
}

/* -------- 提交帖子 -------- */
async function handleSubmit() {
  errors.title = !form.title.trim() ? '请输入标题' : ''
  if (errors.title) return
  serverError.value = ''

  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      images: [...uploadedImages.value],
      coffeeName: form.coffeeName.trim() || null,
      coffeeBrand: form.coffeeBrand.trim() || null,
      location: form.location.trim() || null
    }

    const res = await coffeeApi.createPost(payload)
    if (res && res.code === 200) {
      clearDraft()
      toast.show('发布成功！')
      router.push('/')
    } else {
      serverError.value = res?.msg || '发布失败'
    }
  } catch (e) {
    serverError.value = getApiError(e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-enter-active, .modal-leave-active { transition: opacity 0.25s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
