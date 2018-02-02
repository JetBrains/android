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

import com.android.tools.idea.observable.BatchInvokerStrategyRule;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;

public class IdeaWizardAdapterTest {
  private Disposable myTestRootDisposable;

  private TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Rule
  public BatchInvokerStrategyRule myStrategyRule = new BatchInvokerStrategyRule(myInvokeStrategy);

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

  private class DummyHost extends AbstractWizard<Step> {
    boolean updateButtonsCalled;
    boolean lastStepValue;
    boolean canGoNextValue;
    boolean firstStepValue;

    public DummyHost(String title, @Nullable Project project) {
      super(title, project);
      Reset();
      Disposer.register(myTestRootDisposable, getDisposable());
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

  @Before
  public void setUp() {
    myTestRootDisposable = Disposer.newDisposable("IdeaWizardAdapterTest");
  }

  @After
  public void disposeDialogs() {
    EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myTestRootDisposable));
  }

  @Test
  public void canGoForwardsAndBackwards() {
    AbstractWizard host = new DummyHost("None", null);

    DummyStep step1 = new DummyStep(new DummyModel(), "");
    DummyStep step2 = new DummyStep(new DummyModel(), "");
    ModelWizard guest = new ModelWizard.Builder(step1, step2).build();

    IdeaWizardAdapter adaptor = new IdeaWizardAdapter(host, guest);
    myInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isFalse();

    adaptor.doNextAction();
    myInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isTrue();

    adaptor.doPreviousAction();
    myInvokeStrategy.updateAllSteps();
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
    myInvokeStrategy.updateAllSteps();
    assertThat(guest.onLastStep().get()).isTrue();

    try {
      adaptor.doFinishAction();
    }
    catch (IllegalStateException e) {
      // TODO: can't mock out the close method on DialogWrapper as it is final, so this exception is generated. Is there a better way
      // to handle this?
      assertThat(e.getStackTrace()[0].getMethodName()).contains("ensureEventDispatchThread");
    }
    myInvokeStrategy.updateAllSteps();
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
    myInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isFalse();

    host.Reset();
    adaptor.doNextAction();
    myInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isFalse();
    assertThat(host.lastStepValue).isFalse();

    host.Reset();
    adaptor.doNextAction();
    myInvokeStrategy.updateAllSteps();
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
    myInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    host.Reset();
    step.goForwardValue.set(false);
    myInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isFalse();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    host.Reset();
    step.goForwardValue.set(true);
    myInvokeStrategy.updateAllSteps();
    assertThat(host.updateButtonsCalled).isTrue();
    assertThat(host.canGoNextValue).isTrue();
    assertThat(host.firstStepValue).isTrue();
    assertThat(host.lastStepValue).isTrue();

    Disposer.dispose(adaptor);
  }
}
