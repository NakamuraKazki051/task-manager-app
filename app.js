const STORAGE_KEY = 'task-manager-app:tasks';
const STATUSES = ['todo', 'doing', 'done'];
const PRIORITY_LABEL = { high: '高', mid: '中', low: '低' };
const PRIORITY_ORDER = { high: 0, mid: 1, low: 2 };
const TAG_COLORS = ['sky', 'lime', 'green', 'red', 'azure', 'purple', 'yellow', 'orange', 'pink', 'slate'];

function tagColorClass(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return TAG_COLORS[hash % TAG_COLORS.length];
}

let tasks = loadTasks();
let editingId = null;
let currentChecklist = [];

function loadTasks() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('Failed to load tasks from localStorage', e);
    return [];
  }
}

function saveTasks() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
  } catch (e) {
    console.error('Failed to save tasks to localStorage', e);
    alert('保存に失敗しました。ブラウザのストレージ容量を確認してください。');
  }
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

function parseCategories(str) {
  return str.split(',').map(s => s.trim()).filter(Boolean);
}

function isOverdue(task) {
  if (!task.dueDate || task.status === 'done') return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(task.dueDate) < today;
}

function getAllCategories() {
  const set = new Set();
  tasks.forEach(t => (t.categories || []).forEach(c => set.add(c)));
  return [...set].sort();
}

function render() {
  const filterCategory = document.getElementById('filterCategory').value;
  const filterPriority = document.getElementById('filterPriority').value;

  renderCategoryFilterOptions(filterCategory);

  STATUSES.forEach(status => {
    const list = document.getElementById(`list-${status}`);
    list.innerHTML = '';

    let filtered = tasks.filter(t => t.status === status);
    if (filterCategory) filtered = filtered.filter(t => (t.categories || []).includes(filterCategory));
    if (filterPriority) filtered = filtered.filter(t => t.priority === filterPriority);

    filtered.sort((a, b) => {
      const pd = PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
      if (pd !== 0) return pd;
      if (a.dueDate && b.dueDate) return a.dueDate.localeCompare(b.dueDate);
      if (a.dueDate) return -1;
      if (b.dueDate) return 1;
      return 0;
    });

    document.getElementById(`count-${status}`).textContent = filtered.length;

    if (filtered.length === 0) {
      const hint = document.createElement('div');
      hint.className = 'empty-hint';
      hint.textContent = 'タスクはありません';
      list.appendChild(hint);
    }

    filtered.forEach(task => list.appendChild(renderCard(task)));
  });
}

function renderCategoryFilterOptions(selectedValue) {
  const select = document.getElementById('filterCategory');
  const categories = getAllCategories();
  select.innerHTML = '<option value="">全カテゴリ</option>' +
    categories.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
  select.value = categories.includes(selectedValue) ? selectedValue : '';
}

function renderCard(task) {
  const card = document.createElement('div');
  card.className = 'task-card';
  card.draggable = true;
  card.dataset.id = task.id;

  const overdue = isOverdue(task);
  const dueLabel = task.dueDate ? formatDate(task.dueDate) : '';
  const checklist = task.checklist || [];
  const checklistDone = checklist.filter(i => i.done).length;

  card.innerHTML = `
    <div class="task-card-title"></div>
    ${task.description ? '<div class="task-card-desc"></div>' : ''}
    <div class="task-meta">
      <span class="badge ${task.priority}">${PRIORITY_LABEL[task.priority]}</span>
      ${task.dueDate ? `<span class="badge due ${overdue ? 'overdue' : ''}">${dueLabel}${overdue ? ' (期限超過)' : ''}</span>` : ''}
      ${checklist.length ? `<span class="badge checklist ${checklistDone === checklist.length ? 'complete' : ''}">✓ ${checklistDone}/${checklist.length}</span>` : ''}
      ${(task.categories || []).map(c => `<span class="tag ${tagColorClass(c)}"></span>`).join('')}
    </div>
  `;

  card.querySelector('.task-card-title').textContent = task.title;
  if (task.description) {
    card.querySelector('.task-card-desc').textContent = task.description;
  }
  const tagEls = card.querySelectorAll('.tag');
  (task.categories || []).forEach((c, i) => { tagEls[i].textContent = c; });

  card.addEventListener('click', () => openModal(task));
  card.addEventListener('dragstart', (e) => {
    card.classList.add('dragging');
    e.dataTransfer.setData('text/plain', task.id);
    e.dataTransfer.effectAllowed = 'move';
  });
  card.addEventListener('dragend', () => card.classList.remove('dragging'));

  return card;
}

function formatDate(isoDate) {
  const [y, m, d] = isoDate.split('-');
  return `${m}/${d}`;
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function setupDropZones() {
  STATUSES.forEach(status => {
    const column = document.querySelector(`.column[data-status="${status}"]`);
    column.addEventListener('dragover', (e) => {
      e.preventDefault();
      column.classList.add('drag-over');
    });
    column.addEventListener('dragleave', () => column.classList.remove('drag-over'));
    column.addEventListener('drop', (e) => {
      e.preventDefault();
      column.classList.remove('drag-over');
      const id = e.dataTransfer.getData('text/plain');
      const task = tasks.find(t => t.id === id);
      if (task && task.status !== status) {
        task.status = status;
        saveTasks();
        render();
      }
    });
  });
}

function openModal(task) {
  editingId = task ? task.id : null;
  document.getElementById('modalTitle').textContent = task ? 'タスク編集' : 'タスク追加';
  document.getElementById('taskId').value = task ? task.id : '';
  document.getElementById('taskTitle').value = task ? task.title : '';
  document.getElementById('taskDesc').value = task ? (task.description || '') : '';
  document.getElementById('taskDue').value = task ? (task.dueDate || '') : '';
  document.getElementById('taskPriority').value = task ? task.priority : 'mid';
  document.getElementById('taskCategory').value = task ? (task.categories || []).join(', ') : '';
  document.getElementById('deleteTaskBtn').classList.toggle('hidden', !task);
  currentChecklist = task ? (task.checklist || []).map(i => ({ ...i })) : [];
  document.getElementById('checklistNewItem').value = '';
  renderChecklist();
  document.getElementById('modalOverlay').classList.remove('hidden');
  document.getElementById('taskTitle').focus();
}

function closeModal() {
  document.getElementById('modalOverlay').classList.add('hidden');
  editingId = null;
  currentChecklist = [];
}

function renderChecklist() {
  const list = document.getElementById('checklistItems');
  list.innerHTML = '';

  currentChecklist.forEach(item => {
    const li = document.createElement('li');
    li.className = 'checklist-item';
    li.innerHTML = `
      <input type="checkbox" class="checklist-check">
      <span class="checklist-text"></span>
      <button type="button" class="checklist-remove" aria-label="削除">×</button>
    `;
    const checkbox = li.querySelector('.checklist-check');
    checkbox.checked = item.done;
    li.classList.toggle('done', item.done);
    li.querySelector('.checklist-text').textContent = item.text;

    checkbox.addEventListener('change', () => {
      item.done = checkbox.checked;
      li.classList.toggle('done', item.done);
      updateChecklistProgress();
    });
    li.querySelector('.checklist-remove').addEventListener('click', () => {
      currentChecklist = currentChecklist.filter(i => i.id !== item.id);
      renderChecklist();
    });

    list.appendChild(li);
  });

  updateChecklistProgress();
}

function updateChecklistProgress() {
  const progress = document.getElementById('checklistProgress');
  if (!currentChecklist.length) {
    progress.textContent = '';
    return;
  }
  const done = currentChecklist.filter(i => i.done).length;
  progress.textContent = `${done}/${currentChecklist.length}`;
}

function addChecklistItem() {
  const input = document.getElementById('checklistNewItem');
  const text = input.value.trim();
  if (!text) return;
  currentChecklist.push({ id: uid(), text, done: false });
  input.value = '';
  renderChecklist();
  input.focus();
}

function handleSubmit(e) {
  e.preventDefault();
  const title = document.getElementById('taskTitle').value.trim();
  if (!title) return;

  const data = {
    title,
    description: document.getElementById('taskDesc').value.trim(),
    dueDate: document.getElementById('taskDue').value,
    priority: document.getElementById('taskPriority').value,
    categories: parseCategories(document.getElementById('taskCategory').value),
    checklist: currentChecklist,
  };

  if (editingId) {
    const task = tasks.find(t => t.id === editingId);
    Object.assign(task, data);
  } else {
    tasks.push({ id: uid(), status: 'todo', createdAt: new Date().toISOString(), ...data });
  }

  saveTasks();
  closeModal();
  render();
}

function handleDelete() {
  if (!editingId) return;
  if (!confirm('このタスクを削除しますか?')) return;
  tasks = tasks.filter(t => t.id !== editingId);
  saveTasks();
  closeModal();
  render();
}

document.getElementById('addTaskBtn').addEventListener('click', () => openModal(null));
document.getElementById('cancelBtn').addEventListener('click', closeModal);
document.getElementById('taskForm').addEventListener('submit', handleSubmit);
document.getElementById('deleteTaskBtn').addEventListener('click', handleDelete);
document.getElementById('checklistAddBtn').addEventListener('click', addChecklistItem);
document.getElementById('checklistNewItem').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    addChecklistItem();
  }
});
document.getElementById('modalOverlay').addEventListener('click', (e) => {
  if (e.target.id === 'modalOverlay') closeModal();
});
document.getElementById('filterCategory').addEventListener('change', render);
document.getElementById('filterPriority').addEventListener('change', render);
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !document.getElementById('modalOverlay').classList.contains('hidden')) {
    closeModal();
  }
});

setupDropZones();
render();
