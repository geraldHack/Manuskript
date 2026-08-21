// Füllt Version, Größe und Download-Link aus downloads/manuskript.json
// (wird beim Deploy von create-installer.sh hochgeladen).
(function () {
  function apply(meta) {
    if (!meta || !meta.version) {
      return;
    }
    document.querySelectorAll("[data-manuskript-version]").forEach(function (el) {
      el.textContent = meta.version;
    });
    document.querySelectorAll("[data-manuskript-size]").forEach(function (el) {
      el.textContent = meta.sizeLabel || "";
    });
    document.querySelectorAll("[data-manuskript-platform]").forEach(function (el) {
      if (meta.platform) {
        el.textContent = meta.platform;
      }
    });
    document.querySelectorAll("a[data-manuskript-href]").forEach(function (el) {
      if (meta.url) {
        el.setAttribute("href", meta.url);
      }
    });
    document.querySelectorAll("[data-manuskript-label]").forEach(function (el) {
      var size = meta.sizeLabel ? " (DMG, ca. " + meta.sizeLabel + ")" : "";
      el.textContent = "Manuskript " + meta.version + size;
    });
  }

  fetch("downloads/manuskript.json", { cache: "no-store" })
    .then(function (response) {
      if (!response.ok) {
        throw new Error("manuskript.json: " + response.status);
      }
      return response.json();
    })
    .then(apply)
    .catch(function () {
      // Fallback: feste Links in der HTML bleiben stehen.
    });
})();
