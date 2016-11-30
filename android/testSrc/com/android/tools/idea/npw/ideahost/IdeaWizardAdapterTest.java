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
package com.android.tools.idea.npw.ideahost;

import com.android.tools.idea.ui.properties.BatchInvoker;
import com.android.tools.idea.ui.properties.TestInvokeStrategy;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;

public class IdeaWizardAdapterTest {

  private static TestInvokeStrategy ourInvokeStrategy;

  private static class DummyModel extends WizardModel {
    boolean isFinished;

    @Override
    protected void handleFinished() {
      isFinished = true;
    }
  }

  private static class DummyStep extends ModelWizardStep<DummyModel> {

    public BoolValueProperty goForwardValue = new BoolValueProperty(true);

    protected DummyStep(@NotNull DummyModel model, @NotNull String title) {
      super(model, title);
    }

    @NotNull
    @Override
    protected JComponent getComponent() {
      return new JPanel();
    }

    @NotNull
    @Override
    protected ObservableBool canGoForward() {
      return goForwardValue;
    }
  }

  private static class DummyHost extends AbstractWizard<Step> {
    boolean updateButtonsCalled;
    boolean lastStepValue;
    boolean canGoNextValue;
    boolean firstStepValue;

    public DummyHost(String title, @Nullable Project project) {
      super(title, project);
      Reset();
    }

    void Reset() {
      updateButtonsCalled = false;
      lastStepValue = false;
      canGoNextValue = false;
      firstStepValue = false;
    }

    @Nullable
    @Override
    protected String getHelpID() {
      return null;
    }

    @Override
    public void updateButtons(boolean lastStep, boolean canGoNext, boolean firstStep) {
      updateButtonsCalled = true;
      lastStepValue = lastStep;
      canGoNextValue = canGoNext;
      firstStepValue = firstStep;
    }
  }

  @BeforeClass
  public static void setUpBatchInvoker() {
    ourInvokeStrategy = new TestInvokeStrategy();
    BatchInvoker.setOverrideStrategy(ourInvokeStrategy);
  }

  @AfterClass
  public static void restoreBatchInvoker() {
    BatchInvoker.clearOverrideStrategy();
  }

  @Test
  public void canGoForwardsAndBackwards() {
    AbstractWizard host = new DummyHost("None", null);

    DummyStep step1 = new DummyStep(new DummyModel(), "");
    DummyStep step2 = new DummyStep(new DummyModel(), "");
    ModelWizard guest = new ModelWizard.Builder(step1, step2).build();

    IdeaWizardAdapter adaptor = new IdeaWizardAdapter(host, guest);
    ourInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isFalse();

    adaptor.doNextAction();
    ourInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isTrue();

    adaptor.doPreviousAction();
    ourInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isFalse();

    Disposer.dispose(adaptor);
  }

  @Test
  public void canFinish() {
    AbstractWizard host = new DummyHost("None", null);

    DummyModel model = new DummyModel();
    DummyStep step1 = new DummyStep(model, "");
    ModelWizard guest = new ModelWizard.Builder(step1).build();

    IdeaWizardAdapter adaptor = new IdeaWizardAdapter(host, guest);
    ourInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isTrue();

    try {
      adaptor.doFinishAction();
    }
    catch (IllegalStateException e) {
      // TODO: can't mock out the close method on DialogWrapper as it is final, so this exception is generated. Is there a better way
      // to handle this?
      assertThat(e.getStackTrace()[0].getMethodName()).contains("ensureEventDispatchThread");
    }
    ourInvokeStrategy.updateAllSteps();
    assertThat(guest.isFinished()).isTrue();
    assertThat(model.isFinished).isTrue();

    Disposer.dispose(adaptor);
  }

  @Test
  public void updatesButtonsOnNavigation() {
    DummyHost host = new DummyHost("None", null);

    DummyStep step1 = new DummyStep(new DummyModel(), "");
    DummyStep step2 = new DummyStep(new DummyModel(), "");
    DummyStep step3 = new DummyStep(new DummyModel(), "");
    ModelWizard guest = new ModelWizard.Builder(step1, step2, step3).build();

    IdeaWizardAdapter adaptor = new IdeaWizardAdapter(host, guest);
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isFalse();

    host.Reset();
    adaptor.doNextAction();
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isFalse();
    assertThat(host.lastStepValue).isFalse();

    host.Reset();
    adaptor.doNextAction();
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isFalse();
    assertThat(host.lastStepValue).isTrue();

    Disposer.dispose(adaptor);
  }

  @Test
  public void updatesButtonsOnInput() throws Exception {
    DummyHost host = new DummyHost("None", null);
    DummyStep step = new DummyStep(new DummyModel(), "");
    ModelWizard guest = new ModelWizard.Builder(step).build();

    IdeaWizardAdapter adaptor = new IdeaWizardAdapter(host, guest);
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    host.Reset();
    step.goForwardValue.set(false);
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isFalse();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    host.Reset();
    step.goForwardValue.set(true);
    ourInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    Disposer.dispose(adaptor);
  }
}
