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

import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Mockito.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Methods for replacing services in tests.
 *
 * @deprecated Use methods defined in {@link ServiceContainerUtil} directly.
 */
@Deprecated
public final class IdeComponents {
  private Project myProject;
  private final Disposable myDisposable;

  public IdeComponents(@NotNull Project project) {
    this(project, project);
  }

  public IdeComponents(@NotNull IdeaProjectTestFixture fixture) {
    this(fixture.getProject(), fixture.getTestRootDisposable());
  }

  public IdeComponents(@Nullable Project project, @NotNull Disposable disposable) {
    if (project instanceof ProjectImpl) {
      if (((ProjectImpl)project).isLight() && disposable == project) {
        throw new AssertionError("Light (in-memory) projects are not disposed between tests, please use other IdeComponents " +
                                 "constructor when using light fixtures.");
      }
    }

    myProject = project;
    if (project != null) {
      Disposer.register(project, () -> myProject = null);
    }

    myDisposable = disposable;
  }

  public <T> void replaceApplicationService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), serviceType, newServiceInstance, myDisposable);
  }

  public <T> void replaceProjectService(@NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    ServiceContainerUtil.replaceService(myProject, serviceType, newServiceInstance, myDisposable);
  }

  public <T> void replaceModuleService(@NotNull Module module, @NotNull Class<T> serviceType, @NotNull T newServiceInstance) {
    ServiceContainerUtil.replaceService(module, serviceType, newServiceInstance, myDisposable);
  }

  @NotNull
  public <T> T mockApplicationService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), serviceType, mock, myDisposable);
    return mock;
  }

  @NotNull
  public <T> T mockProjectService(@NotNull Class<T> serviceType) {
    T mock = mock(serviceType);
    checkState(myProject != null);
    ServiceContainerUtil.replaceService(myProject, serviceType, mock, myDisposable);
    return mock;
  }
}
