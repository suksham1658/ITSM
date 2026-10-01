/* ITSM shell — sidebar, header menus, modal, toasts. No LDAP or ticket engine. */
(function () {
  "use strict";

  var MOBILE = 768;

  function $(id) { return document.getElementById(id); }

  window.toggleSidebar = function () {
    var sb = $("sidebar");
    if (!sb) { return; }
    if (window.innerWidth <= MOBILE) {
      sb.classList.toggle("mobile-open");
      var bd = $("sidebarBackdrop");
      if (bd) { bd.classList.toggle("show"); }
      return;
    }
    setCollapsed(!sb.classList.contains("collapsed"));
    try { window.localStorage.setItem(COLLAPSE_KEY, sb.classList.contains("collapsed") ? "1" : "0"); } catch (e) { /* storage blocked */ }
  };

  var COLLAPSE_KEY = "itsm.sidebarCollapsed";

  function setCollapsed(collapsed) {
    var sb = $("sidebar");
    if (!sb) { return; }
    sb.classList.toggle("collapsed", collapsed);
    var icon = $("collapseIcon");
    var lbl = $("collapseLbl");
    if (icon) { icon.className = collapsed ? "fa-solid fa-angles-right" : "fa-solid fa-angles-left"; }
    if (lbl) { lbl.textContent = collapsed ? "Expand" : "Collapse"; }
    var btn = document.querySelector(".sidebar-foot [data-action='toggle-sidebar']");
    if (btn) {
      btn.setAttribute("aria-expanded", collapsed ? "false" : "true");
      btn.setAttribute("aria-label", collapsed ? "Expand sidebar" : "Collapse sidebar");
      btn.title = collapsed ? "Expand" : "Collapse";
    }
  }

  // Restore the desktop collapsed state chosen on a previous page.
  try {
    if (window.innerWidth > MOBILE && window.localStorage.getItem(COLLAPSE_KEY) === "1") { setCollapsed(true); }
  } catch (e) { /* storage blocked */ }

  window.closeMobileSidebar = function () {
    var sb = $("sidebar");
    var bd = $("sidebarBackdrop");
    if (sb) { sb.classList.remove("mobile-open"); }
    if (bd) { bd.classList.remove("show"); }
  };

  window.toggleNotifPanel = function () {
    var p = $("notifPanel");
    var u = $("userPanel");
    if (u) { u.classList.remove("show"); }
    if (p) { p.classList.toggle("show"); }
  };

  window.toggleUserPanel = function () {
    var p = $("userPanel");
    var n = $("notifPanel");
    if (n) { n.classList.remove("show"); }
    if (p) { p.classList.toggle("show"); }
  };

  window.closeModal = function () {
    var ov = $("modalOverlay");
    if (ov) { ov.classList.remove("show"); }
  };

  window.openModal = function (title, bodyHtml, footHtml) {
    var ov = $("modalOverlay");
    if ($("modalTitle")) { $("modalTitle").textContent = title || ""; }
    if ($("modalBody")) { $("modalBody").innerHTML = bodyHtml || ""; }
    if ($("modalFoot")) { $("modalFoot").innerHTML = footHtml || ""; }
    if (ov) { ov.classList.add("show"); }
  };

  window.showToast = function (kind, message) {
    var stack = $("toastStack");
    if (!stack) { return; }
    var el = document.createElement("div");
    el.className = "toast " + (kind || "info");
    el.setAttribute("role", "status");
    el.textContent = message;
    stack.appendChild(el);
    setTimeout(function () {
      if (el.parentNode) { el.parentNode.removeChild(el); }
    }, 4200);
  };

  // The CSP (script-src 'self') blocks inline onclick="…" handlers, so every shell control
  // declares data-action="…" and is wired here.
  var ACTIONS = {
    "toggle-sidebar": function () { window.toggleSidebar(); },
    "close-mobile-sidebar": function () { window.closeMobileSidebar(); },
    "toggle-notif-panel": function () { window.toggleNotifPanel(); },
    "toggle-user-panel": function () { window.toggleUserPanel(); },
    "close-modal": function () { window.closeModal(); },
    "print": function () { window.print(); },
    // Error page: return to the previous page when there is one, else follow the link (portal home).
    "history-back": function (el) {
      if (document.referrer && window.history.length > 1) { window.history.back(); }
      else { window.location.href = el.getAttribute("href"); }
    }
  };

  document.addEventListener("click", function (ev) {
    var el = ev.target.closest ? ev.target.closest("[data-action]") : null;
    if (!el) { return; }
    var fn = ACTIONS[el.getAttribute("data-action")];
    if (fn) {
      ev.preventDefault();
      fn(el);
    }
  });

  document.addEventListener("click", function (ev) {
    var t = ev.target;
    if (!t.closest) { return; }
    if (!t.closest("#notifPanel") && !t.closest(".header-icon-btn")) {
      var np = $("notifPanel");
      if (np) { np.classList.remove("show"); }
    }
    if (!t.closest("#userPanel") && !t.closest(".user-menu")) {
      var up = $("userPanel");
      if (up) { up.classList.remove("show"); }
    }
  });

  document.addEventListener("keydown", function (ev) {
    if (ev.key === "Escape") {
      window.closeModal();
      window.closeMobileSidebar();
    }
  });

  var cat = document.getElementById("categorySelect");
  var sub = document.getElementById("subCategorySelect");
  if (cat && sub) {
    var opts = [];
    for (var i = 0; i < sub.options.length; i++) {
      opts.push(sub.options[i]);
    }
    function filterSubs() {
      var id = cat.value;
      var keep = sub.value;
      sub.innerHTML = "";
      var ph = document.createElement("option");
      ph.value = "";
      ph.textContent = "Select sub-category";
      sub.appendChild(ph);
      for (var j = 0; j < opts.length; j++) {
        var o = opts[j];
        var cid = o.getAttribute("data-category-id");
        if (!cid) { continue; }
        if (String(cid) === String(id)) {
          sub.appendChild(o);
        }
      }
      if (keep) { sub.value = keep; }
    }
    cat.addEventListener("change", filterSubs);
    filterSubs();
  }

  // ---- Raise Request: Serial Number appears (and is mandatory) only for the Hardware category ----
  var serialGroup = document.getElementById("serialGroup");
  if (cat && serialGroup) {
    var serialEnter = document.getElementById("serialEnter");
    var serialNa = document.getElementById("serialNa");
    var serialText = document.getElementById("serialNumber");
    function syncSerialText() {
      var typing = serialEnter.checked;
      serialText.required = typing && !serialGroup.hidden;
      serialText.disabled = serialNa.checked;
      if (serialNa.checked) { serialText.value = ""; }
    }
    function toggleSerial() {
      var opt = cat.options[cat.selectedIndex];
      var show = !!opt && opt.getAttribute("data-code") === serialGroup.getAttribute("data-serial-category");
      serialGroup.hidden = !show;
      serialEnter.required = show; // a required radio makes the whole Enter / Not Available choice mandatory
      if (!show) {
        serialEnter.checked = false;
        serialNa.checked = false;
        serialText.value = "";
      }
      syncSerialText();
    }
    serialText.addEventListener("focus", function () { serialEnter.checked = true; syncSerialText(); });
    serialEnter.addEventListener("change", function () { syncSerialText(); serialText.focus(); });
    serialNa.addEventListener("change", syncSerialText);
    cat.addEventListener("change", toggleSerial);
    toggleSerial();
  }

  // ---- Raise Request: warn before upload when a PDF is larger than allowed ----
  var attachment = document.getElementById("attachment");
  if (attachment && attachment.getAttribute("data-pdf-max-mb")) {
    attachment.addEventListener("change", function () {
      var maxMb = Number(attachment.getAttribute("data-pdf-max-mb"));
      var f = attachment.files && attachment.files[0];
      var tooBig = f && /\.pdf$/i.test(f.name) && f.size > maxMb * 1024 * 1024;
      attachment.setCustomValidity(tooBig ? "PDF files must not exceed " + maxMb + " MB." : "");
      if (tooBig) { attachment.reportValidity(); }
    });
  }

  // ---- Mobile tables: each cell gets its column heading as data-label so CSS can show rows as cards ----
  var tables = document.querySelectorAll("table.data-table:not(.no-stack)");
  for (var ti = 0; ti < tables.length; ti++) {
    var table = tables[ti];
    var heads = table.querySelectorAll("thead th");
    var labels = [];
    for (var hi = 0; hi < heads.length; hi++) {
      labels.push((heads[hi].textContent || "").replace(/\s+/g, " ").trim());
    }
    var rows = table.querySelectorAll("tbody tr");
    for (var ri = 0; ri < rows.length; ri++) {
      var col = 0;
      var cells = rows[ri].children;
      for (var ci = 0; ci < cells.length; ci++) {
        var cell = cells[ci];
        var span = parseInt(cell.getAttribute("colspan") || "1", 10);
        var label = span > 1 ? "" : (labels[col] || "");
        if (label) { cell.setAttribute("data-label", label); } else { cell.classList.add("no-label"); }
        col += span;
      }
    }
    table.classList.add("stack");
  }

  // ---- Form validation (mirrors the server rules; the server still re-checks everything) ----

  // Plain HTML required/minlength accepts spaces-only text and skips pre-filled values, so check
  // the trimmed value of every text field on submit and let the browser show the message.
  function trimmedProblem(f) {
    var v = (f.value || "").replace(/^\s+|\s+$/g, "");
    var min = parseInt(f.getAttribute("minlength") || "0", 10);
    var max = parseInt(f.getAttribute("maxlength") || "0", 10);
    if (f.required && !v) { return "Please fill in this field."; }
    if (v && min && v.length < min) { return "Enter at least " + min + " characters (currently " + v.length + ")."; }
    if (v && max && v.length > max) { return "Enter at most " + max + " characters (currently " + v.length + ")."; }
    return "";
  }

  var allForms = document.querySelectorAll("form");
  for (var fi = 0; fi < allForms.length; fi++) {
    allForms[fi].addEventListener("submit", function (ev) {
      var form = this;
      var fields = form.querySelectorAll("input[type='text'], input[type='search'], input:not([type]), textarea");
      for (var k = 0; k < fields.length; k++) {
        fields[k].setCustomValidity(fields[k].disabled ? "" : trimmedProblem(fields[k]));
      }
      if (!form.checkValidity()) {
        ev.preventDefault();
        ev.stopImmediatePropagation();
        form.reportValidity();
      }
    }, true);
    allForms[fi].addEventListener("input", function (ev) {
      if (ev.target && ev.target.setCustomValidity) { ev.target.setCustomValidity(""); }
    });
  }

  // "n / max" counter under long text fields.
  var counted = document.querySelectorAll("[data-counter][maxlength]");
  for (var ci = 0; ci < counted.length; ci++) {
    (function (field) {
      var out = document.createElement("small");
      out.className = "text-muted field-counter";
      field.parentNode.appendChild(out);
      function update() {
        var len = (field.value || "").length;
        out.textContent = len + " / " + field.getAttribute("maxlength");
      }
      field.addEventListener("input", update);
      update();
    })(counted[ci]);
  }

  // Date ranges: keep "from" on or before "to".
  var fromDates = document.querySelectorAll("input[type='date'][name='from']");
  for (var di = 0; di < fromDates.length; di++) {
    (function (fromEl) {
      var toEl = fromEl.form ? fromEl.form.querySelector("input[type='date'][name='to']") : null;
      if (!toEl) { return; }
      function sync() {
        toEl.min = fromEl.value || "";
        fromEl.max = toEl.value || "";
      }
      fromEl.addEventListener("change", sync);
      toEl.addEventListener("change", sync);
      sync();
    })(fromDates[di]);
  }

  // Ticket action button follows the chosen action: red to reject / send back, grey to hold,
  // green for everything that moves the ticket forward.
  var actionSelect = document.getElementById("actionCode");
  var actionBtn = document.getElementById("actionSubmit");
  if (actionSelect && actionBtn) {
    var paintAction = function () {
      var code = (actionSelect.value || "").toUpperCase();
      actionBtn.classList.remove("btn-accent", "btn-danger", "btn-neutral");
      if (code === "REJECT" || code === "SEND_BACK") {
        actionBtn.classList.add("btn-danger");
      } else if (code === "HOLD") {
        actionBtn.classList.add("btn-neutral");
      } else {
        actionBtn.classList.add("btn-accent");
      }
      actionBtn.textContent = code ? code.charAt(0) + code.slice(1).toLowerCase().replace("_", " ") : "Submit action";
    };
    actionSelect.addEventListener("change", paintAction);
    paintAction();
  }

  var actionForm = document.getElementById("ticketActionForm");
  if (actionForm) {
    actionForm.addEventListener("submit", function (ev) {
      var act = document.getElementById("actionCode");
      var rem = document.getElementById("actionRemarks");
      if (!act || !rem) { return; }
      var code = (act.value || "").toUpperCase();
      if (code === "ASSIGN" && document.getElementById("implementorPicker")
          && !actionForm.querySelector("input[name='assigneeIds']:checked")) {
        ev.preventDefault();
        window.showToast("error", "Tick at least one implementor to assign the ticket to.");
        return;
      }
      if (code === "APPROVE" || code === "REJECT" || code === "SEND_BACK") {
        var t = (rem.value || "").replace(/^\s+|\s+$/g, "");
        if (t.length < 10) {
          ev.preventDefault();
          window.showToast("error", "Remarks are mandatory (at least 10 characters) for Approve, Reject and Send Back.");
        }
      }
    });
  }

  // Role switcher and other selects that post their form on change (CSP forbids inline onchange).
  var autoSubmits = document.querySelectorAll("select[data-autosubmit]");
  for (var a = 0; a < autoSubmits.length; a++) {
    autoSubmits[a].addEventListener("change", function () {
      if (this.form) { this.form.submit(); }
    });
  }

  // <input type="checkbox" data-select-all="name"> ticks / unticks every checkbox with that name,
  // and follows them (ticked when all are ticked).
  var selectAlls = document.querySelectorAll("input[data-select-all]");
  for (var sa = 0; sa < selectAlls.length; sa++) {
    (function (all) {
      var name = all.getAttribute("data-select-all");
      var boxes = function () {
        return (all.form || document).querySelectorAll("input[type='checkbox'][name='" + name + "']");
      };
      all.addEventListener("change", function () {
        var b = boxes();
        for (var i = 0; i < b.length; i++) { b[i].checked = all.checked; }
      });
      var b0 = boxes();
      for (var j = 0; j < b0.length; j++) {
        b0[j].addEventListener("change", function () {
          var b = boxes(), every = b.length > 0;
          for (var k = 0; k < b.length; k++) { if (!b[k].checked) { every = false; } }
          all.checked = every;
        });
      }
    })(selectAlls[sa]);
  }

  // <form data-confirm="Question?"> asks before submitting (delete, publish, discard).
  var confirmForms = document.querySelectorAll("form[data-confirm]");
  for (var cf = 0; cf < confirmForms.length; cf++) {
    confirmForms[cf].addEventListener("submit", function (ev) {
      if (!window.confirm(this.getAttribute("data-confirm"))) { ev.preventDefault(); }
    });
  }

  // Workflow stage editor: only offer "who acts" options that fit the stage type, and show the role /
  // group / send-back fields only when they apply. The server checks the same rules.
  var stageForms = document.querySelectorAll("form.stage-form");
  for (var sf = 0; sf < stageForms.length; sf++) {
    (function (form) {
      var type = form.querySelector("[data-stage-type]");
      var who = form.querySelector("[data-stage-strategy]");
      if (!type || !who) { return; }
      var show = function (sel, on) {
        var el = form.querySelector(sel);
        if (el) { el.style.display = on ? "" : "none"; }
      };
      var refresh = function () {
        var t = type.value;
        var firstFit = null;
        for (var i = 0; i < who.options.length; i++) {
          var o = who.options[i];
          var fits = ("," + (o.getAttribute("data-types") || "") + ",").indexOf("," + t + ",") >= 0;
          o.hidden = !fits;
          o.disabled = !fits;
          if (fits && firstFit === null) { firstFit = o; }
        }
        if (who.selectedOptions.length === 0 || who.selectedOptions[0].disabled) {
          if (firstFit) { firstFit.selected = true; }
        }
        var s = who.value;
        show("[data-stage-role]", s === "NAMED_ROLE");
        show("[data-stage-group]", s === "SERVICE_DESK" || s === "ASSIGNMENT_GROUP" || s === "IMPLEMENTOR");
        show("[data-stage-sendback]", t === "APPROVAL" || t === "CONFIRMATION");
      };
      type.addEventListener("change", refresh);
      who.addEventListener("change", refresh);
      refresh();
    })(stageForms[sf]);
  }
})();
