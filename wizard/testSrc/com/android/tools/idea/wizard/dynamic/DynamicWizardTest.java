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
package com.android.tools.idea.wizard.dynamic;

import static com.android.tools.idea.wizard.dynamic.DynamicWizardPathTest.SampleDynamicWizardPath;
import static com.android.tools.idea.wizard.dynamic.DynamicWizardStepTest.SampleDynamicWizardStep;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightIdeaTestCase;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link DynamicWizard} and a sample implementation
 */
public class DynamicWizardTest extends LightIdeaTestCase {

  SampleDynamicWizard myWizard;
  SampleDynamicWizardPath myPath1;
  SampleDynamicWizardPath myPath2;
  SampleDynamicWizardStep myStep1;
  SampleDynamicWizardStep myStep2;
  SampleDynamicWizardStep myStep3;
  SampleDynamicWizardStep myStep4;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myWizard = new SampleDynamicWizard(null, null, "TestWizard");
    Disposer.register(getTestRootDisposable(), myWizard.getDisposable());
    myPath1 = new SampleDynamicWizardPath("TestPath1");
    myPath2 = new SampleDynamicWizardPath("TestPath2");
    myStep1 = new SampleDynamicWizardStep("TestStep1");
    myStep2 = new SampleDynamicWizardStep("TestStep2");
    myStep3 = new SampleDynamicWizardStep("TestStep3");
    myStep4 = new SampleDynamicWizardStep("TestStep4");

    myPath1.addStep(myStep1);
    myPath1.addStep(myStep2);
    myPath2.addStep(myStep3);
    myPath2.addStep(myStep4);
  }

  public void testAddPath() throws Exception {
    assertEquals(0, myWizard.getVisibleStepCount());
    assertEquals(0, myWizard.getAllPaths().size());

    myWizard.addPath(myPath1);
    assertEquals(2, myWizard.getVisibleStepCount());
    assertEquals(1, myWizard.getAllPaths().size());

    myWizard.addPath(myPath2);
    assertEquals(4, myWizard.getVisibleStepCount());
    assertEquals(2, myWizard.getAllPaths().size());
  }

  public void testInvisibleFirstPage() {
    final LabelStep visibleStep = new LabelStep(true);
    DynamicWizard wizard = new VisibilityTestWizard(new LabelStep(false), new LabelStep(false), visibleStep);
    wizard.init();
    assertEquals(1, wizard.getVisibleStepCount(), 1);
    assertEquals(visibleStep, wizard.getCurrentPath().getCurrentStep());
  }

  public void testVisibleFirstPage() {
    final LabelStep visibleStep = new LabelStep(true);
    DynamicWizard wizard = new VisibilityTestWizard(visibleStep, new LabelStep(false), new LabelStep(false), new LabelStep(true));
    wizard.init();
    assertEquals(1, wizard.getVisibleStepCount(), 2);
    assertEquals(visibleStep, wizard.getCurrentPath().getCurrentStep());
  }

  public static class SampleDynamicWizard extends DynamicWizard {

    public SampleDynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name) {
      super(project, module, name);
    }

    @Override
    public void performFinishingActions() {

    }

    @NotNull
    @Override
    protected String getProgressTitle() {
      return "sample";
    }

    @Override
    protected String getWizardActionDescription() {
      return "Nothing is Done";
    }
  }

  private static class LabelStep extends DynamicWizardStep {
    private final JLabel myLabel = new JLabel("a label");
    private final boolean myIsVisible;

    public LabelStep(boolean isVisible) {
      myIsVisible = isVisible;
    }

    @Override
    public void init() {
    }

    @Override
    public boolean isStepVisible() {
      return myIsVisible;
    }

    @NotNull
    @Override
    public JComponent createStepBody() {
      return myLabel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myLabel;
    }

    @Nullable
    @Override
    public JLabel getMessageLabel() {
      return myLabel;
    }

    @NotNull
    @Override
    public String getStepName() {
      return "visible step";
    }

    @NotNull
    @Override
    protected String getStepTitle() {
      return getStepName();
    }

    @Nullable
    @Override
    protected String getStepDescription() {
      return null;
    }
  }

  private class VisibilityTestWizard extends DynamicWizard {
    private final DynamicWizardStep[] mySteps;

    public VisibilityTestWizard(DynamicWizardStep... steps) {
      super(null, null, "test wizard");
      disposeOnTearDown(getDisposable());
      mySteps = steps;
    }

    @Override
    public void init() {
      for (DynamicWizardStep step : mySteps) {
        addPath(new SingleStepPath(step) {
          @Override
          public boolean isPathVisible() {
            return true;
          }
        });
      }
      super.init();
    }

    @Override
    public void performFinishingActions() {
    }

    @NotNull
    @Override
    protected String getProgressTitle() {
      return "sample";
    }

    @Override
    protected String getWizardActionDescription() {
      return getName();
    }
  }
}
