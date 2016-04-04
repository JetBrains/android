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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.util.Function;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.lang.reflect.Method;
import java.util.List;

import static org.easymock.classextension.EasyMock.createMock;

/**
 * This class exists because it is impossible to mock invocations to
 * {@link GradleExecutionHelper#execute(String, GradleExecutionSettings, Function)}: EasyMock's {@code anyObject} method is not doing what
 * supposed to do, resulting in "unexpected" calls to {@code execute}.
 *
 * </p>As a workaround, when specify what the return value of {@code execute} should be, via {@link #setExecutionResult(Object)}.
 */
class GradleExecutionHelperDouble extends GradleExecutionHelper {
  @Nullable private Object myExecutionResult;

  @NotNull
  static GradleExecutionHelperDouble newMock() throws Exception {
    Class<GradleExecutionHelper> target = GradleExecutionHelper.class;
    Method m = target.getDeclaredMethod("getModelBuilder", Class.class, ExternalSystemTaskId.class, GradleExecutionSettings.class,
                                        ProjectConnection.class, ExternalSystemTaskNotificationListener.class, List.class);
    return createMock(GradleExecutionHelperDouble.class, m);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T execute(@NotNull String projectPath, @Nullable GradleExecutionSettings settings, @NotNull Function<ProjectConnection, T> f) {
    T executionResult = (T)myExecutionResult;
    setExecutionResult(null);
    return executionResult;
  }

  void setExecutionResult(@Nullable Object o) {
    myExecutionResult = o;
  }
}
