<?php
declare(strict_types=1);

const CHAT_MAX_NAME = 32;
const CHAT_MAX_TEXT = 4000;
const CHAT_RATE_SECONDS = 1;
/** Als online, wenn last_seen innerhalb dieser Sekunden (Poll ~8s). */
const CHAT_ONLINE_SECONDS = 45;

function data_dir(): string
{
    $dir = __DIR__ . '/data';
    if (!is_dir($dir)) {
        mkdir($dir, 0750, true);
    }
    return $dir;
}

function chat_db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    $path = data_dir() . '/discharge.sqlite';
    $pdo = new PDO('sqlite:' . $path, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $pdo->exec('PRAGMA journal_mode=WAL');
    $pdo->exec('PRAGMA foreign_keys=ON');
    $pdo->exec(<<<'SQL'
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  token TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS friendships (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_a TEXT NOT NULL,
  user_b TEXT NOT NULL,
  requester TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(user_a, user_b)
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  channel_id TEXT NOT NULL,
  from_id TEXT NOT NULL,
  to_id TEXT NOT NULL,
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_messages_channel_created ON messages(channel_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_to_created ON messages(to_id, created_at);
SQL);
    ensure_schema($pdo);
    return $pdo;
}

/** Spalten-Migrationen für bestehende DBs. */
function ensure_schema(PDO $pdo): void
{
    static $done = false;
    if ($done) {
        return;
    }
    $done = true;
    try {
        $pdo->exec('ALTER TABLE users ADD COLUMN last_seen INTEGER NOT NULL DEFAULT 0');
    } catch (Throwable $ignored) {
        // Spalte existiert bereits
    }
}

function read_json_body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') {
        return [];
    }
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

function require_method(string $actual, string $expected): void
{
    if (strcasecmp($actual, $expected) !== 0) {
        json_error(405, 'method_not_allowed', "Erwartet $expected");
    }
}

/** @return never */
function json_out(array $payload, int $code = 200)
{
    http_response_code($code);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** @return never */
function json_error(int $code, string $error, string $message)
{
    json_out(['ok' => false, 'error' => $error, 'message' => $message], $code);
}

function bearer_token(): ?string
{
    $candidates = [
        $_SERVER['HTTP_AUTHORIZATION'] ?? '',
        $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '',
        $_SERVER['HTTP_X_DISCHARGE_TOKEN'] ?? '',
    ];
    foreach ($candidates as $hdr) {
        $hdr = trim((string)$hdr);
        if ($hdr === '') {
            continue;
        }
        if (preg_match('/^Bearer\s+(\S+)$/i', $hdr, $m)) {
            return $m[1];
        }
        // Roher Token im Custom-Header
        if (!str_contains($hdr, ' ')) {
            return $hdr;
        }
    }
    $q = trim((string)($_GET['token'] ?? ''));
    return $q !== '' ? $q : null;
}

/** Client will eigenen Online-Status teilen (Header X-Discharge-Presence: 1|0). Default: ja. */
function want_share_presence(): bool
{
    $raw = $_SERVER['HTTP_X_DISCHARGE_PRESENCE'] ?? '';
    if ($raw === '') {
        $raw = (string)($_GET['presence'] ?? '');
    }
    $raw = strtolower(trim((string)$raw));
    if ($raw === '') {
        return true;
    }
    return !in_array($raw, ['0', 'false', 'no', 'off'], true);
}

function require_auth(PDO $db): array
{
    $token = bearer_token();
    if ($token === null || $token === '') {
        json_error(401, 'unauthorized', 'Token fehlt');
    }
    $stmt = $db->prepare('SELECT id, display_name, token, created_at, COALESCE(last_seen, 0) AS last_seen FROM users WHERE token = ?');
    $stmt->execute([$token]);
    $user = $stmt->fetch();
    if (!$user) {
        json_error(401, 'unauthorized', 'Ungültiges Token');
    }
    if (want_share_presence()) {
        $now = time();
        $touch = $db->prepare('UPDATE users SET last_seen = ? WHERE id = ?');
        $touch->execute([$now, $user['id']]);
        $user['last_seen'] = $now;
    } else {
        // Sofort offline für Freunde: last_seen zurücksetzen, nicht bei Poll „anstoßen“
        $clear = $db->prepare('UPDATE users SET last_seen = 0 WHERE id = ?');
        $clear->execute([$user['id']]);
        $user['last_seen'] = 0;
    }
    return $user;
}

function sanitize_display_name(string $name): string
{
    $name = trim($name);
    // Nutzer gibt nur den Namen ein — die .nnnn-ID vergibt der Server.
    // Deshalb: nur Buchstaben und Ziffern, kein Punkt/Leerzeichen/Sonderzeichen.
    if ($name === '' || preg_match('/\./u', $name)) {
        json_error(400, 'invalid_name', 'Bitte nur den Namen eingeben (ohne .Nummer). Die ID vergibt Discharge automatisch.');
    }
    if (!preg_match('/^[\p{L}\p{N}]+$/u', $name)) {
        json_error(400, 'invalid_name', 'Anzeigename: nur Buchstaben und Zahlen, ohne Leerzeichen oder Sonderzeichen');
    }
    if (mb_strlen($name) < 2) {
        json_error(400, 'invalid_name', 'Anzeigename zu kurz (min. 2 Zeichen)');
    }
    if (mb_strlen($name) > CHAT_MAX_NAME) {
        $name = mb_substr($name, 0, CHAT_MAX_NAME);
    }
    return $name;
}

function id_prefix_from_name(string $displayName): string
{
    $parts = preg_split('/\s+/u', $displayName) ?: [];
    $first = $parts[0] ?? 'User';
    $first = preg_replace('/[^\p{L}\p{N}]/u', '', $first) ?? 'User';
    if ($first === '') {
        $first = 'User';
    }
    // ASCII-freundlich halten, Umlaute grob ersetzen
    $map = ['Ä' => 'Ae', 'Ö' => 'Oe', 'Ü' => 'Ue', 'ä' => 'ae', 'ö' => 'oe', 'ü' => 'ue', 'ß' => 'ss'];
    $first = strtr($first, $map);
    $first = preg_replace('/[^A-Za-z0-9]/', '', $first) ?? 'User';
    if ($first === '') {
        $first = 'User';
    }
    return mb_substr($first, 0, 16);
}

function allocate_user_id(PDO $db, string $displayName): string
{
    $prefix = id_prefix_from_name($displayName);
    for ($i = 0; $i < 40; $i++) {
        $num = random_int(1000, 9999);
        $id = $prefix . '.' . $num;
        $check = $db->prepare('SELECT 1 FROM users WHERE id = ?');
        $check->execute([$id]);
        if (!$check->fetch()) {
            return $id;
        }
    }
    json_error(500, 'id_exhausted', 'Konnte keine freie ID erzeugen');
}

function handle_register(PDO $db, array $body)
{
    $display = sanitize_display_name((string)($body['displayName'] ?? ''));
    $id = allocate_user_id($db, $display);
    $token = bin2hex(random_bytes(24));
    $now = time();
    $stmt = $db->prepare('INSERT INTO users (id, display_name, token, created_at) VALUES (?, ?, ?, ?)');
    $stmt->execute([$id, $display, $token, $now]);
    json_out(['ok' => true, 'id' => $id, 'displayName' => $display, 'token' => $token]);
}

function ordered_pair(string $a, string $b): array
{
    return strcmp($a, $b) <= 0 ? [$a, $b] : [$b, $a];
}

function channel_id(string $a, string $b): string
{
    [$x, $y] = ordered_pair($a, $b);
    return $x . '_' . $y;
}

function handle_friend_request(PDO $db, array $user, array $body)
{
    $to = trim((string)($body['to'] ?? ''));
    if ($to === '' || $to === $user['id']) {
        json_error(400, 'invalid_target', 'Ziel-ID ungültig');
    }
    $exists = $db->prepare('SELECT id FROM users WHERE id = ?');
    $exists->execute([$to]);
    if (!$exists->fetch()) {
        json_error(404, 'user_not_found', 'Unbekannte ID');
    }
    [$a, $b] = ordered_pair($user['id'], $to);
    $now = time();
    $find = $db->prepare('SELECT id, status, requester FROM friendships WHERE user_a = ? AND user_b = ?');
    $find->execute([$a, $b]);
    $row = $find->fetch();
    if ($row) {
        if ($row['status'] === 'accepted') {
            json_error(409, 'already_friends', 'Bereits befreundet');
        }
        if ($row['status'] === 'pending') {
            json_error(409, 'already_pending', 'Anfrage bereits offen');
        }
        $upd = $db->prepare('UPDATE friendships SET status = ?, requester = ?, updated_at = ? WHERE id = ?');
        $upd->execute(['pending', $user['id'], $now, $row['id']]);
    } else {
        $ins = $db->prepare('INSERT INTO friendships (user_a, user_b, requester, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
        $ins->execute([$a, $b, $user['id'], 'pending', $now, $now]);
    }
    json_out(['ok' => true, 'status' => 'pending', 'to' => $to]);
}

function handle_friend_respond(PDO $db, array $user, array $body)
{
    $from = trim((string)($body['from'] ?? ''));
    $action = strtolower(trim((string)($body['action'] ?? '')));
    if ($from === '' || !in_array($action, ['accept', 'reject'], true)) {
        json_error(400, 'invalid_request', 'from und action (accept|reject) nötig');
    }
    [$a, $b] = ordered_pair($user['id'], $from);
    $find = $db->prepare('SELECT id, status, requester FROM friendships WHERE user_a = ? AND user_b = ?');
    $find->execute([$a, $b]);
    $row = $find->fetch();
    if (!$row || $row['status'] !== 'pending') {
        json_error(404, 'no_pending', 'Keine offene Anfrage');
    }
    if ($row['requester'] === $user['id']) {
        json_error(403, 'not_recipient', 'Nur der Empfänger darf antworten');
    }
    $now = time();
    $status = $action === 'accept' ? 'accepted' : 'rejected';
    $upd = $db->prepare('UPDATE friendships SET status = ?, updated_at = ? WHERE id = ?');
    $upd->execute([$status, $now, $row['id']]);
    json_out(['ok' => true, 'status' => $status, 'with' => $from]);
}

function handle_friends_list(PDO $db, array $user)
{
    $uid = $user['id'];
    $stmt = $db->prepare(<<<'SQL'
SELECT f.status, f.requester, f.user_a, f.user_b,
       CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END AS peer_id,
       u.display_name AS peer_name,
       COALESCE(u.last_seen, 0) AS last_seen
FROM friendships f
JOIN users u ON u.id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END
WHERE (f.user_a = ? OR f.user_b = ?)
  AND f.status IN ('pending', 'accepted')
ORDER BY f.updated_at DESC
SQL);
    $stmt->execute([$uid, $uid, $uid, $uid]);
    $friends = [];
    $incoming = [];
    $outgoing = [];
    $now = time();
    while ($row = $stmt->fetch()) {
        $lastSeen = (int)$row['last_seen'];
        $item = [
            'id' => $row['peer_id'],
            'displayName' => $row['peer_name'],
            'status' => $row['status'],
            'lastSeen' => $lastSeen,
            'online' => $lastSeen > 0 && ($now - $lastSeen) <= CHAT_ONLINE_SECONDS,
        ];
        if ($row['status'] === 'accepted') {
            $friends[] = $item;
        } elseif ($row['requester'] === $uid) {
            $outgoing[] = $item;
        } else {
            $incoming[] = $item;
        }
    }
    json_out(['ok' => true, 'friends' => $friends, 'incoming' => $incoming, 'outgoing' => $outgoing]);
}

function assert_friends(PDO $db, string $a, string $b): void
{
    [$x, $y] = ordered_pair($a, $b);
    $stmt = $db->prepare('SELECT status FROM friendships WHERE user_a = ? AND user_b = ?');
    $stmt->execute([$x, $y]);
    $row = $stmt->fetch();
    if (!$row || $row['status'] !== 'accepted') {
        json_error(403, 'not_friends', 'Keine akzeptierte Freundschaft');
    }
}

function handle_send(PDO $db, array $user, array $body)
{
    $to = trim((string)($body['to'] ?? ''));
    $text = trim((string)($body['text'] ?? ''));
    if ($to === '' || $text === '') {
        json_error(400, 'invalid_message', 'to und text nötig');
    }
    if (mb_strlen($text) > CHAT_MAX_TEXT) {
        json_error(400, 'too_long', 'Nachricht zu lang');
    }
    assert_friends($db, $user['id'], $to);
    $now = time();
    // einfaches Rate-Limit
    $rl = $db->prepare('SELECT created_at FROM messages WHERE from_id = ? ORDER BY id DESC LIMIT 1');
    $rl->execute([$user['id']]);
    $last = $rl->fetch();
    if ($last && ($now - (int)$last['created_at']) < CHAT_RATE_SECONDS) {
        json_error(429, 'rate_limited', 'Zu schnell hintereinander');
    }
    $channel = channel_id($user['id'], $to);
    $ins = $db->prepare('INSERT INTO messages (channel_id, from_id, to_id, body, created_at) VALUES (?, ?, ?, ?, ?)');
    $ins->execute([$channel, $user['id'], $to, $text, $now]);
    $id = (int)$db->lastInsertId();
    json_out([
        'ok' => true,
        'message' => [
            'id' => $id,
            'channelId' => $channel,
            'from' => $user['id'],
            'to' => $to,
            'text' => $text,
            'createdAt' => $now,
        ],
    ]);
}

function handle_poll(PDO $db, array $user)
{
    $since = isset($_GET['since']) ? (int)$_GET['since'] : 0;
    if ($since < 0) {
        $since = 0;
    }
    $uid = $user['id'];
    $stmt = $db->prepare(<<<'SQL'
SELECT id, channel_id, from_id, to_id, body, created_at
FROM messages
WHERE (to_id = ? OR from_id = ?) AND created_at > ?
ORDER BY created_at ASC, id ASC
LIMIT 200
SQL);
    $stmt->execute([$uid, $uid, $since]);
    $messages = [];
    $maxTs = $since;
    while ($row = $stmt->fetch()) {
        $ts = (int)$row['created_at'];
        $maxTs = max($maxTs, $ts);
        $messages[] = [
            'id' => (int)$row['id'],
            'channelId' => $row['channel_id'],
            'from' => $row['from_id'],
            'to' => $row['to_id'],
            'text' => $row['body'],
            'createdAt' => $ts,
        ];
    }
    $unreadStmt = $db->prepare(<<<'SQL'
SELECT from_id AS peer, COUNT(*) AS cnt
FROM messages
WHERE to_id = ? AND created_at > ?
GROUP BY from_id
SQL);
    $unreadStmt->execute([$uid, $since]);
    $unread = [];
    while ($row = $unreadStmt->fetch()) {
        $unread[$row['peer']] = (int)$row['cnt'];
    }
    // Incoming friend requests count as attention too
    $pending = $db->prepare(<<<'SQL'
SELECT COUNT(*) AS c FROM friendships
WHERE status = 'pending' AND requester != ? AND (user_a = ? OR user_b = ?)
SQL);
    $pending->execute([$uid, $uid, $uid]);
    $pendingCount = (int)($pending->fetch()['c'] ?? 0);

    // Damit beide Seiten nach Accept/Reject die Freundesliste aktualisieren
    $revStmt = $db->prepare(<<<'SQL'
SELECT COALESCE(MAX(updated_at), 0) AS rev,
       COALESCE(SUM(CASE WHEN status = 'accepted' THEN 1 ELSE 0 END), 0) AS accepted
FROM friendships
WHERE user_a = ? OR user_b = ?
SQL);
    $revStmt->execute([$uid, $uid]);
    $revRow = $revStmt->fetch() ?: ['rev' => 0, 'accepted' => 0];

    json_out([
        'ok' => true,
        'since' => $since,
        'cursor' => $maxTs,
        'messages' => $messages,
        'unread' => $unread,
        'pendingFriendRequests' => $pendingCount,
        'friendshipsRevision' => (int)$revRow['rev'],
        'friendsAccepted' => (int)$revRow['accepted'],
        'hasAttention' => !empty($messages) || $pendingCount > 0 || array_sum($unread) > 0,
    ]);
}
