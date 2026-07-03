package org.woheller69.gptassist;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ExpiryScheduler {

    static final String CHANNEL_ID = "gptassist_expiry";
    static final String ACTION_NOTIFY = "org.woheller69.gptassist.NOTIFY_EXPIRY";
    static final String EXTRA_KIND = "kind"; // "warn" | "expired"

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    public static void scheduleAll(Context ctx, long expiresAt) {
        ensureChannel(ctx);
        // Aviso día 28 (24h antes de expirar) y día 29 (al expirar).
        schedule(ctx, expiresAt - DAY_MS, "warn", 1001);
        schedule(ctx, expiresAt,          "expired", 1002);
    }

    private static void schedule(Context ctx, long at, String kind, int reqCode) {
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, Receiver.class);
        i.setAction(ACTION_NOTIFY);
        i.putExtra(EXTRA_KIND, kind);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode, i, flags);

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException ignored) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Vencimiento", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos de vencimiento de la suscripción");
            nm.createNotificationChannel(ch);
        }
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            ensureChannel(ctx);
            String kind = intent.getStringExtra(EXTRA_KIND);
            String title, text;
            if ("expired".equals(kind)) {
                title = "Tu suscripción venció";
                text  = "Abre ChatGuard e ingresa la contraseña para renovar 29 días.";
            } else {
                title = "Tu suscripción vence mañana";
                text  = "Solicita tu nueva contraseña por WhatsApp para no perder el acceso.";
            }

            Intent open = new Intent(ctx, GateActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, flags);

            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify("expired".equals(kind) ? 2002 : 2001, b.build());
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            long expires = ctx.getSharedPreferences("gptassist_gate", Context.MODE_PRIVATE)
                .getLong("expires_at", 0L);
            if (expires > System.currentTimeMillis()) scheduleAll(ctx, expires);
        }
    }
}
