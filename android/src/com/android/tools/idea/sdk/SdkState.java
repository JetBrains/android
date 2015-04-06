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
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.Update;
import com.android.tools.idea.sdk.remote.UpdateResult;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.reference.SoftReference;
import com.intellij.util.concurrency.FutureResult;
import org.jetbrains.android.sdk.AndroidSdkData;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SdkState {

  /** Default expiration delay is 24 hours. */
  public final static long DEFAULT_EXPIRATION_PERIOD_MS = 24 * 3600 * 1000;

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
  private LoadTask myTask;

  private final Object myTaskLock = new Object();

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

  public Multimap<PkgType, RemotePkgInfo> getRemotePkgInfos() {
    return myRemotePkgs;
  }

  @Nullable
  public UpdateResult getUpdates() {
    return myUpdates;
  }

  public boolean loadAsync(long timeoutMs,
                           boolean canBeCancelled,
                           @Nullable Runnable onLocalComplete,
                           @Nullable Runnable onSuccess,
                           @Nullable Runnable onError,
                           boolean forceRefresh) {
    return load(timeoutMs, canBeCancelled, createList(onLocalComplete), createList(onSuccess), createList(onError), forceRefresh, false);
  }

  private boolean load(long timeoutMs,
                       boolean canBeCancelled,
                       @NonNull List<Runnable> onLocalComplete,
                       @NonNull List<Runnable> onSuccess,
                       @NonNull List<Runnable> onError,
                       boolean forceRefresh,
                       boolean sync) {
    if (!forceRefresh && System.currentTimeMillis() - myLastRefreshMs < timeoutMs) {
      for (Runnable localComplete : onLocalComplete) {
        localComplete.run();
      }
      for (Runnable success : onSuccess) {
        success.run();
      }
      return false;
    }
    synchronized (myTaskLock) {
      if (myTask != null) {
        myTask.addCallbacks(onLocalComplete, onSuccess, onError);
        return false;
      }

      myTask = new LoadTask(canBeCancelled, onLocalComplete, onSuccess, onError, forceRefresh, sync);
      ProgressWindow progress = new BackgroundableProcessIndicator(myTask);
      myTask.setProgress(progress);
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(myTask, progress);
    }
    return true;
  }

  public boolean loadSynchronously(long timeoutMs,
                                   boolean canBeCancelled,
                                   @Nullable Runnable onLocalComplete,
                                   @Nullable Runnable onSuccess,
                                   @Nullable final Runnable onError,
                                   boolean forceRefresh) {
    final FutureResult<Boolean> completed = new FutureResult<Boolean>();
    Runnable complete = new Runnable() {
      @Override
      public void run() {
        completed.set(true);
      }
    };

    List<Runnable> onLocalCompletes = createList(onLocalComplete);
    List<Runnable> onSuccesses = createList(onSuccess);
    List<Runnable> onErrors = createList(onError);
    onSuccesses.add(complete);
    onErrors.add(complete);
    boolean result = load(timeoutMs, canBeCancelled, onLocalCompletes, onSuccesses, onErrors, forceRefresh, true);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        boolean success = false;
        try {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          completed.get();
          success = true;
        }
        catch (InterruptedException e) {
          LOG.warn(e);
        }
        catch (ExecutionException e) {
          LOG.warn(e);
        }
        if (!success) {
          if (onError != null) {
            onError.run();
          }
        }
      }
    }, "Loading SDK", false, null);
    return result;
  }

  @NonNull
  private static List<Runnable> createList(@Nullable Runnable r) {
    if (r == null) {
      return Lists.newArrayList();
    }
    return Lists.newArrayList(r);
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

    private final List<Runnable> myOnSuccesses = Lists.newArrayList();
    private final List<Runnable> myOnErrors = Lists.newArrayList();
    private final List<Runnable> myOnLocalCompletes = Lists.newArrayList();
    private final boolean myForceRefresh;
    private final boolean myModal;
    private ProgressWindow myProgress;

    public LoadTask(boolean canBeCancelled,
                    @NonNull List<Runnable> onLocalComplete,
                    @NonNull List<Runnable> onSuccess,
                    @NonNull List<Runnable> onError,
                    boolean forceRefresh,
                    boolean modal) {
      super(null /*project*/, "Loading Android SDK", canBeCancelled,
            modal ? PerformInBackgroundOption.DEAF : PerformInBackgroundOption.ALWAYS_BACKGROUND);
      addCallbacks(onLocalComplete, onSuccess, onError);
      myForceRefresh = forceRefresh;
      myModal = modal;
    }

    public void setProgress(ProgressWindow progress) {
      assert myProgress == null;
      myProgress = progress;
    }

    public void addCallbacks(@NonNull List<Runnable> onLocalComplete, @NonNull List<Runnable> onSuccess, @NonNull List<Runnable> onError) {
      myOnLocalCompletes.addAll(onLocalComplete);
      myOnSuccesses.addAll(onSuccess);
      myOnErrors.addAll(onError);
    }

    public ProgressWindow getProgress() {
      return myProgress;
    }

    @Override
    public boolean isConditionalModal() {
      return myModal;
    }

    @Override
    public void run(@NonNull ProgressIndicator indicator) {
      assert myProgress != null;
      boolean success = false;
      try {
        IndicatorLogger logger = new IndicatorLogger(indicator);

        ApplicationEx app = ApplicationManagerEx.getApplicationEx();
        SdkLifecycleListener notifier = app.getMessageBus().syncPublisher(SdkLifecycleListener.TOPIC);

        // fetch local sdk
        indicator.setText("Loading local SDK...");
        indicator.setText2("");
        if (myForceRefresh) {
          mySdkData.getLocalSdk().clearLocalPkg(PkgType.PKG_ALL);
        }
        myLocalPkgInfos = mySdkData.getLocalSdk().getPkgsInfos(PkgType.PKG_ALL);
        notifier.localSdkLoaded(mySdkData);
        indicator.setFraction(0.25);

        if (indicator.isCanceled()) {
          return;
        }
        synchronized (myTaskLock) {
          for (Runnable onLocalComplete : myOnLocalCompletes) {
            ApplicationManager.getApplication().invokeLater(onLocalComplete, ModalityState.any());
          }
          myOnLocalCompletes.clear();
        }

        // fetch sdk repository sources.
        indicator.setText("Find SDK Repository...");
        indicator.setText2("");
        mySources = mySdkData.getRemoteSdk().fetchSources(myForceRefresh ? 0 : RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
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

        if (indicator.isCanceled()) {
          return;
        }
        success = true;
      }
      finally {
        myLastRefreshMs = System.currentTimeMillis();
        synchronized (myTaskLock) {
          // The processing of the task is now complete. To ensure that no more callbacks are added, and to allow another task to be
          // kicked off when needed, set myTask to null.
          myTask = null;
          if (success) {
            for (Runnable onLocalComplete : myOnLocalCompletes) {  // in case some were added by another call in the interim.
              ApplicationManager.getApplication().invokeLater(onLocalComplete, ModalityState.any());
            }
            for (Runnable onSuccess : myOnSuccesses) {
              ApplicationManager.getApplication().invokeLater(onSuccess, ModalityState.any());
            }
          }
          else {
            for (Runnable onError : myOnErrors) {
              ApplicationManager.getApplication().invokeLater(onError, ModalityState.any());
            }
          }
        }
      }
    }
  }
}
