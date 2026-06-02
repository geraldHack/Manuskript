package com.manuskript;

/**
 * Einmalige Wiederholung bei vorübergehenden Gateway-Fehlern (502/503/504).
 * Typisch beim ersten Agenten-Request nach Cold Start; manueller Zweitversuch hilft dann auch.
 */
public final class GatewayHttpRetry {

    private static final int RETRY_DELAY_MS = 1500;

    private GatewayHttpRetry() {
    }

    public static boolean isRetryableStatus(int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public static boolean isRetryableThrowable(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage();
        if (msg != null) {
            if (msg.contains("502") || msg.contains("503") || msg.contains("504")
                    || msg.contains("Bad Gateway") || msg.contains("Timeout") || msg.contains("timeout")) {
                return true;
            }
        }
        return isRetryableThrowable(t.getCause());
    }

    public static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
