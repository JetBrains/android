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
package com.android.tools.idea.run.editor;

import com.android.annotations.Nullable;
import com.android.ddmlib.Client;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

/**
 * An interface to implement Android debugger.
 *
 * <p>This interface is exposed publicly as an extension point of Android plugin. Any IntelliJ plugin
 * may supply their debugger implementations by registering it to {@link #EP_NAME} from their
 * plugin.xml file.
 *
 * <p>This interface provides two entry points to start the debugger: {@link #getConnectDebuggerTask}
 * and {@link #attachToClient}.
 *
 * <p>{@link #getConnectDebuggerTask} is used when you run a {@link com.android.tools.idea.run.AndroidRunConfiguration}
 * with {@link com.intellij.execution.executors.DefaultDebugExecutor}. It creates a task which is to be executed at
 * the end of application launch pipeline by {@link com.android.tools.idea.run.LaunchTaskRunner}.
 *
 * <p>{@link #attachToClient} is used by {@link org.jetbrains.android.actions.AndroidConnectDebuggerAction} which
 * is an action to attach Android debugger to running Android processes.
 *
 * @param <S> a class which represents a state and configuration of your android debugger
 */
public interface AndroidDebugger<S extends AndroidDebuggerState> {

  /**
   * Extension point for any IntelliJ plugins to supply their {@link AndroidDebugger} implementations.
   *
   * If there multiple debugger implementations available for a project. Ones with {@link #shouldBeDefault}
   * returning true will be prioritized. A user may specify a debugger by run configuration or in
   * {@link org.jetbrains.android.actions.AndroidProcessChooserDialog}.
   */
  ExtensionPointName<AndroidDebugger> EP_NAME = ExtensionPointName.create("com.android.run.androidDebugger");

  /**
   * An arbitrary identifier string of this debugger. The ID must be unique among all other registered debuggers.
   */
  @NotNull
  String getId();

  /**
   * A name of this debugger. This string may be displayed to user to ask them choose a debugger if there
   * are multiple eligible debuggers for a run.
   */
  @NotNull
  String getDisplayName();

  /**
   * Creates a new state object. Although this is called state, it contains mostly about configurations of
   * how you run your debugger. The created state will be associated with one of your
   * {@link com.android.tools.idea.run.AndroidRunConfiguration} and properties will be persisted onto xml file.
   *
   * <p>Note: this method is supposed to be used for {@link #getConnectDebuggerTask}. {@link #attachToClient}
   * does not use this state at all.
   */
  @NotNull
  S createState();

  /**
   * Creates a run configuration to start debugger executable with a context of debugging a given
   * {@code runConfiguration}.
   *
   * @param runConfiguration a run configuration of an executable to be debugged
   */
  @NotNull
  AndroidDebuggerConfigurable<S> createConfigurable(@NotNull RunConfiguration runConfiguration);

  /**
   * An main entry point of starting a debugger. This is used for attaching a debugger to a process
   * started by run action with {@link com.intellij.execution.executors.DefaultDebugExecutor} type.
   * When you attach a debugger to an arbitrary running Android processes without run configuration,
   * {@link #attachToClient} is used instead.
   *
   * @param env an execution environment of a debugee process is running
   * @param applicationIdProvider provides the Android application IDs for the targets to be debugged
   * @param state an Android debugger state and configuration to be used to start the debugger
   * @return a task which starts a debugger and attach to target processes
   */
  @NotNull
  ConnectDebuggerTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                             @NotNull ApplicationIdProvider applicationIdProvider,
                                             @NotNull AndroidFacet facet,
                                             @NotNull S state);

  /**
   * Returns true if this debugger supports a given {@code project}.
   */
  boolean supportsProject(@NotNull Project project);

  /**
   * An alternative entry point of starting a debugger. This is used for attaching a debugger to an arbitrary
   * running Android processes without associated run configuration and run action. When you attach a debugger
   * through debug run action, {@link #getConnectDebuggerTask} is used instead.
   **/
  Promise<XDebugSession> attachToClient(@NotNull Project project, @NotNull Client client, @Nullable S debugState);

  /**
   * Indicates whether this debugger should be the default.
   *
   * @return true if it should be the default.
   */
  boolean shouldBeDefault();

  Promise<XDebugProcessStarter> getDebugProcessStarterForExistingProcess(@NotNull Project project,
                                                                         @NotNull Client client,
                                                                         @Nullable S debugState);
}
