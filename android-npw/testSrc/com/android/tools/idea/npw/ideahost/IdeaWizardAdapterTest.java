/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

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
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunsInEdt;
import java.awt.Dimension;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class IdeaWizardAdapterTest {
  private Disposable myTestRootDisposable;

  private final TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Rule
  public BatchInvokerStrategyRule myStrategyRule = new BatchInvokerStrategyRule(myInvokeStrategy);

  @Rule
  public EdtRule edtRule = new EdtRule();

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
  public void hostDoesNotMeasureUnboundedContent() {
    var content = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        throw new AssertionError("The host must not measure the Compose content");
      }
    };
    var step = new SampleStep(new SampleModel()) {
      @Override
      protected JComponent getComponent() {
        return content;
      }
    };
    var adapter = new IdeaWizardAdapter(new SampleHost(), new ModelWizard.Builder(step).build());
    var proxyStep = adapter.getCustomOptionsStep();
    var component = proxyStep.getComponent();
    assertThat(component.isPreferredSizeSet()).isTrue();
    assertThat(component.getPreferredSize().width).isGreaterThan(0);
    assertThat(component.getPreferredSize().height).isGreaterThan(0);
    assertThat(proxyStep.getComponent()).isSameAs(component);
    assertThat(adapter.getCustomOptionsStep()).isNotSameAs(proxyStep);
  }

  @Test
  public void hostFollowsViewportAboveItsMinimumSize() {
    var adapter = new IdeaWizardAdapter(new SampleHost(), new ModelWizard.Builder(new SampleStep(new SampleModel())).build());
    var component = adapter.getCustomOptionsStep().getComponent();
    var viewport = new JViewport();
    viewport.setView(component);
    var size = new Dimension(component.getMinimumSize().width + 200, component.getMinimumSize().height + 200);
    viewport.setSize(size);
    viewport.doLayout();
    assertThat(component.getSize()).isEqualTo(size);
    assertThat(((Scrollable)component).getScrollableTracksViewportWidth()).isTrue();
    assertThat(((Scrollable)component).getScrollableTracksViewportHeight()).isTrue();
  }

  @Test
  public void hostKeepsItsMinimumSizeInSmallViewport() {
    var adapter = new IdeaWizardAdapter(new SampleHost(), new ModelWizard.Builder(new SampleStep(new SampleModel())).build());
    var component = adapter.getCustomOptionsStep().getComponent();
    var viewport = new JViewport();
    viewport.setView(component);
    viewport.setSize(100, 100);
    viewport.doLayout();
    assertThat(component.getSize()).isEqualTo(component.getMinimumSize());
    assertThat(((Scrollable)component).getScrollableTracksViewportWidth()).isFalse();
    assertThat(((Scrollable)component).getScrollableTracksViewportHeight()).isFalse();
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

    adaptor.doFinishAction();
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