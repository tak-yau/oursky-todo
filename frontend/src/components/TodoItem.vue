<template>
  <div class="todo-item" :class="{ 'item-completed': todo.completed }">
    <input 
      type="checkbox" 
      :checked="todo.completed" 
      @change="$emit('toggle')" 
      :disabled="disabled" 
    />
    
    <input 
      v-if="isEditing" 
      v-model="editText" 
      @keyup.enter="save" 
      @keyup.escape="cancel" 
      @blur="handleBlur"
      class="edit-input" 
      ref="editInput" 
      :disabled="disabled" 
    />
    <span v-else class="title" :class="{ 'text-completed': todo.completed }">{{ todo.title }}</span>
    
    <div class="actions">
      <button @click="startEdit" :disabled="disabled">✏️</button>
      <button @click="$emit('add-subtask')" :disabled="disabled">➕</button>
      <button @click="$emit('ai')" :disabled="disabled">🤖</button>
      <button @click="$emit('delete')" :disabled="disabled">🗑️</button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

const props = defineProps({
  todo: { type: Object, required: true },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle', 'delete', 'edit', 'add-subtask', 'ai'])

const isEditing = ref(false)
const editText = ref('')
const editInput = ref(null)
let blurTimer = null

const startEdit = () => {
  isEditing.value = true
  editText.value = props.todo.title
  nextTick(() => {
    editInput.value?.focus()
    editInput.value?.select()
  })
}

const cancel = () => {
  isEditing.value = false
  editText.value = ''
  clearTimeout(blurTimer)
}

const save = () => {
  if (editText.value.trim()) {
    emit('edit', editText.value.trim())
  }
  cancel()
}

const handleBlur = () => {
  blurTimer = setTimeout(() => {
    if (isEditing.value && editText.value.trim()) {
      save()
    } else {
      cancel()
    }
  }, 150)
}

watch(() => props.todo.id, () => {
  cancel()
})
</script>

<style scoped>
.todo-item { 
  display: flex; 
  align-items: center; 
  padding: 15px; 
  border-bottom: 1px solid #f0f0f0; 
  gap: 10px; 
  transition: all 0.3s ease;
  border-radius: 4px;
}

.todo-item.item-completed {
  background: #f0fdf4;
  opacity: 0.75;
  border-left: 4px solid #10b981;
}

.todo-item.completed .title,
.title.text-completed { 
  text-decoration: line-through; 
  color: #9ca3af; 
}

.todo-item.item-completed .title {
  color: #6b7280;
}

.title { flex: 1; font-size: 16px; transition: all 0.3s ease; }
.edit-input { flex: 1; padding: 8px; border: 2px solid #667eea; border-radius: 5px; font-size: 16px; }
.edit-input:disabled { background: #f5f5f5; }

.actions { display: flex; gap: 5px; }
.actions button { background: none; border: none; cursor: pointer; font-size: 16px; padding: 4px; border-radius: 4px; transition: background 0.2s; }
.actions button:hover { background: #f0f0f0; }
.actions button:disabled { opacity: 0.5; cursor: not-allowed; }

input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #667eea;
}

.todo-item.item-completed input[type="checkbox"] {
  accent-color: #10b981;
}
</style>
