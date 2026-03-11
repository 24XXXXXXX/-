<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
    <n-loading-bar-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-message-provider>
            <n-global-style />
            <GlobalMessageSetup />
            <router-view />
          </n-message-provider>
        </n-notification-provider>
      </n-dialog-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>

<script setup>
import { zhCN, dateZhCN, useMessage } from 'naive-ui'
import { defineComponent, h } from 'vue'

// 创建一个组件来设置全局 $message
const GlobalMessageSetup = defineComponent({
  setup() {
    const message = useMessage()
    window.$message = message
    return () => null
  }
})
</script>
