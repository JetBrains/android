/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.Update;
import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.utils.ILogger;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.reference.SoftReference;
import org.jetbrains.android.sdk.AndroidSdkData;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SdkState {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.sdk.SdkState");

  @GuardedBy(value = "sSdkStates")
  private static final Set<SoftReference<SdkState>> sSdkStates = new HashSet<SoftReference<SdkState>>();

  @NonNull
  private final AndroidSdkData mySdkData;
  private LocalPkgInfo[] myLocalPkgInfos = new LocalPkgInfo[0];
  private SdkSources mySources;
  private UpdateResult myUpdates;
  private Multimap<PkgType, RemotePkgInfo> myRemotePkgs;

  private long myLastRefreshMs;
  private BackgroundableProcessIndicator myIndicator;

  private SdkState(@NonNull AndroidSdkData sdkData) {
    mySdkData = sdkData;
  }

  @NonNull
  public static SdkState getInstance(@NonNull AndroidSdkData sdkData) {
    synchronized (sSdkStates) {
      for (Iterator<SoftReference<SdkState>> it = sSdkStates.iterator(); it.hasNext(); ) {
        SoftReference<SdkState> ref = it.next();
        SdkState s = ref.get();
        if (s == null) {
          it.remove();
          continue;
        }
        // Note: check the cache for actual AndroidSdkData references, not equality.
        if (s.mySdkData == sdkData) {
          return s;
        }
      }

      SdkState s = new SdkState(sdkData);
      sSdkStates.add(new SoftReference<SdkState>(s));
      return s;
    }
  }

  @NonNull
  public AndroidSdkData getSdkData() {
    return mySdkData;
  }

  @NonNull
  public LocalPkgInfo[] getLocalPkgInfos() {
    return myLocalPkgInfos;
  }

  @Nullable
  public UpdateResult getUpdates() {
    return myUpdates;
  }

  public boolean loadAsync(long timeoutMs,
                           boolean canBeCancelled,
                           @Nullable Runnable onSuccess,
                           @Nullable Runnable onError) {
    if (myIndicator != null) {
      return false;
    }

    if (System.currentTimeMillis() - myLastRefreshMs < timeoutMs) {
      return false;
    }

    LoadTask task = new LoadTask(canBeCancelled, onSuccess, onError);
    myIndicator = new BackgroundableProcessIndicator(task);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, myIndicator);
    return true;
  }


  // -----

  private static class IndicatorLogger implements ILogger {
    @NonNull private final ProgressIndicator myIndicator;

    public IndicatorLogger(@NonNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
      if (msgFormat == null && t != null) {
        myIndicator.setText2(t.toString());
      } else if (msgFormat != null) {
        myIndicator.setText2(String.format(msgFormat, args));
      }
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
      myIndicator.setText2(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
      myIndicator.setText2(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
      // skip here, don't log verbose strings
    }
  }


  private class LoadTask extends Task.Backgroundable {

    @Nullable private final Runnable myOnSuccess;
    @Nullable private final Runnable myOnError;

    public LoadTask(boolean canBeCancelled,
                    @Nullable Runnable onSuccess,
                    @Nullable Runnable onError) {
      super(null /*project*/,
            "Loading Android SDK",
            canBeCancelled,
            PerformInBackgroundOption.ALWAYS_BACKGROUND);
      myOnSuccess = onSuccess;
      myOnError = onError;
    }

    @Override
    public void run(@NonNull ProgressIndicator indicator) {
      boolean success = false;
      try {
        IndicatorLogger logger = new IndicatorLogger(indicator);

        ApplicationEx app = ApplicationManagerEx.getApplicationEx();
        SdkLifecycleListener notifier = app.getMessageBus().syncPublisher(SdkLifecycleListener.TOPIC);

        // fetch local sdk
        indicator.setText("Loading local SDK...");
        indicator.setText2("");
        myLocalPkgInfos = mySdkData.getLocalSdk().getPkgsInfos(PkgType.PKG_ALL);
        notifier.localSdkLoaded(mySdkData);
        indicator.setFraction(0.25);

        if (indicator.isCanceled()) {
          return;
        }

        // fetch sdk repository sources.
        indicator.setText("Find SDK Repository...");
        indicator.setText2("");
        mySources = mySdkData.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
        indicator.setFraction(0.50);

        if (indicator.isCanceled()) {
          return;
        }

        // fetch remote sdk
        indicator.setText("Check SDK Repository...");
        indicator.setText2("");
        myRemotePkgs = mySdkData.getRemoteSdk().fetch(mySources, logger);
        notifier.remoteSdkLoaded(mySdkData);
        indicator.setFraction(0.75);

        if (indicator.isCanceled()) {
          return;
        }

        // compute updates
        indicator.setText("Compute SDK updates...");
        indicator.setText2("");
        myUpdates = Update.computeUpdates(myLocalPkgInfos, myRemotePkgs);
        notifier.updatesComputed(mySdkData);
        indicator.setFraction(1.0);

        success = true;
        if (myOnSuccess != null) {
          ApplicationManager.getApplication().invokeLater(myOnSuccess);
        }
      }
      finally {
        myIndicator = null;
        myLastRefreshMs = System.currentTimeMillis();
        if (!success && myOnError != null) {
          ApplicationManager.getApplication().invokeLater(myOnError);
        }
      }
    }
  }

}
