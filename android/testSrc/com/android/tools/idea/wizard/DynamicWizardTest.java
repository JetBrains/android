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
package com.android.tools.idea.wizard;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.wizard.DynamicWizardPathTest.DummyDynamicWizardPath;
import static com.android.tools.idea.wizard.DynamicWizardStepTest.DummyDynamicWizardStep;

/**
 * Tests for {@link DynamicWizard} and a dummy implementation
 */
public class DynamicWizardTest extends LightIdeaTestCase {

  DummyDynamicWizard myWizard;
  DummyDynamicWizardPath myPath1;
  DummyDynamicWizardPath myPath2;
  DummyDynamicWizardStep myStep1;
  DummyDynamicWizardStep myStep2;
  DummyDynamicWizardStep myStep3;
  DummyDynamicWizardStep myStep4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myWizard = new DummyDynamicWizard(null, null, "TestWizard");
    myPath1 = new DummyDynamicWizardPath("TestPath1");
    myPath2 = new DummyDynamicWizardPath("TestPath2");
    myStep1 = new DummyDynamicWizardStep("TestStep1");
    myStep2 = new DummyDynamicWizardStep("TestStep2");
    myStep3 = new DummyDynamicWizardStep("TestStep3");
    myStep4 = new DummyDynamicWizardStep("TestStep4");

    myPath1.addStep(myStep1);
    myPath1.addStep(myStep2);
    myPath2.addStep(myStep3);
    myPath2.addStep(myStep4);
  }

  public void testAddPath() throws Exception {
    assertEquals(0, myWizard.getVisibleStepCount());
    assertEquals(0, myWizard.myPaths.size());

    myWizard.addPath(myPath1);
    assertEquals(2, myWizard.getVisibleStepCount());
    assertEquals(1, myWizard.myPaths.size());

    myWizard.addPath(myPath2);
    assertEquals(4, myWizard.getVisibleStepCount());
    assertEquals(2, myWizard.myPaths.size());
  }

  public void testNavigation() throws Exception {

  }

  public static class DummyDynamicWizard extends DynamicWizard {

    public DummyDynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name) {
      super(project, module, name);
    }

    @Override
    public void performFinishingActions() {

    }

    @Override
    protected void init() {
      super.init();
    }
  }
}
