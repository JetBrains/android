/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.deprecated.ConfigureAndroidModuleStepDynamic;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;

import java.io.File;

public class ConfigureAndroidModuleStepDynamicTest extends AndroidGradleTestCase {

  private ConfigureAndroidModuleStepDynamic myStep;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModuleManager manager = ModuleManager.getInstance(getProject());
        File moduleRoot = new File(getProject().getBasePath(), "app");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);

        moduleRoot = new File(getProject().getBasePath(), "Lib");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);

        moduleRoot = new File(getProject().getBasePath(), "lib2");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);
      }
    });

    ScopedStateStore wizardState = new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, null);
    ScopedStateStore pathState = new ScopedStateStore(ScopedStateStore.Scope.PATH, wizardState, null);

    myStep = new ConfigureAndroidModuleStepDynamic(getTestRootDisposable(), null);
    myStep.myState = new ScopedStateStore(ScopedStateStore.Scope.STEP, pathState, myStep);
  }

  public void testComputeModuleName_deduplication() throws Exception {
    ModuleManager manager = ModuleManager.getInstance(getProject());
    Module module = manager.getModules()[0];
    assertEquals("app", module.getName());

    // "Lib" and "lib2" already exist
    assertEquals("lib3", WizardUtils.computeModuleName("Lib", getProject()));

    // "app" already exists
    assertEquals("app2", WizardUtils.computeModuleName("app", getProject()));
  }

}