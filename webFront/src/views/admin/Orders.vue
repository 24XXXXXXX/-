<template>
  <div class="admin-orders">
    <n-card title="订单管理">
      <n-space vertical :size="16">
        <n-space>
          <n-input v-model:value="filters.orderNo" placeholder="订单号" clearable style="width: 180px" @keyup.enter="handleSearch" />
          <n-input v-model:value="filters.keyword" placeholder="收货人/手机号" clearable style="width: 150px" @keyup.enter="handleSearch" />
          <n-select v-model:value="filters.status" placeholder="状态" clearable style="width: 130px" :options="statusOptions" />
          <n-button type="primary" @click="handleSearch">搜索</n-button>
          <n-button @click="handleReset">重置</n-button>
        </n-space>

        <n-data-table :columns="columns" :data="orders" :loading="loading" :pagination="pagination" remote @update:page="handlePageChange" />
      </n-space>
    </n-card>

    <n-modal v-model:show="showDetailModal" preset="card" title="订单详情" style="width: 800px">
      <template v-if="currentOrder">
        <n-descriptions :column="2" bordered>
          <n-descriptions-item label="订单号">{{ currentOrder.orderNo }}</n-descriptions-item>
          <n-descriptions-item label="状态">
            <n-tag :type="statusMap[currentOrder.status]?.type">{{ statusMap[currentOrder.status]?.label }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="下单用户">{{ currentOrder.username || currentOrder.userId }}</n-descriptions-item>
          <n-descriptions-item label="下单时间">{{ currentOrder.createdAt }}</n-descriptions-item>
          <n-descriptions-item label="收货人">{{ currentOrder.receiverName }}</n-descriptions-item>
          <n-descriptions-item label="联系电话">{{ currentOrder.receiverPhone }}</n-descriptions-item>
          <n-descriptions-item label="收货地址" :span="2">{{ currentOrder.receiverAddress }}</n-descriptions-item>
          <n-descriptions-item label="订单金额">¥{{ Number(currentOrder.totalAmount ?? 0).toFixed(2) }}</n-descriptions-item>
        </n-descriptions>

        <n-divider>商品列表</n-divider>
        <n-table :bordered="false" :single-line="false">
          <thead>
            <tr>
              <th>商品</th>
              <th>单价</th>
              <th>数量</th>
              <th>小计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in currentOrder.items" :key="item.equipmentId">
              <td>
                <n-space align="center">
                  <span>{{ item.equipmentName }}</span>
                </n-space>
              </td>
              <td>¥{{ Number(item.price ?? 0).toFixed(2) }}</td>
              <td>{{ item.quantity }}</td>
              <td>¥{{ Number(item.subtotal ?? 0).toFixed(2) }}</td>
            </tr>
          </tbody>
        </n-table>

        <template v-if="currentOrder.status === 'PAID'">
          <n-divider />
          <n-form :model="shipForm" label-placement="left" label-width="80">
            <n-form-item label="快递公司">
              <n-input v-model:value="shipForm.logisticsCompany" placeholder="请输入快递公司" />
            </n-form-item>
            <n-form-item label="快递单号">
              <n-input v-model:value="shipForm.trackingNo" placeholder="请输入快递单号" />
            </n-form-item>
          </n-form>
          <n-space justify="end">
            <n-button type="primary" :loading="shipping" @click="handleShip">确认发货</n-button>
          </n-space>
        </template>

        <template v-if="currentOrder.trackingNo">
          <n-divider>物流信息</n-divider>
          <n-descriptions :column="2" bordered>
            <n-descriptions-item label="快递公司">{{ currentOrder.logisticsCompany }}</n-descriptions-item>
            <n-descriptions-item label="快递单号">{{ currentOrder.trackingNo }}</n-descriptions-item>
            <n-descriptions-item label="发货时间">{{ currentOrder.shippedAt }}</n-descriptions-item>
          </n-descriptions>
        </template>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, h, onMounted } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import { getAdminOrders, getAdminOrderDetail, shipOrder } from '@/api/equipment'

const message = useMessage()

const loading = ref(false)
const orders = ref([])
const filters = reactive({ orderNo: '', keyword: '', status: null })
const pagination = reactive({ page: 1, pageSize: 10, itemCount: 0 })

const statusOptions = [
  { label: '待支付', value: 'CREATED' },
  { label: '已付款', value: 'PAID' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已签收', value: 'RECEIVED' }
]

const statusMap = {
  CREATED: { label: '待支付', type: 'default' },
  PAID: { label: '已付款', type: 'info' },
  SHIPPED: { label: '已发货', type: 'warning' },
  RECEIVED: { label: '已签收', type: 'success' }
}

const showDetailModal = ref(false)
const currentOrder = ref(null)
const shipForm = reactive({ logisticsCompany: '', trackingNo: '' })
const shipping = ref(false)

const columns = [
  { title: '订单号', key: 'orderNo', width: 180 },
  { title: '用户', key: 'userId', render: (row) => row.userId },
  { title: '收货人', key: 'receiverName' },
  { title: '金额', key: 'totalAmount', render: (row) => `¥${row.totalAmount?.toFixed(2)}` },
  {
    title: '状态',
    key: 'status',
    render: (row) => h(NTag, { type: statusMap[row.status]?.type }, () => statusMap[row.status]?.label)
  },
  { title: '下单时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => '查看')
  }
]

const fetchOrders = async () => {
  loading.value = true
  try {
    const res = await getAdminOrders({
      page: pagination.page,
      size: pagination.pageSize,
      orderNo: filters.orderNo || undefined,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined
    })
    orders.value = res.data?.content || []
    pagination.itemCount = res.data?.totalElements || 0
  } catch (e) {
    message.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchOrders()
}

const handleReset = () => {
  filters.orderNo = ''
  filters.keyword = ''
  filters.status = null
  handleSearch()
}

const handlePageChange = (page) => {
  pagination.page = page
  fetchOrders()
}

const openDetail = async (row) => {
  try {
    const res = await getAdminOrderDetail(row.id)
    currentOrder.value = res.data
    shipForm.logisticsCompany = ''
    shipForm.trackingNo = ''
    showDetailModal.value = true
  } catch (e) {
    message.error('获取订单详情失败')
  }
}

const handleShip = async () => {
  if (!shipForm.logisticsCompany || !shipForm.trackingNo) {
    message.warning('请填写快递信息')
    return
  }
  shipping.value = true
  try {
    await shipOrder(currentOrder.value.id, shipForm)
    message.success('发货成功')
    showDetailModal.value = false
    fetchOrders()
  } catch (e) {
    message.error('发货失败')
  } finally {
    shipping.value = false
  }
}

onMounted(() => fetchOrders())
</script>

<style scoped>
.admin-orders { padding: 20px; }
</style>
