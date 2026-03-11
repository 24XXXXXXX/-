<template>
  <div class="admin-settings">
    <n-card title="系统配置">
      <template #header-extra>
        <n-space>
          <n-input v-model:value="keyword" placeholder="搜索 key/备注" clearable style="width: 220px" @keyup.enter="fetchConfigs" />
          <n-button @click="fetchConfigs">查询</n-button>
          <n-button type="primary" :loading="submitting" @click="handleSubmit">保存</n-button>
        </n-space>
      </template>

      <n-spin :show="loading">
        <n-data-table :columns="columns" :data="configs" :pagination="false" />
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, h, onMounted } from 'vue'
import { useMessage, NInput } from 'naive-ui'
import { getSysConfigs, updateSysConfigs } from '@/api/admin'

const message = useMessage()

const loading = ref(false)
const submitting = ref(false)

const keyword = ref('')
const configs = ref([])

const columns = [
  { title: 'Key', key: 'cfgKey', width: 240, ellipsis: { tooltip: true } },
  {
    title: 'Value',
    key: 'cfgValue',
    render: (row) => h(NInput, {
      value: row.cfgValue,
      onUpdateValue: (v) => { row.cfgValue = v },
      placeholder: '请输入配置值'
    })
  },
  {
    title: '备注',
    key: 'remark',
    render: (row) => h(NInput, {
      value: row.remark,
      onUpdateValue: (v) => { row.remark = v },
      placeholder: '备注(可选)'
    })
  }
]

const fetchConfigs = async () => {
  loading.value = true
  try {
    const res = await getSysConfigs(keyword.value ? { keyword: keyword.value } : undefined)
    configs.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    message.error('获取配置失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const payload = (configs.value || []).map((c) => ({
      cfgKey: c.cfgKey,
      cfgValue: c.cfgValue,
      remark: c.remark
    }))
    await updateSysConfigs(payload)
    message.success('配置保存成功')
  } catch (e) {
    message.error('保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => fetchConfigs())
</script>

<style scoped>
.admin-settings { padding: 20px; }
</style>
