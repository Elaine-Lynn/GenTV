package com.gentv.tv;

import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import java.io.File;

import dalvik.system.DexClassLoader;

/** Instantiate Gecko child processes from the private game archive on Android 9+. */
@android.annotation.TargetApi(28)
public final class GameRuntimeComponentFactory extends android.app.AppComponentFactory {
    @Override public Service instantiateService(ClassLoader baseLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (!className.startsWith("org.mozilla.gecko.process.GeckoChildProcessServices$")
                && !"org.mozilla.gecko.media.MediaManager".equals(className)) {
            return super.instantiateService(baseLoader, className, intent);
        }
        String processName = Application.getProcessName();
        String packageName = processName.split(":", 2)[0];
        int userId = Process.myUid() / 100000;
        File appData = new File("/data/user/" + userId + "/" + packageName);
        String versionAndAbi = GameRuntimeStore.activeVersion(new File(appData, "files")) + "/" + GameRuntimeStore.abi();
        File archive = new File(appData, "files/game-runtime/plugins/" + versionAndAbi + "/game-"
                + GameRuntimeStore.abi().replace('-', '_') + ".apk");
        File nativeDirectory = new File(appData, "code_cache/game-runtime-libs/" + versionAndAbi);
        if (!archive.isFile() || !nativeDirectory.isDirectory()) {
            throw new ClassNotFoundException("Game runtime archives are not staged");
        }
        DexClassLoader loader = new DexClassLoader(archive.getAbsolutePath(),
                new File(appData, "code_cache").getAbsolutePath(),
                nativeDirectory.getAbsolutePath(), baseLoader);
        Log.i("GenTVGameRuntime", "Child service via plugin DEX: " + className);
        return (Service) loader.loadClass(className).newInstance();
    }
}
