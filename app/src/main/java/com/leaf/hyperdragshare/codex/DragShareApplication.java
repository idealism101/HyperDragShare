package com.leaf.hyperdragshare.codex;

import android.app.Application;

/** Starts the optional tokenizer warm-up for every module-app process. */
public final class DragShareApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DragShareLog.configure(DragShareSettings.readLocal(this));
        // The framework binder is delivered to the process, not to an activity, so activation
        // detection has an answer waiting the first time the home page asks for one.
        XposedServiceStatus.register();
        DragShareDiagnostics.captureRuntimeOnce(this, "module application created", null);
        TextSegmenter.preloadIfEnabled(this);
    }
}
