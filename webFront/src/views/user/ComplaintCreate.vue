<template>
  <div class="complaint-create-page">
    <n-card title="提交投诉">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="top">
        <n-form-item label="投诉类型" path="complaintType">
          <n-select
            v-model:value="formData.complaintType"
            :options="typeOptions"
            placeholder="请选择投诉类型"
          />
        </n-form-item>
        <n-form-item label="投诉内容" path="content">
          <n-input
            v-model:value="formData.content"
            type="textarea"
            placeholder="请详细描述您的问题"
            :rows="6"
          />
        </n-form-item>
        <n-form-item label="相关附件">
          <n-upload
            v-model:file-list="fileList"
            :max="5"
            list-type="image-card"
            accept="image/*"
            :custom-request="handleUpload"
          >
            <n-button>上传图片</n-button>
          </n-upload>
          <p class="upload-tip">最多上传5张图片，支持jpg、png格式</p>
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button type="primary" :loading="submitting" @click="handleSubmit">提交投诉</n-button>
            <n-button @click="$router.back()">取消</n-button>
          </n-space>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { createComplaint } from '@/api/complaint'
import { uploadFile } from '@/api/upload'

// 用户端「提交投诉」页面
//
// 页面职责：
// - 提交一张新的投诉单（投诉类型 + 文字描述 + 可选附件）
// - 附件使用 n-upload 的 custom-request，自行调用通用上传接口 uploadFile
// - 提交成功后跳回“我的投诉”列表页
//
// 数据流：
// - 表单：n-form 基于 formData 双向绑定；rules 负责前端最小校验
// - 上传：handleUpload -> uploadFile('complaint', file) -> 把返回 url 写回 file.url
// - 提交：handleSubmit -> formRef.validate -> 组装 attachments -> createComplaint(payload)
//
// 约束与边界：
// - 这里的校验是“用户体验”层面的兜底，最终合法性仍以服务端校验为准
// - attachments 发送的是 URL 数组；后端决定如何持久化与关联

const router = useRouter()
const submitting = ref(false)
const formRef = ref(null)
const fileList = ref([])

const typeOptions = [
  { label: '场地问题', value: 'VENUE' },
  { label: '器材问题', value: 'EQUIPMENT' },
  { label: '课程问题', value: 'COURSE' },
  { label: '其他问题', value: 'OTHER' }
]

// typeOptions：投诉类型枚举。
// value 需要与后端投诉类型枚举严格一致，否则 createComplaint 会校验失败。

const formData = reactive({
  complaintType: null,
  content: ''
})

const rules = {
  complaintType: { required: true, message: '请选择投诉类型' },
  content: [
    { required: true, message: '请输入投诉内容' },
    { min: 10, message: '投诉内容至少10个字符' }
  ]
}

// rules：前端表单校验规则。
// 注意：min=10 只是体验上的提醒，后端也可能有更严格的长度限制。

const handleUpload = async ({ file, onFinish, onError }) => {
  // custom-request：接管 naive-ui 的上传流程。
  // - uploadFile(category, file)：通用上传接口；这里使用 category='complaint'
  // - 把返回的 url 写到 file.url，方便后续从 fileList 汇总 attachments
  try {
    const res = await uploadFile('complaint', file.file)
    file.url = res.data?.url || res.data
    onFinish()
  } catch (e) {
    onError()
    window.$message?.error('上传失败')
  }
}

const handleSubmit = async () => {
  // 提交投诉：
  // 1) validate：确保 complaintType/content 满足最小约束
  // 2) 从 fileList 过滤 status=finished 且具备 url 的项，组成 attachments(url[]) 
  // 3) 调用 createComplaint 创建投诉单
  // 4) 成功后跳回列表页（/user/complaints）
  try {
    await formRef.value?.validate()
    submitting.value = true
    
    const attachments = fileList.value
      .filter(f => f.status === 'finished' && f.url)
      .map(f => f.url)
    
    await createComplaint({
      complaintType: formData.complaintType,
      content: formData.content,
      attachments
    })
    
    window.$message?.success('投诉已提交，我们会尽快处理')
    router.push('/user/complaints')
  } catch (e) {
    // validate 失败也会抛异常；这里仅做最小提示，不改变原有控制流。
    if (e?.message) window.$message?.error(e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.complaint-create-page {
  padding: 20px;
  max-width: 700px;
  margin: 0 auto;
}
.upload-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
}
</style>
