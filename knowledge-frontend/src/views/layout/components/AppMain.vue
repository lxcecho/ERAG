<!--
 * @since: 2026-08-01
 * @author: lxcechoo@gmail.com
-->

<template>
  <section class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade-transform" mode="out-in">
        <component :is="Component" :key="route.fullPath" />
      </transition>
    </router-view>
  </section>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped lang="scss">
.app-main {
  flex: 1;
  overflow: auto;
  background-color: #f0f2f5;
}
</style>
