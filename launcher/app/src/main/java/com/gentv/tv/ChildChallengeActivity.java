package com.gentv.tv;

import android.app.Activity;
import android.os.Bundle;

/** Hosts the challenge over the current app. Home also restores the same saved round on launch. */
public final class ChildChallengeActivity extends Activity {
    private ChildLock lock;
    private String resumePlayer = "";

    void resumePlayerAfterFinish(String pkg) { resumePlayer = pkg; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        lock = new ChildLock(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!lock.showIfLocked()) finish();
    }

    @Override protected void onPause() {
        lock.dismiss();
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (!resumePlayer.isEmpty()) ChildWatchService.challengeFinished(resumePlayer);
    }

    @Override public void onBackPressed() {
        if (!ChildLock.locked(this)) super.onBackPressed();
    }
}
