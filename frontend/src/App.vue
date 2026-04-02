<template>
  <div class="app">
    <header>
      <h1>📋 Oursky Todo</h1>
      <p>Smart Task Management with AI</p>
    </header>
    <TodoList />
    <footer>
      <p>Built with ❤️ by Oursky</p>
    </footer>
  </div>
</template>

<script setup>
import TodoList from './components/TodoList.vue'
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}
.app { max-width: 800px; margin: 0 auto; padding: 20px; }
header { text-align: center; color: white; margin-bottom: 30px; }
header h1 { font-size: 2.5rem; margin-bottom: 10px; }
header p { opacity: 0.9; }
footer { text-align: center; color: white; margin-top: 40px; opacity: 0.8; }
</style>
