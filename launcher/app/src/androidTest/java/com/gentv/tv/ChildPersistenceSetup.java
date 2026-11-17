package com.gentv.tv;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.util.Collections;
/** Only seeds isolated emulator data. No instrumentation remains running during persistence tests. */
public final class ChildPersistenceSetup extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            if(!getTargetContext().getPackageName().endsWith(".localadbqa")
                    || !(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish")))throw new AssertionError("Isolated emulator required");
            if(!ChildLock.state(getTargetContext()).edit().clear().putBoolean(ChildSettingsActivity.ENABLED,true)
                    .putBoolean(ChildSettingsActivity.TEST_INTERVAL,true).putInt(ChildSettingsActivity.MODE,0)
                    .putInt(ChildGrade.KEY,3).commit())throw new AssertionError("State write failed");
            if(!ChildApps.save(ChildLock.state(getTargetContext()),Collections.singleton("com.gentv.persistfixture")))throw new AssertionError("Target write failed");
            result.putString("stream","PASS: isolated emulator 30-second state saved; no instrumentation retained during persistence tests.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
