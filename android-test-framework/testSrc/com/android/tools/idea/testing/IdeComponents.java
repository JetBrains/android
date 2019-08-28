/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.mockito.Mockito.mock;

public final class IdeComponents {
  public IdeComponents(@Nullable Project project, @NotNull Disposable disposable) {
    if (project instanceof ProjectEx) {
      if (((ProjectEx)project).isLight() && disposable == project) {
        throw new AssertionError("Light (in-memory) projects are not disposed between tests, please use other IdeComponents " +
                                 "constructor when using light fixtures.");
      }
    }
  }

  @NotNull
  public static <T> T mockApplicationService(@NotNull Class<T> serviceType, @NotNull Disposable parentDisposable) {
    T mock = mock(serviceType);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), serviceType, mock, parentDisposable);
    return mock;
  }

  @NotNull
  public static <T> T mockProjectService(@NotNull Project project, @NotNull Class<T> serviceType, @NotNull Disposable parentDisposable) {
    T mock = mock(serviceType);
    ServiceContainerUtil.replaceService(project, serviceType, mock, parentDisposable);
    return mock;
  }
}
