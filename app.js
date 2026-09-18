const LEGACY_TASKS_KEY = 'task-manager-app:tasks';
const LEGACY_COLUMNS_KEY = 'task-manager-app:columns';
const BOARDS_KEY = 'task-manager-app:boards';
const CURRENT_BOARD_KEY = 'task-manager-app:currentBoard';
const THEME_KEY = 'task-manager-app:theme';
const NOTIFY_KEY = 'task-manager-app:notify';
const LAST_NOTIFIED_KEY = 'task-manager-app:lastNotifiedDate';
const DEFAULT_COLUMNS = [
  { id: 'todo', name: '未着手', done: false },
  { id: 'doing', name: '進行中', done: false },
  { id: 'done', name: '完了', done: true },
];
const PRIORITY_LABEL = { high: '高', mid: '中', low: '低' };
const PRIORITY_ORDER = { high: 0, mid: 1, low: 2 };
const TAG_COLORS = ['sky', 'lime', 'green', 'red', 'azure', 'purple', 'yellow', 'orange', 'pink', 'slate'];

function tagColorClass(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return TAG_COLORS[hash % TAG_COLORS.length];
}

function taskKey(boardId) { return `task-manager-app:tasks:${boardId}`; }
function columnKey(boardId) { return `task-manager-app:columns:${boardId}`; }

let boards = loadBoards();
let currentBoardId = loadCurrentBoardId();
let tasks = loadTasks(currentBoardId);
let columns = loadColumns(currentBoardId);
let editingId = null;
let currentChecklist = [];
let currentSort = 'manual';

function loadBoards() {
  try {
    const raw = localStorage.getItem(BOARDS_KEY);
    const parsed = raw ? JSON.parse(raw) : null;
    if (Array.isArray(parsed) && parsed.length) {
      return parsed.map(b => ({ id: String(b.id), name: b && b.name ? String(b.name) : '無題のボード' }));
    }
  } catch (e) {
    console.error('Failed to load boards from localStorage', e);
  }
  // 旧バージョン(単一ボードのみ)のデータを新形式(ボードごとの名前空間)へ移行する
  const legacyTasks = localStorage.getItem(LEGACY_TASKS_KEY);
  const legacyColumns = localStorage.getItem(LEGACY_COLUMNS_KEY);
  if (localStorage.getItem(taskKey('default')) == null) {
    localStorage.setItem(taskKey('default'), legacyTasks != null ? legacyTasks : '[]');
  }
  if (localStorage.getItem(columnKey('default')) == null) {
    localStorage.setItem(columnKey('default'), legacyColumns != null ? legacyColumns : JSON.stringify(DEFAULT_COLUMNS));
  }
  const migrated = [{ id: 'default', name: 'マイボード' }];
  try {
    localStorage.setItem(BOARDS_KEY, JSON.stringify(migrated));
  } catch (e) {
    console.error('Failed to save migrated boards to localStorage', e);
  }
  return migrated;
}

function loadCurrentBoardId() {
  const id = localStorage.getItem(CURRENT_BOARD_KEY);
  return (id && boards.some(b => b.id === id)) ? id : boards[0].id;
}

function saveBoards() {
  try {
    localStorage.setItem(BOARDS_KEY, JSON.stringify(boards));
  } catch (e) {
    console.error('Failed to save boards to localStorage', e);
  }
}

function loadTasks(boardId) {
  try {
    const raw = localStorage.getItem(taskKey(boardId));
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('Failed to load tasks from localStorage', e);
    return [];
  }
}

function saveTasks() {
  try {
    localStorage.setItem(taskKey(currentBoardId), JSON.stringify(tasks));
  } catch (e) {
    console.error('Failed to save tasks to localStorage', e);
    alert('保存に失敗しました。ブラウザのストレージ容量を確認してください。');
  }
}

function loadColumns(boardId) {
  try {
    const raw = localStorage.getItem(columnKey(boardId));
    const parsed = raw ? JSON.parse(raw) : null;
    const cols = Array.isArray(parsed) && parsed.length ? parsed : DEFAULT_COLUMNS.map(c => ({ ...c }));
    // 旧バージョンのデータには done フラグが無いため、元のデフォルト「完了」列のIDだけ true として補う
    cols.forEach(c => { if (typeof c.done !== 'boolean') c.done = c.id === 'done'; });
    return cols;
  } catch (e) {
    console.error('Failed to load columns from localStorage', e);
    return DEFAULT_COLUMNS.map(c => ({ ...c }));
  }
}

function saveColumns() {
  try {
    localStorage.setItem(columnKey(currentBoardId), JSON.stringify(columns));
  } catch (e) {
    console.error('Failed to save columns to localStorage', e);
    alert('保存に失敗しました。ブラウザのストレージ容量を確認してください。');
  }
}

function ensureTaskOrder() {
  let changed = false;
  columns.forEach(col => {
    const inColumn = tasks.filter(t => t.status === col.id);
    if (inColumn.every(t => typeof t.order === 'number')) return;
    inColumn.sort((a, b) => {
      const pd = PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
      if (pd !== 0) return pd;
      if (a.dueDate && b.dueDate) return a.dueDate.localeCompare(b.dueDate);
      if (a.dueDate) return -1;
      if (b.dueDate) return 1;
      return 0;
    });
    inColumn.forEach((t, idx) => { t.order = idx; });
    changed = true;
  });
  if (changed) saveTasks();
}

function nextOrder(status) {
  const inColumn = tasks.filter(t => t.status === status);
  if (!inColumn.length) return 0;
  return Math.max(...inColumn.map(t => t.order ?? 0)) + 1;
}

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
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

function render() {
  const filterCategory = document.getElementById('filterCategory').value;
  const filterPriority = document.getElementById('filterPriority').value;
  const searchQuery = document.getElementById('searchInput').value.trim().toLowerCase();

  renderCategoryFilterOptions(filterCategory);
  renderColumns(filterCategory, filterPriority, searchQuery);
}

function sortFiltered(filtered) {
  if (currentSort === 'due') {
    filtered.sort((a, b) => {
      if (a.dueDate && b.dueDate) return a.dueDate.localeCompare(b.dueDate);
      if (a.dueDate) return -1;
      if (b.dueDate) return 1;
      return (a.order ?? 0) - (b.order ?? 0);
    });
  } else if (currentSort === 'priority') {
    filtered.sort((a, b) => {
      const pd = PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
      if (pd !== 0) return pd;
      return (a.order ?? 0) - (b.order ?? 0);
    });
  } else {
    filtered.sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  }
  return filtered;
}

function renderColumns(filterCategory, filterPriority, searchQuery) {
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
    nameSpan.title = 'クリックして列名を編集';
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

    let filtered = tasks.filter(t => t.status === col.id);
    if (filterCategory) filtered = filtered.filter(t => (t.categories || []).includes(filterCategory));
    if (filterPriority) filtered = filtered.filter(t => t.priority === filterPriority);
    if (searchQuery) {
      filtered = filtered.filter(t =>
        (t.title || '').toLowerCase().includes(searchQuery) ||
        (t.description || '').toLowerCase().includes(searchQuery));
    }
    sortFiltered(filtered);

    count.textContent = filtered.length;

    if (filtered.length === 0) {
      const hint = document.createElement('div');
      hint.className = 'empty-hint';
      hint.textContent = 'タスクはありません';
      list.appendChild(hint);
    }

    filtered.forEach(task => list.appendChild(renderCard(task)));

    nameSpan.addEventListener('click', () => startEditColumnName(col, nameSpan));
    doneBtn.addEventListener('click', () => toggleColumnDone(col));
    delBtn.addEventListener('click', () => deleteColumn(col.id));
    setupDropZone(section, list, col.id);
  });

  board.appendChild(renderAddColumn());
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
  const commit = () => {
    if (committed) return;
    committed = true;
    const val = input.value.trim();
    if (val) col.name = val;
    saveColumns();
    render();
  };
  input.addEventListener('blur', commit);
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); input.blur(); }
    if (e.key === 'Escape') { e.preventDefault(); committed = true; render(); }
  });
}

function toggleColumnDone(col) {
  col.done = !col.done;
  saveColumns();
  render();
}

function deleteColumn(id) {
  if (columns.length <= 1) { alert('最後の列は削除できません。'); return; }
  const hasTasks = tasks.some(t => t.status === id);
  if (hasTasks) { alert('この列にはタスクがあります。先にタスクを他の列へ移動してください。'); return; }
  if (!confirm('この列を削除しますか?')) return;
  columns = columns.filter(c => c.id !== id);
  saveColumns();
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
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const name = input.value.trim();
    if (!name) return;
    columns.push({ id: uid(), name, done: doneCheckbox.checked });
    saveColumns();
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

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function setupDropZone(section, list, statusId) {
  section.addEventListener('dragover', (e) => {
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
  section.addEventListener('drop', (e) => {
    e.preventDefault();
    section.classList.remove('drag-over');
    const id = e.dataTransfer.getData('text/plain');
    const task = tasks.find(t => t.id === id);
    if (!task) return;
    const statusChanged = task.status !== statusId;
    task.status = statusId;
    if (currentSort === 'manual') {
      [...list.querySelectorAll('.task-card')].forEach((el, idx) => {
        const t = tasks.find(t2 => t2.id === el.dataset.id);
        if (t) t.order = idx;
      });
    } else if (statusChanged) {
      task.order = nextOrder(statusId);
    }
    saveTasks();
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
  const selectedStatus = document.getElementById('taskStatus').value || (columns[0] ? columns[0].id : 'todo');

  if (editingId) {
    const task = tasks.find(t => t.id === editingId);
    if (task.status !== selectedStatus) {
      data.status = selectedStatus;
      data.order = nextOrder(selectedStatus);
    }
    Object.assign(task, data);
  } else {
    tasks.push({ id: uid(), status: selectedStatus, order: nextOrder(selectedStatus), createdAt: new Date().toISOString(), ...data });
  }

  saveTasks();
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
  reader.onload = () => {
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

    columns = data.columns.map(c => ({
      id: c && c.id != null ? String(c.id) : uid(),
      name: c && c.name ? String(c.name) : '無題の列',
      done: !!(c && c.done),
    }));
    tasks = data.tasks.map(t => ({
      ...t,
      id: t && t.id != null ? String(t.id) : uid(),
      title: t && t.title ? String(t.title) : '無題のタスク',
      description: t && t.description ? String(t.description) : '',
      dueDate: t && typeof t.dueDate === 'string' ? t.dueDate : '',
      status: columns.some(c => c.id === (t && t.status)) ? t.status : columns[0].id,
      priority: PRIORITY_LABEL[t && t.priority] ? t.priority : 'mid',
      categories: [...new Set((Array.isArray(t && t.categories) ? t.categories : [])
        .filter(c => typeof c === 'string' && c.trim())
        .map(c => c.trim()))],
      checklist: (Array.isArray(t && t.checklist) ? t.checklist : [])
        .filter(i => i && typeof i.text === 'string' && i.text.trim())
        .map(i => ({ id: i.id != null ? String(i.id) : uid(), text: String(i.text), done: !!i.done })),
    }));
    ensureTaskOrder();
    saveColumns();
    saveTasks();
    render();
    checkDueNotifications();
    alert('インポートが完了しました。');
  };
  reader.onerror = () => alert('ファイルの読み込みに失敗しました。');
  reader.readAsText(file);
}

function handleDelete() {
  if (!editingId) return;
  if (!confirm('このタスクを削除しますか?')) return;
  tasks = tasks.filter(t => t.id !== editingId);
  saveTasks();
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

function switchBoard(id) {
  if (!boards.some(b => b.id === id) || id === currentBoardId) {
    renderBoardSelect();
    return;
  }
  currentBoardId = id;
  localStorage.setItem(CURRENT_BOARD_KEY, currentBoardId);
  tasks = loadTasks(currentBoardId);
  columns = loadColumns(currentBoardId);
  ensureTaskOrder();
  currentSort = 'manual';
  document.getElementById('sortSelect').value = 'manual';
  document.getElementById('searchInput').value = '';
  renderBoardSelect();
  render();
  checkDueNotifications();
}

function addBoard() {
  const name = prompt('新しいボード名を入力してください', '新しいボード');
  if (name == null) return;
  const trimmed = name.trim();
  if (!trimmed) return;
  const id = uid();
  boards.push({ id, name: trimmed });
  saveBoards();
  try {
    localStorage.setItem(columnKey(id), JSON.stringify(DEFAULT_COLUMNS.map(c => ({ ...c }))));
    localStorage.setItem(taskKey(id), JSON.stringify([]));
  } catch (e) {
    console.error('Failed to initialize new board storage', e);
  }
  switchBoard(id);
}

function renameBoard() {
  const board = boards.find(b => b.id === currentBoardId);
  if (!board) return;
  const name = prompt('ボード名を変更', board.name);
  if (name == null) return;
  const trimmed = name.trim();
  if (!trimmed) return;
  board.name = trimmed;
  saveBoards();
  renderBoardSelect();
}

function deleteBoard() {
  if (boards.length <= 1) { alert('最後のボードは削除できません。'); return; }
  const board = boards.find(b => b.id === currentBoardId);
  if (!board) return;
  if (!confirm(`ボード「${board.name}」を削除しますか?このボードのタスクと列もすべて削除されます。`)) return;
  localStorage.removeItem(taskKey(currentBoardId));
  localStorage.removeItem(columnKey(currentBoardId));
  boards = boards.filter(b => b.id !== currentBoardId);
  saveBoards();
  switchBoard(boards[0].id);
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
document.getElementById('searchInput').addEventListener('input', render);
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
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !document.getElementById('modalOverlay').classList.contains('hidden')) {
    closeModal();
  }
});

applyTheme(loadTheme());
updateNotifyBtn();
renderBoardSelect();
ensureTaskOrder();
render();
checkDueNotifications();
