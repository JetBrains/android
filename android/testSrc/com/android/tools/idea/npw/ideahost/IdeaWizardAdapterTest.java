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

  private final TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Rule
  public BatchInvokerStrategyRule myStrategyRule = new BatchInvokerStrategyRule(myInvokeStrategy);

  private static class SampleModel extends WizardModel {
    boolean isFinished;

    @Override
    protected void handleFinished() {
      isFinished = true;
    }
  }

  private static class SampleStep extends ModelWizardStep<SampleModel> {

    public final BoolValueProperty goForwardValue = new BoolValueProperty(true);

    protected SampleStep(@NotNull IdeaWizardAdapterTest.SampleModel model) {
      super(model, "Step title");
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

  private class SampleHost extends AbstractWizard<Step> {
    boolean updateButtonsCalled;
    boolean lastStepValue;
    boolean canGoNextValue;
    boolean firstStepValue;

    private SampleHost() {
      super("Host title", (Project) null);
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
    AbstractWizard<Step> host = new SampleHost();

    SampleStep step1 = new SampleStep(new SampleModel());
    SampleStep step2 = new SampleStep(new SampleModel());
    ModelWizard guest = new ModelWizard.Builder(step1, step2).build();

    IdeaWizardDelegate adaptor = new IdeaWizardAdapter(host, guest);
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
    AbstractWizard<Step> host = new SampleHost();

    SampleModel model = new SampleModel();
    SampleStep step1 = new SampleStep(model);
    ModelWizard guest = new ModelWizard.Builder(step1).build();

    IdeaWizardDelegate adaptor = new IdeaWizardAdapter(host, guest);
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
    SampleHost host = new SampleHost();

    SampleStep step1 = new SampleStep(new SampleModel());
    SampleStep step2 = new SampleStep(new SampleModel());
    SampleStep step3 = new SampleStep(new SampleModel());
    ModelWizard guest = new ModelWizard.Builder(step1, step2, step3).build();

    IdeaWizardDelegate adaptor = new IdeaWizardAdapter(host, guest);
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
  public void updatesButtonsOnInput() {
    SampleHost host = new SampleHost();
    SampleStep step = new SampleStep(new SampleModel());
    ModelWizard guest = new ModelWizard.Builder(step).build();

    IdeaWizardDelegate adaptor = new IdeaWizardAdapter(host, guest);
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
