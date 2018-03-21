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
package com.android.tools.profilers.network.details;

import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.StackTrace;
import com.android.tools.profilers.stacktrace.StackTraceView;
import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Tab which shows a stack trace to where a network request was created.
 */
final class CallStackTabContent extends TabContent {
  @NotNull
  private final NetworkConnectionsModel myConnectionsModel;

  @NotNull
  private final StackTraceView myStackTraceView;

  public CallStackTabContent(@NotNull NetworkConnectionsModel model, @NotNull StackTraceView stackTraceView) {
    myConnectionsModel = model;
    myStackTraceView = stackTraceView;
  }

  @NotNull
  @Override
  public String getTitle() {
    return "Call Stack";
  }

  @NotNull
  @Override
  protected JComponent createComponent() {
    return myStackTraceView.getComponent();
  }

  @NotNull
  public StackTraceView getStackTraceView() {
    return myStackTraceView;
  }

  @Override
  public void populateFor(@Nullable HttpData data) {
    if (data != null) {
      StackTrace stackTrace = new StackTrace(myConnectionsModel, data);
      myStackTraceView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, stackTrace.getCodeLocations());
    }
    else {
      myStackTraceView.getModel().clearStackFrames();
    }
  }

  @Override
  public void trackWith(@NotNull FeatureTracker featureTracker) {
    featureTracker.trackSelectNetworkDetailsStack();
  }
}
