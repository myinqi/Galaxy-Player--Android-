"use strict";
(function () {
  function openLink(href, label) {
    const link = document.createElement("a");
    link.href = href;
    link.textContent = label;
    return link;
  }

  function render(payload) {
    const root = document.getElementById("galaxy-bookmarks");
    if (!root) return;
    const items = (payload && payload.bookmarks) || [];
    const clear = document.getElementById("galaxy-bookmarks-clear");
    if (clear) clear.hidden = !items.length;
    root.replaceChildren();
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "galaxy-muted";
      empty.textContent = "No bookmarked songs yet.";
      root.append(empty);
      return;
    }
    const list = document.createElement("div");
    list.className = "galaxy-bookmark-list";
    items.forEach(function (item) {
      const card = document.createElement("article");
      card.className = "galaxy-bookmark-item";
      const title = document.createElement("strong");
      title.textContent = item.title || "Saved song";
      const meta = document.createElement("small");
      meta.textContent = [item.artist, item.source].filter(Boolean).join(" · ");
      card.append(title, meta);
      const links = document.createElement("div");
      links.className = "galaxy-bookmark-links";
      if (item.suno_url) links.append(openLink(item.suno_url, "Open on Suno"));
      if (item.wlm_url) links.append(openLink(item.wlm_url, "Open on WLM"));
      if (links.childNodes.length) card.append(links);
      list.append(card);
    });
    root.append(list);
  }

  function refresh(payload) {
    if (payload) {
      render(payload);
      return;
    }
    try {
      render(JSON.parse(GalaxyNative.request("GET", "/galaxy/api/bookmarks", "") || "{}"));
    } catch (error) {
      render({ bookmarks: [] });
    }
  }

  window.galaxyRefreshBookmarks = refresh;
  const clear = document.getElementById("galaxy-bookmarks-clear");
  if (clear) {
    clear.addEventListener("click", function () {
      if (!window.confirm("Clear the bookmarks on this device?")) return;
      try {
        const result = JSON.parse(GalaxyNative.request("POST", "/galaxy/api/bookmarks/clear", "") || "{}");
        if (typeof bookmarkKeys !== "undefined") bookmarkKeys = new Set(result.keys || []);
        if (typeof syncBookmarkButton === "function") syncBookmarkButton();
        refresh(result);
      } catch (error) {
        refresh({ bookmarks: [] });
      }
    });
  }
  document.querySelectorAll(".galaxy-bookmark").forEach(function (button) {
    button.addEventListener("click", function () {
      setTimeout(function () { refresh(); }, 250);
    });
  });
  refresh();
})();
