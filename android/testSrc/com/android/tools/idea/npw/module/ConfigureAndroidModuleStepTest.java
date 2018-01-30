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

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.truth.Truth;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

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
  public void testPackageNameDependsOnModuleName() throws Exception {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);

    when(project.getComponent(ModuleManager.class)).thenReturn(moduleManager);
    when(project.getBasePath()).thenReturn("/");
    when(moduleManager.getModules()).thenReturn(Module.EMPTY_ARRAY);

    NewModuleModel newModuleModel = new NewModuleModel(project);
    ConfigureAndroidModuleStep configureAndroidModuleStep =
      new ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, "com.example", false, false, "Test Title");

    Disposer.register(getTestRootDisposable(), newModuleModel);
    Disposer.register(getTestRootDisposable(), configureAndroidModuleStep);
    myInvokeStrategy.updateAllSteps();

    newModuleModel.moduleName().set("myLib");
    myInvokeStrategy.updateAllSteps();
    Truth.assertThat(newModuleModel.packageName().get()).isEqualTo("com.example.mylib");

    newModuleModel.moduleName().set("myLib2");
    myInvokeStrategy.updateAllSteps();
    Truth.assertThat(newModuleModel.packageName().get()).isEqualTo("com.example.mylib2");
  }
}
