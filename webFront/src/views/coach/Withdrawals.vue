<template>
  <div class="coach-withdrawals-page">
    <n-card title="提现申请">
      <n-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-placement="left"
        label-width="100"
        style="max-width: 500px; margin: 0 auto"
      >
        <n-form-item label="可提现金额">
          <span class="available-amount">¥{{ availableBalance.toFixed(2) }}</span>
        </n-form-item>
        <n-form-item label="提现金额" path="amount">
          <n-input-number
            v-model:value="formData.amount"
            :min="1"
            :max="availableBalance"
            placeholder="请输入提现金额"
            style="width: 100%"
          >
            <template #prefix>¥</template>
          </n-input-number>
        </n-form-item>
        <n-form-item label="收款方式" path="method">
          <n-select v-model:value="formData.method" :options="methodOptions" placeholder="请选择收款方式" />
        </n-form-item>
        <n-form-item label="收款账号" path="account">
          <n-input v-model:value="formData.account" placeholder="请输入收款账号" />
        </n-form-item>
        <n-form-item label="收款人" path="accountName">
          <n-input v-model:value="formData.accountName" placeholder="请输入收款人姓名" />
        </n-form-item>
        <n-form-item>
          <n-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</n-button>
        </n-form-item>
      </n-form>
    </n-card>

    <n-card title="提现记录" style="margin-top: 20px">
      <n-spin :show="loading">
        <div v-if="withdrawals.length" class="withdrawal-list">
          <div v-for="item in withdrawals" :key="item.id" class="withdrawal-item">
            <div class="withdrawal-info">
              <span class="withdrawal-amount">¥{{ (item.amount ?? 0).toFixed(2) }}</span>
              <span class="withdrawal-method">{{ item.remark || '-' }}</span>
              <span class="withdrawal-time">{{ formatDate(item.requestedAt) }}</span>
            </div>
            <n-tag :type="getStatusType(item.status)">{{ getStatusText(item.status) }}</n-tag>
          </div>
        </div>
        <n-empty v-else description="暂无提现记录" />
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getWithdrawRequests, submitWithdrawRequest } from '@/api/coach'
import { http } from '@/api/http'
import dayjs from 'dayjs'

const loading = ref(false)
const submitting = ref(false)
const availableBalance = ref(0)
const withdrawals = ref([])
const formRef = ref(null)

const methodOptions = [
  { label: '支付宝', value: 'alipay' },
  { label: '微信', value: 'wechat' },
  { label: '银行卡', value: 'bank' }
]

const formData = reactive({
  amount: null,
  method: null,
  account: '',
  accountName: ''
})

const rules = {
  amount: { required: true, type: 'number', min: 1, message: '请输入提现金额' },
  method: { required: true, message: '请选择收款方式' },
  account: { required: true, message: '请输入收款账号' },
  accountName: { required: true, message: '请输入收款人姓名' }
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const methodMap = {
  alipay: '支付宝',
  wechat: '微信',
  bank: '银行卡'
}

const getMethodText = (method) => methodMap[method] || method

const statusMap = {
  PENDING: { text: '待审核', type: 'warning' },
  APPROVED: { text: '已通过', type: 'success' },
  REJECTED: { text: '已拒绝', type: 'error' },
  COMPLETED: { text: '已到账', type: 'success' }
}

const getStatusText = (status) => statusMap[status]?.text || status
const getStatusType = (status) => statusMap[status]?.type || 'default'

const fetchBalance = async () => {
  try {
    const res = await http.get('/api/coach/earnings/stats')
    availableBalance.value = res.data?.availableBalance || 0
  } catch (e) {
    console.error(e)
  }
}

const fetchWithdrawals = async () => {
  loading.value = true
  try {
    const res = await getWithdrawRequests({ page: 1, size: 20 })
    withdrawals.value = res.data?.items || res.data?.list || res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    if (formData.amount > availableBalance.value) {
      window.$message?.warning('提现金额不能超过可提现余额')
      return
    }
    submitting.value = true
    const remark = `${getMethodText(formData.method)} ${formData.account} ${formData.accountName}`.trim()
    await submitWithdrawRequest({ amount: formData.amount, remark: remark || null })
    window.$message?.success('提现申请已提交')
    formData.amount = null
    formData.method = null
    formData.account = ''
    formData.accountName = ''
    fetchBalance()
    fetchWithdrawals()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchBalance()
  fetchWithdrawals()
})
</script>

<style scoped>
.coach-withdrawals-page {
  padding: 20px;
}
.available-amount {
  font-size: 24px;
  font-weight: 600;
  color: #18a058;
}
.withdrawal-list {
  display: flex;
  flex-direction: column;
}
.withdrawal-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}
.withdrawal-item:last-child {
  border-bottom: none;
}
.withdrawal-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.withdrawal-amount {
  font-size: 16px;
  font-weight: 500;
}
.withdrawal-method {
  font-size: 13px;
  color: #666;
}
.withdrawal-time {
  font-size: 12px;
  color: #999;
}
</style>
