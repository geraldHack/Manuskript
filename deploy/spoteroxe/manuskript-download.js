// Füllt Version, Größe, Download-Links und Release Notes aus downloads/*.txt
// Format:
//   Manuskript
//   2.1.73
//
//   macOS (Apple Silicon / arm64)
//   545 MB
//   Manuskript-2.1.73-macos-arm64.dmg
//   Release-Notes (beliebig viele Zeilen)
//
// Es gibt eine „latest“-Datei (Manuskript-macos-arm64.txt) und eine
// versionsgebundene Datei (Manuskript-2.1.73-macos-arm64.txt). Nach dem Laden
// der latest-Datei wird die versionsgebundene bevorzugt – manuelle Edits dort greifen.
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
    function nextNonEmpty() {
      while (i < lines.length && !String(lines[i]).trim()) {
        i++;
      }
      if (i >= lines.length) {
        return "";
      }
      return String(lines[i++]).trim().replace(/^\uFEFF/, "");
    }
    var label = nextNonEmpty();
    var version = nextNonEmpty();
    var platform = nextNonEmpty();
    var sizeLabel = nextNonEmpty();
    var filename = nextNonEmpty();
    if (!version || !/^\d+(?:\.\d+)*$/.test(version) || !filename) {
      return null;
    }
    var releaseNotes = lines.slice(i).join("\n").replace(/^\n+/, "").replace(/\s+$/, "");
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
      setNotes("[data-manuskript-" + prefix + "-notes]", "");
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
    var bust = path + (path.indexOf("?") >= 0 ? "&" : "?") + "_=" + Date.now();
    return fetch(bust, { cache: "no-store", headers: { "Cache-Control": "no-cache" } })
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

  function versionedNotesPath(pack) {
    if (!pack || !pack.filename) {
      return null;
    }
    var filename = String(pack.filename);
    var base = filename
      .replace(/\.pkg\.tar\.zst$/i, "-arch")
      .replace(/\.AppImage$/i, "-appimage")
      .replace(/\.deb$/i, "-deb")
      .replace(/\.(dmg|exe|zip)$/i, "");
    if (!base) {
      return null;
    }
    return "/downloads/" + base + ".txt";
  }

  /** latest-Alias laden, dann versionsgebundene .txt bevorzugen (manuelle Edits). */
  function loadPlatform(latestPath) {
    return loadNotes(latestPath).then(function (latest) {
      if (!latest) {
        return null;
      }
      var versionedPath = versionedNotesPath(latest);
      if (!versionedPath || versionedPath === latestPath) {
        return latest;
      }
      return loadNotes(versionedPath).then(function (versioned) {
        if (!versioned) {
          return latest;
        }
        // Metadaten aus latest behalten, falls versionsdatei unvollständig ist
        return {
          label: versioned.label || latest.label,
          version: versioned.version || latest.version,
          platform: versioned.platform || latest.platform,
          sizeLabel: versioned.sizeLabel || latest.sizeLabel,
          filename: versioned.filename || latest.filename,
          releaseNotes: versioned.releaseNotes || latest.releaseNotes,
          url: "/downloads/" + (versioned.filename || latest.filename)
        };
      });
    });
  }

  Promise.all([
    loadPlatform("/downloads/Manuskript-macos-arm64.txt"),
    loadPlatform("/downloads/Manuskript-windows-x64.txt"),
    loadPlatform("/downloads/Manuskript-linux-x64-deb.txt"),
    loadPlatform("/downloads/Manuskript-linux-x64-appimage.txt"),
    loadPlatform("/downloads/Manuskript-linux-x64-arch.txt")
  ]).then(function (packs) {
    var macos = packs[0];
    var windows = packs[1];
    fillPrefix("macos", macos);
    fillPrefix("windows", windows);
    fillPrefix("linux-deb", packs[2]);
    fillPrefix("linux-appimage", packs[3]);
    fillPrefix("linux-arch", packs[4]);
    fillLegacy(macos || windows);
  });
})();
