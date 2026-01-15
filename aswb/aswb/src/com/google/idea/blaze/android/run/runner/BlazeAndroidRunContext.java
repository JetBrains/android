/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.editor.ProfilerState;
import com.intellij.execution.Executor;

/** Holds the context data required to run an Android application. */
public final class BlazeAndroidRunContext {

  private final ConsoleProvider consoleProvider;
  private final ApkBuildStep buildStep;
  private final ApplicationIdProvider applicationIdProvider;
  private final ApkProvider apkProvider;
  private final ApplicationProjectContext applicationProjectContext;
  private final Executor executor;
  private final ProfilerState profileState;

  public BlazeAndroidRunContext(
      ConsoleProvider consoleProvider,
      ApkBuildStep buildStep,
      ApplicationIdProvider applicationIdProvider,
      ApkProvider apkProvider,
      ApplicationProjectContext applicationProjectContext,
      Executor executor,
      ProfilerState profileState) {
    this.consoleProvider = consoleProvider;
    this.buildStep = buildStep;
    this.applicationIdProvider = applicationIdProvider;
    this.apkProvider = apkProvider;
    this.applicationProjectContext = applicationProjectContext;
    this.executor = executor;
    this.profileState = profileState;
  }

  public ConsoleProvider getConsoleProvider() {
    return consoleProvider;
  }

  public ApkBuildStep getBuildStep() {
    return buildStep;
  }

  public ApplicationIdProvider getApplicationIdProvider() {
    return applicationIdProvider;
  }

  public ApkProvider getApkProvider() {
    return apkProvider;
  }

  public ApplicationProjectContext getApplicationProjectContext() {
    return applicationProjectContext;
  }

  public Executor getExecutor() {
    return executor;
  }

  public ProfilerState getProfileState() {
    return profileState;
  }
}
