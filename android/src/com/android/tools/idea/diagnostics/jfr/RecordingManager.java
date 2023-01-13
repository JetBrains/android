/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.jfr;

import com.android.tools.idea.serverflags.ServerFlagService;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.messages.MessageBusConnection;
import jdk.jfr.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.concurrent.TimeUnit;

public final class RecordingManager {

  public static final int JFR_RECORDING_DURATION_SECONDS = 30;
  public static final int MAX_FROZEN_TIME_RECORDED_SECONDS = 90;

  private static final String JFR_SERVER_FLAG_NAME = "diagnostics/jfr";

  private enum FreezeState { NOT_FROZEN, FROZEN }

  private static RecordingBuffer recordings = new RecordingBuffer();
  private static final Object jfrLock = new Object();
  private static FreezeState freezeState = FreezeState.NOT_FROZEN;
  private static long freezeStart;
  private static LowMemoryWatcher lowMemoryWatcher;

  public static void init() {
    if (ServerFlagService.Companion.getInstance().getBoolean(JFR_SERVER_FLAG_NAME, false)) {
      setupActionEvents();
      setupFreezeEvents();
      setupLowMemoryEvents();

      JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          synchronized (jfrLock) {
            if (freezeState == FreezeState.NOT_FROZEN) {
              recordings.swapBuffers();
            } else if (recordings.isRecordingAndFrozen()) {
              if (System.currentTimeMillis() - freezeStart > MAX_FROZEN_TIME_RECORDED_SECONDS * 1000) {
                recordings.truncateLongFreeze();
              }
            }
          }
        }
      }, 0, JFR_RECORDING_DURATION_SECONDS, TimeUnit.SECONDS);
    }
  }

  private static void setupActionEvents() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    IdentityHashMap<AnActionEvent, Event> jfrEventMap = new IdentityHashMap<>();
    connection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action,
                                        @NotNull DataContext dataContext,
                                        @NotNull AnActionEvent event) {
        Action a = new Action();
        a.actionId = ActionManager.getInstance().getId(action);
        a.begin();
        jfrEventMap.put(event, a);
      }

      @Override
      public void afterActionPerformed(@NotNull AnAction action,
                                       @NotNull DataContext dataContext,
                                       @NotNull AnActionEvent event) {
        Action a = (Action) jfrEventMap.get(event);
        if (a != null) {
          jfrEventMap.remove(event);
          a.commit();
        }
      }
    });
  }

  private static void setupFreezeEvents() {
    Application application = ApplicationManager.getApplication();
    application.getMessageBus().connect(application).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      @Override
      public void uiFreezeStarted(@NotNull File reportDir) {
        synchronized (jfrLock) {
          recordings.startFreeze();
          freezeStart = System.currentTimeMillis();
          freezeState = FreezeState.FROZEN;
        }
      }

      @Override
      public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
        synchronized (jfrLock) {
          freezeState = FreezeState.NOT_FROZEN;
        }
      }
    });
  }

  private static void setupLowMemoryEvents() {
    lowMemoryWatcher = LowMemoryWatcher.register(() -> {
      new LowMemory().commit();
    });
  }

  public static Path dumpJfrTo(Path directory) {
    synchronized (jfrLock) {
      return recordings.dumpJfrTo(directory);
    }
  }

  public static class DumpJfrAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      dumpJfrTo(new File(PathManager.getLogPath()).toPath());
    }
  }
}
