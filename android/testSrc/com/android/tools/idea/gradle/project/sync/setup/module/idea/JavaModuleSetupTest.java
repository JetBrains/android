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
package com.android.tools.idea.gradle.project.sync.setup.module.idea;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link JavaModuleSetup}.
 */
public class JavaModuleSetupTest extends PlatformTestCase {
  @Mock private ModuleSetupContext myContext;
  @Mock private JavaModuleModel myJavaModel;
  @Mock private JavaModuleSetupStep mySetupStep1;
  @Mock private JavaModuleSetupStep mySetupStep2;

  private JavaModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModuleSetup = new JavaModuleSetup(mySetupStep1, mySetupStep2);
    when(myContext.getModule()).thenReturn(myModule);
  }

  public void testSetUpModule() {
    myModuleSetup.setUpModule(myContext, myJavaModel);

    verify(mySetupStep1, times(1)).setUpModule(myContext, myJavaModel);
    verify(mySetupStep2, times(1)).setUpModule(myContext, myJavaModel);
  }
}
