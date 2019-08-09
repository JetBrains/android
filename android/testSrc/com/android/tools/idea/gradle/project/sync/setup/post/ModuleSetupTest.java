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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ModuleSetup}
 */
public class ModuleSetupTest extends PlatformTestCase {
  @Mock private ModuleSetupStep myStep1;
  @Mock private ModuleSetupStep myStep2;

  private ModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModuleSetup = new ModuleSetup(getProject(), myStep1, myStep2);
  }

  public void testSetUpModules() throws Exception {
    Module module2 = createModule("module2");
    Module module3 = createModule("module3");

    ProgressIndicator indicator = mock(ProgressIndicator.class);
    myModuleSetup.setUpModules(indicator);

    verify(myStep1).setUpModule(myModule, indicator);
    verify(myStep1).setUpModule(module2, indicator);
    verify(myStep1).setUpModule(module3, indicator);

    verify(myStep2).setUpModule(myModule, indicator);
    verify(myStep2).setUpModule(module2, indicator);
    verify(myStep2).setUpModule(module3, indicator);
  }
}