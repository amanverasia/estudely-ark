package cc.esdy.ark;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Shares (seeds) the downloaded modules in short sessions, only on unmetered Wi-Fi while charging.
 * Android starts it when both hold and stops it (onStopJob) as soon as either goes away.
 * A download always wins: DownloadService asks a running session to stop and waits for it.
 */
public class SeedJob extends JobService {
    static final int ID = 7;
    static final long SESSION_MS = 9 * 60_000L; // jobs get about 10 minutes
    private static volatile boolean active, stopRequested;

    public static boolean isActive() { return active; }

    /** Asks a running session to end and waits up to 15 s for it. */
    public static void stopAndWait() {
        stopRequested = true;
        for (int i = 0; i < 150 && active; i++) try { Thread.sleep(100); } catch (InterruptedException e) { return; }
    }

    public static void schedule(Context c, boolean on) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (!on) { js.cancel(ID); return; }
        js.schedule(new JobInfo.Builder(ID, new ComponentName(c, SeedJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true)
                .setPeriodic(30 * 60_000L)
                .setPersisted(true)
                .build());
    }

    @Override public boolean onStartJob(JobParameters p) {
        LibraryEngine e = LibraryEngine.get(this);
        String skip = !e.sharingOn() ? "switched off" : e.pausedByUser() ? "downloads paused by you"
                : e.isMoving() ? "library move in progress" : !e.storageAvailable() ? "library storage unavailable"
                : DownloadService.isRunning() || e.isRunning() ? "busy"
                : e.verified().isEmpty() ? "nothing downloaded" : !conditionsHold(this) ? "not charging on Wi-Fi" : null;
        if (skip != null) { android.util.Log.i("EstudelyArk", "Sharing skipped: " + skip); return false; }
        active = true;
        stopRequested = false;
        new Thread(() -> {
            try {
                e.start();
                e.log("Sharing on Wi-Fi while charging");
                long end = System.currentTimeMillis() + SESSION_MS;
                while (!stopRequested && System.currentTimeMillis() < end) {
                    Thread.sleep(5000);
                    if (!conditionsHold(this)) { e.log("Charger or Wi-Fi gone; sharing stopped"); break; }
                    if (e.status() == null) break;
                }
                e.recordUpload();
            } catch (Exception ex) {
                e.log("Sharing stopped: " + ex);
            } finally {
                e.stop();
                active = false;
                jobFinished(p, false);
            }
        }, "seed").start();
        return true;
    }

    /** Our own check as well as the scheduler's: charging, and on an unmetered network. */
    static boolean conditionsHold(Context c) {
        android.os.BatteryManager bm = c.getSystemService(android.os.BatteryManager.class);
        android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return bm.isCharging() && nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    @Override public boolean onStopJob(JobParameters p) {
        stopRequested = true; // Wi-Fi or charger gone, or Android's time limit
        return false;         // the periodic schedule brings it back
    }
}
