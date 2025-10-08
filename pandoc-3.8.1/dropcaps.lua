-- dropcaps.lua — Automatische Initiale inkl. Anführungszeichen
-- Für XeLaTeX + lettrine + Pandoc

local text = pandoc.text

-- öffnende deutschen Anführungszeichen (du kannst weitere hinzufügen)
local open_quotes = { "»", "„", "“", "\"", "‚", "‘" }

local function starts_with_quote(s)
  for _, q in ipairs(open_quotes) do
    if text.sub(s, 1, text.len(q)) == q then
      return q
    end
  end
  return nil
end

-- erzeugt \lettrine mit optionalem Anführungszeichen
local function add_dropcap_to_para(para)
  local inlines = para.content
  local idx = nil

  -- finde erstes Textsegment
  for k, inline in ipairs(inlines) do
    if inline.t == 'Str' and inline.text ~= '' then
      idx = k
      break
    end
  end
  if not idx then return para end

  local s = inlines[idx].text
  local quote = starts_with_quote(s)
  local first, rest

  if quote then
    -- Initiale umfasst Anführungszeichen + nächsten Buchstaben
    local after = text.sub(s, text.len(quote) + 1, text.len(quote) + 1)
    first = quote .. after
    rest  = text.sub(s, text.len(quote) + 2) or ''
  else
    first = text.sub(s, 1, 1)
    rest  = text.sub(s, 2) or ''
  end

  local raw = pandoc.RawInline('latex',
    '\\lettrine[' ..
      'lines=3,' ..
      'loversize=0.12,' ..
      'findent=0.2em,' ..
      'nindent=0.3em,' ..
      'lraise=0.2' ..
    ']{' .. first .. '}{' .. rest .. '}'
  )

  inlines[idx] = raw
  return pandoc.Para(inlines)
end

function Pandoc(doc)
  local blocks = doc.blocks
  local i = 1
  while i <= #blocks do
    local b = blocks[i]
    if b.t == 'Header' and b.level == 1 then
      local j = i + 1
      while j <= #blocks and blocks[j].t ~= 'Para' do
        j = j + 1
      end
      if j <= #blocks and blocks[j].t == 'Para' then
        blocks[j] = add_dropcap_to_para(blocks[j])
        i = j
      end
    end
    i = i + 1
  end
  return pandoc.Pandoc(blocks, doc.meta)
end
