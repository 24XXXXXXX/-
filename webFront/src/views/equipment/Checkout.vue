<template>
  <div class="checkout-page">
    <n-card title="确认订单">
      <n-spin :show="loading">
        <!-- 收货地址 -->
        <div class="section">
          <h3 class="section-title">收货地址</h3>
          <div v-if="addresses.length" class="address-list">
            <div
              v-for="addr in addresses"
              :key="addr.id"
              :class="['address-item', { active: selectedAddressId === addr.id }]"
              @click="selectedAddressId = addr.id"
            >
              <div class="addr-info">
                <span class="name">{{ addr.receiverName }}</span>
                <span class="phone">{{ addr.receiverPhone }}</span>
                <n-tag v-if="addr.isDefault" size="small" type="primary">默认</n-tag>
              </div>
              <p class="addr-detail">{{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}</p>
            </div>
          </div>
          <n-empty v-else description="暂无收货地址">
            <template #extra>
              <n-button type="primary" @click="showAddressModal = true">添加地址</n-button>
            </template>
          </n-empty>
          <n-button v-if="addresses.length" text type="primary" @click="showAddressModal = true">
            + 添加新地址
          </n-button>
        </div>

        <n-divider />

        <!-- 商品列表 -->
        <div class="section">
          <h3 class="section-title">商品清单</h3>
          <div class="goods-list">
            <div v-for="item in checkoutItems" :key="item.equipmentId" class="goods-item">
              <img :src="item.coverUrl || '/placeholder.svg'" class="goods-img" />
              <div class="goods-info">
                <h4>{{ item.name }}</h4>
                <p class="goods-price">¥{{ item.price }} × {{ item.quantity }}</p>
              </div>
              <span class="goods-total">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
            </div>
          </div>
        </div>

        <n-divider />

        <!-- 订单摘要 -->
        <div class="order-summary">
          <div class="summary-row">
            <span>商品总额</span>
            <span>¥{{ totalAmount.toFixed(2) }}</span>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <div class="summary-row total">
            <span>应付金额</span>
            <span class="amount">¥{{ totalAmount.toFixed(2) }}</span>
          </div>
        </div>

        <div class="submit-bar">
          <n-button type="primary" size="large" :loading="submitting" @click="submitOrder">
            提交订单
          </n-button>
        </div>
      </n-spin>
    </n-card>

    <!-- 添加地址弹窗 -->
    <n-modal v-model:show="showAddressModal" preset="card" title="添加收货地址" style="width: 500px">
      <n-form ref="addressFormRef" :model="addressForm" :rules="addressRules" label-placement="left" label-width="80">
        <n-form-item label="收货人" path="receiverName">
          <n-input v-model:value="addressForm.receiverName" placeholder="请输入收货人姓名" />
        </n-form-item>
        <n-form-item label="手机号" path="receiverPhone">
          <n-input v-model:value="addressForm.receiverPhone" placeholder="请输入手机号" />
        </n-form-item>
        <n-form-item label="省份" path="province">
          <n-input v-model:value="addressForm.province" placeholder="省份" />
        </n-form-item>
        <n-form-item label="城市" path="city">
          <n-input v-model:value="addressForm.city" placeholder="城市" />
        </n-form-item>
        <n-form-item label="区县" path="district">
          <n-input v-model:value="addressForm.district" placeholder="区县" />
        </n-form-item>
        <n-form-item label="详细地址" path="detail">
          <n-input v-model:value="addressForm.detail" type="textarea" placeholder="详细地址" />
        </n-form-item>
        <n-form-item label="设为默认">
          <n-switch v-model:value="addressForm.isDefault" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddressModal = false">取消</n-button>
          <n-button type="primary" :loading="savingAddress" @click="saveAddress">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAddresses, createAddress } from '@/api/user'
import { createOrder } from '@/api/equipment'
import { useCartStore } from '@/stores/cart'

// 器材商城「结算/确认订单」页面
//
// 页面职责：
// - 展示并选择收货地址（支持在弹窗中新增地址）
// - 展示本次结算的商品清单（来自购物车中被勾选的条目）
// - 计算应付金额（当前实现：仅商品合计，运费固定 0）
// - 提交订单：调用 createOrder，把收货人/电话/拼接后的收货地址提交给后端
//
// 数据流：
// - Cart.vue 在“去结算”时，把选中商品 equipmentId 列表写入 sessionStorage.checkoutItems
// - 本页 onMounted：
//   1) 先 fetchCart() 同步购物车到 store
//   2) loadCheckoutItems() 根据 checkoutItems id 列表从 cartStore.items 过滤出结算清单
//   3) fetchAddresses() 拉取地址列表，并按默认地址进行预选
//
// 边界与风险提示：
// - checkoutItems 存在于 sessionStorage：跨标签页/跨设备不共享，且可能被用户清除
//   所以本页对“结算清单为空”做了兜底：提示并跳回购物车
// - createOrder 属于关键业务动作：后端会校验库存/价格/地址合法性等，失败时要把错误提示展示给用户

const router = useRouter()
const cartStore = useCartStore()

const loading = ref(false)
const submitting = ref(false)
const addresses = ref([])
const selectedAddressId = ref(null)
const checkoutItems = ref([])

const showAddressModal = ref(false)
const savingAddress = ref(false)
const addressFormRef = ref(null)
const addressForm = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const addressRules = {
  receiverName: { required: true, message: '请输入收货人姓名' },
  receiverPhone: { required: true, message: '请输入手机号' },
  province: { required: true, message: '请输入省份' },
  city: { required: true, message: '请输入城市' },
  district: { required: true, message: '请输入区县' },
  detail: { required: true, message: '请输入详细地址' }
}

const totalAmount = computed(() => {
  // 应付金额：对结算清单求和。
  return checkoutItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
})

const fetchAddresses = async () => {
  // 拉取地址列表，并根据 isDefault 做默认选中。
  try {
    const res = await getAddresses()
    addresses.value = res.data || []
    // 默认选中默认地址
    const defaultAddr = addresses.value.find(a => a.isDefault)
    if (defaultAddr) {
      selectedAddressId.value = defaultAddr.id
    } else if (addresses.value.length) {
      selectedAddressId.value = addresses.value[0].id
    }
  } catch (e) {
    console.error(e)
  }
}

const loadCheckoutItems = () => {
  // 读取 sessionStorage.checkoutItems 并从购物车中过滤出结算条目。
  // 注意：结算清单依赖 cartStore.items，因此一般要先 fetchCart 再调用此函数。
  const itemIds = JSON.parse(sessionStorage.getItem('checkoutItems') || '[]')
  checkoutItems.value = cartStore.items.filter(item => itemIds.includes(item.equipmentId))
  if (checkoutItems.value.length === 0) {
    window.$message?.warning('请先选择商品')
    router.push('/cart')
  }
}

const saveAddress = async () => {
  // 新增地址：
  // - 先做表单校验
  // - payload 字段命名按后端接口约定
  // - 保存成功后刷新地址列表，并重置表单
  try {
    await addressFormRef.value?.validate()
    savingAddress.value = true
    const payload = {
      receiverName: addressForm.receiverName,
      receiverPhone: addressForm.receiverPhone,
      province: addressForm.province,
      city: addressForm.city,
      district: addressForm.district,
      detail: addressForm.detail,
      isDefault: addressForm.isDefault ? 1 : 0
    }
    await createAddress(payload)
    window.$message?.success('地址添加成功')
    showAddressModal.value = false
    await fetchAddresses()
    // 重置表单
    Object.assign(addressForm, { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detail: '', isDefault: false })
  } catch (e) {
    if (e?.message) window.$message?.error(e.message)
  } finally {
    savingAddress.value = false
  }
}

const submitOrder = async () => {
  // 提交订单：
  // - 必须先选中一个地址
  // - 订单条目来自购物车（后端通常会以服务端购物车为准进行下单）
  // - 成功后清理 sessionStorage.checkoutItems，并刷新购物车，最后跳转到“我的订单”列表
  if (!selectedAddressId.value) {
    window.$message?.warning('请选择收货地址')
    return
  }
  submitting.value = true
  try {
    const selected = addresses.value.find(a => a.id === selectedAddressId.value)
    if (!selected) {
      window.$message?.warning('请选择收货地址')
      return
    }

    const receiverAddress = `${selected.province || ''}${selected.city || ''}${selected.district || ''}${selected.detail || ''}`
    const res = await createOrder({
      receiverName: selected.receiverName,
      receiverPhone: selected.receiverPhone,
      receiverAddress
    })
    // 检查响应体中的业务错误码
    if (res.data?.code && res.data.code !== 200) {
      window.$message?.error(res.data?.msg || res.data?.message || '订单提交失败')
      return
    }
    window.$message?.success('订单提交成功')
    sessionStorage.removeItem('checkoutItems')
    await cartStore.fetchCart()
    router.push('/user/orders')
  } catch (e) {
    window.$message?.error(e?.response?.data?.msg || e?.response?.data?.message || '订单提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 进入结算页时：
  // - 先同步购物车（避免用户在别处改了购物车导致结算清单与服务端不一致）
  // - 再读取 checkoutItems
  // - 再拉地址（用于选择收货地址）
  loading.value = true
  await cartStore.fetchCart()
  loadCheckoutItems()
  await fetchAddresses()
  loading.value = false
})
</script>

<style scoped>
.checkout-page {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.section-title {
  font-size: 16px;
  margin: 0 0 16px;
}
.address-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.address-item {
  padding: 12px 16px;
  border: 2px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  min-width: 280px;
}
.address-item.active {
  border-color: #18a058;
  background: #f0fdf4;
}
.addr-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.addr-info .name {
  font-weight: 500;
}
.addr-detail {
  margin: 0;
  color: #666;
  font-size: 13px;
}
.goods-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.goods-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.goods-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}
.goods-info {
  flex: 1;
}
.goods-info h4 {
  margin: 0 0 4px;
  font-size: 14px;
}
.goods-price {
  margin: 0;
  color: #999;
  font-size: 13px;
}
.goods-total {
  font-weight: 500;
  color: #f5222d;
}
.order-summary {
  max-width: 300px;
  margin-left: auto;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}
.summary-row.total {
  font-size: 16px;
  font-weight: 500;
  border-top: 1px solid #eee;
  padding-top: 12px;
}
.summary-row .amount {
  color: #f5222d;
  font-size: 20px;
}
.submit-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}
</style>
