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
package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.project.IndexingSuspender;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.concurrent.GuardedBy;

import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

public class GradleBuildState {
  @VisibleForTesting
  static final Topic<GradleBuildListener> GRADLE_BUILD_TOPIC = new Topic<>("Gradle build", GradleBuildListener.class);

  private static final int INDEXING_WAIT_TIMEOUT_MILLIS = 10000;

  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;

  @NotNull private final Object myLock = new Object();
  @NotNull private final Object myIndexingLock = new Object();
  private boolean myFlagIsIndexingAware = StudioFlags.GRADLE_INVOCATIONS_INDEXING_AWARE.get();

  @GuardedBy("myLock")
  @Nullable
  private BuildContext myCurrentContext;

  @GuardedBy("myLock")
  @Nullable
  private BuildSummary mySummary;

  @NotNull
  public static MessageBusConnection subscribe(@NotNull Project project, @NotNull GradleBuildListener listener) {
    return subscribe(project, listener, project);
  }

  @NotNull
  public static MessageBusConnection subscribe(@NotNull Project project,
                                               @NotNull GradleBuildListener listener,
                                               @NotNull Disposable parentDisposable) {
    MessageBusConnection connection = project.getMessageBus().connect(parentDisposable);
    connection.subscribe(GRADLE_BUILD_TOPIC, listener);
    return connection;
  }

  @NotNull
  public static GradleBuildState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleBuildState.class);
  }

  public GradleBuildState(@NotNull Project project, @NotNull MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
  }

  public void buildStarted(@NotNull BuildContext context) {
    synchronized (myLock) {
      myCurrentContext = context;
      myFlagIsIndexingAware = StudioFlags.GRADLE_INVOCATIONS_INDEXING_AWARE.get();
      ensureNoIndexingDuringBuild();
    }
    syncPublisher(listener -> listener.buildStarted(context));
  }

  private void ensureNoIndexingDuringBuild() {
    if (myFlagIsIndexingAware) {
      IndexingSuspender.queue(myProject, "Gradle Build", myIndexingLock,
                              this::isBuildInProgress, INDEXING_WAIT_TIMEOUT_MILLIS);
    }
  }

  public void buildFinished(@NotNull BuildStatus status) {
    BuildContext context;
    synchronized (myLock) {
      context = myCurrentContext;
      myCurrentContext = null;
      mySummary = new BuildSummary(status, context);
      if (myFlagIsIndexingAware) {
        unblockIndexing();
      }
    }
    syncPublisher(listener -> listener.buildFinished(status, context));
  }

  private void unblockIndexing() {
    if (myFlagIsIndexingAware) {
      synchronized (myIndexingLock) {
        myIndexingLock.notifyAll();
      }
    }
  }

  private void syncPublisher(@NotNull Consumer<GradleBuildListener> consumer) {
    invokeLaterIfProjectAlive(myProject, () -> consumer.consume(myMessageBus.syncPublisher(GRADLE_BUILD_TOPIC)));
  }

  public boolean isBuildInProgress() {
    synchronized (myLock) {
      return myCurrentContext != null;
    }
  }

  @Nullable
  public BuildContext getCurrentContext() {
    synchronized (myLock) {
      return myCurrentContext;
    }
  }

  @Nullable
  public BuildSummary getSummary() {
    synchronized (myLock) {
      return mySummary;
    }
  }

  @TestOnly
  void clear() {
    synchronized (myLock) {
      myCurrentContext = null;
      mySummary = null;
    }
  }
}
