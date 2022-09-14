/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.prefs.AndroidLocationsException;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.utils.ILogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.EventListenerList;
import org.jetbrains.android.AndroidPluginDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The purpose of this class is to watch for changes to AVDs. Most operations within Studio on the AVDs are already tracked by Device
 * Manager, but if the user manually deletes files outside of Studio, we also need to update Device Manager. We save the current AVDs when
 * Studio loses focus, and then check the differences when Studio regains focus.
 */
@Service
final class VirtualDeviceWatcher {
  private @NotNull Map<String, AvdInfo> myAvds;
  private final @NotNull AvdManager myAvdManager;
  private final @NotNull EventListenerList myListeners;

  private final @NotNull Alarm myAlarm;

  @UiThread
  @SuppressWarnings("unused")
  private VirtualDeviceWatcher() throws AndroidLocationsException {
    this(getAvdManagerInstance(), AndroidPluginDisposable.Companion.getApplicationInstance());
  }

  @UiThread
  private VirtualDeviceWatcher(@NotNull AvdManager avdManager, @NotNull Disposable parent) {
    myAvds = new HashMap<>();
    myAvdManager = avdManager;
    myListeners = new EventListenerList();

    myAlarm = new Alarm();
    Disposer.register(parent, myAlarm);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(
      ApplicationActivationListener.TOPIC,
      new ApplicationActivationListener() {
        @UiThread
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          // Use a delay to avoid excessive operations
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(() -> {
            if (!ApplicationManager.getApplication().isActive()) {
              return;
            }

            ListenableFuture<Void> future = DeviceManagerFutures.appExecutorServiceSubmit(VirtualDeviceWatcher.this::processAvdInfoChanges);
            Futures.addCallback(future, new FailedFutureCallback(), AppExecutorUtil.getAppExecutorService());
          }, 1000);
        }

        @UiThread
        @Override
        public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
          // If there is a delay in progress, we should not overwrite the AVDs because the baseline for the change needs to remain the same
          if (myAlarm.getActiveRequestCount() > 0) {
            return;
          }

          ListenableFuture<Void> future = DeviceManagerFutures.appExecutorServiceSubmit(VirtualDeviceWatcher.this::snapshotAvds);
          Futures.addCallback(future, new FailedFutureCallback(), AppExecutorUtil.getAppExecutorService());
        }
      });
  }

  @UiThread
  private static @NotNull AvdManager getAvdManagerInstance() throws AndroidLocationsException {
    ILogger logger = new LogWrapper(Logger.getInstance(VirtualDeviceWatcher.class));

    AvdManager manager = AvdManager.getInstance(AndroidSdks.getInstance().tryToChooseSdkHandler(), logger);
    assert manager != null;

    return manager;
  }

  @UiThread
  static @NotNull VirtualDeviceWatcher getInstance() {
    return ApplicationManager.getApplication().getService(VirtualDeviceWatcher.class);
  }

  @UiThread
  void addVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener listener) {
    myListeners.add(VirtualDeviceWatcherListener.class, listener);
  }

  @UiThread
  void removeVirtualDeviceWatcherListener(@NotNull VirtualDeviceWatcherListener listener) {
    myListeners.remove(VirtualDeviceWatcherListener.class, listener);
  }

  /**
   * Checks the differences between the saved AVDs and the current AVDs. Called by an application pool thread.
   */
  @WorkerThread
  private synchronized void processAvdInfoChanges() {
    Map<String, AvdInfo> currentAvds = getCurrentAvds();
    Application application = ApplicationManager.getApplication();

    for (String currentId : currentAvds.keySet()) {
      AvdInfo currentAvd = currentAvds.get(currentId);
      AvdInfo pastAvd = myAvds.remove(currentId);
      if (pastAvd == null) {
        // If this AVD doesn't exist in the past set, then this is a new AVD
        application.invokeLater(() -> fireVirtualDeviceAdded(new VirtualDevicePath(currentAvd.getId())));
      }
      else if (!currentAvd.equals(pastAvd)) {
        // If the AVD ID is the same but the fields are not equal, the AVD has been changed
        application.invokeLater(() -> fireVirtualDeviceChanged(new VirtualDevicePath(currentAvd.getId())));
      }
    }

    // Any AVDs left over in myAvds are AVDs that have been deleted
    myAvds.values().stream()
      .map(AvdInfo::getId)
      .map(VirtualDevicePath::new)
      .forEach(path -> application.invokeLater(() -> fireVirtualDeviceRemoved(path)));

    // Save the new set of AVDs
    myAvds = currentAvds;
  }

  @UiThread
  private void fireVirtualDeviceAdded(@NotNull Key key) {
    EventListenerLists.fire(myListeners,
                            VirtualDeviceWatcherListener::virtualDeviceAdded,
                            VirtualDeviceWatcherListener.class,
                            () -> new VirtualDeviceWatcherEvent(this, key));
  }

  @UiThread
  private void fireVirtualDeviceChanged(@NotNull Key key) {
    EventListenerLists.fire(myListeners,
                            VirtualDeviceWatcherListener::virtualDeviceChanged,
                            VirtualDeviceWatcherListener.class,
                            () -> new VirtualDeviceWatcherEvent(this, key));
  }

  @UiThread
  private void fireVirtualDeviceRemoved(@NotNull Key key) {
    EventListenerLists.fire(myListeners,
                            VirtualDeviceWatcherListener::virtualDeviceRemoved,
                            VirtualDeviceWatcherListener.class,
                            () -> new VirtualDeviceWatcherEvent(this, key));
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private synchronized void snapshotAvds() {
    myAvds = getCurrentAvds();
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private synchronized @NotNull Map<@NotNull String, @NotNull AvdInfo> getCurrentAvds() {
    try {
      myAvdManager.reloadAvds();
      Map<String, AvdInfo> idToAvdInfo = new HashMap<>();
      Arrays.stream(myAvdManager.getAllAvds()).forEach(avdInfo -> idToAvdInfo.put(avdInfo.getId(), avdInfo));
      return idToAvdInfo;
    }
    catch (AndroidLocationsException e) {
      throw new RuntimeException(e);
    }
  }

  private static class FailedFutureCallback implements FutureCallback<Void> {
    /**
     * Called by an application pool thread
     */
    @WorkerThread
    @Override
    public void onSuccess(@Nullable Void result) {
    }

    /**
     * Called by an application pool thread
     */
    @WorkerThread
    @Override
    public void onFailure(@NotNull Throwable t) {
      Logger.getInstance(VirtualDevice.class).warn(t);
    }
  }
}
