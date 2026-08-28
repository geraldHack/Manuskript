// Füllt Version, Größe und Download-Links aus downloads/manuskript.json
// (wird beim Deploy von create-installer.sh / create-installer.bat hochgeladen).
(function () {
  function text(selector, value) {
    if (value == null || value === "") {
      return;
    }
    document.querySelectorAll(selector).forEach(function (el) {
      el.textContent = value;
    });
  }

  function href(selector, url) {
    if (!url) {
      return;
    }
    document.querySelectorAll(selector).forEach(function (el) {
      el.setAttribute("href", url);
    });
  }

  function fillPrefix(prefix, pack) {
    var row = document.querySelector("[data-manuskript-" + prefix + "-row]");
    if (row) {
      row.hidden = !pack;
    }
    if (!pack) {
      return;
    }
    text("[data-manuskript-" + prefix + "-version]", pack.version);
    text("[data-manuskript-" + prefix + "-size]", pack.sizeLabel || "");
    text("[data-manuskript-" + prefix + "-platform]", pack.platform || "");
    href("a[data-manuskript-" + prefix + "-href]", pack.url);
    document.querySelectorAll("[data-manuskript-" + prefix + "-label]").forEach(function (el) {
      var kind = prefix === "windows" ? "Windows" : "DMG";
      var size = pack.sizeLabel ? " (" + kind + ", ca. " + pack.sizeLabel + ")" : "";
      var ver = pack.version ? " " + pack.version : "";
      el.textContent = "Manuskript" + ver + size;
    });
  }

  function applyLegacy(pack) {
    if (!pack) {
      return;
    }
    text("[data-manuskript-version]", pack.version);
    text("[data-manuskript-size]", pack.sizeLabel || "");
    text("[data-manuskript-platform]", pack.platform || "");
    href("a[data-manuskript-href]", pack.url);
    document.querySelectorAll("[data-manuskript-label]").forEach(function (el) {
      var size = pack.sizeLabel ? " (ca. " + pack.sizeLabel + ")" : "";
      el.textContent = "Manuskript " + (pack.version || "") + size;
    });
  }

  function apply(meta) {
    if (!meta) {
      return;
    }
    var macos = meta.macos;
    if (!macos && meta.url && String(meta.platform || "").toLowerCase().indexOf("windows") < 0) {
      macos = meta;
    }
    var windows = meta.windows;
    if (!windows && meta.url && String(meta.platform || "").toLowerCase().indexOf("windows") >= 0) {
      windows = meta;
    }
    fillPrefix("macos", macos);
    fillPrefix("windows", windows);
    applyLegacy(macos || windows || meta);
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
