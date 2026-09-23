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
    sb.classList.toggle("collapsed");
    var icon = $("collapseIcon");
    var lbl = $("collapseLbl");
    var collapsed = sb.classList.contains("collapsed");
    if (icon) { icon.className = collapsed ? "fa-solid fa-angles-right" : "fa-solid fa-angles-left"; }
    if (lbl) { lbl.textContent = collapsed ? "Expand" : "Collapse"; }
  };

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

  var search = $("globalSearchInput");
  var box = $("searchResultsBox");
  if (search && box) {
    search.addEventListener("focus", function () {
      box.classList.add("show");
      box.innerHTML = "<div class=\"empty-state\" style=\"padding:18px\"><p>Ticket, employee and asset search is connected in Phase 6. No live results yet.</p></div>";
    });
    search.addEventListener("input", function () {
      box.classList.add("show");
    });
  }

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

  var actionForm = document.getElementById("ticketActionForm");
  if (actionForm) {
    actionForm.addEventListener("submit", function (ev) {
      var act = document.getElementById("actionCode");
      var rem = document.getElementById("actionRemarks");
      if (!act || !rem) { return; }
      var code = (act.value || "").toUpperCase();
      if (code === "APPROVE" || code === "REJECT" || code === "SEND_BACK") {
        var t = (rem.value || "").replace(/^\s+|\s+$/g, "");
        if (t.length < 10) {
          ev.preventDefault();
          window.showToast("error", "Remarks are mandatory (at least 10 characters) for Approve, Reject and Send Back.");
        }
      }
    });
  }
})();
