/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.assistant.AssistActionState;
import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.datamodel.DefaultActionState;
import com.android.tools.idea.assistant.view.StatefulButtonMessage;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.utils.HtmlBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * StateManager for RestartAdbAction, displays if there are any connected devices to the user through the
 * state message.
 */
public final class RestartAdbActionStateManager extends AssistActionStateManager implements AndroidDebugBridge.IDebugBridgeChangeListener {
  @Nullable private Project myProject;
  @Nullable private ListenableFuture<AndroidDebugBridge> myAdbFuture;
  private boolean myLoading;

  @NotNull
  private static String generateMessage(@NotNull IDevice[] devices) {

    if (devices.length == 0) {
      return AndroidBundle.message("connection.assistant.adb.no_devices");
    }
    else {
      HtmlBuilder builder = new HtmlBuilder().openHtmlBody();
      builder
        .add(AndroidBundle.message("connection.assistant.adb.devices"))
        .newline();
      for (IDevice device : devices) {
        builder.addHtml("<h2>" + device.getName() + "</h2>")
          .newline()
          .add(device.getVersion().toString())
          .newlineIfNecessary();
      }
      return builder.closeHtmlBody().getHtml();
    }
  }

  @NotNull
  @Override
  public String getId() {
    return RestartAdbAction.ACTION_ID;
  }

  @Override
  public void init(@NotNull Project project, @NotNull ActionData actionData) {
    myProject = project;
    initDebugBridge(myProject);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
  }

  @NotNull
  @Override
  public AssistActionState getState(@NotNull Project project, @NotNull ActionData actionData) {
    if (myLoading) {
      return DefaultActionState.IN_PROGRESS;
    }
    if (myAdbFuture == null) {
      return DefaultActionState.INCOMPLETE;
    }
    if (!myAdbFuture.isDone()) {
      return DefaultActionState.IN_PROGRESS;
    }
    AndroidDebugBridge adb = AndroidDebugBridge.getBridge();
    if (adb == null || adb.getDevices().length == 0) {
      return DefaultActionState.ERROR_RETRY;
    }
    return DefaultActionState.PARTIALLY_COMPLETE;
  }

  @NotNull
  @Override
  public StatefulButtonMessage getStateDisplay(@NotNull Project project, @NotNull ActionData actionData, @Nullable String message) {
    AssistActionState state = getState(project, actionData);
    if (message == null) {
      message = "";
    }
    if (state == DefaultActionState.IN_PROGRESS) {
      message = AndroidBundle.message("connection.assistant.adb.loading");
    }
    else if (state == DefaultActionState.PARTIALLY_COMPLETE || state == DefaultActionState.ERROR_RETRY) {
      AndroidDebugBridge adb = AndroidDebugBridge.getBridge();
      if (adb != null) {
        message = generateMessage(adb.getDevices());
      }
      else {
        message = AndroidBundle.message("connection.assistant.adb.failure");
      }
    }
    return new StatefulButtonMessage(message, state);
  }

  private void setLoading(boolean loading) {
    myLoading = loading;
    if (myProject != null) {
      refreshDependencyState(myProject);
    }
  }

  private void initDebugBridge(@NotNull Project project) {
    File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      return;
    }
    myAdbFuture = AdbService.getInstance().getDebugBridge(adb);
    if (myAdbFuture == null) return;
    Futures.addCallback(myAdbFuture, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        refreshDependencyState(project);
      }

      @Override
      public void onFailure(@Nullable Throwable t) {
        refreshDependencyState(project);
      }
    }, EdtExecutor.INSTANCE);
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {}

  @Override
  public void restartInitiated() {
    setLoading(true);
  }

  @Override
  public void restartCompleted(boolean isSuccessful) {
    setLoading(false);
  }
}
