/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.fd.FlightRecorder;
import com.android.tools.idea.fd.InstantRunBuildProgressListener;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.AfterGradleInvocationTask;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import net.jcip.annotations.GuardedBy;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.CONFIGURE_ON_DEMAND_OPTION;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static com.intellij.openapi.ui.MessageType.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jetbrains.android.AndroidPlugin.*;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

/**
 * Invokes Gradle tasks as a IDEA task in the background.
 */
public abstract class GradleTasksExecutor extends Task.Backgroundable {
  @NotNull public static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle Build (Logging)");
  @NotNull public static final NotificationGroup BALLOON_NOTIFICATION = NotificationGroup.balloonGroup("Gradle Build (Balloon)");

  protected GradleTasksExecutor(@Nullable Project project) {
    super(project, "Gradle Build Running", true);
  }

  /**
   * Regular {@link #queue()} method might return immediately if current task is executed in a separate non-calling thread.
   * <p/>
   * However, sometimes we want to wait for the task completion, e.g. consider a use-case when we execute an IDE run configuration.
   * It opens dedicated run/debug tool window and displays execution output there. However, it is shown as finished as soon as
   * control flow returns. That's why we don't want to return control flow until the actual task completion.
   * <p/>
   * This method allows to achieve that target - it executes gradle tasks under the IDE 'progress management system' (shows progress
   * bar at the bottom) in a separate thread and doesn't return control flow to the calling thread until all target tasks are actually
   * executed.
   */
  public abstract void queueAndWaitForCompletion();
}
