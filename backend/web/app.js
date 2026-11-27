(() => {
  const TOKEN_KEY = "spending_tracker_jwt";
  let CATEGORIES = [
    "Debt Repayment", "Dining", "Education", "Entertainment", "Family & Kids",
    "Fees & Interest", "Gifts & Donations", "Groceries", "Health", "Housing",
    "Income", "Insurance", "Investments", "Personal Care", "Refund", "Salary",
    "Shopping", "Subscriptions", "Transfer", "Transport", "Travel", "Utilities",
    "Uncategorized"
  ];
  const CATEGORY_COLORS = {
    Groceries: "#0D9488",
    Dining: "#D97706",
    Utilities: "#2563EB",
    Transport: "#7C3AED",
    Shopping: "#DB2777",
    Entertainment: "#EA580C",
    Health: "#059669",
    Housing: "#4F46E5",
    Transfer: "#475569",
    Subscriptions: "#0284C7",
    Education: "#6366F1",
    Travel: "#0891B2",
    "Personal Care": "#EC4899",
    "Family & Kids": "#F59E0B",
    "Gifts & Donations": "#E11D48",
    Insurance: "#3B82F6",
    "Fees & Interest": "#DC2626",
    Investments: "#16A34A",
    Salary: "#10B981",
    Income: "#059669",
    Refund: "#14B8A6",
    "Debt Repayment": "#B45309",
    Uncategorized: "#94A3B8"
  };

  const now = new Date();
  const state = {
    token: localStorage.getItem(TOKEN_KEY) || null,
    user: null,
    authMode: "login",
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    baseCurrency: "VND",
    typeFilter: "all",
    selectedFiles: [],
    transactions: [],
    wallets: [],
    templates: [],
    rules: [],
    tags: [],
    selectedTxIds: new Set(),
    rangeMode: false,
    rangeStart: null,
    rangeEnd: null,
    pendingDraft: null,
    pendingPasswordFiles: null,
  };

  // Theme Toggle (#8)
  const THEME_KEY = "spending_tracker_theme";
  const btnThemeToggle = document.getElementById("btn-theme-toggle");
  function applyTheme(theme) {
    if (theme === "light" || theme === "dark") {
      document.documentElement.setAttribute("data-theme", theme);
      localStorage.setItem(THEME_KEY, theme);
    } else {
      document.documentElement.removeAttribute("data-theme");
      localStorage.removeItem(THEME_KEY);
    }
    const isDark = theme === "dark" || (!theme && window.matchMedia("(prefers-color-scheme: dark)").matches);
    if (btnThemeToggle) btnThemeToggle.textContent = isDark ? "☀ Light" : "☾ Dark";
  }
  applyTheme(localStorage.getItem(THEME_KEY));
  if (btnThemeToggle) {
    btnThemeToggle.addEventListener("click", () => {
      const cur = document.documentElement.getAttribute("data-theme");
      const isDark = cur === "dark" || (!cur && window.matchMedia("(prefers-color-scheme: dark)").matches);
      applyTheme(isDark ? "light" : "dark");
    });
  }

  // Privacy Mode (#4)
  const PRIVACY_KEY = "spending_tracker_privacy";
  const btnPrivacyToggle = document.getElementById("btn-privacy-toggle");
  function applyPrivacy(hidden) {
    document.body.classList.toggle("privacy-mode", hidden);
    localStorage.setItem(PRIVACY_KEY, hidden ? "1" : "0");
    if (btnPrivacyToggle) btnPrivacyToggle.textContent = hidden ? "👁 Show" : "👁 Hide";
  }
  applyPrivacy(localStorage.getItem(PRIVACY_KEY) === "1");
  if (btnPrivacyToggle) {
    btnPrivacyToggle.addEventListener("click", () => {
      applyPrivacy(!document.body.classList.contains("privacy-mode"));
    });
  }

  // Section Tabs (#7)
  const webTabBtns = Array.from(document.querySelectorAll("[data-web-tab]"));
  const webTabPanels = Array.from(document.querySelectorAll("[data-tab-panel]"));
  function activateWebTab(tabName) {
    webTabBtns.forEach((b) => b.classList.toggle("active", b.dataset.webTab === tabName));
    webTabPanels.forEach((p) => p.classList.toggle("hidden", p.dataset.tabPanel !== tabName));
  }
  webTabBtns.forEach((btn) => {
    btn.addEventListener("click", () => activateWebTab(btn.dataset.webTab));
  });

  // Elements
  const authView = document.getElementById("auth-view");
  const appView = document.getElementById("app-view");
  const tabLogin = document.getElementById("tab-login");
  const tabRegister = document.getElementById("tab-register");
  const registerNameGroup = document.getElementById("register-name-group");
  const authForm = document.getElementById("auth-form");
  const authEmail = document.getElementById("auth-email");
  const authPassword = document.getElementById("auth-password");
  const authName = document.getElementById("auth-name");
  const authError = document.getElementById("auth-error");
  const authSubmit = document.getElementById("auth-submit");

  const userEmailEl = document.getElementById("user-email");
  const btnLogout = document.getElementById("btn-logout");
  const btnPrevMonth = document.getElementById("btn-prev-month");
  const btnNextMonth = document.getElementById("btn-next-month");
  const monthLabel = document.getElementById("current-month-label");
  const netBalanceEl = document.getElementById("net-balance");
  const debtBadgeEl = document.getElementById("debt-badge");
  const btnZeroBalance = document.getElementById("btn-zero-balance");
  const carryForwardEl = document.getElementById("carry-forward");
  const monthNetEl = document.getElementById("month-net");
  const totalSpentEl = document.getElementById("total-spent");
  const totalCreditEl = document.getElementById("total-credit");
  const txCountEl = document.getElementById("tx-count");
  const categoryListEl = document.getElementById("category-list");
  const txTbody = document.getElementById("tx-tbody");
  const txSearch = document.getElementById("tx-search");
  const txCategoryFilter = document.getElementById("tx-category-filter");
  const txWalletFilter = document.getElementById("tx-wallet-filter");
  const txTagFilter = document.getElementById("tx-tag-filter");
  const txStatusFilter = document.getElementById("tx-status-filter");
  const btnToggleRange = document.getElementById("btn-toggle-range");
  const rangeBar = document.getElementById("range-bar");
  const rangeStartEl = document.getElementById("range-start");
  const rangeEndEl = document.getElementById("range-end");
  const btnRangeApply = document.getElementById("btn-range-apply");
  const btnRangeYtd = document.getElementById("btn-range-ytd");
  const btnRange30d = document.getElementById("btn-range-30d");
  const btnRangeClear = document.getElementById("btn-range-clear");
  const rangeSummaryEl = document.getElementById("range-summary");
  const btnExportCsv = document.getElementById("btn-export-csv");
  const templateListEl = document.getElementById("template-list");
  const btnNewTemplate = document.getElementById("btn-new-template");
  const walletListEl = document.getElementById("wallet-list");
  const walletsTotalEl = document.getElementById("wallets-total");
  const walletForm = document.getElementById("wallet-form");
  const walletNameEl = document.getElementById("wallet-name");
  const walletTypeEl = document.getElementById("wallet-type");
  const walletInitialEl = document.getElementById("wallet-initial");
  const btnToggleTransfer = document.getElementById("btn-toggle-transfer");
  const transferForm = document.getElementById("transfer-form");
  const transferFromEl = document.getElementById("transfer-from");
  const transferToEl = document.getElementById("transfer-to");
  const transferAmountEl = document.getElementById("transfer-amount");
  const rulesListEl = document.getElementById("rules-list");
  const ruleForm = document.getElementById("rule-form");
  const rulePatternEl = document.getElementById("rule-pattern");
  const ruleCategoryEl = document.getElementById("rule-category");
  const btnBackupExport = document.getElementById("btn-backup-export");
  const btnBackupImport = document.getElementById("btn-backup-import");
  const backupFileInput = document.getElementById("backup-file-input");
  const trendChartEl = document.getElementById("trend-chart");
  const budgetListEl = document.getElementById("budget-list");
  const budgetPacingEl = document.getElementById("budget-pacing");
  const budgetForm = document.getElementById("budget-form");
  const budgetCategoryEl = document.getElementById("budget-category");
  const budgetLimitEl = document.getElementById("budget-limit");
  const btnNewCategory = document.getElementById("btn-new-category");
  const pendingBanner = document.getElementById("pending-banner");
  const pendingBannerText = document.getElementById("pending-banner-text");
  const btnReviewPending = document.getElementById("btn-review-pending");
  const recurringTotalEl = document.getElementById("recurring-total");
  const recurringListEl = document.getElementById("recurring-list");
  const btnGenInsights = document.getElementById("btn-gen-insights");
  const insightsBodyEl = document.getElementById("insights-body");
  const goalsListEl = document.getElementById("goals-list");
  const goalForm = document.getElementById("goal-form");
  const goalNameEl = document.getElementById("goal-name");
  const goalTypeEl = document.getElementById("goal-type");
  const goalTargetEl = document.getElementById("goal-target");
  const iouSummaryEl = document.getElementById("iou-summary");
  const iouListEl = document.getElementById("iou-list");
  const iouForm = document.getElementById("iou-form");
  const iouPersonEl = document.getElementById("iou-person");
  const iouDirectionEl = document.getElementById("iou-direction");
  const iouAmountEl = document.getElementById("iou-amount");
  const typePillButtons = Array.from(document.querySelectorAll("[data-type-filter]"));

  const btnOpenAdd = document.getElementById("btn-open-add");
  const addModal = document.getElementById("add-modal");
  const addModalTitle = document.getElementById("add-modal-title");
  const editTxIdEl = document.getElementById("edit-tx-id");
  const btnCloseAdd = document.getElementById("btn-close-add");
  const btnCancelAdd = document.getElementById("btn-cancel-add");
  const btnDeleteInModal = document.getElementById("btn-delete-in-modal");
  const btnDuplicateInModal = document.getElementById("btn-duplicate-in-modal");
  const btnSplitInModal = document.getElementById("btn-split-in-modal");
  const safeTodayWrap = document.getElementById("safe-today-wrap");
  const safeTodayVal = document.getElementById("safe-today-val");
  const nlForm = document.getElementById("nl-form");
  const nlInput = document.getElementById("nl-input");
  const calendarHeatmapEl = document.getElementById("calendar-heatmap");
  const splitModal = document.getElementById("split-modal");
  const splitMetaEl = document.getElementById("split-meta");
  const splitRowsEl = document.getElementById("split-rows");
  const splitRemainingEl = document.getElementById("split-remaining");
  const btnAddSplitRow = document.getElementById("btn-add-split-row");
  const btnCloseSplit = document.getElementById("btn-close-split");
  const btnCancelSplit = document.getElementById("btn-cancel-split");
  const btnConfirmSplit = document.getElementById("btn-confirm-split");
  const savingsRateBadge = document.getElementById("savings-rate-badge");
  const noSpendStreakBadge = document.getElementById("nospend-streak-badge");
  const momChangesList = document.getElementById("mom-changes-list");
  const topMerchantsList = document.getElementById("top-merchants-list");
  const bulkBar = document.getElementById("bulk-bar");
  const bulkCountEl = document.getElementById("bulk-count");
  const bulkSelectAll = document.getElementById("bulk-select-all");
  const bulkCategoryEl = document.getElementById("bulk-category");
  const bulkWalletEl = document.getElementById("bulk-wallet");
  const bulkTagEl = document.getElementById("bulk-tag");
  const btnBulkApply = document.getElementById("btn-bulk-apply");
  const btnBulkDelete = document.getElementById("btn-bulk-delete");
  const btnBulkClear = document.getElementById("btn-bulk-clear");
  let splittingTx = null;
  const addForm = document.getElementById("add-form");
  const addTypeEl = document.getElementById("add-type");
  const addDateEl = document.getElementById("add-date");
  const addVendorEl = document.getElementById("add-vendor");
  const addAmountEl = document.getElementById("add-amount");
  const addCurrencyEl = document.getElementById("add-currency");
  const addCategoryEl = document.getElementById("add-category");
  const addWalletEl = document.getElementById("add-wallet");
  const addTagsEl = document.getElementById("add-tags");
  const addDescEl = document.getElementById("add-desc");
  const addRecurringEl = document.getElementById("add-recurring");
  const btnSubmitAdd = document.getElementById("btn-submit-add");

  const dropzone = document.getElementById("dropzone");
  const fileInput = document.getElementById("file-input");
  const selectedFilesLabel = document.getElementById("selected-files-label");
  const sourceTypeSelect = document.getElementById("source-type");
  const pdfPasswordInput = document.getElementById("pdf-password");
  const btnUpload = document.getElementById("btn-upload");

  const draftModal = document.getElementById("draft-modal");
  const draftMeta = document.getElementById("draft-meta");
  const reconciliationBanner = document.getElementById("reconciliation-banner");
  const skippedBanner = document.getElementById("skipped-banner");
  const draftItemsEl = document.getElementById("draft-items");
  const btnCloseDraft = document.getElementById("btn-close-draft");
  const btnCancelDraft = document.getElementById("btn-cancel-draft");
  const btnConfirmDraft = document.getElementById("btn-confirm-draft");

  const pwdModal = document.getElementById("pwd-modal");
  const modalPdfPwd = document.getElementById("modal-pdf-password");
  const btnPwdCancel = document.getElementById("btn-pwd-cancel");
  const btnPwdSubmit = document.getElementById("btn-pwd-submit");

  const toastEl = document.getElementById("toast");
  let toastTimer = null;

  function showToast(message) {
    toastEl.textContent = message;
    toastEl.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.add("hidden"), 4500);
  }

  function formatNumber(val) {
    const n = Number(val);
    if (Number.isNaN(n)) return String(val ?? "0");
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(n);
  }

  function categoryColor(name) {
    return CATEGORY_COLORS[name] || CATEGORY_COLORS.Uncategorized;
  }

  function escapeHtml(str) {
    return String(str ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) {
      headers.set("Authorization", `Bearer ${state.token}`);
    }
    const res = await fetch(path, { ...options, headers });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch (_) {}

    if (res.status === 401 && state.token) {
      logout("Session expired. Please sign in again.");
      throw new Error("Unauthorized");
    }

    if (!res.ok) {
      const msg = json?.error?.message || `Request failed (${res.status})`;
      const err = new Error(msg);
      err.status = res.status;
      throw err;
    }
    return json;
  }

  // --- Auth ---

  function setAuthMode(mode) {
    state.authMode = mode;
    tabLogin.classList.toggle("active", mode === "login");
    tabRegister.classList.toggle("active", mode === "register");
    registerNameGroup.classList.toggle("hidden", mode !== "register");
    authSubmit.textContent = mode === "login" ? "Sign In" : "Create Account";
    authError.classList.add("hidden");
  }

  tabLogin.addEventListener("click", () => setAuthMode("login"));
  tabRegister.addEventListener("click", () => setAuthMode("register"));

  authForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    authError.classList.add("hidden");
    authSubmit.disabled = true;

    try {
      const endpoint = state.authMode === "login" ? "/api/v1/auth/login" : "/api/v1/auth/register";
      const body = {
        email: authEmail.value.trim(),
        password: authPassword.value,
      };
      if (state.authMode === "register" && authName.value.trim()) {
        body.display_name = authName.value.trim();
      }

      const data = await apiFetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      state.token = data.access_token;
      state.user = data.user;
      localStorage.setItem(TOKEN_KEY, data.access_token);
      authPassword.value = "";
      enterApp();
    } catch (err) {
      authError.textContent = err.message;
      authError.classList.remove("hidden");
    } finally {
      authSubmit.disabled = false;
    }
  });

  function logout(reason) {
    state.token = null;
    state.user = null;
    localStorage.removeItem(TOKEN_KEY);
    appView.classList.add("hidden");
    authView.classList.remove("hidden");
    if (reason) {
      authError.textContent = reason;
      authError.classList.remove("hidden");
    }
  }

  btnLogout.addEventListener("click", () => logout());

  function syncCategorySelects() {
    const html = CATEGORIES.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join("");
    if (budgetCategoryEl) budgetCategoryEl.innerHTML = html;
    if (addCategoryEl) addCategoryEl.innerHTML = html;
    if (ruleCategoryEl) ruleCategoryEl.innerHTML = html;
    if (bulkCategoryEl) bulkCategoryEl.innerHTML = `<option value="">Keep Category</option>` + html;
    if (txCategoryFilter) {
      const prev = txCategoryFilter.value || "all";
      txCategoryFilter.innerHTML = `<option value="all">All Categories</option>` + html;
      txCategoryFilter.value = prev;
    }
  }

  async function loadCategories() {
    try {
      const cats = await apiFetch("/api/v1/categories");
      if (Array.isArray(cats) && cats.length) {
        CATEGORIES = cats.map((c) => c.name);
      }
    } catch (_) {
      // Fall back to built-in CATEGORIES list
    }
    syncCategorySelects();
  }

  async function enterApp() {
    authView.classList.add("hidden");
    appView.classList.remove("hidden");
    userEmailEl.textContent = state.user?.email || "";
    updateMonthLabel();
    await loadCategories();
    await refreshDashboard();
  }

  // --- Dashboard ---

  function updateMonthLabel() {
    const d = new Date(state.year, state.month - 1, 1);
    monthLabel.textContent = d.toLocaleDateString(undefined, { month: "long", year: "numeric" });
  }

  btnPrevMonth.addEventListener("click", () => {
    state.month -= 1;
    if (state.month < 1) { state.month = 12; state.year -= 1; }
    updateMonthLabel();
    refreshDashboard();
  });

  btnNextMonth.addEventListener("click", () => {
    state.month += 1;
    if (state.month > 12) { state.month = 1; state.year += 1; }
    updateMonthLabel();
    refreshDashboard();
  });

  txStatusFilter.addEventListener("change", () => refreshDashboard());
  txCategoryFilter.addEventListener("change", () => renderTransactions());
  if (txWalletFilter) txWalletFilter.addEventListener("change", () => renderTransactions());
  if (txTagFilter) txTagFilter.addEventListener("change", () => renderTransactions());
  txSearch.addEventListener("input", () => renderTransactions());

  // Click any category in Category Breakdown to filter the Ledger
  categoryListEl.addEventListener("click", (e) => {
    const row = e.target.closest("[data-cat-click]");
    if (!row || !txCategoryFilter) return;
    const cat = row.dataset.catClick;
    txCategoryFilter.value = txCategoryFilter.value === cat ? "all" : cat;
    renderTransactions();
    activateWebTab("ledger");
  });
  typePillButtons.forEach((btn) => {
    btn.addEventListener("click", () => {
      state.typeFilter = btn.dataset.typeFilter;
      typePillButtons.forEach((b) => b.classList.toggle("active", b === btn));
      renderTransactions();
    });
  });

  btnZeroBalance.addEventListener("click", async () => {
    const label = netBalanceEl.textContent;
    if (!confirm(`Zero out your running balance (${label})?\n\nThis adds a reversible balancing entry to your ledger so your balance becomes 0 without deleting history.`)) {
      return;
    }
    btnZeroBalance.disabled = true;
    try {
      const res = await apiFetch("/api/v1/spending/zero-balance", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ year: state.year, month: state.month }),
      });
      showToast(res.already_zero ? "Balance is already 0." : "Balance zeroed out to 0.");
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    } finally {
      btnZeroBalance.disabled = false;
    }
  });

  btnNewCategory.addEventListener("click", async () => {
    const name = prompt("Enter new category name (e.g. Pets, Coffee, Side Hustle):");
    if (!name || !name.trim()) return;
    try {
      await apiFetch("/api/v1/categories", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: name.trim() }),
      });
      await loadCategories();
      showToast(`Category "${name.trim()}" added.`);
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  btnReviewPending.addEventListener("click", async () => {
    try {
      const pending = await apiFetch("/api/v1/transactions/pending");
      openDraftModal({
        source_type: "pending_drafts",
        model_used: "saved drafts",
        opening_balance: null,
        closing_balance: null,
        reconciled: false,
        skipped_exact_duplicates: 0,
        transactions: (pending || []).map((t) => ({
          ...t,
          confidence: 1.0,
        })),
      });
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  async function refreshDashboard() {
    const q = `year=${state.year}&month=${state.month}`;
    const status = txStatusFilter.value;
    const txUrl = state.rangeMode && state.rangeStart && state.rangeEnd
      ? `/api/v1/transactions?start_date=${encodeURIComponent(state.rangeStart)}&end_date=${encodeURIComponent(state.rangeEnd)}&status=${encodeURIComponent(status)}`
      : `/api/v1/transactions?${q}&status=${encodeURIComponent(status)}`;
    try {
      const [summary, txs, trend, budgets, recurring, goals, ious, wallets, templates, rules, tags, comparison] = await Promise.all([
        apiFetch(`/api/v1/spending/monthly?${q}`),
        apiFetch(txUrl),
        apiFetch(`/api/v1/spending/trend?months=6`),
        apiFetch(`/api/v1/budgets?${q}`),
        apiFetch(`/api/v1/recurring`),
        apiFetch(`/api/v1/goals`),
        apiFetch(`/api/v1/ious`),
        apiFetch(`/api/v1/wallets`),
        apiFetch(`/api/v1/templates`),
        apiFetch(`/api/v1/rules`),
        apiFetch(`/api/v1/tags`),
        apiFetch(`/api/v1/spending/comparison?${q}`),
      ]);
      state.baseCurrency = summary?.base_currency || "VND";
      state.wallets = wallets || [];
      state.templates = templates || [];
      state.rules = rules || [];
      state.tags = tags || [];
      state.selectedTxIds.clear();
      syncBulkBar();
      renderSummary(summary);
      renderComparison(comparison);
      renderTemplates(state.templates);
      renderWallets(state.wallets);
      renderRules(state.rules);
      syncTagFilter(state.tags);
      state.transactions = txs || [];
      renderTransactions();
      renderCalendarHeatmap(state.transactions);
      renderTrend(trend || []);
      renderBudgets(budgets || []);
      renderSafeToSpend(budgets || []);
      renderRecurring(recurring);
      renderGoals(goals || []);
      renderIous(ious || []);
      if (state.rangeMode && state.rangeStart && state.rangeEnd) {
        const rangeRes = await apiFetch(
          `/api/v1/spending/range?start_date=${encodeURIComponent(state.rangeStart)}&end_date=${encodeURIComponent(state.rangeEnd)}`
        );
        if (rangeSummaryEl && rangeRes) {
          rangeSummaryEl.textContent = `Spent: -${formatNumber(rangeRes.total_spent)} · Credit: +${formatNumber(rangeRes.total_credit)} · Net: ${formatNumber(rangeRes.net_balance)} ${rangeRes.base_currency}`;
        }
      }
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  }

  // --- Safe to Spend Today Pacing (#3) ---
  function renderSafeToSpend(budgets) {
    if (!safeTodayWrap || !safeTodayVal) return;
    const isCurrentMonth = state.year === now.getFullYear() && state.month === (now.getMonth() + 1);
    if (!budgets.length || !isCurrentMonth) {
      safeTodayWrap.classList.add("hidden");
      return;
    }
    const totalLimit = budgets.reduce((a, b) => a + Number(b.monthly_limit || 0), 0);
    const totalSpent = budgets.reduce((a, b) => a + Number(b.spent || 0), 0);
    const daysInMonth = new Date(state.year, state.month, 0).getDate();
    const daysLeft = Math.max(1, daysInMonth - now.getDate() + 1);
    const perDay = Math.round((totalLimit - totalSpent) / daysLeft);
    safeTodayWrap.classList.remove("hidden");
    safeTodayVal.textContent = perDay >= 0
      ? `${formatNumber(perDay)} / day`
      : `Over ${formatNumber(Math.abs(perDay))}/d`;
    safeTodayVal.className = `kpi-val mono ${perDay >= 0 ? "text-credit" : "text-debt"}`;
  }

  // --- Monthly Spending Calendar Heatmap (#4) ---
  function renderCalendarHeatmap(txs) {
    if (!calendarHeatmapEl) return;
    const daysInMonth = new Date(state.year, state.month, 0).getDate();
    const firstDow = (new Date(state.year, state.month - 1, 1).getDay() + 6) % 7; // Mon=0..Sun=6
    const dayTotals = {};
    for (const t of txs) {
      if ((t.tx_type || "debit") !== "debit" || t.is_transfer) continue;
      const d = Number((t.tx_date || "").slice(8, 10));
      if (d >= 1 && d <= daysInMonth) {
        dayTotals[d] = (dayTotals[d] || 0) + Number(t.base_amount ?? t.amount ?? 0);
      }
    }
    const maxDay = Math.max(1, ...Object.values(dayTotals));
    const cells = [];
    for (let i = 0; i < firstDow; i++) {
      cells.push(`<div class="hm-cell hm-empty"></div>`);
    }
    const today = new Date();
    for (let d = 1; d <= daysInMonth; d++) {
      const iso = `${state.year}-${String(state.month).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
      const amt = dayTotals[d] || 0;
      const isFuture = new Date(state.year, state.month - 1, d) > today;
      let cls = "";
      if (amt === 0 && !isFuture) cls = "hm-zero";
      else if (amt > 0) {
        const ratio = amt / maxDay;
        cls = ratio > 0.66 ? "hm-l3" : (ratio > 0.33 ? "hm-l2" : "hm-l1");
      }
      const compact = amt >= 1000000 ? `${(amt / 1000000).toFixed(1)}m` : (amt >= 1000 ? `${Math.round(amt / 1000)}k` : (amt > 0 ? String(Math.round(amt)) : ""));
      cells.push(`
        <button class="hm-cell ${cls}" type="button" data-hm-date="${iso}" title="${iso}: ${formatNumber(amt)} ${state.baseCurrency}">
          <span>${d}</span>
          ${compact ? `<span class="hm-amt mono">${compact}</span>` : ""}
        </button>`);
    }
    calendarHeatmapEl.innerHTML = cells.join("");
  }

  if (calendarHeatmapEl) {
    calendarHeatmapEl.addEventListener("click", (e) => {
      const cell = e.target.closest("[data-hm-date]");
      if (!cell) return;
      const iso = cell.dataset.hmDate;
      txSearch.value = iso;
      renderTransactions();
      activateWebTab("ledger");
    });
  }

  // --- Natural-Language Quick Input (#2, 100% local, 0 AI calls) ---
  function parseNaturalQuickInput(raw) {
    const tokens = raw.trim().split(/\s+/).filter(Boolean);
    if (!tokens.length) return null;
    let txType = "debit";
    let amount = null;
    const tags = [];
    let walletId = null;
    const words = [];

    for (const tok of tokens) {
      if (tok.startsWith("#") && tok.length > 1) {
        tags.push(tok.slice(1).toLowerCase());
        continue;
      }
      const amtMatch = tok.match(/^([+-])?(\d+(?:[.,]\d+)?)(k|m|tr)?$/i);
      if (amtMatch && amount === null) {
        if (amtMatch[1] === "+") txType = "credit";
        let num = Number(amtMatch[2].replace(",", "."));
        const unit = (amtMatch[3] || "").toLowerCase();
        if (unit === "k") num *= 1000;
        else if (unit === "m" || unit === "tr") num *= 1000000;
        if (num > 0) {
          amount = num;
          continue;
        }
      }
      const matchedWallet = state.wallets.find((w) => w.name.toLowerCase() === tok.toLowerCase());
      if (matchedWallet && !walletId) {
        walletId = matchedWallet.id;
        continue;
      }
      words.push(tok);
    }
    if (!amount || !words.length) return null;
    const vendor = words.join(" ").replace(/\b\w/g, (c) => c.toUpperCase());
    const lower = vendor.toLowerCase();
    let category = "Uncategorized";
    if (txType === "credit" && /salary|lương|bonus|payroll/.test(lower)) category = "Salary";
    else if (txType === "credit") category = "Income";
    else if (/coffee|cafe|cà phê|lunch|dinner|breakfast|pho|phở|food|eat|tea|trà/.test(lower)) category = "Dining";
    else if (/grab|taxi|uber|gas|xăng|parking|bus|metro|train/.test(lower)) category = "Transport";
    else if (/mart|grocery|supermarket|siêu thị|market|coop|winmart|bhx/.test(lower)) category = "Groceries";
    else if (/electric|water|internet|wifi|phone|bill|điện|nước/.test(lower)) category = "Utilities";
    else if (/netflix|spotify|icloud|youtube|gym/.test(lower)) category = "Subscriptions";
    return { amount: String(amount), vendor, txType, category, walletId, tags };
  }

  if (nlForm && nlInput) {
    nlForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const parsed = parseNaturalQuickInput(nlInput.value);
      if (!parsed) {
        showToast("Format: e.g. '50k coffee cash #work' or '+15m salary bank'");
        return;
      }
      try {
        await apiFetch("/api/v1/transactions", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tx_date: new Date().toISOString().slice(0, 10),
            vendor: parsed.vendor,
            category_name: parsed.category,
            wallet_id: parsed.walletId,
            amount: parsed.amount,
            currency: state.baseCurrency || "VND",
            tx_type: parsed.txType,
            tags: parsed.tags,
          }),
        });
        nlInput.value = "";
        showToast(`Logged ${parsed.vendor} (${parsed.txType === "credit" ? "+" : "-"}${formatNumber(parsed.amount)} ${state.baseCurrency})`);
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  // --- Split Transaction Modal (#5) ---
  function renderSplitRemaining() {
    if (!splittingTx || !splitRemainingEl) return;
    const orig = Number(splittingTx.amount || 0);
    const sum = Array.from(splitRowsEl.querySelectorAll(".split-amt"))
      .reduce((acc, el) => acc + (Number(el.value) || 0), 0);
    const rem = Math.round((orig - sum) * 100) / 100;
    splitRemainingEl.textContent = rem === 0 ? "Balanced ✓" : `Remaining: ${formatNumber(rem)}`;
    btnConfirmSplit.disabled = Math.abs(rem) > 0.01;
  }

  function addSplitRow(cat = "Uncategorized", amt = "") {
    const opts = CATEGORIES.map((c) => `<option value="${escapeHtml(c)}" ${c === cat ? "selected" : ""}>${escapeHtml(c)}</option>`).join("");
    const row = document.createElement("div");
    row.className = "split-row";
    row.innerHTML = `
      <select class="split-cat">${opts}</select>
      <input class="split-amt mono" type="number" step="0.01" min="0.01" value="${escapeHtml(amt)}" placeholder="Amount" />
      <button class="btn-del" type="button" title="Remove">&times;</button>`;
    row.querySelector(".split-amt").addEventListener("input", renderSplitRemaining);
    row.querySelector(".btn-del").addEventListener("click", () => {
      if (splitRowsEl.children.length > 2) {
        row.remove();
        renderSplitRemaining();
      }
    });
    splitRowsEl.appendChild(row);
    renderSplitRemaining();
  }

  function openSplitModal(tx) {
    splittingTx = tx;
    const total = Number(tx.amount || 0);
    const half1 = Math.floor(total / 2);
    const half2 = total - half1;
    splitMetaEl.textContent = `${tx.vendor} · Total: ${formatNumber(total)} ${tx.currency}`;
    splitRowsEl.innerHTML = "";
    addSplitRow(tx.category_name || "Groceries", String(half1));
    addSplitRow("Uncategorized", String(half2));
    splitModal.classList.remove("hidden");
  }

  function closeSplitModal() {
    splitModal.classList.add("hidden");
    splittingTx = null;
  }

  if (btnAddSplitRow) btnAddSplitRow.addEventListener("click", () => addSplitRow("Uncategorized", ""));
  if (btnCloseSplit) btnCloseSplit.addEventListener("click", closeSplitModal);
  if (btnCancelSplit) btnCancelSplit.addEventListener("click", closeSplitModal);
  if (btnSplitInModal) {
    btnSplitInModal.addEventListener("click", () => {
      const tx = state.transactions.find((t) => t.id === editTxIdEl.value);
      if (!tx) return;
      closeAddModal();
      openSplitModal(tx);
    });
  }
  if (btnConfirmSplit) {
    btnConfirmSplit.addEventListener("click", async () => {
      if (!splittingTx) return;
      const items = Array.from(splitRowsEl.querySelectorAll(".split-row")).map((row) => ({
        category_name: row.querySelector(".split-cat").value,
        amount: row.querySelector(".split-amt").value,
      }));
      btnConfirmSplit.disabled = true;
      try {
        await apiFetch(`/api/v1/transactions/${encodeURIComponent(splittingTx.id)}/split`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ items }),
        });
        closeSplitModal();
        showToast(`Split into ${items.length} categories.`);
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      } finally {
        btnConfirmSplit.disabled = false;
      }
    });
  }

  // --- Quick-Add Templates (#1) ---
  function renderTemplates(templates) {
    if (!templateListEl) return;
    if (!templates.length) {
      templateListEl.innerHTML = `<span class="muted" style="font-size:0.8rem;">No favorites yet</span>`;
      return;
    }
    templateListEl.innerHTML = templates.map((t) => `
      <span class="tpl-chip" data-tpl-use="${escapeHtml(t.id)}" title="1-tap log ${escapeHtml(t.vendor)}">
        <span>${t.tx_type === "credit" ? "+" : ""}${escapeHtml(t.label)}</span>
        <span class="mono muted">${formatNumber(t.amount)}</span>
        <button class="tpl-del" data-tpl-del="${escapeHtml(t.id)}" type="button" title="Delete template">&times;</button>
      </span>`).join("");
  }

  if (templateListEl) {
    templateListEl.addEventListener("click", async (e) => {
      const del = e.target.closest("[data-tpl-del]");
      if (del) {
        e.stopPropagation();
        if (!confirm("Delete this quick-add template?")) return;
        await apiFetch(`/api/v1/templates/${encodeURIComponent(del.dataset.tplDel)}`, { method: "DELETE" });
        showToast("Template deleted.");
        await refreshDashboard();
        return;
      }
      const chip = e.target.closest("[data-tpl-use]");
      if (chip) {
        try {
          const tx = await apiFetch(`/api/v1/templates/${encodeURIComponent(chip.dataset.tplUse)}/use`, { method: "POST" });
          showToast(`Logged ${tx.vendor} (${formatNumber(tx.amount)} ${tx.currency}).`);
          await refreshDashboard();
        } catch (err) {
          if (err.message !== "Unauthorized") showToast(err.message);
        }
      }
    });
  }

  if (btnNewTemplate) {
    btnNewTemplate.addEventListener("click", async () => {
      const label = prompt("Template button label (e.g. Morning Coffee):");
      if (!label || !label.trim()) return;
      const amount = prompt("Amount (e.g. 35000):");
      if (!amount || Number(amount) <= 0) return;
      const category = prompt("Category (e.g. Dining, Transport, Groceries):", "Dining") || "Uncategorized";
      try {
        await apiFetch("/api/v1/templates", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            label: label.trim(),
            vendor: label.trim(),
            amount: String(amount).trim(),
            category_name: category.trim(),
            currency: state.baseCurrency || "VND",
          }),
        });
        showToast("Quick-add template saved.");
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  // --- Wallets & Internal Transfers (#2) ---
  function renderWallets(wallets) {
    if (!walletListEl) return;
    const total = wallets.reduce((acc, w) => acc + Number(w.current_balance || 0), 0);
    if (walletsTotalEl) walletsTotalEl.textContent = `Total: ${formatNumber(total)} ${state.baseCurrency}`;

    const opts = wallets.map((w) => `<option value="${escapeHtml(w.id)}">${escapeHtml(w.name)}</option>`).join("");
    if (addWalletEl) {
      const prev = addWalletEl.value;
      addWalletEl.innerHTML = `<option value="">None</option>` + opts;
      addWalletEl.value = prev;
    }
    if (bulkWalletEl) {
      bulkWalletEl.innerHTML = `<option value="">Keep Wallet</option>` + opts;
    }
    if (txWalletFilter) {
      const prev = txWalletFilter.value || "all";
      txWalletFilter.innerHTML = `<option value="all">All Wallets</option>` + opts;
      txWalletFilter.value = prev;
    }
    if (transferFromEl) transferFromEl.innerHTML = opts;
    if (transferToEl) transferToEl.innerHTML = opts;

    if (!wallets.length) {
      walletListEl.innerHTML = `<div class="empty-block">No wallets yet. Add Cash, Bank, Credit Card, or E-Wallet below.</div>`;
      return;
    }
    const typeLabel = { cash: "Cash", bank: "Bank", credit_card: "Card", ewallet: "E-Wallet", other: "Other" };
    walletListEl.innerHTML = wallets.map((w) => {
      const bal = Number(w.current_balance || 0);
      return `
        <div class="wallet-card">
          <div class="wallet-top">
            <strong style="font-size:0.9rem;">${escapeHtml(w.name)}</strong>
            <span style="display:flex;align-items:center;gap:4px;">
              <span class="pill">${typeLabel[w.wallet_type] || w.wallet_type}</span>
              <button class="btn-del" data-wallet-del="${escapeHtml(w.id)}" title="Delete wallet">&times;</button>
            </span>
          </div>
          <div class="wallet-bal mono ${bal < 0 ? "text-debt" : "text-credit"}">${formatNumber(bal)} ${escapeHtml(w.currency)}</div>
          <div class="muted" style="font-size:0.74rem;">${w.transaction_count} tx</div>
        </div>`;
    }).join("");
  }

  if (walletForm) {
    walletForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await apiFetch("/api/v1/wallets", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: walletNameEl.value.trim(),
            wallet_type: walletTypeEl.value,
            initial_balance: walletInitialEl.value || "0",
            currency: state.baseCurrency || "VND",
          }),
        });
        walletNameEl.value = "";
        walletInitialEl.value = "0";
        showToast("Wallet added.");
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  if (walletListEl) {
    walletListEl.addEventListener("click", async (e) => {
      const del = e.target.closest("[data-wallet-del]");
      if (!del || !confirm("Delete this wallet? Existing transactions are kept.")) return;
      await apiFetch(`/api/v1/wallets/${encodeURIComponent(del.dataset.walletDel)}`, { method: "DELETE" });
      showToast("Wallet deleted.");
      await refreshDashboard();
    });
  }

  if (btnToggleTransfer && transferForm) {
    btnToggleTransfer.addEventListener("click", () => {
      if (state.wallets.length < 2) {
        showToast("Add at least 2 wallets to transfer between them.");
        return;
      }
      transferForm.classList.toggle("hidden");
    });
    transferForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await apiFetch("/api/v1/wallets/transfer", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            from_wallet_id: transferFromEl.value,
            to_wallet_id: transferToEl.value,
            amount: transferAmountEl.value,
          }),
        });
        transferAmountEl.value = "";
        transferForm.classList.add("hidden");
        showToast("Internal transfer recorded (spending/income unchanged).");
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  // --- Merchant Rules UI (#6) ---
  function renderRules(rules) {
    if (!rulesListEl) return;
    if (!rules.length) {
      rulesListEl.innerHTML = `<div class="empty-block">No rules yet. Add one below or edit any transaction's category.</div>`;
      return;
    }
    rulesListEl.innerHTML = rules.slice(0, 25).map((r) => `
      <div class="rule-row">
        <span><strong>${escapeHtml(r.merchant_pattern)}</strong> <span class="muted">→</span> <span class="pill">${escapeHtml(r.category_name)}</span></span>
        <button class="btn-del" data-rule-del="${escapeHtml(r.id)}" title="Delete rule">&times;</button>
      </div>`).join("");
  }

  if (ruleForm) {
    ruleForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await apiFetch("/api/v1/rules", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            merchant_pattern: rulePatternEl.value.trim(),
            category_name: ruleCategoryEl.value,
          }),
        });
        rulePatternEl.value = "";
        showToast("Auto-categorization rule saved.");
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  if (rulesListEl) {
    rulesListEl.addEventListener("click", async (e) => {
      const del = e.target.closest("[data-rule-del]");
      if (!del) return;
      await apiFetch(`/api/v1/rules/${encodeURIComponent(del.dataset.ruleDel)}`, { method: "DELETE" });
      showToast("Rule deleted.");
      await refreshDashboard();
    });
  }

  // --- Tags & Custom Date-Range / YTD (#5) ---
  function syncTagFilter(tags) {
    if (!txTagFilter) return;
    const prev = txTagFilter.value || "all";
    txTagFilter.innerHTML = `<option value="all">All Tags</option>` +
      tags.map((t) => `<option value="${escapeHtml(t.tag)}">#${escapeHtml(t.tag)} (${t.transaction_count})</option>`).join("");
    txTagFilter.value = prev;
  }

  if (btnToggleRange && rangeBar) {
    btnToggleRange.addEventListener("click", () => {
      rangeBar.classList.toggle("hidden");
      if (!rangeStartEl.value) {
        const y = new Date().getFullYear();
        rangeStartEl.value = `${y}-01-01`;
        rangeEndEl.value = new Date().toISOString().slice(0, 10);
      }
    });
    btnRangeApply.addEventListener("click", () => {
      if (!rangeStartEl.value || !rangeEndEl.value) return;
      state.rangeMode = true;
      state.rangeStart = rangeStartEl.value;
      state.rangeEnd = rangeEndEl.value;
      refreshDashboard();
    });
    btnRangeYtd.addEventListener("click", () => {
      const today = new Date();
      rangeStartEl.value = `${today.getFullYear()}-01-01`;
      rangeEndEl.value = today.toISOString().slice(0, 10);
      state.rangeMode = true;
      state.rangeStart = rangeStartEl.value;
      state.rangeEnd = rangeEndEl.value;
      refreshDashboard();
    });
    btnRange30d.addEventListener("click", () => {
      const today = new Date();
      const past = new Date();
      past.setDate(today.getDate() - 30);
      rangeStartEl.value = past.toISOString().slice(0, 10);
      rangeEndEl.value = today.toISOString().slice(0, 10);
      state.rangeMode = true;
      state.rangeStart = rangeStartEl.value;
      state.rangeEnd = rangeEndEl.value;
      refreshDashboard();
    });
    btnRangeClear.addEventListener("click", () => {
      state.rangeMode = false;
      state.rangeStart = null;
      state.rangeEnd = null;
      if (rangeSummaryEl) rangeSummaryEl.textContent = "";
      rangeBar.classList.add("hidden");
      refreshDashboard();
    });
  }

  // --- Full JSON Backup & Restore (#7) ---
  if (btnBackupExport) {
    btnBackupExport.addEventListener("click", async () => {
      try {
        const data = await apiFetch("/api/v1/backup/export");
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `spending-backup-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
        showToast(`Exported ${data.transactions?.length || 0} transactions to JSON.`);
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  if (btnBackupImport && backupFileInput) {
    btnBackupImport.addEventListener("click", () => backupFileInput.click());
    backupFileInput.addEventListener("change", async () => {
      const file = backupFileInput.files?.[0];
      backupFileInput.value = "";
      if (!file) return;
      try {
        const text = await file.text();
        const parsed = JSON.parse(text);
        const res = await apiFetch("/api/v1/backup/import", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(parsed),
        });
        showToast(`Restored ${res.transactions_added} tx (${res.transactions_skipped} skipped), ${res.wallets_added} wallets, ${res.rules_upserted} rules.`);
        await loadCategories();
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(`Import failed: ${err.message}`);
      }
    });
  }

  // AI Monthly Coach
  btnGenInsights.addEventListener("click", async () => {
    btnGenInsights.disabled = true;
    btnGenInsights.textContent = "Thinking...";
    try {
      const res = await apiFetch(`/api/v1/insights/monthly?year=${state.year}&month=${state.month}`);
      const tips = res?.tips || [];
      if (!tips.length) {
        insightsBodyEl.innerHTML = `<div class="empty-block">No insights generated for this month yet.</div>`;
      } else {
        insightsBodyEl.innerHTML = tips.map((t, i) =>
          `<div class="insight-item"><strong>${i + 1}.</strong> ${escapeHtml(t)}</div>`
        ).join("");
      }
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    } finally {
      btnGenInsights.disabled = false;
      btnGenInsights.textContent = "Generate Insights";
    }
  });

  // Goals
  function renderGoals(goals) {
    if (!goals.length) {
      goalsListEl.innerHTML = `<div class="empty-block">No goals yet. Add a savings or debt payoff target below.</div>`;
      return;
    }
    goalsListEl.innerHTML = goals.map((g) => {
      const cur = Number(g.current_amount || 0);
      const tgt = Number(g.target_amount || 1);
      const pct = Math.min(100, Math.round((cur / tgt) * 100));
      const color = g.goal_type === "debt_payoff" ? "var(--warn)" : "var(--accent)";
      return `
        <div>
          <div class="cat-row-top">
            <span class="cat-name-wrap">
              <span class="cat-dot" style="background:${color}"></span>
              <span>${escapeHtml(g.name)}</span>
              <span class="pill">${g.goal_type === "debt_payoff" ? "Debt" : "Savings"}</span>
            </span>
            <span class="row-actions">
              <span class="mono" style="font-size:0.82rem;">${formatNumber(cur)} / ${formatNumber(tgt)} (${pct}%)</span>
              <button class="btn-edit" data-goal-add="${escapeHtml(g.id)}" data-goal-cur="${cur}" title="Update progress">+</button>
              <button class="btn-del" data-goal-del="${escapeHtml(g.id)}" title="Delete goal">&times;</button>
            </span>
          </div>
          <div class="cat-bar-track">
            <div class="cat-bar-fill" style="width:${pct}%;background:${color}"></div>
          </div>
        </div>`;
    }).join("");
  }

  goalForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await apiFetch("/api/v1/goals", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: goalNameEl.value.trim(),
          goal_type: goalTypeEl.value,
          target_amount: goalTargetEl.value,
        }),
      });
      goalNameEl.value = "";
      goalTargetEl.value = "";
      showToast("Goal added.");
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  goalsListEl.addEventListener("click", async (e) => {
    const addBtn = e.target.closest("[data-goal-add]");
    if (addBtn) {
      const delta = prompt("Add progress amount (e.g. 500000):");
      if (!delta || Number.isNaN(Number(delta))) return;
      const nextVal = Math.max(0, Number(addBtn.dataset.goalCur) + Number(delta));
      try {
        await apiFetch(`/api/v1/goals/${encodeURIComponent(addBtn.dataset.goalAdd)}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ current_amount: String(nextVal) }),
        });
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
      return;
    }
    const delBtn = e.target.closest("[data-goal-del]");
    if (delBtn && confirm("Delete this goal?")) {
      await apiFetch(`/api/v1/goals/${encodeURIComponent(delBtn.dataset.goalDel)}`, { method: "DELETE" });
      await refreshDashboard();
    }
  });

  // IOUs
  function renderIous(ious) {
    const owedToMe = ious
      .filter((i) => !i.settled && i.direction === "owed_to_me")
      .reduce((a, b) => a + Number(b.amount || 0), 0);
    const iOwe = ious
      .filter((i) => !i.settled && i.direction === "i_owe")
      .reduce((a, b) => a + Number(b.amount || 0), 0);
    iouSummaryEl.textContent = `Owed to me: +${formatNumber(owedToMe)} · I owe: -${formatNumber(iOwe)}`;

    if (!ious.length) {
      iouListEl.innerHTML = `<div class="empty-block">No IOUs yet. Track splits or money lent below.</div>`;
      return;
    }
    iouListEl.innerHTML = ious.map((i) => {
      const isOwed = i.direction === "owed_to_me";
      return `
        <div class="iou-row ${i.settled ? "iou-settled" : ""}">
          <div>
            <strong>${escapeHtml(i.person_name)}</strong>
            <span class="pill ${isOwed ? "pill-ok" : "pill-debt"}" style="margin-left:6px;">
              ${isOwed ? "Owes me" : "I owe"}
            </span>
          </div>
          <span class="row-actions">
            <span class="mono ${isOwed ? "text-credit" : "text-debt"}" style="font-size:0.86rem;">
              ${isOwed ? "+" : "-"}${formatNumber(i.amount)} ${escapeHtml(i.currency)}
            </span>
            <button class="btn-edit" data-iou-settle="${escapeHtml(i.id)}" title="Toggle settled">${i.settled ? "Undo" : "✓"}</button>
            <button class="btn-del" data-iou-del="${escapeHtml(i.id)}" title="Delete">&times;</button>
          </span>
        </div>`;
    }).join("");
  }

  iouForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await apiFetch("/api/v1/ious", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          person_name: iouPersonEl.value.trim(),
          direction: iouDirectionEl.value,
          amount: iouAmountEl.value,
        }),
      });
      iouPersonEl.value = "";
      iouAmountEl.value = "";
      showToast("IOU added.");
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  iouListEl.addEventListener("click", async (e) => {
    const settleBtn = e.target.closest("[data-iou-settle]");
    if (settleBtn) {
      await apiFetch(`/api/v1/ious/${encodeURIComponent(settleBtn.dataset.iouSettle)}/settle`, { method: "PATCH" });
      await refreshDashboard();
      return;
    }
    const delBtn = e.target.closest("[data-iou-del]");
    if (delBtn && confirm("Delete this IOU?")) {
      await apiFetch(`/api/v1/ious/${encodeURIComponent(delBtn.dataset.iouDel)}`, { method: "DELETE" });
      await refreshDashboard();
    }
  });

  // --- MoM Comparison, Top Merchants, Savings Rate & No-Spend Streak (#2 & #3) ---
  function renderComparison(comp) {
    if (!comp) return;
    if (savingsRateBadge) {
      savingsRateBadge.textContent = comp.savings_rate_pct != null
        ? `Savings Rate: ${comp.savings_rate_pct}%`
        : "Savings Rate: —";
    }
    if (noSpendStreakBadge) {
      noSpendStreakBadge.textContent = `No-spend: ${comp.no_spend_days}d · Streak: ${comp.current_no_spend_streak}d`;
    }
    if (momChangesList) {
      const changes = comp.category_changes || [];
      if (!changes.length) {
        momChangesList.innerHTML = `<div class="empty-block">No month-over-month data yet.</div>`;
      } else {
        momChangesList.innerHTML = changes.slice(0, 6).map((c) => {
          const diff = Number(c.diff || 0);
          const pctStr = c.pct_change != null ? ` (${c.pct_change > 0 ? "+" : ""}${c.pct_change}%)` : "";
          const cls = diff > 0 ? "text-debt" : (diff < 0 ? "text-credit" : "");
          return `
            <div class="mom-row">
              <span>${escapeHtml(c.category_name)}</span>
              <span class="mono ${cls}">${diff > 0 ? "+" : ""}${formatNumber(diff)}${pctStr}</span>
            </div>`;
        }).join("");
      }
    }
    if (topMerchantsList) {
      const merchants = comp.top_merchants || [];
      if (!merchants.length) {
        topMerchantsList.innerHTML = `<div class="empty-block">No spending merchants this month.</div>`;
      } else {
        topMerchantsList.innerHTML = merchants.map((m) => `
          <div class="mom-row">
            <span>${escapeHtml(m.vendor)} <span class="muted">(${m.transaction_count}x)</span></span>
            <span class="mono">${formatNumber(m.total_spent)} ${escapeHtml(comp.base_currency)}</span>
          </div>`).join("");
      }
    }
  }

  // --- Bulk Selection & Actions (#4) ---
  function syncBulkBar() {
    if (!bulkBar) return;
    const count = state.selectedTxIds.size;
    bulkBar.classList.toggle("hidden", count === 0);
    if (bulkCountEl) bulkCountEl.textContent = `${count} selected`;
    if (bulkSelectAll && count === 0) bulkSelectAll.checked = false;
  }

  if (bulkSelectAll) {
    bulkSelectAll.addEventListener("change", () => {
      const checks = Array.from(txTbody.querySelectorAll(".tx-bulk-check"));
      checks.forEach((cb) => {
        cb.checked = bulkSelectAll.checked;
        if (cb.checked) state.selectedTxIds.add(cb.dataset.bulkId);
        else state.selectedTxIds.delete(cb.dataset.bulkId);
      });
      syncBulkBar();
    });
  }

  if (btnBulkClear) {
    btnBulkClear.addEventListener("click", () => {
      state.selectedTxIds.clear();
      txTbody.querySelectorAll(".tx-bulk-check").forEach((cb) => { cb.checked = false; });
      syncBulkBar();
    });
  }

  if (btnBulkApply) {
    btnBulkApply.addEventListener("click", async () => {
      const ids = Array.from(state.selectedTxIds);
      if (!ids.length) return;
      try {
        const res = await apiFetch("/api/v1/transactions/bulk", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            ids,
            action: "update",
            category_name: bulkCategoryEl.value || null,
            wallet_id: bulkWalletEl.value || null,
            add_tag: bulkTagEl.value.trim() || null,
          }),
        });
        bulkTagEl.value = "";
        showToast(`Updated ${res.affected_count} transaction(s).`);
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  if (btnBulkDelete) {
    btnBulkDelete.addEventListener("click", async () => {
      const ids = Array.from(state.selectedTxIds);
      if (!ids.length || !confirm(`Delete ${ids.length} selected transaction(s)?`)) return;
      try {
        const res = await apiFetch("/api/v1/transactions/bulk", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ids, action: "delete" }),
        });
        showToast(`Deleted ${res.affected_count} transaction(s).`);
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      }
    });
  }

  function renderRecurring(data) {
    const total = Number(data?.monthly_fixed_total || 0);
    const cur = data?.base_currency || state.baseCurrency;
    recurringTotalEl.textContent = `Fixed: ${formatNumber(total)} ${cur} / mo`;
    const items = data?.items || [];
    if (!items.length) {
      recurringListEl.innerHTML = `<div class="empty-block">No recurring bills detected yet. Mark any transaction as recurring or upload 2+ months of statements.</div>`;
      return;
    }
    recurringListEl.innerHTML = items.map((item, idx) => `
      <div class="recurring-card">
        <div class="recurring-top">
          <span>${escapeHtml(item.vendor)}</span>
          <span class="mono">${formatNumber(item.avg_amount)} ${escapeHtml(item.currency)}</span>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:4px;gap:6px;">
          <span class="muted" style="font-size:0.78rem;">
            ${escapeHtml(item.category_name)} · Next: <span class="mono">${escapeHtml(item.next_expected_date)}</span>
          </span>
          <button class="btn btn-ghost btn-sm" type="button" data-log-bill="${idx}">Log Now</button>
        </div>
      </div>`).join("");
    state.recurringItems = items;
  }

  if (recurringListEl) {
    recurringListEl.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-log-bill]");
      if (!btn) return;
      const item = (state.recurringItems || [])[Number(btn.dataset.logBill)];
      if (!item) return;
      btn.disabled = true;
      try {
        await apiFetch("/api/v1/transactions", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tx_date: new Date().toISOString().slice(0, 10),
            vendor: item.vendor,
            category_name: item.category_name,
            amount: String(item.avg_amount),
            currency: item.currency || state.baseCurrency || "VND",
            tx_type: "debit",
            is_recurring: true,
          }),
        });
        showToast(`Logged recurring bill: ${item.vendor}`);
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      } finally {
        btn.disabled = false;
      }
    });
  }

  function renderTrend(points) {
    if (!points.length) {
      trendChartEl.innerHTML = `<div class="empty-block">No cash flow history yet.</div>`;
      return;
    }
    const maxVal = Math.max(1, ...points.flatMap((p) => [Number(p.total_credit || 0), Number(p.total_spent || 0)]));
    trendChartEl.innerHTML = points.map((p) => {
      const credit = Number(p.total_credit || 0);
      const debit = Number(p.total_spent || 0);
      const cH = Math.max(3, Math.round((credit / maxVal) * 124));
      const dH = Math.max(3, Math.round((debit / maxVal) * 124));
      const label = new Date(p.year, p.month - 1, 1).toLocaleDateString(undefined, { month: "short" });
      const title = `${label} ${p.year} — Credit: +${formatNumber(credit)}, Debit: -${formatNumber(debit)}`;
      return `
        <div class="trend-col" title="${escapeHtml(title)}">
          <div class="trend-bars">
            <div class="trend-bar trend-bar-credit" style="height:${cH}px"></div>
            <div class="trend-bar trend-bar-debit" style="height:${dH}px"></div>
          </div>
          <span class="trend-label">${escapeHtml(label)}</span>
        </div>`;
    }).join("");
  }

  function renderBudgets(budgets) {
    const daysInMonth = new Date(state.year, state.month, 0).getDate();
    const isCurrentMonth = state.year === now.getFullYear() && state.month === (now.getMonth() + 1);
    const daysLeft = isCurrentMonth ? Math.max(0, daysInMonth - now.getDate()) : 0;
    budgetPacingEl.textContent = isCurrentMonth ? `${daysLeft}d left in month` : "";

    if (!budgets.length) {
      budgetListEl.innerHTML = `<div class="empty-block">Set a monthly limit below to track pacing.</div>`;
      return;
    }

    budgetListEl.innerHTML = budgets.map((b) => {
      const limit = Number(b.monthly_limit || 1);
      const spent = Number(b.spent || 0);
      const pct = Math.round((spent / limit) * 100);
      const barPct = Math.min(100, pct);
      const barColor = pct >= 100 ? "var(--danger)" : (pct >= 75 ? "var(--warn)" : "var(--accent)");
      const remaining = limit - spent;
      const statusText = remaining >= 0
        ? `${formatNumber(remaining)} left`
        : `Over by ${formatNumber(Math.abs(remaining))}`;
      return `
        <div>
          <div class="cat-row-top">
            <span class="cat-name-wrap">
              <span class="cat-dot" style="background:${barColor}"></span>
              <span>${escapeHtml(b.category_name)}</span>
            </span>
            <span class="mono" style="font-size:0.84rem;">${formatNumber(spent)} / ${formatNumber(limit)} <span class="muted">(${statusText})</span></span>
          </div>
          <div class="cat-bar-track">
            <div class="cat-bar-fill" style="width:${barPct}%;background:${barColor}"></div>
          </div>
        </div>`;
    }).join("");
  }

  function renderSummary(summary) {
    const carryForward = Number(summary?.carry_forward_balance || 0);
    const totalSpent = Number(summary?.total_spent || 0);
    const totalCredit = Number(summary?.total_credit || 0);
    const monthNet = summary?.net_balance != null ? Number(summary.net_balance) : (totalCredit - totalSpent);
    const running = summary?.cumulative_balance != null ? Number(summary.cumulative_balance) : (carryForward + monthNet);
    const count = Number(summary?.transaction_count || 0);

    netBalanceEl.textContent = `${running > 0 ? "+" : ""}${formatNumber(running)}`;
    netBalanceEl.classList.remove("text-credit", "text-debt");
    debtBadgeEl.classList.remove("pill-ok", "pill-debt");

    if (running < 0) {
      netBalanceEl.classList.add("text-debt");
      debtBadgeEl.classList.add("pill-debt");
      debtBadgeEl.textContent = "In Debt";
      btnZeroBalance.disabled = false;
    } else if (running > 0) {
      netBalanceEl.classList.add("text-credit");
      debtBadgeEl.classList.add("pill-ok");
      debtBadgeEl.textContent = "Surplus";
      btnZeroBalance.disabled = false;
    } else {
      debtBadgeEl.classList.add("pill-ok");
      debtBadgeEl.textContent = "Balanced";
      btnZeroBalance.disabled = true;
    }

    carryForwardEl.textContent = `${carryForward > 0 ? "+" : ""}${formatNumber(carryForward)}`;
    carryForwardEl.className = `kpi-val mono ${carryForward < 0 ? "text-debt" : (carryForward > 0 ? "text-credit" : "")}`;

    monthNetEl.textContent = `${monthNet > 0 ? "+" : ""}${formatNumber(monthNet)}`;
    monthNetEl.className = `kpi-val mono ${monthNet < 0 ? "text-debt" : (monthNet > 0 ? "text-credit" : "")}`;

    totalSpentEl.textContent = `-${formatNumber(totalSpent)}`;
    totalCreditEl.textContent = `+${formatNumber(totalCredit)}`;
    txCountEl.textContent = `${count} tx`;

    const pendingCount = Number(summary?.pending_draft_count || 0);
    if (pendingCount > 0) {
      pendingBannerText.textContent = `You have ${pendingCount} unreviewed draft transaction${pendingCount === 1 ? "" : "s"} waiting for confirmation.`;
      pendingBanner.classList.remove("hidden");
    } else {
      pendingBanner.classList.add("hidden");
    }

    const cats = summary?.by_category || [];
    if (!cats.length) {
      categoryListEl.innerHTML = `<div class="empty-block">No confirmed spending for this month yet.</div>`;
      return;
    }

    const total = totalSpent || 1;
    categoryListEl.innerHTML = cats.map((c) => {
      const pct = Math.min(100, Math.round((Number(c.total_spent) / total) * 100));
      const color = categoryColor(c.category_name);
      return `
        <div data-cat-click="${escapeHtml(c.category_name)}" style="cursor:pointer;" title="Click to filter Ledger by ${escapeHtml(c.category_name)}">
          <div class="cat-row-top">
            <span class="cat-name-wrap">
              <span class="cat-dot" style="background:${color}"></span>
              <span>${escapeHtml(c.category_name)}</span>
              <span class="muted">(${c.transaction_count})</span>
            </span>
            <span class="mono" style="font-size:0.86rem;">${formatNumber(c.total_spent)} <span class="muted">${pct}%</span></span>
          </div>
          <div class="cat-bar-track">
            <div class="cat-bar-fill" style="width:${pct}%;background:${color}"></div>
          </div>
        </div>`;
    }).join("");
  }

  function formatDateHeading(isoDate) {
    const todayIso = new Date().toISOString().slice(0, 10);
    const yest = new Date();
    yest.setDate(yest.getDate() - 1);
    const yestIso = yest.toISOString().slice(0, 10);
    if (isoDate === todayIso) return `Today · ${isoDate}`;
    if (isoDate === yestIso) return `Yesterday · ${isoDate}`;
    return isoDate;
  }

  function renderTransactions() {
    const query = txSearch.value.trim().toLowerCase();
    const catFilter = txCategoryFilter ? txCategoryFilter.value : "all";
    const walletFilter = txWalletFilter ? txWalletFilter.value : "all";
    const tagFilter = txTagFilter ? txTagFilter.value : "all";
    const filtered = state.transactions.filter((t) => {
      if (state.typeFilter !== "all" && (t.tx_type || "debit") !== state.typeFilter) return false;
      if (catFilter !== "all" && (t.category_name || "Uncategorized") !== catFilter) return false;
      if (walletFilter !== "all" && t.wallet_id !== walletFilter) return false;
      if (tagFilter !== "all" && !(t.tags || []).includes(tagFilter)) return false;
      if (!query) return true;
      return (t.vendor || "").toLowerCase().includes(query) ||
        (t.tx_date || "").toLowerCase().includes(query) ||
        (t.category_name || "").toLowerCase().includes(query) ||
        (t.wallet_name || "").toLowerCase().includes(query) ||
        (t.tags || []).some((tg) => tg.toLowerCase().includes(query.replace(/^#/, ""))) ||
        (t.description || "").toLowerCase().includes(query) ||
        (t.tx_type || "").toLowerCase().includes(query);
    });

    if (!filtered.length) {
      txTbody.innerHTML = `<tr><td colspan="8" class="empty-cell">No transactions match this filter.</td></tr>`;
      return;
    }

    // Group transactions by date with daily net subtotals
    const groups = new Map();
    for (const t of filtered) {
      const key = t.tx_date || "Unknown";
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(t);
    }

    const htmlParts = [];
    for (const [dateKey, items] of groups.entries()) {
      const dayNet = items.reduce((acc, t) => {
        const val = Number(t.base_amount ?? t.amount ?? 0);
        return acc + ((t.tx_type || "debit") === "credit" ? val : -val);
      }, 0);
      htmlParts.push(`
        <tr class="date-group-row">
          <td colspan="6">${escapeHtml(formatDateHeading(dateKey))}</td>
          <td class="num ${dayNet < 0 ? "text-debt" : (dayNet > 0 ? "text-credit" : "")}" colspan="2">
            ${dayNet > 0 ? "+" : ""}${formatNumber(dayNet)} ${escapeHtml(state.baseCurrency)}
          </td>
        </tr>`);

      for (const t of items) {
        const cat = t.category_name || "Uncategorized";
        const color = categoryColor(cat);
        const isCredit = (t.tx_type || "debit") === "credit";
        const sign = isCredit ? "+" : "-";
        const amtClass = isCredit ? "text-credit" : "text-debit";
        const statusClass = t.status === "duplicate_flagged" ? "pill-warn" : (t.status === "confirmed" ? "pill-ok" : "");
        const fxHint = t.base_amount && t.currency && t.currency !== state.baseCurrency
          ? `<div class="muted mono" style="font-size:0.74rem;">≈ ${formatNumber(t.base_amount)} ${escapeHtml(state.baseCurrency)}</div>`
          : "";
        const checked = state.selectedTxIds.has(t.id) ? "checked" : "";
        htmlParts.push(`
          <tr>
            <td><input class="tx-bulk-check" type="checkbox" data-bulk-id="${escapeHtml(t.id)}" ${checked} /></td>
            <td class="mono" style="font-size:0.83rem;color:var(--muted);white-space:nowrap;">${escapeHtml(t.tx_date)}</td>
            <td>
              <div style="font-weight:500;">
                ${escapeHtml(t.vendor)}
                ${t.is_recurring ? `<span class="pill" style="margin-left:4px;">↻ Recurring</span>` : ""}
                ${t.is_transfer ? `<span class="pill" style="margin-left:4px;">⇄ Transfer</span>` : ""}
                ${t.wallet_name ? `<span class="pill" style="margin-left:4px;">${escapeHtml(t.wallet_name)}</span>` : ""}
              </div>
              ${t.description ? `<div class="muted" style="font-size:0.8rem;">${escapeHtml(t.description)}</div>` : ""}
              ${(t.tags && t.tags.length) ? `<div style="margin-top:3px;">${t.tags.map((tg) => `<span class="tag-pill">#${escapeHtml(tg)}</span>`).join("")}</div>` : ""}
            </td>
            <td>
              <span class="pill">
                <span class="cat-dot" style="background:${color}"></span>
                ${escapeHtml(cat)}
              </span>
            </td>
            <td class="muted">${escapeHtml(t.source)}</td>
            <td>
              <span class="pill ${isCredit ? "pill-ok" : ""}" style="margin-right:4px;">${isCredit ? "Credit" : "Debit"}</span>
              <span class="pill ${statusClass}">${escapeHtml(t.status)}</span>
            </td>
            <td class="num ${amtClass}">
              ${sign}${formatNumber(t.amount)} <span class="muted" style="font-weight:500;">${escapeHtml(t.currency)}</span>
              ${fxHint}
            </td>
            <td style="text-align:right;">
              <span class="row-actions">
                <button class="btn-edit" data-edit-id="${escapeHtml(t.id)}" title="Edit transaction" aria-label="Edit">&#9998;</button>
                <button class="btn-del" data-del-id="${escapeHtml(t.id)}" title="Delete transaction" aria-label="Delete">&times;</button>
              </span>
            </td>
          </tr>`);
      }
    }
    txTbody.innerHTML = htmlParts.join("");
  }

  // Edit & Delete transaction click delegation
  txTbody.addEventListener("change", (e) => {
    const cb = e.target.closest(".tx-bulk-check");
    if (!cb) return;
    if (cb.checked) state.selectedTxIds.add(cb.dataset.bulkId);
    else state.selectedTxIds.delete(cb.dataset.bulkId);
    syncBulkBar();
  });

  txTbody.addEventListener("click", async (e) => {
    const editBtn = e.target.closest("[data-edit-id]");
    if (editBtn) {
      const tx = state.transactions.find((t) => t.id === editBtn.dataset.editId);
      if (tx) openEditModal(tx);
      return;
    }

    const delBtn = e.target.closest("[data-del-id]");
    if (!delBtn) return;
    const id = delBtn.dataset.delId;
    if (!confirm("Delete this transaction?")) return;
    try {
      await apiFetch(`/api/v1/transactions/${encodeURIComponent(id)}`, { method: "DELETE" });
      showToast("Transaction deleted.");
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  // Export CSV
  btnExportCsv.addEventListener("click", () => {
    if (!state.transactions.length) {
      showToast("No transactions to export.");
      return;
    }
    const header = ["Date", "Merchant", "Description", "Category", "Type", "Amount", "Currency", "Source", "Status"];
    const esc = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
    const rows = state.transactions.map((t) => [
      t.tx_date, t.vendor, t.description || "", t.category_name || "Uncategorized",
      t.tx_type || "debit", t.amount, t.currency, t.source, t.status
    ].map(esc).join(","));
    const csv = [header.join(","), ...rows].join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `spending-${state.year}-${String(state.month).padStart(2, "0")}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  });

  // Budget Upsert
  budgetForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const limit = budgetLimitEl.value.trim();
    if (!limit) return;
    try {
      await apiFetch("/api/v1/budgets", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          category_name: budgetCategoryEl.value,
          monthly_limit: limit,
        }),
      });
      budgetLimitEl.value = "";
      showToast(`Budget updated for ${budgetCategoryEl.value}.`);
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  // Quick Add / Edit Transaction Modal
  function openAddModal() {
    editTxIdEl.value = "";
    addModalTitle.textContent = "Add Transaction";
    btnSubmitAdd.textContent = "Save Transaction";
    btnDeleteInModal.classList.add("hidden");
    if (btnDuplicateInModal) btnDuplicateInModal.classList.add("hidden");
    if (btnSplitInModal) btnSplitInModal.classList.add("hidden");
    addTypeEl.value = "debit";
    addDateEl.value = new Date().toISOString().slice(0, 10);
    addVendorEl.value = "";
    addAmountEl.value = "";
    addCurrencyEl.value = state.baseCurrency || "VND";
    if (addWalletEl) addWalletEl.value = "";
    if (addTagsEl) addTagsEl.value = "";
    addDescEl.value = "";
    addRecurringEl.checked = false;
    addModal.classList.remove("hidden");
    addVendorEl.focus();
  }

  function openEditModal(tx) {
    editTxIdEl.value = tx.id;
    addModalTitle.textContent = "Edit Transaction";
    btnSubmitAdd.textContent = "Save Changes";
    btnDeleteInModal.classList.remove("hidden");
    if (btnDuplicateInModal) btnDuplicateInModal.classList.remove("hidden");
    if (btnSplitInModal) btnSplitInModal.classList.remove("hidden");
    addTypeEl.value = (tx.tx_type || "debit") === "credit" ? "credit" : "debit";
    addDateEl.value = tx.tx_date || new Date().toISOString().slice(0, 10);
    addVendorEl.value = tx.vendor || "";
    addAmountEl.value = tx.amount || "";
    addCurrencyEl.value = tx.currency || state.baseCurrency || "VND";
    addCategoryEl.value = tx.category_name || "Uncategorized";
    if (addWalletEl) addWalletEl.value = tx.wallet_id || "";
    if (addTagsEl) addTagsEl.value = (tx.tags || []).join(", ");
    addDescEl.value = tx.description || "";
    addRecurringEl.checked = Boolean(tx.is_recurring);
    addModal.classList.remove("hidden");
    addVendorEl.focus();
  }

  function closeAddModal() {
    addModal.classList.add("hidden");
  }

  btnOpenAdd.addEventListener("click", openAddModal);
  btnCloseAdd.addEventListener("click", closeAddModal);
  btnCancelAdd.addEventListener("click", closeAddModal);

  btnDeleteInModal.addEventListener("click", async () => {
    const id = editTxIdEl.value;
    if (!id || !confirm("Delete this transaction?")) return;
    try {
      await apiFetch(`/api/v1/transactions/${encodeURIComponent(id)}`, { method: "DELETE" });
      closeAddModal();
      showToast("Transaction deleted.");
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    }
  });

  if (btnDuplicateInModal) {
    btnDuplicateInModal.addEventListener("click", async () => {
      btnDuplicateInModal.disabled = true;
      try {
        const rawTags = addTagsEl ? addTagsEl.value.split(",").map((s) => s.trim()).filter(Boolean) : [];
        await apiFetch("/api/v1/transactions", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            tx_date: new Date().toISOString().slice(0, 10),
            vendor: addVendorEl.value.trim(),
            description: addDescEl.value.trim(),
            category_name: addCategoryEl.value,
            wallet_id: (addWalletEl && addWalletEl.value) ? addWalletEl.value : null,
            amount: addAmountEl.value,
            currency: addCurrencyEl.value,
            tx_type: addTypeEl.value,
            is_recurring: addRecurringEl.checked,
            tags: rawTags,
          }),
        });
        closeAddModal();
        showToast("Duplicated transaction to today.");
        await refreshDashboard();
      } catch (err) {
        if (err.message !== "Unauthorized") showToast(err.message);
      } finally {
        btnDuplicateInModal.disabled = false;
      }
    });
  }

  addForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    btnSubmitAdd.disabled = true;
    const editId = editTxIdEl.value;
    try {
      const rawTags = addTagsEl ? addTagsEl.value.split(",").map((s) => s.trim()).filter(Boolean) : [];
      const payload = {
        tx_date: addDateEl.value,
        vendor: addVendorEl.value.trim(),
        description: addDescEl.value.trim(),
        category_name: addCategoryEl.value,
        wallet_id: (addWalletEl && addWalletEl.value) ? addWalletEl.value : null,
        amount: addAmountEl.value,
        currency: addCurrencyEl.value,
        tx_type: addTypeEl.value,
        is_recurring: addRecurringEl.checked,
        tags: rawTags,
      };
      if (editId) {
        await apiFetch(`/api/v1/transactions/${encodeURIComponent(editId)}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        showToast("Transaction updated.");
      } else {
        await apiFetch("/api/v1/transactions", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        showToast("Transaction added.");
      }
      closeAddModal();
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    } finally {
      btnSubmitAdd.disabled = false;
    }
  });

  // --- Upload & Client-Side Image Downscaling (1600px JPEG) ---

  function setSelectedFiles(fileList) {
    state.selectedFiles = Array.from(fileList || []);
    if (!state.selectedFiles.length) {
      selectedFilesLabel.textContent = "JPG, PNG, PDF (encrypted supported), CSV";
      btnUpload.disabled = true;
    } else {
      selectedFilesLabel.textContent = state.selectedFiles.map((f) => f.name).join(" · ");
      btnUpload.disabled = false;
    }
  }

  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fileInput.click(); }
  });
  fileInput.addEventListener("change", () => setSelectedFiles(fileInput.files));
  dropzone.addEventListener("dragover", (e) => { e.preventDefault(); dropzone.classList.add("dragover"); });
  dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
  dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    setSelectedFiles(e.dataTransfer.files);
  });

  async function downscaleImageIfNeeded(file) {
    if (!file.type.startsWith("image/")) return file;
    try {
      const bitmap = await createImageBitmap(file);
      const maxDim = 1600;
      const scale = Math.min(1, maxDim / Math.max(bitmap.width, bitmap.height));
      const w = Math.max(1, Math.round(bitmap.width * scale));
      const h = Math.max(1, Math.round(bitmap.height * scale));
      const canvas = document.createElement("canvas");
      canvas.width = w;
      canvas.height = h;
      canvas.getContext("2d").drawImage(bitmap, 0, 0, w, h);
      bitmap.close();
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.85));
      if (!blob) return file;
      const jpgName = file.name.replace(/\.[^.]+$/, "") + ".jpg";
      return new File([blob], jpgName, { type: "image/jpeg" });
    } catch (_) {
      return file;
    }
  }

  async function performUpload(files, sourceType, pdfPassword) {
    btnUpload.disabled = true;
    btnUpload.textContent = "Extracting with Gemini AI...";

    try {
      const form = new FormData();
      for (const f of files) {
        const processed = await downscaleImageIfNeeded(f);
        form.append("files[]", processed, processed.name);
      }
      if (sourceType) form.append("source_type", sourceType);
      if (pdfPassword) form.append("pdf_password", pdfPassword);

      const data = await apiFetch("/api/v1/ingest/media", {
        method: "POST",
        body: form,
      });

      setSelectedFiles([]);
      fileInput.value = "";
      pdfPasswordInput.value = "";
      openDraftModal(data);
    } catch (err) {
      if (err.status === 422 && /password/i.test(err.message)) {
        state.pendingPasswordFiles = { files, sourceType };
        modalPdfPwd.value = "";
        pwdModal.classList.remove("hidden");
        modalPdfPwd.focus();
      } else if (err.message !== "Unauthorized") {
        showToast(err.message);
      }
    } finally {
      btnUpload.textContent = "Extract Transactions";
      btnUpload.disabled = state.selectedFiles.length === 0;
    }
  }

  btnUpload.addEventListener("click", () => {
    performUpload(state.selectedFiles, sourceTypeSelect.value, pdfPasswordInput.value.trim());
  });

  btnPwdCancel.addEventListener("click", () => {
    state.pendingPasswordFiles = null;
    pwdModal.classList.add("hidden");
  });

  btnPwdSubmit.addEventListener("click", () => {
    const pwd = modalPdfPwd.value.trim();
    if (!pwd || !state.pendingPasswordFiles) return;
    const { files, sourceType } = state.pendingPasswordFiles;
    state.pendingPasswordFiles = null;
    pwdModal.classList.add("hidden");
    performUpload(files, sourceType, pwd);
  });

  // --- Draft Confirmation Modal ---

  function openDraftModal(ingest) {
    state.pendingDraft = ingest;
    draftMeta.textContent = `${ingest.source_type} · ${ingest.model_used}`;

    if (ingest.opening_balance != null && ingest.closing_balance != null) {
      reconciliationBanner.classList.remove("hidden", "banner-ok", "banner-warn");
      if (ingest.reconciled) {
        reconciliationBanner.classList.add("banner-ok");
        reconciliationBanner.textContent = `Statement balances reconciled (${formatNumber(ingest.opening_balance)} → ${formatNumber(ingest.closing_balance)})`;
      } else {
        reconciliationBanner.classList.add("banner-warn");
        reconciliationBanner.textContent = `Balance discrepancy detected (diff: ${ingest.reconciliation_diff}). Please verify rows below.`;
      }
    } else {
      reconciliationBanner.classList.add("hidden");
    }

    if (ingest.skipped_exact_duplicates > 0) {
      skippedBanner.textContent = `Skipped ${ingest.skipped_exact_duplicates} exact duplicate row(s).`;
      skippedBanner.classList.remove("hidden");
    } else {
      skippedBanner.classList.add("hidden");
    }

    const txs = ingest.transactions || [];
    if (!txs.length) {
      draftItemsEl.innerHTML = `<div class="empty-block">No new transactions were found in this document.</div>`;
    } else {
      draftItemsEl.innerHTML = txs.map((tx) => {
        const isDup = tx.status === "duplicate_flagged";
        const isCredit = (tx.tx_type || "debit") === "credit";
        const catOptions = CATEGORIES.map((c) =>
          `<option value="${c}" ${c.toLowerCase() === (tx.category_name || "").toLowerCase() ? "selected" : ""}>${c}</option>`
        ).join("");
        return `
          <div class="draft-item" data-id="${escapeHtml(tx.id)}">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
              <label style="display:flex;align-items:center;gap:8px;cursor:pointer;font-size:0.84rem;font-weight:600;">
                <input type="checkbox" class="draft-check" style="width:auto;" ${isDup ? "" : "checked"} />
                Include
              </label>
              <div style="display:flex;align-items:center;gap:6px;">
                <span class="pill ${isCredit ? "pill-ok" : ""}">${isCredit ? "Credit (+)" : "Debit (-)"}</span>
                ${isDup ? `<span class="pill pill-warn">Possible duplicate (±2 days)</span>` : `<span class="muted mono" style="font-size:0.78rem;">${Math.round((tx.confidence || 0) * 100)}%</span>`}
              </div>
            </div>
            <div class="draft-item-grid">
              <input type="text" class="draft-vendor" value="${escapeHtml(tx.vendor)}" placeholder="Merchant" />
              <select class="draft-type">
                <option value="debit" ${!isCredit ? "selected" : ""}>Debit (-)</option>
                <option value="credit" ${isCredit ? "selected" : ""}>Credit (+)</option>
              </select>
              <input type="number" step="0.01" class="draft-amount mono" value="${escapeHtml(tx.amount)}" placeholder="Amount" />
              <input type="date" class="draft-date mono" value="${escapeHtml(tx.tx_date)}" />
              <select class="draft-category">${catOptions}</select>
            </div>
          </div>`;
      }).join("");
    }

    draftModal.classList.remove("hidden");
  }

  function closeDraftModal() {
    draftModal.classList.add("hidden");
    state.pendingDraft = null;
  }

  btnCloseDraft.addEventListener("click", closeDraftModal);
  btnCancelDraft.addEventListener("click", closeDraftModal);

  btnConfirmDraft.addEventListener("click", async () => {
    const cards = Array.from(draftItemsEl.querySelectorAll(".draft-item"));
    if (!cards.length) {
      closeDraftModal();
      return;
    }

    const transactions = [];
    const discardIds = [];

    for (const card of cards) {
      const id = card.dataset.id;
      const checked = card.querySelector(".draft-check").checked;
      if (checked) {
        transactions.push({
          id,
          vendor: card.querySelector(".draft-vendor").value.trim(),
          tx_type: card.querySelector(".draft-type").value,
          amount: card.querySelector(".draft-amount").value,
          tx_date: card.querySelector(".draft-date").value,
          category_name: card.querySelector(".draft-category").value,
        });
      } else {
        discardIds.push(id);
      }
    }

    btnConfirmDraft.disabled = true;
    try {
      const res = await apiFetch("/api/v1/transactions/confirm", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ transactions, discard_ids: discardIds }),
      });
      closeDraftModal();
      showToast(`Confirmed ${res.confirmed_count} transaction(s), discarded ${res.discarded_count}.`);
      await refreshDashboard();
    } catch (err) {
      if (err.message !== "Unauthorized") showToast(err.message);
    } finally {
      btnConfirmDraft.disabled = false;
    }
  });

  // --- Bootstrap ---

  async function init() {
    if (!state.token) {
      authView.classList.remove("hidden");
      return;
    }
    try {
      state.user = await apiFetch("/api/v1/auth/me");
      await enterApp();
    } catch (_) {
      logout();
    }
  }

  // Register PWA Service Worker
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  }

  init();
})();
