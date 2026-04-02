<template>
  <ul v-show="displayedSubtasks.length > 0" class="subtask-list" :class="`depth-${depth}`">
    <li v-for="subtask in displayedSubtasks" 
        :key="`subtask-${subtask.id}-${subtask.completed}`" 
        :class="{ completed: subtask.completed }">
      <div class="subtask-item" 
           :class="{ 'item-completed': subtask.completed }">
        <input type="checkbox" 
               :checked="subtask.completed" 
               @change="handleToggle(subtask)" 
               :disabled="loading" />
        
        <input v-if="editingState[subtask.id]" 
               v-model="editingState[subtask.id]" 
               @keyup.enter="saveEdit(subtask)" 
               @keyup.escape="cancelEdit(subtask)" 
               @blur="handleBlur(subtask)"
               class="edit-input" 
               :ref="el => setEditRef(subtask.id, el)" 
               :disabled="loading" />
        <span v-else class="subtask-title" :class="{ 'text-completed': subtask.completed }">{{ subtask.title }}</span>
        
        <div class="actions">
          <button @click="startEdit(subtask)" :disabled="loading">✏️</button>
          <button v-if="depth < MAX_SUBTASK_LEVEL" @click="handleAdd(subtask)" :disabled="loading">➕</button>
          <button v-if="depth < MAX_SUBTASK_LEVEL" @click="$emit('ai', { todo: { id: todoId }, subtask })" :disabled="loading">🤖</button>
          <button @click="$emit('delete', { todoId, subtaskId: subtask.id })" :disabled="loading">🗑️</button>
        </div>
      </div>

      <div v-if="formParentSubtaskId === subtask.id" class="add-subtask-form">
        <input
          :value="addDraft"
          type="text"
          placeholder="Subtask..."
          :disabled="loading"
          :ref="(el) => focusAddInput(el, subtask.id)"
          @input="emit('update:addDraft', $event.target.value)"
          @keyup.enter="emit('submitAdd')" />
        <button type="button" @click="emit('submitAdd')" :disabled="loading">Add</button>
        <button type="button" @click="emit('cancelAdd')">Cancel</button>
      </div>
      
      <SubtaskList v-if="subtask.subtasks?.length && depth < MAX_SUBTASK_LEVEL" 
                   :subtasks="subtask.subtasks" 
                   :todoId="todoId" 
                   :parentId="subtask.id"
                   :todo="todo"
                   :form-parent-subtask-id="formParentSubtaskId"
                   :add-draft="addDraft"
                   :loading="loading"
                   :completion-filter="completionFilter"
                   @toggle="$emit('toggle', $event)" 
                   @delete="$emit('delete', $event)"
                   @edit="$emit('edit', $event)" 
                   @add="(tid, pid) => emit('add', tid, pid)"
                   @update:add-draft="emit('update:addDraft', $event)"
                   @submit-add="emit('submitAdd')"
                   @cancel-add="emit('cancelAdd')"
                   @ai="$emit('ai', $event)"
                   :depth="depth + 1" />
    </li>
  </ul>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import SubtaskList from './SubtaskList.vue'

/** Root task is level 0; first subtasks use depth 1; deepest allowed subtask level is 3. */
const MAX_SUBTASK_LEVEL = 3

const props = defineProps({
  subtasks: { type: Array, required: true },
  todoId: { type: Number, required: true },
  parentId: { type: Number, default: null },
  todo: { type: Object, default: null },
  depth: { type: Number, default: 1 },
  formParentSubtaskId: { type: Number, default: null },
  addDraft: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  /** 'all' | 'active' (!completed) | 'completed' — same as root todo filter; applied per level after parent passes. */
  completionFilter: { type: String, default: 'all' }
})

const emit = defineEmits(['toggle', 'delete', 'edit', 'add', 'ai', 'update:addDraft', 'submitAdd', 'cancelAdd'])

const displayedSubtasks = computed(() => {
  const items = props.subtasks || []
  const f = props.completionFilter
  if (!f || f === 'all') return items
  if (f === 'active') return items.filter((s) => !s.completed)
  if (f === 'completed') return items.filter((s) => s.completed)
  return items
})

const editingState = ref({})
const editRefs = ref({})
const blurTimers = ref({})

const handleAdd = (subtask) => {
  emit('add', props.todoId, subtask.id)
}

const focusAddInput = (el, subtaskId) => {
  if (props.formParentSubtaskId === subtaskId && el) {
    nextTick(() => el.focus())
  }
}

const startEdit = (subtask) => {
  editingState.value[subtask.id] = subtask.title
  nextTick(() => {
    editRefs.value[subtask.id]?.focus()
    editRefs.value[subtask.id]?.select()
  })
}

const cancelEdit = (subtask) => {
  delete editingState.value[subtask.id]
  delete editRefs.value[subtask.id]
  if (blurTimers.value[subtask.id]) {
    clearTimeout(blurTimers.value[subtask.id])
    delete blurTimers.value[subtask.id]
  }
}

const saveEdit = (subtask) => {
  const title = editingState.value[subtask.id]?.trim()
  if (title) {
    emit('edit', { todoId: props.todoId, subtaskId: subtask.id, title })
  }
  cancelEdit(subtask)
}

const handleBlur = (subtask) => {
  blurTimers.value[subtask.id] = setTimeout(() => {
    if (editingState.value[subtask.id]) {
      saveEdit(subtask)
    }
  }, 150)
}

const setEditRef = (subtaskId, el) => {
  if (el) editRefs.value[subtaskId] = el
}

const handleToggle = (subtask) => {
  if (props.loading) return
  emit('toggle', { todoId: props.todoId, subtask })
}
</script>

<style scoped>
.subtask-list { list-style: none; padding-left: 30px; margin: 10px 0; }

.subtask-item { 
  display: flex; 
  align-items: center; 
  padding: 10px; 
  border-bottom: 1px solid #f0f0f0; 
  gap: 10px;
  transition: all 0.3s ease;
  border-radius: 4px;
}

.subtask-title { 
  flex: 1; 
  font-size: 14px; 
  transition: all 0.3s ease; 
}

.edit-input { 
  flex: 1; 
  padding: 6px; 
  border: 2px solid #667eea; 
  border-radius: 4px; 
  font-size: 14px; 
}
.edit-input:disabled { background: #f5f5f5; }

.actions { display: flex; gap: 5px; }
.actions button { 
  background: none; 
  border: none; 
  cursor: pointer; 
  font-size: 14px; 
  padding: 4px; 
  border-radius: 4px; 
  transition: background 0.2s;
}
.actions button:hover { background: #f0f0f0; }
.actions button:disabled { opacity: 0.5; cursor: not-allowed; }

input[type="checkbox"] { 
  cursor: pointer; 
  width: 16px; 
  height: 16px;
  transition: all 0.2s;
}
input[type="checkbox"]:disabled { cursor: not-allowed; }

/* Depth indicators */
.depth-1 { border-left: 3px solid #667eea; }
.depth-2 { border-left: 3px solid #10b981; }
.depth-3 { border-left: 3px solid #764ba2; }

.add-subtask-form {
  display: flex;
  gap: 5px;
  margin: 5px 0 10px 0;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 8px;
}
.add-subtask-form input {
  flex: 1;
  padding: 8px;
  border: 2px solid #e0e0e0;
  border-radius: 4px;
}
.add-subtask-form button { padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer; }
.add-subtask-form button:first-of-type { background: #10b981; color: white; }
.add-subtask-form button:last-of-type { background: #e0e0e0; color: #666; }
</style>
