package cc.esdy.ark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import java.io.File;

/** Keeps a long SD-card copy alive while the user leaves the settings screen. */
public class LibraryMoveService extends Service {
    private static final String TARGET = "target";
    private static volatile boolean running, cancelRequested;
    private static volatile long done, total;
    private static volatile String phase = "", result = "";
    private int lastPercent = -1;
    private long lastNotification;

    public static boolean isRunning() { return running; }
    public static long done() { return done; }
    public static long total() { return total; }
    public static String phase() { return phase; }
    public static String result() { return result; }
    public static void requestCancel() { cancelRequested = true; }

    public static void start(Context c, File target) {
        if (running) return;
        result = "";
        c.startForegroundService(new Intent(c, LibraryMoveService.class).putExtra(TARGET, target.getAbsolutePath()));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification note(String text, int percent) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n = new Notification.Builder(this, DownloadService.CHANNEL)
                .setContentTitle("Moving your library")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (percent >= 0) n.setProgress(100, percent, false);
        else n.setProgress(0, 0, true);
        return n.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running || intent == null || intent.getStringExtra(TARGET) == null) return START_NOT_STICKY;
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(DownloadService.CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(3, note("Preparing", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(3, note("Preparing", -1));
        running = true;
        cancelRequested = false;
        done = total = 0;
        phase = "Preparing";
        lastPercent = -1;
        File target = new File(intent.getStringExtra(TARGET));
        new Thread(() -> move(target), "library-move").start();
        return START_NOT_STICKY;
    }

    private void move(File target) {
        LibraryEngine engine = LibraryEngine.get(this);
        try {
            engine.moveLibrary(target, new LibraryMover.Progress() {
                @Override public void update(long bytes, long bytesTotal) {
                    done = bytes;
                    total = bytesTotal;
                    int percent = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                    long now = System.currentTimeMillis();
                    if (percent != lastPercent && (percent % 5 == 0 || now - lastNotification > 3000)) {
                        lastPercent = percent;
                        lastNotification = now;
                        getSystemService(NotificationManager.class).notify(3, note(phase + " · " + percent + "%", percent));
                    }
                }
                @Override public boolean cancelled() { return cancelRequested || Thread.currentThread().isInterrupted(); }
                @Override public void phase(String next) {
                    phase = next;
                    getSystemService(NotificationManager.class).notify(3, note(next, -1));
                }
            });
            result = "Library moved to " + engine.storageName() + ". Everything was checked before the old copy was removed.";
        } catch (InterruptedException e) {
            result = cancelRequested ? "Move cancelled. Your library stayed where it was." : "Move interrupted. Your library stayed where it was.";
        } catch (Exception e) {
            result = "Could not move the library: " + (e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Override public void onTimeout(int startId, int fgsType) {
        cancelRequested = true;
        result = "Android's background time limit stopped the move. Open Estudely Ark to finish or undo it safely.";
    }

    @Override public void onDestroy() {
        if (running) cancelRequested = true;
        super.onDestroy();
    }
}
