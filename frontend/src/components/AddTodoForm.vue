<template>
  <form @submit.prevent="$emit('submit')" class="add-form">
    <input 
      :value="modelValue" 
      @input="$emit('update:modelValue', $event.target.value)"
      placeholder="Add a new task..." 
      required 
    />
    <button type="submit" class="add-btn" :disabled="disabled">Add</button>
  </form>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

defineEmits(['submit', 'update:modelValue'])
</script>

<style scoped>
.add-form { display: flex; gap: 10px; margin-bottom: 20px; }
.add-form input { 
  flex: 1; 
  padding: 12px; 
  border: 2px solid #e0e0e0; 
  border-radius: 5px; 
  font-size: 16px; 
}
.add-btn { 
  padding: 12px 25px; 
  background: #667eea; 
  color: white; 
  border: none; 
  border-radius: 5px; 
  cursor: pointer; 
  font-weight: bold; 
}
.add-btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
