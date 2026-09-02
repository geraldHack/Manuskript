// Füllt Version, Größe, Download-Links und Release Notes aus downloads/*.txt
// Format:
//   Manuskript
//   2.1.73
//
//   macOS (Apple Silicon / arm64)
//   545 MB
//   Manuskript-2.1.73-macos-arm64.dmg
//   Release-Notes (beliebig viele Zeilen)
(function () {
  function text(selector, value) {
    document.querySelectorAll(selector).forEach(function (el) {
      el.textContent = value == null ? "" : value;
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

  function setNotes(selector, notes) {
    document.querySelectorAll(selector).forEach(function (el) {
      var value = notes == null ? "" : String(notes).trim();
      el.textContent = value;
      el.hidden = !value;
    });
  }

  function parseNotes(raw) {
    if (!raw) {
      return null;
    }
    var lines = String(raw).replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
    var i = 0;
    while (i < lines.length && !String(lines[i]).trim()) {
      i++;
    }
    if (i >= lines.length) {
      return null;
    }
    var label = String(lines[i++]).trim().replace(/^\uFEFF/, "");
    while (i < lines.length && !String(lines[i]).trim()) {
      i++;
    }
    var version = "";
    if (i < lines.length && /^\d+(?:\.\d+)*$/.test(String(lines[i]).trim())) {
      version = String(lines[i++]).trim();
    }
    while (i < lines.length && !String(lines[i]).trim()) {
      i++;
    }
    var body = [];
    for (; i < lines.length; i++) {
      var line = String(lines[i]).trim();
      if (line) {
        body.push(line);
      }
    }
    var platform = body[0] || "";
    var sizeLabel = body[1] || "";
    var filename = body[2] || "";
    var releaseNotes = body.slice(3).join("\n");
    if (!version || !filename) {
      return null;
    }
    return {
      label: label,
      version: version,
      platform: platform,
      sizeLabel: sizeLabel,
      filename: filename,
      releaseNotes: releaseNotes,
      url: "/downloads/" + filename
    };
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
    setNotes("[data-manuskript-" + prefix + "-notes]", pack.releaseNotes || "");
    href("a[data-manuskript-" + prefix + "-href]", pack.url);
  }

  function fillLegacy(pack) {
    if (!pack) {
      return;
    }
    text("[data-manuskript-version]", pack.version);
    text("[data-manuskript-size]", pack.sizeLabel || "");
    text("[data-manuskript-platform]", pack.platform || "");
    setNotes("[data-manuskript-notes]", pack.releaseNotes || "");
    href("a[data-manuskript-href]", pack.url);
    document.querySelectorAll("[data-manuskript-label]").forEach(function (el) {
      var size = pack.sizeLabel ? " (DMG, ca. " + pack.sizeLabel + ")" : "";
      el.textContent = "Manuskript " + pack.version + size;
    });
  }

  function loadNotes(path) {
    return fetch(path, { cache: "no-store" })
      .then(function (response) {
        if (!response.ok) {
          return null;
        }
        return response.text();
      })
      .then(function (raw) {
        return parseNotes(raw);
      })
      .catch(function () {
        return null;
      });
  }

  Promise.all([
    loadNotes("/downloads/Manuskript-macos-arm64.txt"),
    loadNotes("/downloads/Manuskript-windows-x64.txt")
  ]).then(function (packs) {
    var macos = packs[0];
    var windows = packs[1];
    fillPrefix("macos", macos);
    fillPrefix("windows", windows);
    fillLegacy(macos || windows);
  });
})();
