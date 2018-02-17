/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.ui.HideablePanel;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profilers.stacktrace.*;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class EnergyCallstackView extends JPanel {

  private final EnergyProfilerStageView myStageView;

  public EnergyCallstackView(EnergyProfilerStageView stageView) {
    super(new VerticalFlowLayout());
    myStageView = stageView;
  }

  /**
   * Set the details view for all callstacks of a duration, if given {@code duration} is {@code null}, this clears the view.
   */
  public void setDuration(@Nullable EventDuration duration) {
    removeAll();
    if (duration == null) {
      return;
    }

    long sessionStartTime = myStageView.getStage().getStudioProfilers().getSession().getStartTimestamp();
    for (EnergyProfiler.EnergyEvent event : duration.getEventList()) {
      if (event.getTraceId().isEmpty()) {
        continue;
      }
      long timeMs = TimeUnit.NANOSECONDS.toMillis(event.getTimestamp() - sessionStartTime);
      String description = event.getMetadataCase() + " " + StringUtil.formatDuration(timeMs);

      // TODO: Get real data and remove the fake callstack string below.
      String callstackString = getCallstackString(event.getTraceId());
      List<CodeLocation> codeLocationList = Arrays.stream(callstackString.split("\\n"))
        .filter(line -> !line.trim().isEmpty())
        .map(line -> new StackFrameParser(line).toCodeLocation())
        .collect(Collectors.toList());
      StackTraceModel model = new StackTraceModel(myStageView.getStage().getStudioProfilers().getIdeServices().getCodeNavigator());
      StackTraceView stackTraceView = myStageView.getIdeComponents().createStackView(model);
      stackTraceView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, codeLocationList);
      HideablePanel hideablePanel = new HideablePanel.Builder(description, stackTraceView.getComponent()).build();
      add(hideablePanel);
    }
  }

  @NotNull
  private static String getCallstackString(String traceId) {
    if (traceId.contains("acquire")) {
      return "android.os.PowerManager$WakeLock.acquire(PowerManager.java:32)\n" +
             "com.activity.energy.WakeLockActivity.runAcquire(WakeLockActivity.java:29)\n" +
             "java.lang.reflect.Method.invoke(Native Method)\n" +
             "com.android.tools.profiler.FakeAndroid.onRequest(FakeAndroid.java:133)\n" +
             "android.tools.SimpleWebServer.handle(SimpleWebServer.java:179)\n" +
             "android.tools.SimpleWebServer.run(SimpleWebServer.java:127)";
    }
    else {
      return "android.os.PowerManager$WakeLock.release(PowerManager.java:42)\n" +
             "com.activity.energy.WakeLockActivity.runRelease(WakeLockActivity.java:30)\n" +
             "java.lang.reflect.Method.invoke(Native Method)\n" +
             "com.android.tools.profiler.FakeAndroid.onRequest(FakeAndroid.java:133)\n" +
             "android.tools.SimpleWebServer.handle(SimpleWebServer.java:179)\n" +
             "android.tools.SimpleWebServer.run(SimpleWebServer.java:127)";
    }
  }
}
