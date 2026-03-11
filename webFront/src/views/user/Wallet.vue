<template>
  <div class="wallet-page">
    <n-card>
      <div class="balance-section">
        <div class="balance-card">
          <span class="label">账户余额</span>
          <span class="amount">¥{{ balance.toFixed(2) }}</span>
        </div>
        <div class="action-buttons">
          <n-button type="primary" @click="showTopupModal = true">充值</n-button>
          <n-button :type="signedIn ? 'default' : 'success'" :disabled="signedIn" @click="handleSignin">
            {{ signedIn ? '今日已签到' : '签到领积分' }}
          </n-button>
        </div>
      </div>
    </n-card>

    <n-card title="交易记录" style="margin-top: 20px">
      <n-spin :show="loading">
        <div v-if="transactions.length" class="transaction-list">
          <div v-for="tx in transactions" :key="tx.id" class="transaction-item">
            <div class="tx-info">
              <span class="tx-type">{{ getTxTitle(tx) }}</span>
              <span class="tx-time">{{ formatDate(tx.createdAt) }}</span>
            </div>
            <span :class="['tx-amount', tx.amount > 0 ? 'income' : 'expense']">
              {{ tx.amount > 0 ? '+' : '' }}{{ tx.amount.toFixed(2) }}
            </span>
          </div>
        </div>
        <n-empty v-else description="暂无交易记录" />
      </n-spin>

      <div class="pagination-wrap" v-if="total > 0">
        <n-pagination
          v-model:page="pagination.page"
          :page-size="pagination.pageSize"
          :item-count="total"
          @update:page="fetchTransactions"
        />
      </div>
    </n-card>

    <n-card title="充值记录" style="margin-top: 20px">
      <n-spin :show="loadingTopups">
        <div v-if="topups.length" class="topup-list">
          <div v-for="topup in topups" :key="topup.id" class="topup-item">
            <div class="topup-info">
              <span class="topup-amount">¥{{ formatMoney(topup.amount) }}</span>
              <span class="topup-time">{{ formatDate(topup.requestedAt || topup.createdAt) }}</span>
            </div>
            <n-tag :type="getTopupStatusType(topup.status)">{{ getTopupStatusText(topup.status) }}</n-tag>
          </div>
        </div>
        <n-empty v-else description="暂无充值记录" />
      </n-spin>
    </n-card>

    <!-- 充值弹窗 -->
    <n-modal v-model:show="showTopupModal" preset="card" title="充值申请" style="width: 400px">
      <n-form ref="topupFormRef" :model="topupForm" :rules="topupRules">
        <n-form-item label="充值金额" path="amount">
          <n-input-number
            v-model:value="topupForm.amount"
            :min="1"
            :max="10000"
            placeholder="请输入充值金额"
            style="width: 100%"
          >
            <template #prefix>¥</template>
          </n-input-number>
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="topupForm.remark" placeholder="可选备注" />
        </n-form-item>
      </n-form>
      <n-alert type="info">
        提交充值申请后，请联系管理员完成付款，审核通过后余额将自动到账
      </n-alert>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showTopupModal = false">取消</n-button>
          <n-button type="primary" :loading="submittingTopup" @click="handleTopup">提交申请</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getBalance, getTransactions, submitTopup, getMyTopups, signin, getSigninStatus } from '@/api/wallet'
import dayjs from 'dayjs'

// 用户端「钱包」页面
//
// 页面职责：
// - 展示余额（getBalance）
// - 展示交易流水（getTransactions，分页）
// - 发起充值申请（submitTopup），并展示“我的充值记录”（getMyTopups）
// - 签到领积分（signin），并展示“今日已签到”状态（getSigninStatus）
//
// 数据流：
// onMounted 同时触发：fetchBalance / fetchTransactions / fetchTopups / fetchSigninStatus
// - 余额变动后（签到成功等）会再次拉取余额和流水，用于即时刷新
//
// 交易流水的兼容与归一化：
// - 后端字段可能是 amount/direction 或 amount/txnDirection
// - 本页把 OUT 方向的金额转为负数，方便 UI 统一用“正=收入/负=支出”展示
//
// 充值状态机：
// - pending/approved/rejected（大小写兼容）
// - 充值申请通常需要管理员审核；审核通过后余额才会到账（见页面提示）

const loading = ref(false)
const loadingTopups = ref(false)
const balance = ref(0)
const transactions = ref([])
const topups = ref([])
const total = ref(0)
const signedIn = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const showTopupModal = ref(false)
const submittingTopup = ref(false)
const topupFormRef = ref(null)
const topupForm = reactive({
  amount: 100,
  remark: ''
})

const topupRules = {
  amount: { required: true, type: 'number', min: 1, message: '请输入充值金额' }
}

const formatDate = (date) => dayjs(date).format('YYYY-MM-DD HH:mm')

const formatMoney = (v) => {
  const n = Number(v)
  if (Number.isFinite(n)) return n.toFixed(2)
  return '0.00'
}

const typeTextMap = {
  topup: '充值',
  TOPUP: '充值',
  consume: '消费',
  CONSUME: '消费',
  refund: '退款',
  REFUND: '退款',
  signin: '签到奖励',
  SIGNIN: '签到奖励',
  withdraw: '提现',
  WITHDRAW: '提现',
  COURSE_BOOKING: '课程预约',
  COURSE_REFUND: '课程退款',
  COACH_COURSE_EARNING: '课程收入',
  COACH_VIDEO_EARNING: '视频收入',
  COACH_VIDEO: '购买教学视频',
  EQUIPMENT_ORDER: '器材购买',
  VENUE_BOOKING: '场地预约',
  VENUE_REFUND: '场地退款'
}

const getTypeText = (type) => typeTextMap[type] || type

const translateText = (text) => {
  // translateText：对 description/remark 做额外翻译。
  // - 适用于后端返回英文短语、但 UI 希望展示中文的场景
  // - 如果没有命中映射则返回空串，让调用方回退到 getTypeText
  if (!text || typeof text !== 'string') return ''
  const s = text.trim()
  if (!s) return ''

  const key = s.toLowerCase()
  const map = {
    'wallet topup': '钱包充值',
    'venue refund': '场地退款',
    'withdraw approved': '提现(已通过)',
    'withdraw rejected': '提现(已拒绝)',
    'withdraw pending': '提现(待审核)',
    'topup approved': '充值(已通过)',
    'topup rejected': '充值(已拒绝)',
    'topup pending': '充值(待审核)'
  }
  if (map[key]) return map[key]
  return ''
}

const getTxTitle = (tx) => {
  const translated = translateText(tx?.description)
  if (translated) return translated
  return getTypeText(tx?.type)
}

const topupStatusMap = {
  pending: { text: '待审核', type: 'warning' },
  PENDING: { text: '待审核', type: 'warning' },
  approved: { text: '已通过', type: 'success' },
  APPROVED: { text: '已通过', type: 'success' },
  rejected: { text: '已拒绝', type: 'error' },
  REJECTED: { text: '已拒绝', type: 'error' }
}

const getTopupStatusText = (status) => topupStatusMap[status]?.text || status
const getTopupStatusType = (status) => topupStatusMap[status]?.type || 'default'

const fetchBalance = async () => {
  // 拉取余额：兼容后端返回 {balance:xx} 或直接 number。
  try {
    const res = await getBalance()
    const v = res.data?.balance ?? res.data
    balance.value = Number(v) || 0
  } catch (e) {
    console.error(e)
  }
}

const fetchTransactions = async () => {
  // 拉取流水：
  // - 分页参数 page/pageSize
  // - 返回结构兼容 items/list/content
  // - 归一化字段：type/description/amount/id
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    const res = await getTransactions(params)
    const rows = res.data?.items || res.data?.list || res.data?.content || []
    const arr = Array.isArray(rows) ? rows : []
    transactions.value = arr.map((r) => {
      // 方向归一化：
      // - OUT：支出（负数）
      // - 其他：收入（正数）
      const amount = Number(r.amount ?? 0) || 0
      const direction = r.direction || r.txnDirection
      const signedAmount = direction === 'OUT' ? -amount : amount
      return {
        id: r.id ?? r.txnNo,
        txnNo: r.txnNo,
        type: r.type ?? r.txnType,
        description: r.description ?? r.remark,
        amount: signedAmount,
        createdAt: r.createdAt
      }
    })
    total.value = res.data?.total ?? res.data?.totalElements ?? transactions.value.length
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const fetchTopups = async () => {
  // 拉取充值记录：
  // - 这里简单取前 10 条
  // - 状态由 topupStatusMap 翻译为 Tag 文案
  loadingTopups.value = true
  try {
    const res = await getMyTopups({ page: 1, pageSize: 10 })
    topups.value = res.data?.items || res.data?.list || res.data?.content || []
  } catch (e) {
    console.error(e)
  } finally {
    loadingTopups.value = false
  }
}

const fetchSigninStatus = async () => {
  // 查询今日签到状态：
  // - 用于禁用按钮，避免重复点击
  // - 真实幂等仍由后端保证
  try {
    const res = await getSigninStatus()
    signedIn.value = res.data?.signedIn || false
  } catch (e) {
    console.error(e)
  }
}

const handleSignin = async () => {
  // 签到：典型幂等接口。
  // - 成功后把 signedIn 置为 true，并刷新余额/流水
  // - 若已签到，后端可能返回业务错误（前端展示 message）
  try {
    await signin()
    signedIn.value = true
    window.$message?.success('签到成功，积分已到账')
    fetchBalance()
    fetchTransactions()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '签到失败')
  }
}

const handleTopup = async () => {
  // 提交充值申请：
  // - 这里只是“申请”，实际入账通常依赖管理员审核
  // - 成功后重置表单并刷新充值记录
  try {
    await topupFormRef.value?.validate()
    submittingTopup.value = true
    await submitTopup(topupForm)
    window.$message?.success('充值申请已提交')
    showTopupModal.value = false
    topupForm.amount = 100
    topupForm.remark = ''
    fetchTopups()
  } catch (e) {
    window.$message?.error(e?.response?.data?.message || '提交失败')
  } finally {
    submittingTopup.value = false
  }
}

onMounted(() => {
  fetchBalance()
  fetchTransactions()
  fetchTopups()
  fetchSigninStatus()
})
</script>

<style scoped>
.wallet-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}
.balance-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.balance-card {
  display: flex;
  flex-direction: column;
}
.balance-card .label {
  font-size: 14px;
  color: #666;
}
.balance-card .amount {
  font-size: 36px;
  font-weight: 600;
  color: #18a058;
}
.action-buttons {
  display: flex;
  gap: 12px;
}
.transaction-list, .topup-list {
  display: flex;
  flex-direction: column;
}
.transaction-item, .topup-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}
.transaction-item:last-child, .topup-item:last-child {
  border-bottom: none;
}
.tx-info, .topup-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tx-type {
  font-weight: 500;
}
.tx-time, .topup-time {
  font-size: 12px;
  color: #999;
}
.tx-amount {
  font-size: 16px;
  font-weight: 500;
}
.tx-amount.income {
  color: #18a058;
}
.tx-amount.expense {
  color: #f5222d;
}
.topup-amount {
  font-weight: 500;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
