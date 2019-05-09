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
package com.android.tools.idea.npw.module;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.VersionCodes;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigureAndroidModuleStepTest extends AndroidGradleTestCase {
  private static TestInvokeStrategy myInvokeStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInvokeStrategy = new TestInvokeStrategy();
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      BatchInvoker.clearOverrideStrategy();
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * When adding two libraries to a project, the second library package name should have a distinct value from the first one
   * (com.example.mylib vs com.example.mylib2). See http://b/68177735 for more details.
   */
  public void testPackageNameDependsOnModuleName() {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);

    when(project.getComponent(ModuleManager.class)).thenReturn(moduleManager);
    when(project.getBasePath()).thenReturn("/");
    when(moduleManager.getModules()).thenReturn(Module.EMPTY_ARRAY);

    NewModuleModel newModuleModel = new NewModuleModel(project, new ProjectSyncInvoker.DefaultProjectSyncInvoker());
    ConfigureAndroidModuleStep configureAndroidModuleStep =
      new ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, "com.example", "Test Title");

    Disposer.register(getTestRootDisposable(), newModuleModel);
    Disposer.register(getTestRootDisposable(), configureAndroidModuleStep);
    myInvokeStrategy.updateAllSteps();

    newModuleModel.moduleName().set("myLib");
    myInvokeStrategy.updateAllSteps();
    assertThat(newModuleModel.packageName().get()).isEqualTo("com.example.mylib");

    newModuleModel.moduleName().set("myLib2");
    myInvokeStrategy.updateAllSteps();
    assertThat(newModuleModel.packageName().get()).isEqualTo("com.example.mylib2");
  }

  /**
   * This tests assumes Project without androidx configuration.
   * Selecting and Android API of 27(P) or less should allow the use to "Go Forward", and 28(Q) or more
   * should stop the user from "Go Forward"
   */
  public void testSelectAndroid_Q_onNonAndroidxProjects() {
    Project project = getProject();

    NewModuleModel newModuleModel = new NewModuleModel(project, new ProjectSyncInvoker.DefaultProjectSyncInvoker());
    ConfigureAndroidModuleStep configureAndroidModuleStep =
      new ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, "com.example", "Test Title");

    Disposer.register(getTestRootDisposable(), newModuleModel);
    Disposer.register(getTestRootDisposable(), configureAndroidModuleStep);
    myInvokeStrategy.updateAllSteps();

    VersionItem androidTarget_P = createMockAndroidVersion(VersionCodes.P);
    VersionItem androidTarget_Q = createMockAndroidVersion(VersionCodes.Q);

    configureAndroidModuleStep.getRenderModel().androidSdkInfo().setValue(androidTarget_P);
    assertThat(configureAndroidModuleStep.canGoForward().get()).isTrue();

    configureAndroidModuleStep.getRenderModel().androidSdkInfo().setValue(androidTarget_Q);
    assertThat(configureAndroidModuleStep.canGoForward().get()).isFalse();
  }

  private static VersionItem createMockAndroidVersion(int apiLevel) {
    AndroidVersionsInfo myMockAndroidVersionsInfo = mock(AndroidVersionsInfo.class);
    return myMockAndroidVersionsInfo.new VersionItem(new MockPlatformTarget(apiLevel, 0) {
      @NonNull
      @Override
      public AndroidVersion getVersion() {
        return new AndroidVersion(apiLevel - 1, "TEST_PLATFORM");
      }
    });
  }
}