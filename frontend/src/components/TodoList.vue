<template>
  <div class="todo-container">
    <!-- Notification Toast -->
    <transition name="toast">
      <div v-if="notification.show" :class="['toast', notification.type]">
        <span>{{ notification.message }}</span>
        <button @click="notification.show = false">×</button>
      </div>
    </transition>

    <!-- Add Task Form -->
    <form @submit.prevent="addTodo" class="add-form">
      <input v-model="newTodo" placeholder="Add a new task..." required />
      <button type="submit" class="add-btn" :disabled="loading">Add</button>
    </form>

    <!-- Filters -->
    <div class="filters">
      <button 
        v-for="f in filters" 
        :key="f.value"
        :class="['filter-btn', { active: filter === f.value }]" 
        @click="setFilter(f.value)">
        {{ f.label }}
      </button>
    </div>

    <!-- Task List -->
    <ul class="todo-list">
      <li v-for="todo in filteredTodos" 
          :key="`todo-${todo.id}-${todo.completed}`" 
          :class="{ completed: todo.completed }">
        <div class="todo-item" :class="{ 'item-completed': todo.completed }">
          <input type="checkbox" 
                 :checked="todo.completed" 
                 @change="toggleTodo(todo)" 
                 :disabled="loading" />
          
          <input v-if="editingId === todo.id" v-model="editText" 
                 @keyup.enter="updateTodo(todo, 'title', editText)" 
                 @keyup.escape="cancelEdit" @blur="handleBlur(todo)"
                 class="edit-input" ref="editInput" :disabled="loading" />
          <span v-else class="title" :class="{ 'text-completed': todo.completed }">{{ todo.title }}</span>
          
          <div class="actions">
            <button @click="startEdit(todo)" :disabled="loading">✏️</button>
            <button @click="openAddForm(todo.id)" :disabled="loading">➕</button>
            <button @click="openAiModal(todo)" :disabled="loading">🤖</button>
            <button @click="deleteItem(todo.id)" :disabled="loading">🗑️</button>
          </div>
        </div>
        
        <!-- Add Subtask Form (direct child of root todo only) -->
        <div v-if="addFormTodo && addFormTodo.id === todo.id && addParentId === null" class="add-subtask-form">
          <input v-model="newSubtask" 
                 @keyup.enter="submitSubtask(addFormTodo.id, addParentId)" 
                 :disabled="loading" 
                 placeholder="Subtask..." 
                 ref="subtaskInput" />
          <button type="button" @click="submitSubtask(addFormTodo.id, addParentId)" :disabled="loading">Add</button>
          <button type="button" @click="closeAddForm()">Cancel</button>
        </div>
        
        <!-- Recursive Subtasks -->
        <SubtaskList v-if="todo.subtasks?.length" 
                     :subtasks="todo.subtasks" 
                     :todoId="todo.id" 
                     :todo="todo"
                     :form-parent-subtask-id="addFormTodo?.id === todo.id ? addParentId : null"
                     :add-draft="newSubtask"
                     :loading="loading"
                     :completion-filter="filter"
                     @toggle="toggleSubtask" 
                     @delete="deleteSubtask" 
                     @edit="editSubtask"
                     @add="(todoId, parentId) => openAddForm(todoId, parentId)" 
                     @update:add-draft="newSubtask = $event"
                     @submit-add="submitSubtask(addFormTodo.id, addParentId)"
                     @cancel-add="closeAddForm"
                     @ai="openAiModal"
                     :depth="1" />
      </li>
    </ul>

    <div v-if="!todos.length" class="empty-state">
      <p>No tasks yet! Add one above.</p>
    </div>

    <transition name="modal">
      <div v-if="showAiModal" class="modal-overlay" @click.self="showAiModal = false">
        <div class="modal">
          <div class="modal-header">
            <h3>🤖 AI Suggestions</h3>
            <button type="button" @click="showAiModal = false">×</button>
          </div>
          <p v-if="aiContext" class="modal-desc">Generating suggestions for: <strong>{{ aiContext }}</strong></p>

          <div v-if="aiLoading" class="loading">
            <div class="spinner"></div>
            <p>Thinking...</p>
          </div>

          <div v-else-if="aiError" class="error">
            <p>{{ aiError }}</p>
            <button type="button" @click="fetchAiSuggestions">Try Again</button>
          </div>

          <div v-else class="suggestions-list">
            <div v-for="(sugg, index) in aiSuggestions"
                 :key="index"
                 class="suggestion-item"
                 :class="{ selected: aiSelected.includes(index) }"
                 @click="toggleAiSelect(index)">
              <span>{{ sugg }}</span>
            </div>
            <div v-if="!aiSuggestions.length" class="empty-state">
              <p>No suggestions available.</p>
            </div>
          </div>

          <div class="modal-actions">
            <button type="button" @click="showAiModal = false" :disabled="aiLoading">Cancel</button>
            <button type="button" @click="addAiSubtasks"
                    :disabled="!aiSelected.length || aiLoading || aiAdding">
              Add {{ aiSelected.length }} Step{{ aiSelected.length !== 1 ? 's' : '' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Filter Info -->
    <div class="filter-info" v-if="filter !== 'all'">
      <span>Showing: <strong>{{ filter }}</strong> tasks</span>
      <button @click="filter = 'all'" class="clear-filter">Clear Filter</button>
    </div>

    <!-- Stats -->
    <div class="stats">
      <span>{{ todos.filter(t => !t.completed).length }} active</span>
      <span>{{ todos.filter(t => t.completed).length }} completed</span>
      <span>{{ todos.length }} total</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import axios from 'axios'
import SubtaskList from './SubtaskList.vue'

// ============ STATE ============
const todos = ref([])
const newTodo = ref('')
const filter = ref('all')
const notification = ref({ show: false, message: '', type: 'success' })
const loading = ref(false)

// Filter options
const filters = [
  { value: 'all', label: 'All' },
  { value: 'active', label: 'Active' },
  { value: 'completed', label: 'Completed' }
]

// Edit state
const editingId = ref(null)
const editText = ref('')
const editInput = ref(null)

// Add subtask form
const addFormTodo = ref(null)
const addParentId = ref(null)
const newSubtask = ref('')
const subtaskInput = ref(null)

/** Root task level 0; subtasks are levels 1–3 (deepest row is level 3). */
const MAX_SUBTASK_LEVEL = 3

const showAiModal = ref(false)
const aiSuggestions = ref([])
const aiSelected = ref([])
const aiLoading = ref(false)
const aiError = ref('')
const aiContext = ref('')
const aiTarget = ref({ todo: null, subtask: null })
const aiAdding = ref(false)

// Blur timeout
let blurTimer = null

// ============ HELPERS ============
const notify = (message, type = 'success') => {
  notification.value = { show: true, message, type }
  setTimeout(() => notification.value.show = false, 3000)
}

const createSubtask = (id, title, completed = false, parentId = null) => ({
  id, title, completed: !!completed, parentId, subtasks: []
})

/** Deep clone subtasks so nested SubtaskList props get new references and Vue re-renders (class bindings, etc.). */
const cloneSubtaskTree = (items) => {
  if (!Array.isArray(items) || !items.length) return []
  return items.map((s) => ({
    id: s.id,
    title: s.title,
    completed: !!s.completed,
    ...(s.parentId != null ? { parentId: s.parentId } : {}),
    subtasks: cloneSubtaskTree(s.subtasks || [])
  }))
}

const findSubtask = (items, id) => {
  for (const item of items) {
    if (item.id === id) return item
    if (item.subtasks?.length) {
      const found = findSubtask(item.subtasks, id)
      if (found) return found
    }
  }
  return null
}

const removeSubtask = (items, id) => {
  return items.filter(item => {
    if (item.id === id) return false
    if (item.subtasks?.length) item.subtasks = removeSubtask(item.subtasks, id)
    return true
  })
}

// ============ FILTER ============
const setFilter = (value) => {
  filter.value = value
}

// ============ API OPERATIONS ============
const api = {
  get: async (url) => (await axios.get(url)).data,
  post: async (url, data) => (await axios.post(url, data)).data,
  put: async (url, data) => (await axios.put(url, data)).data,
  delete: async (url) => await axios.delete(url)
}

// ============ TODO OPERATIONS ============
const fetchTodos = async () => {
  try {
    const data = await api.get('/api/todos')
    todos.value = Array.isArray(data)
      ? data.map((t) => ({ ...t, subtasks: cloneSubtaskTree(t.subtasks || []) }))
      : []
  } catch (err) { notify('Failed to load tasks', 'error') }
}

const addTodo = async () => {
  if (!newTodo.value.trim() || loading.value) return
  loading.value = true
  try {
    const res = await api.post('/api/todos', { title: newTodo.value.trim() })
    todos.value.push(createSubtask(res.id, newTodo.value.trim(), res.completed))
    newTodo.value = ''
    notify('Task added!')
  } catch (err) { notify('Failed to add task', 'error') }
  finally { loading.value = false }
}

const toggleTodo = async (todo) => {
  if (loading.value) return
  const original = todo.completed
  
  try {
    loading.value = true
    todo.completed = !original
    await api.put(`/api/todos/${todo.id}`, { completed: todo.completed })
    notify(todo.completed ? 'Task completed! 🎉' : 'Task reopened')
  } catch (err) {
    todo.completed = original
    notify('Failed to update task', 'error')
  } finally { loading.value = false }
}

const updateTodo = async (todo, field, value) => {
  if (loading.value || field === 'completed') return
  const original = { id: todo.id, [field]: todo[field] }
  
  try {
    loading.value = true
    await api.put(`/api/todos/${todo.id}`, { [field]: value })
    todo[field] = value
    notify(field === 'title' ? 'Task updated!' : 'Task updated')
    cancelEdit()
  } catch (err) {
    todo[field] = original[field]
    notify('Failed to update', 'error')
    cancelEdit()
  } finally { loading.value = false }
}

const deleteItem = async (id) => {
  try {
    await api.delete(`/api/todos/${id}`)
    todos.value = todos.value.filter(t => t.id !== id)
    notify('Task deleted')
  } catch (err) { notify('Failed to delete', 'error') }
}

// ============ EDIT OPERATIONS ============
const startEdit = (todo) => {
  editingId.value = todo.id
  editText.value = todo.title
  nextTick(() => { editInput.value?.focus(); editInput.value?.select() })
}

const cancelEdit = () => {
  editingId.value = null
  editText.value = ''
  clearTimeout(blurTimer)
}

const handleBlur = (todo) => {
  blurTimer = setTimeout(() => {
    if (editingId.value === todo.id && editText.value.trim()) {
      updateTodo(todo, 'title', editText.value.trim())
    } else { cancelEdit() }
  }, 150)
}

// ============ ADD SUBTASK FORM ============
const depthOfSubtaskId = (items, id, level = 1) => {
  if (!items?.length) return null
  for (const item of items) {
    if (item.id === id) return level
    if (item.subtasks?.length) {
      const d = depthOfSubtaskId(item.subtasks, id, level + 1)
      if (d != null) return d
    }
  }
  return null
}

const openAddForm = (todoId, parentId = null) => {
  const todo = todos.value.find(t => t.id === todoId)
  if (!todo) return
  if (parentId != null) {
    const pDepth = depthOfSubtaskId(todo.subtasks || [], parentId)
    if (pDepth == null || pDepth >= MAX_SUBTASK_LEVEL) return
  }
  addFormTodo.value = todo
  addParentId.value = parentId
  newSubtask.value = ''
  nextTick(() => {
    if (parentId != null) return
    const v = subtaskInput.value
    const el = Array.isArray(v) ? v.find((x) => x && typeof x.focus === 'function') : v
    el?.focus()
  })
}

const closeAddForm = () => {
  addFormTodo.value = null
  addParentId.value = null
  newSubtask.value = ''
}

const submitSubtask = (todoId, parentId) => {
  addSubtask({ todoId, parentId, title: newSubtask.value })
}

// ============ SUBTASK OPERATIONS ============
const addSubtask = async ({ todoId, parentId, title }) => {
  if (!title?.trim() || loading.value) return
  const todo = todos.value.find(t => t.id === todoId)
  if (!todo) { notify('Task not found', 'error'); return }
  if (parentId != null) {
    const pDepth = depthOfSubtaskId(todo.subtasks || [], parentId)
    if (pDepth == null || pDepth >= MAX_SUBTASK_LEVEL) {
      notify('Maximum depth is 3 subtask levels under a task', 'error')
      return
    }
  }
  loading.value = true
  
  try {
    const updated = await api.post(`/api/todos/${todoId}/subtasks`, {
      subtaskTitle: title.trim(), parentId: parentId != null && parentId !== '' ? parentId : null
    })
    todo.subtasks = cloneSubtaskTree(updated.subtasks || [])
    notify('Subtask added!')
    closeAddForm()
  } catch (err) { 
    notify('Failed to add subtask', 'error')
    console.error('Add subtask error:', err)
  } finally { loading.value = false }
}

const toggleSubtask = async ({ todoId, subtask }) => {
  if (loading.value) return
  const original = !!subtask.completed
  const todo = todos.value.find((t) => t.id === todoId)
  try {
    loading.value = true
    subtask.completed = !original
    await api.put(`/api/todos/${todoId}/subtasks/${subtask.id}`, { completed: subtask.completed })
    if (todo?.subtasks) todo.subtasks = cloneSubtaskTree(todo.subtasks)
    notify(subtask.completed ? 'Subtask completed! 🎉' : 'Subtask reopened')
  } catch (err) {
    subtask.completed = original
    if (todo?.subtasks) todo.subtasks = cloneSubtaskTree(todo.subtasks)
    notify('Failed to update subtask', 'error')
  } finally { loading.value = false }
}

const editSubtask = async ({ todoId, subtaskId, title }) => {
  if (!title?.trim() || loading.value) return
  try {
    loading.value = true
    await api.put(`/api/todos/${todoId}/subtasks/${subtaskId}`, { title: title.trim() })
    const subtask = findSubtask(todos.value, subtaskId)
    if (subtask) subtask.title = title.trim()
    const todo = todos.value.find((t) => t.id === todoId)
    if (todo?.subtasks) todo.subtasks = cloneSubtaskTree(todo.subtasks)
    notify('Subtask updated!')
  } catch (err) { notify('Failed to update subtask', 'error') }
  finally { loading.value = false }
}

const deleteSubtask = async ({ todoId, subtaskId }) => {
  try {
    await api.delete(`/api/todos/${todoId}/subtasks/${subtaskId}`)
    const todo = todos.value.find(t => t.id === todoId)
    if (todo?.subtasks) todo.subtasks = removeSubtask(todo.subtasks, subtaskId)
    notify('Subtask deleted')
  } catch (err) { notify('Failed to delete subtask', 'error') }
}

const openAiModal = (target) => {
  aiTarget.value = { todo: target.todo || target, subtask: target.subtask || null }
  aiContext.value = target.subtask?.title || target.todo?.title || target.title
  showAiModal.value = true
  fetchAiSuggestions()
}

const fetchAiSuggestions = async () => {
  aiLoading.value = true
  aiError.value = ''
  aiSelected.value = []
  try {
    const res = await api.post('/api/ai/suggestions', {
      title: aiContext.value,
      taskId: aiTarget.value.todo?.id,
      subtaskId: aiTarget.value.subtask?.id
    })
    aiSuggestions.value = res.suggestions || []
  } catch (err) {
    aiError.value = err.response?.data?.error || 'Failed to get suggestions'
  } finally { aiLoading.value = false }
}

const toggleAiSelect = (index) => {
  const pos = aiSelected.value.indexOf(index)
  pos > -1 ? aiSelected.value.splice(pos, 1) : aiSelected.value.push(index)
}

const addAiSubtasks = async () => {
  if (!aiSelected.value.length || !aiTarget.value.todo || aiAdding.value) return
  aiAdding.value = true
  try {
    const todo = todos.value.find(t => t.id === aiTarget.value.todo.id)
    if (!todo) { notify('Task not found', 'error'); return }

    const parentId = aiTarget.value.subtask?.id
    const bodyParentId = parentId != null && parentId !== '' ? parentId : null

    for (const i of aiSelected.value) {
      const updated = await api.post(`/api/todos/${todo.id}/subtasks`, {
        subtaskTitle: aiSuggestions.value[i],
        parentId: bodyParentId
      })
      todo.subtasks = cloneSubtaskTree(updated.subtasks || [])
    }

    showAiModal.value = false
    notify(`${aiSelected.value.length} steps added! 🤖`)
  } catch (err) { notify('Failed to add steps', 'error') }
  finally { aiAdding.value = false }
}

// ============ COMPUTED ============
/** Root tasks: include only if the parent matches the filter; SubtaskList applies the same rule recursively to children. */
const filteredTodos = computed(() => {
  const allTodos = todos.value || []
  if (filter.value === 'active') {
    return allTodos.filter((t) => !t.completed)
  }
  if (filter.value === 'completed') {
    return allTodos.filter((t) => t.completed)
  }
  return allTodos
})

// ============ LIFECYCLE ============
onMounted(fetchTodos)
</script>

<style scoped>
.todo-container { 
  background: white; 
  border-radius: 10px; 
  padding: 25px; 
  box-shadow: 0 10px 40px rgba(0,0,0,0.2); 
  max-width: 800px; 
  margin: 40px auto; 
}

.add-form { display: flex; gap: 10px; margin-bottom: 20px; }
.add-form input { flex: 1; padding: 12px; border: 2px solid #e0e0e0; border-radius: 5px; font-size: 16px; }
.add-btn { padding: 12px 25px; background: #667eea; color: white; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; }
.add-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* FILTERS - FIXED */
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

.todo-list { list-style: none; padding: 0; }
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

.add-subtask-form { display: flex; gap: 5px; margin: 5px 0 10px 0; padding: 10px; background: #f8f9fa; border-radius: 8px; }
.add-subtask-form input { flex: 1; padding: 8px; border: 2px solid #e0e0e0; border-radius: 4px; }
.add-subtask-form button { padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer; }
.add-subtask-form button:first-of-type { background: #10b981; color: white; }
.add-subtask-form button:last-of-type { background: #e0e0e0; color: #666; }

.empty-state { text-align: center; padding: 40px; color: #999; }

.filter-info { 
  display: flex; 
  justify-content: space-between; 
  align-items: center;
  padding: 10px 15px; 
  background: #f0f3ff; 
  border-radius: 8px; 
  margin: 15px 0;
  color: #667eea;
}

.filter-info button {
  background: #667eea;
  color: white;
  border: none;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.stats { 
  display: flex; 
  justify-content: space-between; 
  margin-top: 20px; 
  padding-top: 20px; 
  border-top: 1px solid #f0f0f0; 
  color: #666; 
  font-size: 14px; 
}

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.3s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal { background: white; border-radius: 15px; width: 100%; max-width: 500px; max-height: 80vh; overflow-y: auto; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 20px; border-bottom: 1px solid #e0e0e0; }
.modal-header button { background: none; border: none; font-size: 28px; cursor: pointer; line-height: 1; }
.modal-desc { padding: 0 20px 10px; color: #666; font-style: italic; }
.modal-desc strong { color: #333; }
.suggestions-list { padding: 20px; }
.suggestion-item { display: flex; align-items: center; padding: 15px; margin: 10px 0; background: #f8f9fa; border-radius: 8px; cursor: pointer; border: 2px solid transparent; transition: background 0.2s; }
.suggestion-item:hover { background: #f0f3ff; }
.suggestion-item.selected { border-color: #667eea; background: #f0f3ff; }
.modal-actions { display: flex; gap: 10px; margin-top: 0; padding: 20px; border-top: 1px solid #e0e0e0; }
.modal-actions button { flex: 1; padding: 12px; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; }
.modal-actions button:first-child { background: #f0f0f0; color: #666; }
.modal-actions button:last-child { background: #667eea; color: white; }
.modal-actions button:disabled { opacity: 0.5; cursor: not-allowed; }
.loading, .error { padding: 40px; text-align: center; }
.spinner { width: 40px; height: 40px; border: 4px solid #f0f0f0; border-top-color: #667eea; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 15px; }
@keyframes spin { to { transform: rotate(360deg); } }

.toast { position: fixed; top: 20px; right: 20px; padding: 15px 25px; border-radius: 8px; color: white; font-weight: bold; z-index: 2000; }
.toast.success { background: #10b981; }
.toast.error { background: #ef4444; }
.toast button { background: none; border: none; color: white; font-size: 20px; cursor: pointer; margin-left: 10px; }
.toast-enter-active, .toast-leave-active { transition: opacity 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; }

input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #667eea;
}

.todo-item.item-completed input[type="checkbox"] {
  accent-color: #10b981;
}

/* Subtask rows live in child components; :deep reaches all nesting depths. */
:deep(ul.subtask-list .subtask-item.item-completed) {
  background: #f0fdf4;
  opacity: 0.75;
  border-left: 4px solid #10b981 !important;
}
:deep(ul.subtask-list .subtask-title.text-completed) {
  text-decoration: line-through;
  color: #9ca3af;
}
:deep(ul.subtask-list .subtask-item.item-completed .subtask-title) {
  color: #6b7280;
}
:deep(ul.subtask-list .subtask-item.item-completed input[type="checkbox"]) {
  accent-color: #10b981;
}
</style>
