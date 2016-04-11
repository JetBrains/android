/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.ActivitySelector;
import com.android.tools.idea.editors.gfxtrace.DeviceInfo;
import com.android.tools.idea.editors.gfxtrace.GfxTracer;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.monitor.gpu.GpuMonitorView;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class GfxTraceCaptureAction extends ToggleAction {
  @NotNull protected final GpuMonitorView myView;
  @NotNull protected final String myText;
  private ListenableFuture<GfxTracer> myPending = null;
  private GfxTracer myActive = null;

  public static class Listen extends GfxTraceCaptureAction {
    public Listen(@NotNull GpuMonitorView view) {
      super(view, "Listen", "Listen for GFX traces", AndroidIcons.GfxTrace.ListenForTrace);
    }

    @Override
    ListenableFuture<GfxTracer> start(AnActionEvent event) {
      final IDevice device = myView.getDeviceContext().getSelectedDevice();
      if (device == null) {
        return null;
      }
      GfxTracer.Options options = new GfxTracer.Options();
      GfxTracer tracer = GfxTracer.listen(myView.getProject(), device, options, myView.getEvents());
      return Futures.immediateFuture(tracer);
    }
  }

  public static class Launch extends GfxTraceCaptureAction {
    private static final String NOTIFICATION_GROUP = "GPU trace";
    private static final String NOTIFICATION_LAUNCH_REQUIRES_ROOT_TITLE = "Rooted device required";
    private static final String NOTIFICATION_LAUNCH_REQUIRES_ROOT_CONTENT =
      "The device needs to be rooted in order to launch an application for GPU tracing.<br/>" +
      "To trace your own application on a non-rooted device, build your application with the GPU tracing library.";

    public Launch(@NotNull GpuMonitorView view) {
      super(view, "Launch", "Launch in GFX trace mode", AndroidIcons.GfxTrace.InjectSpy);
    }

    @Override
    ListenableFuture<GfxTracer> start(final AnActionEvent event) {
      final IDevice device = myView.getDeviceContext().getSelectedDevice();
      if (device == null) {
        return null;
      }

      final SettableFuture<GfxTracer> future = SettableFuture.create();
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            if (device.root()) {
              showLauncher(device, future);
              return;
            }
          }
          catch (Exception e) { /* assume non-root. */ }

          // Failed to restart adb as root.
          // Display message and abort.
          Notifications.Bus.notify(
            new Notification(NOTIFICATION_GROUP, NOTIFICATION_LAUNCH_REQUIRES_ROOT_TITLE, NOTIFICATION_LAUNCH_REQUIRES_ROOT_CONTENT,
                             NotificationType.ERROR));

          future.set(null);
        }
      });
      return future;
    }

    private ListenableFuture<GfxTracer> showLauncher(final IDevice device, final SettableFuture<GfxTracer> future) {
      DeviceInfo.Provider provider = new DeviceInfo.PkgInfoProvider(device);
      ActivitySelector.Listener listener = new ActivitySelector.Listener() {
        @Override
        public void OnLaunch(DeviceInfo.Package pkg, DeviceInfo.Activity act) {
          GfxTracer.Options options = new GfxTracer.Options();
          future.set(GfxTracer.launch(myView.getProject(), device, pkg, act, options, myView.getEvents()));
        }

        @Override
        public void OnCancel() {
          future.set(null);
        }
      };
      final ActivitySelector selector = new ActivitySelector(provider, listener);
      selector.setTitle("Launch activity...");
      selector.setVisible(true);

      // Ensure the selector is closed if the future is cancelled.
      future.addListener(new Runnable() {
        @Override
        public void run() {
          selector.setVisible(false);
        }
      }, EdtExecutor.INSTANCE);

      return future;
    }

  }

  public GfxTraceCaptureAction(@NotNull GpuMonitorView view,
                               @Nullable final String text,
                               @Nullable final String description,
                               @Nullable final Icon icon) {
    super(text, description, icon);
    myView = view;
    myText = text;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myPending != null || myActive != null;
  }

  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    if (myPending != null) {
      myPending.cancel(true);
      myPending = null;
      return;
    }

    if (myActive != null) {
      myActive.stop();
      myActive = null;
      return;
    }

    myPending = start(e);
    myPending.addListener(new Runnable() {
      @Override
      public void run() {
        if (myPending != null) {
          myActive = Futures.getUnchecked(myPending);
          myPending = null;
        }
      }
    }, EdtExecutor.INSTANCE);
  }

  @Override
  public final void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!GapiPaths.isValid()) {
      presentation.setEnabled(false);
      presentation.setText(myText + " : GPU debugger tools not installed");
    } else if (myPending == null && myActive == null) {
      presentation.setEnabled(isEnabled());
      presentation.setText(myText + " : start tracing");
    }
    else {
      presentation.setEnabled(true);
      presentation.setText(myText + " : stop tracing");
    }
  }

  boolean isEnabled() {
    return myView.getDeviceContext().getSelectedDevice() != null && GapiPaths.isValid();
  }

  abstract ListenableFuture<GfxTracer> start(AnActionEvent event);
}
