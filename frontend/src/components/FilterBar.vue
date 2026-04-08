<template>
  <div class="filters">
    <button 
      v-for="f in filters" 
      :key="f.value"
      :class="['filter-btn', { active: modelValue === f.value }]" 
      @click="$emit('update:modelValue', f.value)"
    >
      {{ f.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: 'all' }
})

defineEmits(['update:modelValue'])

const filters = [
  { value: 'all', label: 'All' },
  { value: 'active', label: 'Active' },
  { value: 'completed', label: 'Completed' }
]
</script>

<style scoped>
.filters { 
  display: flex; 
  gap: 10px; 
  margin-bottom: 20px; 
  padding: 5px;
  background: #f5f5f5;
  border-radius: 25px;
  width: fit-content;
}

.filter-btn { 
  padding: 10px 20px; 
  border: none; 
  background: transparent; 
  border-radius: 20px; 
  cursor: pointer;
  font-weight: 500;
  color: #666;
  transition: all 0.3s ease;
}

.filter-btn:hover { 
  background: #e0e0e0; 
  color: #333;
}

.filter-btn.active { 
  background: #667eea; 
  color: white;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
}
</style>
