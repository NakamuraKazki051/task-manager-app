const CURRENT_BOARD_KEY = 'task-manager-app:currentBoard';
const THEME_KEY = 'task-manager-app:theme';
const NOTIFY_KEY = 'task-manager-app:notify';
const LAST_NOTIFIED_KEY = 'task-manager-app:lastNotifiedDate';
const DEFAULT_COLUMNS = [
  { name: '未着手', done: false },
  { name: '進行中', done: false },
  { name: '完了', done: true },
];
const PRIORITY_LABEL = { high: '高', mid: '中', low: '低' };
const TAG_COLORS = ['sky', 'lime', 'green', 'red', 'azure', 'purple', 'yellow', 'orange', 'pink', 'slate'];

const API_BASE = 'http://localhost:8080';

function tagColorClass(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return TAG_COLORS[hash % TAG_COLORS.length];
}

let boards = [];
let currentBoardId = null;
let tasks = [];
let visibleTasks = [];
let columns = [];
let editingId = null;
let currentChecklist = [];
let currentSort = 'manual';
let searchDebounceTimer = null;
let currentUser = null;

// --- APIクライアント ---

async function apiFetch(path, options = {}) {
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      ...options,
    });
  } catch (e) {
    const err = new Error('バックエンドに接続できません。Docker/サーバーが起動しているか確認してください。');
    showApiError(err.message);
    throw err;
  }

  let data = null;
  if (res.status !== 204) {
    try { data = await res.json(); } catch (e) { data = null; }
  }

  if (!res.ok) {
    const message = data && data.message ? data.message : `リクエストに失敗しました (${res.status})`;
    showApiError(message);
    throw new Error(message);
  }

  clearApiError();
  return data;
}

function apiGet(path) { return apiFetch(path); }
function apiPost(path, body) { return apiFetch(path, { method: 'POST', body: JSON.stringify(body) }); }
function apiPut(path, body) { return apiFetch(path, { method: 'PUT', body: JSON.stringify(body) }); }
function apiPatch(path, body) { return apiFetch(path, { method: 'PATCH', body: JSON.stringify(body) }); }
function apiDelete(path) { return apiFetch(path, { method: 'DELETE' }); }

function showApiError(message) {
  document.getElementById('apiErrorMessage').textContent = message;
  document.getElementById('apiErrorBanner').classList.remove('hidden');
}

function clearApiError() {
  document.getElementById('apiErrorBanner').classList.add('hidden');
}

function fromApiTask(t) {
  return {
    id: t.id,
    status: t.columnId,
    title: t.title,
    description: t.description || '',
    dueDate: t.dueDate || '',
    priority: (t.priority || 'MID').toLowerCase(),
    categories: t.categories || [],
    checklist: (t.checklist || []).map(i => ({ id: i.id, text: i.text, done: i.done })),
    createdAt: t.createdAt,
    order: t.displayOrder,
  };
}

function toApiTaskPayload(data, columnId) {
  return {
    title: data.title,
    description: data.description,
    columnId,
    dueDate: data.dueDate || null,
    priority: data.priority.toUpperCase(),
    categories: data.categories,
    checklist: data.checklist.map(i => ({ text: i.text, done: i.done })),
  };
}

function fromApiColumn(c) {
  return { id: c.id, name: c.name, done: c.done };
}

async function loadBoardData(boardId) {
  const [cols, taskList] = await Promise.all([
    apiGet(`/api/boards/${boardId}/columns`),
    apiGet(`/api/boards/${boardId}/tasks`),
  ]);
  currentBoardId = boardId;
  localStorage.setItem(CURRENT_BOARD_KEY, boardId);
  columns = cols.map(fromApiColumn);
  tasks = taskList.map(fromApiTask);
}

async function refreshTasks() {
  const taskList = await apiGet(`/api/boards/${currentBoardId}/tasks`);
  tasks = taskList.map(fromApiTask);
}

async function refreshColumns() {
  const cols = await apiGet(`/api/boards/${currentBoardId}/columns`);
  columns = cols.map(fromApiColumn);
}

async function createDefaultColumns(boardId) {
  for (const c of DEFAULT_COLUMNS) {
    await apiPost(`/api/boards/${boardId}/columns`, { name: c.name, done: c.done });
  }
}

function loadCurrentBoardId() {
  const id = localStorage.getItem(CURRENT_BOARD_KEY);
  return (id && boards.some(b => b.id === id)) ? id : boards[0].id;
}

function parseCategories(str) {
  const list = str.split(',').map(s => s.trim()).filter(Boolean);
  return [...new Set(list)];
}

function isOverdue(task) {
  const column = columns.find(c => c.id === task.status);
  if (!task.dueDate || (column && column.done)) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(task.dueDate) < today;
}

function isDueSoon(task) {
  const column = columns.find(c => c.id === task.status);
  if (!task.dueDate || (column && column.done)) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = new Date(task.dueDate);
  if (due < today) return false;
  const diffDays = Math.round((due - today) / 86400000);
  return diffDays <= 2;
}

function getAllCategories() {
  const set = new Set();
  tasks.forEach(t => (t.categories || []).forEach(c => set.add(c)));
  return [...set].sort();
}

function buildTaskSearchParams() {
  const params = new URLSearchParams();
  const category = document.getElementById('filterCategory').value;
  const priority = document.getElementById('filterPriority').value;
  const q = document.getElementById('searchInput').value.trim();
  if (category) params.set('category', category);
  if (priority) params.set('priority', priority.toUpperCase());
  if (q) params.set('q', q);
  params.set('sort', currentSort);
  return params;
}

async function render() {
  const filterCategory = document.getElementById('filterCategory').value;
  renderCategoryFilterOptions(filterCategory);

  const params = buildTaskSearchParams();
  try {
    const taskList = await apiGet(`/api/boards/${currentBoardId}/tasks?${params.toString()}`);
    visibleTasks = taskList.map(fromApiTask);
  } catch (err) {
    return;
  }
  renderColumns();
}

function renderColumns() {
  const board = document.getElementById('board');
  board.innerHTML = '';

  columns.forEach(col => {
    const section = document.createElement('section');
    section.className = 'column';
    section.dataset.status = col.id;

    const h2 = document.createElement('h2');
    const nameSpan = document.createElement('span');
    nameSpan.className = 'column-name';
    nameSpan.textContent = col.name;
    nameSpan.title = 'クリックして列名を編集・ドラッグで並び替え';
    nameSpan.draggable = true;
    const count = document.createElement('span');
    count.className = 'count';
    const doneBtn = document.createElement('button');
    doneBtn.type = 'button';
    doneBtn.className = 'column-done-toggle';
    doneBtn.classList.toggle('active', !!col.done);
    doneBtn.textContent = '✓';
    doneBtn.setAttribute('aria-pressed', String(!!col.done));
    doneBtn.title = col.done
      ? 'この列は完了扱いです(クリックで解除)'
      : 'この列を完了扱いにする(期限超過の表示を消す)';
    const delBtn = document.createElement('button');
    delBtn.type = 'button';
    delBtn.className = 'column-delete';
    delBtn.textContent = '×';
    delBtn.setAttribute('aria-label', '列を削除');

    h2.appendChild(nameSpan);
    h2.appendChild(count);
    h2.appendChild(doneBtn);
    h2.appendChild(delBtn);

    const list = document.createElement('div');
    list.className = 'card-list';

    section.appendChild(h2);
    section.appendChild(list);
    board.appendChild(section);

    const colTasks = visibleTasks.filter(t => t.status === col.id);

    count.textContent = colTasks.length;

    if (colTasks.length === 0) {
      const hint = document.createElement('div');
      hint.className = 'empty-hint';
      hint.textContent = 'タスクはありません';
      list.appendChild(hint);
    }

    colTasks.forEach(task => list.appendChild(renderCard(task)));

    nameSpan.addEventListener('click', () => startEditColumnName(col, nameSpan));
    doneBtn.addEventListener('click', () => toggleColumnDone(col));
    delBtn.addEventListener('click', () => deleteColumn(col.id));
    setupColumnDrag(section, nameSpan, col);
    setupDropZone(section, list, col.id);
  });

  board.appendChild(renderAddColumn());
}

function setupColumnDrag(section, handle, col) {
  handle.addEventListener('dragstart', (e) => {
    section.classList.add('column-dragging');
    e.dataTransfer.setData('application/x-column-id', col.id);
    e.dataTransfer.effectAllowed = 'move';
  });
  handle.addEventListener('dragend', () => {
    section.classList.remove('column-dragging');
  });
  section.addEventListener('dragover', (e) => {
    if (!e.dataTransfer.types.includes('application/x-column-id')) return;
    e.preventDefault();
    section.classList.add('column-drag-over');
  });
  section.addEventListener('dragleave', (e) => {
    if (!section.contains(e.relatedTarget)) section.classList.remove('column-drag-over');
  });
  section.addEventListener('drop', async (e) => {
    if (!e.dataTransfer.types.includes('application/x-column-id')) return;
    e.preventDefault();
    section.classList.remove('column-drag-over');
    const draggedId = e.dataTransfer.getData('application/x-column-id');
    if (!draggedId || draggedId === col.id) return;
    await moveColumn(draggedId, col.id);
  });
}

async function moveColumn(draggedId, targetId) {
  const targetIndex = columns.findIndex(c => c.id === targetId);
  if (targetIndex === -1) return;
  try {
    await apiPatch(`/api/columns/${draggedId}/move`, { displayOrder: targetIndex });
  } catch (e) {
    render();
    return;
  }
  await refreshColumns();
  render();
}

function startEditColumnName(col, nameSpan) {
  const input = document.createElement('input');
  input.type = 'text';
  input.className = 'column-name-input';
  input.value = col.name;
  input.maxLength = 30;
  nameSpan.replaceWith(input);
  input.focus();
  input.select();

  let committed = false;
  const commit = async () => {
    if (committed) return;
    committed = true;
    const val = input.value.trim();
    if (val && val !== col.name) {
      try {
        const updated = await apiPut(`/api/columns/${col.id}`, { name: val });
        col.name = updated.name;
      } catch (e) { /* エラーはバナー表示済み */ }
    }
    render();
  };
  input.addEventListener('blur', commit);
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); input.blur(); }
    if (e.key === 'Escape') { e.preventDefault(); committed = true; render(); }
  });
}

async function toggleColumnDone(col) {
  try {
    const updated = await apiPut(`/api/columns/${col.id}`, { done: !col.done });
    col.done = updated.done;
  } catch (e) { /* エラーはバナー表示済み */ }
  render();
}

async function deleteColumn(id) {
  if (columns.length <= 1) { alert('最後の列は削除できません。'); return; }
  const hasTasks = tasks.some(t => t.status === id);
  if (hasTasks) { alert('この列にはタスクがあります。先にタスクを他の列へ移動してください。'); return; }
  if (!confirm('この列を削除しますか?')) return;
  try {
    await apiDelete(`/api/columns/${id}`);
  } catch (e) { return; }
  columns = columns.filter(c => c.id !== id);
  render();
}

function renderAddColumn() {
  const wrap = document.createElement('section');
  wrap.className = 'column add-column';

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'add-column-btn';
  btn.textContent = '+ 列を追加';

  const form = document.createElement('form');
  form.className = 'add-column-form hidden';
  const input = document.createElement('input');
  input.type = 'text';
  input.placeholder = '列名を入力';
  input.maxLength = 30;
  const doneLabel = document.createElement('label');
  doneLabel.className = 'add-column-done';
  const doneCheckbox = document.createElement('input');
  doneCheckbox.type = 'checkbox';
  doneLabel.appendChild(doneCheckbox);
  doneLabel.appendChild(document.createTextNode('完了扱いの列にする'));
  const formActions = document.createElement('div');
  formActions.className = 'add-column-actions';
  const confirmBtn = document.createElement('button');
  confirmBtn.type = 'submit';
  confirmBtn.className = 'btn-primary';
  confirmBtn.textContent = '追加';
  const cancelBtn = document.createElement('button');
  cancelBtn.type = 'button';
  cancelBtn.className = 'btn-secondary';
  cancelBtn.textContent = 'キャンセル';
  formActions.appendChild(confirmBtn);
  formActions.appendChild(cancelBtn);
  form.appendChild(input);
  form.appendChild(doneLabel);
  form.appendChild(formActions);

  btn.addEventListener('click', () => {
    btn.classList.add('hidden');
    form.classList.remove('hidden');
    input.focus();
  });
  cancelBtn.addEventListener('click', () => {
    form.classList.add('hidden');
    btn.classList.remove('hidden');
    input.value = '';
    doneCheckbox.checked = false;
  });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = input.value.trim();
    if (!name) return;
    try {
      const created = await apiPost(`/api/boards/${currentBoardId}/columns`, { name, done: doneCheckbox.checked });
      columns.push(fromApiColumn(created));
    } catch (err) { return; }
    render();
  });

  wrap.appendChild(btn);
  wrap.appendChild(form);
  return wrap;
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
  const dueSoon = !overdue && isDueSoon(task);
  const dueLabel = task.dueDate ? formatDate(task.dueDate) : '';
  const checklist = task.checklist || [];
  const checklistDone = checklist.filter(i => i.done).length;

  card.innerHTML = `
    <div class="task-card-title"></div>
    ${task.description ? '<div class="task-card-desc"></div>' : ''}
    <div class="task-meta">
      <span class="badge ${task.priority}">${PRIORITY_LABEL[task.priority]}</span>
      ${task.dueDate ? `<span class="badge due ${overdue ? 'overdue' : ''} ${dueSoon ? 'due-soon' : ''}">${dueLabel}${overdue ? ' (期限超過)' : dueSoon ? ' (まもなく)' : ''}</span>` : ''}
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
  card.addEventListener('dragend', () => {
    card.classList.remove('dragging');
    // drop先が無効だった場合、dragover中に移動したDOMが実データとズレたままになるため再描画して同期する
    render();
  });

  return card;
}

function formatDate(isoDate) {
  const [y, m, d] = isoDate.split('-');
  return `${m}/${d}`;
}

function formatCreatedAt(isoInstant) {
  const d = new Date(isoInstant);
  if (Number.isNaN(d.getTime())) return '';
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}/${m}/${day}`;
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function setupDropZone(section, list, statusId) {
  section.addEventListener('dragover', (e) => {
    if (e.dataTransfer.types.includes('application/x-column-id')) return;
    e.preventDefault();
    section.classList.add('drag-over');
    // 手動並び替え以外のソート表示中は、ドロップ時に末尾へ追加するだけなのでプレビュー移動は行わない
    if (currentSort !== 'manual') return;
    const dragging = document.querySelector('.task-card.dragging');
    if (!dragging) return;
    list.querySelector('.empty-hint')?.remove();
    const after = getDragAfterElement(list, e.clientY);
    if (after == null) {
      list.appendChild(dragging);
    } else {
      list.insertBefore(dragging, after);
    }
  });
  section.addEventListener('dragleave', (e) => {
    if (!section.contains(e.relatedTarget)) section.classList.remove('drag-over');
  });
  section.addEventListener('drop', async (e) => {
    if (e.dataTransfer.types.includes('application/x-column-id')) return;
    e.preventDefault();
    section.classList.remove('drag-over');
    const id = e.dataTransfer.getData('text/plain');
    const task = tasks.find(t => t.id === id);
    if (!task) return;

    let displayOrder;
    if (currentSort === 'manual') {
      const cardEls = [...list.querySelectorAll('.task-card')];
      displayOrder = cardEls.findIndex(el => el.dataset.id === id);
      if (displayOrder === -1) displayOrder = cardEls.length;
    } else {
      displayOrder = tasks.filter(t => t.status === statusId && t.id !== id).length;
    }

    try {
      await apiPatch(`/api/tasks/${id}/move`, { columnId: statusId, displayOrder });
    } catch (err) {
      render();
      return;
    }
    try {
      await refreshTasks();
    } catch (err) { /* エラーはバナー表示済み。既存の表示のまま */ }
    render();
  });
}

function getDragAfterElement(container, y) {
  const elements = [...container.querySelectorAll('.task-card:not(.dragging)')];
  return elements.reduce((closest, child) => {
    const box = child.getBoundingClientRect();
    const offset = y - box.top - box.height / 2;
    if (offset < 0 && offset > closest.offset) {
      return { offset, element: child };
    }
    return closest;
  }, { offset: Number.NEGATIVE_INFINITY, element: null }).element;
}

function openModal(task) {
  editingId = task ? task.id : null;
  document.getElementById('modalTitle').textContent = task ? 'タスク編集' : 'タスク追加';
  const createdEl = document.getElementById('taskCreatedAt');
  if (task && task.createdAt) {
    createdEl.textContent = `作成日: ${formatCreatedAt(task.createdAt)}`;
    createdEl.classList.remove('hidden');
  } else {
    createdEl.textContent = '';
    createdEl.classList.add('hidden');
  }
  document.getElementById('taskId').value = task ? task.id : '';
  document.getElementById('taskTitle').value = task ? task.title : '';
  document.getElementById('taskDesc').value = task ? (task.description || '') : '';
  document.getElementById('taskDue').value = task ? (task.dueDate || '') : '';
  document.getElementById('taskPriority').value = task ? task.priority : 'mid';
  const statusSelect = document.getElementById('taskStatus');
  statusSelect.innerHTML = columns.map(c => `<option value="${escapeHtml(c.id)}">${escapeHtml(c.name)}</option>`).join('');
  statusSelect.value = task ? task.status : columns[0].id;
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

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

async function handleSubmit(e) {
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
  const selectedStatus = document.getElementById('taskStatus').value || (columns[0] ? columns[0].id : '');

  try {
    if (editingId) {
      const updated = await apiPut(`/api/tasks/${editingId}`, toApiTaskPayload(data, selectedStatus));
      const idx = tasks.findIndex(t => t.id === editingId);
      if (idx !== -1) tasks[idx] = fromApiTask(updated);
    } else {
      const created = await apiPost(`/api/boards/${currentBoardId}/tasks`, toApiTaskPayload(data, selectedStatus));
      tasks.push(fromApiTask(created));
    }
  } catch (err) {
    return;
  }

  closeModal();
  render();
  checkDueNotifications();
}

function exportData() {
  const board = boards.find(b => b.id === currentBoardId);
  const payload = {
    app: 'task-manager-app',
    version: 2,
    board: board ? board.name : undefined,
    exportedAt: new Date().toISOString(),
    columns,
    tasks,
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `task-manager-backup-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function handleImportFile(e) {
  const file = e.target.files[0];
  e.target.value = '';
  if (!file) return;

  const reader = new FileReader();
  reader.onload = async () => {
    let data;
    try {
      data = JSON.parse(reader.result);
    } catch (err) {
      alert('ファイルの読み込みに失敗しました。正しいJSONファイルを選択してください。');
      return;
    }
    if (!Array.isArray(data.tasks) || !Array.isArray(data.columns) || !data.columns.length) {
      alert('このファイルはタスク管理アプリのバックアップ形式ではないようです。');
      return;
    }
    if (!confirm('インポートすると現在のボードのタスクと列がすべて置き換わります。よろしいですか?')) return;

    const importColumns = data.columns.map((c, idx) => ({
      id: c && c.id != null ? String(c.id) : `col-${idx}`,
      name: c && c.name ? String(c.name) : '無題の列',
      done: !!(c && c.done),
    }));
    const validColumnIds = new Set(importColumns.map(c => c.id));
    const importTasks = data.tasks.map(t => ({
      columnId: validColumnIds.has(t && t.status) ? t.status : importColumns[0].id,
      title: t && t.title ? String(t.title) : '無題のタスク',
      description: t && t.description ? String(t.description) : '',
      dueDate: t && typeof t.dueDate === 'string' && t.dueDate ? t.dueDate : null,
      priority: PRIORITY_LABEL[t && t.priority] ? String(t.priority).toUpperCase() : 'MID',
      categories: [...new Set((Array.isArray(t && t.categories) ? t.categories : [])
        .filter(c => typeof c === 'string' && c.trim())
        .map(c => c.trim()))],
      checklist: (Array.isArray(t && t.checklist) ? t.checklist : [])
        .filter(i => i && typeof i.text === 'string' && i.text.trim())
        .map(i => ({ text: String(i.text), done: !!i.done })),
    }));

    try {
      await apiPut(`/api/boards/${currentBoardId}/import`, { columns: importColumns, tasks: importTasks });
      await loadBoardData(currentBoardId);
    } catch (err) {
      return;
    }
    render();
    checkDueNotifications();
    alert('インポートが完了しました。');
  };
  reader.onerror = () => alert('ファイルの読み込みに失敗しました。');
  reader.readAsText(file);
}

async function handleDelete() {
  if (!editingId) return;
  if (!confirm('このタスクを削除しますか?')) return;
  try {
    await apiDelete(`/api/tasks/${editingId}`);
  } catch (err) {
    return;
  }
  tasks = tasks.filter(t => t.id !== editingId);
  closeModal();
  render();
}

// --- ボード切り替え ---

function renderBoardSelect() {
  const select = document.getElementById('boardSelect');
  select.innerHTML = boards.map(b => `<option value="${escapeHtml(b.id)}">${escapeHtml(b.name)}</option>`).join('');
  select.value = currentBoardId;
  document.getElementById('deleteBoardBtn').disabled = boards.length <= 1;
}

async function switchBoard(id) {
  if (!boards.some(b => b.id === id) || id === currentBoardId) {
    renderBoardSelect();
    return;
  }
  try {
    await loadBoardData(id);
  } catch (err) {
    renderBoardSelect();
    return;
  }
  currentSort = 'manual';
  document.getElementById('sortSelect').value = 'manual';
  document.getElementById('searchInput').value = '';
  renderBoardSelect();
  render();
  checkDueNotifications();
}

async function addBoard() {
  const name = prompt('新しいボード名を入力してください', '新しいボード');
  if (name == null) return;
  const trimmed = name.trim();
  if (!trimmed) return;
  let board;
  try {
    board = await apiPost('/api/boards', { name: trimmed });
    await createDefaultColumns(board.id);
  } catch (err) {
    return;
  }
  boards.push(board);
  await switchBoard(board.id);
}

async function renameBoard() {
  const board = boards.find(b => b.id === currentBoardId);
  if (!board) return;
  const name = prompt('ボード名を変更', board.name);
  if (name == null) return;
  const trimmed = name.trim();
  if (!trimmed) return;
  try {
    const updated = await apiPut(`/api/boards/${currentBoardId}`, { name: trimmed });
    board.name = updated.name;
  } catch (err) {
    return;
  }
  renderBoardSelect();
}

async function deleteBoard() {
  if (boards.length <= 1) { alert('最後のボードは削除できません。'); return; }
  const board = boards.find(b => b.id === currentBoardId);
  if (!board) return;
  if (!confirm(`ボード「${board.name}」を削除しますか?このボードのタスクと列もすべて削除されます。`)) return;
  try {
    await apiDelete(`/api/boards/${currentBoardId}`);
  } catch (err) {
    return;
  }
  boards = boards.filter(b => b.id !== currentBoardId);
  await switchBoard(boards[0].id);
}

// --- テーマ(ライト/ダーク)手動切り替え ---

function loadTheme() {
  const theme = localStorage.getItem(THEME_KEY);
  return theme === 'light' || theme === 'dark' ? theme : 'system';
}

function applyTheme(theme) {
  if (theme === 'system') {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = theme;
  }
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch (e) {
    console.error('Failed to save theme to localStorage', e);
  }
  updateThemeBtn(theme);
}

function updateThemeBtn(theme) {
  const btn = document.getElementById('themeToggleBtn');
  const labels = { system: '🖥️ 自動', light: '☀️ ライト', dark: '🌙 ダーク' };
  btn.textContent = labels[theme];
  btn.title = 'クリックでテーマを切り替え(自動→ライト→ダーク)';
}

function cycleTheme() {
  const order = ['system', 'light', 'dark'];
  const next = order[(order.indexOf(loadTheme()) + 1) % order.length];
  applyTheme(next);
}

// --- 期限リマインダー(通知) ---

function updateNotifyBtn() {
  const btn = document.getElementById('notifyToggleBtn');
  if (!('Notification' in window)) {
    btn.disabled = true;
    btn.textContent = '🔔 通知非対応';
    return;
  }
  const enabled = localStorage.getItem(NOTIFY_KEY) === 'true' && Notification.permission === 'granted';
  btn.textContent = enabled ? '🔔 期限通知ON' : '🔕 期限通知OFF';
  btn.classList.toggle('active', enabled);
}

function toggleNotify() {
  if (!('Notification' in window)) return;
  const enabled = localStorage.getItem(NOTIFY_KEY) === 'true' && Notification.permission === 'granted';
  if (enabled) {
    localStorage.setItem(NOTIFY_KEY, 'false');
    updateNotifyBtn();
    return;
  }
  Notification.requestPermission().then(perm => {
    if (perm === 'granted') {
      localStorage.setItem(NOTIFY_KEY, 'true');
      updateNotifyBtn();
      checkDueNotifications(true);
    } else {
      alert('通知が許可されませんでした。ブラウザの通知設定を確認してください。');
      updateNotifyBtn();
    }
  });
}

function checkDueNotifications(force) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  if (localStorage.getItem(NOTIFY_KEY) !== 'true') return;
  const todayStr = new Date().toISOString().slice(0, 10);
  if (!force && localStorage.getItem(LAST_NOTIFIED_KEY) === todayStr) return;
  const dueTasks = tasks.filter(t => {
    const col = columns.find(c => c.id === t.status);
    if (!t.dueDate || (col && col.done)) return false;
    return t.dueDate <= todayStr;
  });
  if (dueTasks.length) {
    try {
      new Notification('タスク管理', { body: `本日期限・期限超過のタスクが${dueTasks.length}件あります。` });
    } catch (e) {
      console.error('Failed to show notification', e);
    }
  }
  try {
    localStorage.setItem(LAST_NOTIFIED_KEY, todayStr);
  } catch (e) {
    console.error('Failed to save notification date to localStorage', e);
  }
}

// --- ログイン/ログアウト ---

async function checkAuth() {
  try {
    const res = await fetch(`${API_BASE}/api/auth/me`, { credentials: 'include' });
    currentUser = res.ok ? await res.json() : null;
  } catch (e) {
    currentUser = null;
  }
  renderAuthStatus();
}

function setLoggedInUiVisible(visible) {
  document.getElementById('boardSwitcher').classList.toggle('hidden', !visible);
  document.getElementById('headerActions').classList.toggle('hidden', !visible);
}

function showLoggedOutState() {
  boards = [];
  tasks = [];
  visibleTasks = [];
  columns = [];
  currentBoardId = null;
  setLoggedInUiVisible(false);
  const board = document.getElementById('board');
  board.innerHTML = '';
  const hint = document.createElement('div');
  hint.className = 'empty-hint';
  hint.textContent = 'ログインするとボードが表示されます。';
  board.appendChild(hint);
}

async function loadApp() {
  setLoggedInUiVisible(true);
  try {
    boards = await apiGet('/api/boards');
    if (!boards.length) {
      const board = await apiPost('/api/boards', { name: 'マイボード' });
      await createDefaultColumns(board.id);
      boards = [board];
    }
    await loadBoardData(loadCurrentBoardId());
  } catch (err) {
    return;
  }
  renderBoardSelect();
  render();
  checkDueNotifications();
}

function renderAuthStatus() {
  const el = document.getElementById('authStatus');
  el.innerHTML = '';
  if (currentUser) {
    const emailSpan = document.createElement('span');
    emailSpan.className = 'auth-email';
    emailSpan.textContent = currentUser.email;
    const logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.className = 'btn-secondary';
    logoutBtn.textContent = 'ログアウト';
    logoutBtn.addEventListener('click', handleLogout);
    el.appendChild(emailSpan);
    el.appendChild(logoutBtn);
  } else {
    const loginBtn = document.createElement('button');
    loginBtn.type = 'button';
    loginBtn.className = 'btn-secondary';
    loginBtn.textContent = 'ログイン';
    loginBtn.addEventListener('click', openAuthModal);
    el.appendChild(loginBtn);
  }
}

function openAuthModal() {
  document.getElementById('authForm').reset();
  document.getElementById('authError').classList.add('hidden');
  document.getElementById('authModalOverlay').classList.remove('hidden');
  document.getElementById('authEmail').focus();
}

function closeAuthModal() {
  document.getElementById('authModalOverlay').classList.add('hidden');
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const email = document.getElementById('authEmail').value.trim();
  const password = document.getElementById('authPassword').value;
  const errorEl = document.getElementById('authError');
  errorEl.classList.add('hidden');

  let res;
  try {
    res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
  } catch (err) {
    errorEl.textContent = 'バックエンドに接続できません。';
    errorEl.classList.remove('hidden');
    return;
  }

  if (!res.ok) {
    const data = await res.json().catch(() => null);
    errorEl.textContent = (data && data.message) || 'ログインに失敗しました。';
    errorEl.classList.remove('hidden');
    return;
  }

  currentUser = await res.json();
  closeAuthModal();
  renderAuthStatus();
  await loadApp();
}

async function handleLogout() {
  try {
    await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' });
  } catch (e) { /* ローカル状態のクリアは継続する */ }
  currentUser = null;
  renderAuthStatus();
  showLoggedOutState();
}

document.getElementById('addTaskBtn').addEventListener('click', () => openModal(null));
document.getElementById('exportBtn').addEventListener('click', exportData);
document.getElementById('importBtn').addEventListener('click', () => document.getElementById('importFile').click());
document.getElementById('importFile').addEventListener('change', handleImportFile);
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
document.getElementById('searchInput').addEventListener('input', () => {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(render, 300);
});
document.getElementById('sortSelect').addEventListener('change', (e) => {
  currentSort = e.target.value;
  render();
});
document.getElementById('themeToggleBtn').addEventListener('click', cycleTheme);
document.getElementById('notifyToggleBtn').addEventListener('click', toggleNotify);
document.getElementById('boardSelect').addEventListener('change', (e) => switchBoard(e.target.value));
document.getElementById('addBoardBtn').addEventListener('click', addBoard);
document.getElementById('renameBoardBtn').addEventListener('click', renameBoard);
document.getElementById('deleteBoardBtn').addEventListener('click', deleteBoard);
document.getElementById('apiErrorDismiss').addEventListener('click', clearApiError);
document.getElementById('authForm').addEventListener('submit', handleAuthSubmit);
document.getElementById('authCancelBtn').addEventListener('click', closeAuthModal);
document.getElementById('authModalOverlay').addEventListener('click', (e) => {
  if (e.target.id === 'authModalOverlay') closeAuthModal();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !document.getElementById('modalOverlay').classList.contains('hidden')) {
    closeModal();
  }
  if (e.key === 'Escape' && !document.getElementById('authModalOverlay').classList.contains('hidden')) {
    closeAuthModal();
  }
});

async function init() {
  applyTheme(loadTheme());
  updateNotifyBtn();
  await checkAuth();
  if (currentUser) {
    await loadApp();
  } else {
    showLoggedOutState();
  }
}

init();
