<template>
  <transition name="modal">
    <div v-if="show" class="modal-overlay" @click.self="$emit('close')">
      <div class="modal">
        <div class="modal-header">
          <h3>🤖 AI Suggestions</h3>
          <button type="button" @click="$emit('close')">×</button>
        </div>
        <p v-if="context" class="modal-desc">
          Generating suggestions for: <strong>{{ context }}</strong>
        </p>

        <div v-if="loading" class="loading">
          <div class="spinner"></div>
          <p>Thinking...</p>
        </div>

        <div v-else-if="error" class="error">
          <p>{{ error }}</p>
          <button type="button" @click="$emit('retry')">Try Again</button>
        </div>

        <div v-else class="suggestions-list">
          <div 
            v-for="(sugg, index) in suggestions"
            :key="index"
            class="suggestion-item"
            :class="{ selected: selected.includes(index) }"
            @click="toggleSelect(index)"
          >
            <span>{{ sugg }}</span>
          </div>
          <div v-if="!suggestions.length" class="empty-state">
            <p>No suggestions available.</p>
          </div>
        </div>

        <div class="modal-actions">
          <button type="button" @click="$emit('close')" :disabled="loading">Cancel</button>
          <button 
            type="button" 
            @click="$emit('add', selected)"
            :disabled="!selected.length || loading || adding"
          >
            Add {{ selected.length }} Step{{ selected.length !== 1 ? 's' : '' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  context: { type: String, default: '' },
  suggestions: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  adding: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'retry', 'add', 'update:selected'])

const toggleSelect = (index) => {
  const newSelected = [...props.selected]
  const pos = newSelected.indexOf(index)
  if (pos > -1) {
    newSelected.splice(pos, 1)
  } else {
    newSelected.push(index)
  }
  emit('update:selected', newSelected)
}
</script>

<style scoped>
.modal-overlay { 
  position: fixed; 
  inset: 0; 
  background: rgba(0,0,0,0.6); 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  z-index: 1000; 
}

.modal-enter-active, .modal-leave-active { transition: opacity 0.3s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

.modal { 
  background: white; 
  border-radius: 15px; 
  width: 100%; 
  max-width: 500px; 
  max-height: 80vh; 
  overflow-y: auto; 
}

.modal-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  padding: 20px; 
  border-bottom: 1px solid #e0e0e0; 
}

.modal-header button { 
  background: none; 
  border: none; 
  font-size: 28px; 
  cursor: pointer; 
  line-height: 1; 
}

.modal-desc { 
  padding: 0 20px 10px; 
  color: #666; 
  font-style: italic; 
}

.modal-desc strong { color: #333; }

.suggestions-list { padding: 20px; }

.suggestion-item { 
  display: flex; 
  align-items: center; 
  padding: 15px; 
  margin: 10px 0; 
  background: #f8f9fa; 
  border-radius: 8px; 
  cursor: pointer; 
  border: 2px solid transparent; 
  transition: background 0.2s; 
}

.suggestion-item:hover { background: #f0f3ff; }
.suggestion-item.selected { border-color: #667eea; background: #f0f3ff; }

.modal-actions { 
  display: flex; 
  gap: 10px; 
  margin-top: 0; 
  padding: 20px; 
  border-top: 1px solid #e0e0e0; 
}

.modal-actions button { 
  flex: 1; 
  padding: 12px; 
  border: none; 
  border-radius: 5px; 
  cursor: pointer; 
  font-weight: bold; 
}

.modal-actions button:first-child { background: #f0f0f0; color: #666; }
.modal-actions button:last-child { background: #667eea; color: white; }
.modal-actions button:disabled { opacity: 0.5; cursor: not-allowed; }

.loading, .error { padding: 40px; text-align: center; }

.spinner { 
  width: 40px; 
  height: 40px; 
  border: 4px solid #f0f0f0; 
  border-top-color: #667eea; 
  border-radius: 50%; 
  animation: spin 1s linear infinite; 
  margin: 0 auto 15px; 
}

@keyframes spin { to { transform: rotate(360deg); } }

.error button {
  background: #667eea;
  color: white;
  padding: 10px 20px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  margin-top: 10px;
}

.empty-state { text-align: center; padding: 20px; color: #999; }
</style>
