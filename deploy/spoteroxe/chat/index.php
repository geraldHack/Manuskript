<?php
/**
 * Discharge Chat API — Manuskript Plugin Backend
 * Ops via ?op=register|me|friends|friends_request|friends_respond|messages_send|messages_poll
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type, X-Discharge-Token, X-Discharge-Presence');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

require __DIR__ . '/lib.php';

$method = $_SERVER['REQUEST_METHOD'];
$op = trim((string)($_GET['op'] ?? ''));
if ($op === '') {
    // Fallback: /chat/register oder PATH_INFO
    $path = trim(parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/', '/');
    if (str_starts_with($path, 'chat/')) {
        $path = substr($path, 5);
    } elseif ($path === 'chat') {
        $path = '';
    }
    $path = trim($path, '/');
    if ($path === '' || $path === 'index.php') {
        json_out(['ok' => true, 'service' => 'discharge-chat', 'version' => 1]);
    }
    $op = str_replace('/', '_', $path);
}

try {
    $db = chat_db();
    $body = read_json_body();

    switch ($op) {
        case 'register':
            require_method($method, 'POST');
            handle_register($db, $body);
            break;
        case 'me':
            require_method($method, 'GET');
            $user = require_auth($db);
            json_out(['ok' => true, 'id' => $user['id'], 'displayName' => $user['display_name']]);
            break;
        case 'friends':
            require_method($method, 'GET');
            handle_friends_list($db, require_auth($db));
            break;
        case 'friends_request':
        case 'friends/request':
            require_method($method, 'POST');
            handle_friend_request($db, require_auth($db), $body);
            break;
        case 'friends_respond':
        case 'friends/respond':
            require_method($method, 'POST');
            handle_friend_respond($db, require_auth($db), $body);
            break;
        case 'messages_send':
        case 'messages/send':
            require_method($method, 'POST');
            handle_send($db, require_auth($db), $body);
            break;
        case 'messages_poll':
        case 'messages/poll':
            require_method($method, 'GET');
            handle_poll($db, require_auth($db));
            break;
        default:
            json_error(404, 'unknown_route', 'Unbekannte Route: ' . $op);
    }
} catch (Throwable $e) {
    json_error(500, 'server_error', $e->getMessage());
}
