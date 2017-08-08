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
package com.android.tools.idea.wizard.model;

import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.bool.BooleanExpressions;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class ModelWizardTest {

  private static TestInvokeStrategy ourInvokeStrategy;

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
  public void wizardCanProgressThroughAllStepsAsExpected() throws Exception {
    PersonModel personModel = new PersonModel();
    OccupationModel occupationModel = new OccupationModel();
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();

    wizardBuilder.addStep(new NameStep(personModel, "John Doe"));
    wizardBuilder.addStep(new AgeStep(personModel, 25));
    wizardBuilder.addStep(new TitleStep(occupationModel, "Code Monkey"));

    ModelWizard wizard = wizardBuilder.build();
    final ModelWizard.WizardResult[] wizardResult = new ModelWizard.WizardResult[1];
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
        wizardResult[0] = result;
      }
    });

    ourInvokeStrategy.updateAllSteps();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);
    assertThat(wizard.canGoBack().get()).isFalse();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isFalse();

    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    ourInvokeStrategy.updateAllSteps();
    assertThat(wizard.canGoBack().get()).isTrue();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isFalse();

    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(TitleStep.class);
    ourInvokeStrategy.updateAllSteps();
    assertThat(wizard.canGoBack().get()).isTrue();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isTrue();

    assertThat(wizardResult[0]).isNull();
    wizard.goForward();
    ourInvokeStrategy.updateAllSteps();

    assertThat(wizard.isFinished()).isTrue();
    assertThat(wizard.canGoBack().get()).isFalse();
    assertThat(wizard.canGoForward().get()).isFalse();
    assertThat(wizard.onLastStep().get()).isFalse();

    assertThat(personModel.isFinished()).isTrue();
    assertThat(occupationModel.isFinished()).isTrue();

    assertThat(personModel.getName()).isEqualTo("John Doe");
    assertThat(personModel.getAge()).isEqualTo(25);
    assertThat(occupationModel.getTitle()).isEqualTo("Code Monkey");

    assertThat(wizardResult[0].isFinished()).isTrue();

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardCanGoForwardAndBack() throws Exception {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();

    PersonModel personModel = new PersonModel();
    DummyModel dummyModel = new DummyModel();
    OccupationModel occupationModel = new OccupationModel();
    wizardBuilder.addStep(new NameStep(personModel, "John Doe"));
    wizardBuilder.addStep(new ShouldSkipStep(dummyModel));
    wizardBuilder.addStep(new AgeStep(personModel, 25));
    wizardBuilder.addStep(new TitleStep(occupationModel, "Code Monkey"));

    ModelWizard wizard = wizardBuilder.build();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);
    wizard.goForward(); // Skips skippable step
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(TitleStep.class);
    wizard.goBack(); // Skips skippable step
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    wizard.goBack();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardCanBeCancelled() throws Exception {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();

    DummyModel model = new DummyModel();
    wizardBuilder.addStep(new DummyStep(model));

    ModelWizard wizard = wizardBuilder.build();
    final ModelWizard.WizardResult[] wizardResult = new ModelWizard.WizardResult[1];
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
        wizardResult[0] = result;
      }
    });

    assertThat(wizard.isFinished()).isFalse();
    assertThat(wizardResult[0]).isNull();

    wizard.cancel();
    assertThat(wizard.isFinished()).isTrue();
    assertThat(model.myIsFinished).isFalse(); // Models are not finished when cancelled
    assertThat(wizardResult[0].isFinished()).isFalse();

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardRunsFinishOnModelsInOrder() throws Exception {
    List<RecordFinishedModel> finishList = Lists.newArrayList();
    RecordFinishedStep step1 = new RecordFinishedStep(new RecordFinishedModel(finishList));
    RecordFinishedStep step2 = new RecordFinishedStep(new RecordFinishedModel(finishList));
    RecordFinishedStep step3 = new RecordFinishedStep(new RecordFinishedModel(finishList));
    RecordFinishedStep step4 = new RecordFinishedStep(new RecordFinishedModel(finishList));
    RecordFinishedStep extraStep1 = new RecordFinishedStep(step1.getModel());
    RecordFinishedStep extraStep3 = new RecordFinishedStep(step3.getModel());

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(step1, step2, step3, step4);
    wizardBuilder.addStep(extraStep1);
    wizardBuilder.addStep(extraStep3);

    ModelWizard wizard = wizardBuilder.build();
    wizard.goForward(); // Step1
    wizard.goForward(); // Step2
    wizard.goForward(); // Step3
    wizard.goForward(); // Step4
    wizard.goForward(); // ExtraStep1
    wizard.goForward(); // ExtraStep3

    assertThat(finishList).containsExactly(step1.getModel(), step2.getModel(), step3.getModel(), step4.getModel());

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardInformsModelsTheyWereSkipped() throws Exception {
    DummyModel modelFinished1 = new DummyModel();
    DummyModel modelFinished2 = new DummyModel();
    DummyModel modelFinished3 = new DummyModel();
    DummyModel modelSkipped1 = new DummyModel();
    DummyModel modelSkipped2 = new DummyModel();

    DummyStep step1 = new DummyStep(modelFinished1);
    ShouldSkipStep step2 = new ShouldSkipStep(modelSkipped1);
    DummyStep step3 = new DummyStep(modelFinished2);
    ShouldSkipStep step4 = new ShouldSkipStep(modelSkipped2);
    ShouldSkipStep step5 = new ShouldSkipStep(modelSkipped2);

    // If a model is associated with two steps, one which is skipped and one which isn't, it will
    // still be marked as finished.
    ShouldSkipStep step6 = new ShouldSkipStep(modelFinished3);
    DummyStep step7 = new DummyStep(modelFinished3);

    ModelWizard wizard = new ModelWizard.Builder(step1, step2, step3, step4, step5, step6, step7).build();

    wizard.goForward(); // Step1
    // Step 2 skipped
    wizard.goForward(); // Step3
    // Step 4 - 6 skipped
    wizard.goForward(); // Step6

    assertThat(wizard.isFinished()).isTrue();
    assertThat(modelFinished1.myIsFinished).isTrue();
    assertThat(modelFinished1.myIsSkipped).isFalse();
    assertThat(modelFinished2.myIsFinished).isTrue();
    assertThat(modelFinished2.myIsSkipped).isFalse();
    assertThat(modelFinished3.myIsFinished).isTrue();
    assertThat(modelFinished3.myIsSkipped).isFalse();

    assertThat(modelSkipped1.myIsSkipped).isTrue();
    assertThat(modelSkipped1.myIsFinished).isFalse();
    assertThat(modelSkipped2.myIsSkipped).isTrue();
    assertThat(modelSkipped2.myIsFinished).isFalse();

    Disposer.dispose(wizard);
  }

  @Test(expected = IllegalStateException.class)
  public void cantCreateWizardWithoutSteps() throws Exception {
    new ModelWizard.Builder().build();
  }

  @Test(expected = IllegalStateException.class)
  public void cantCreateWizardWithoutAtLeastOneVisibleStep() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    // Creates parent, which creates child
    GrandparentStep grandparentStep = new GrandparentStep(model);
    grandparentStep.setShouldSkip();

    wizardBuilder.addStep(grandparentStep);
    wizardBuilder.build();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoForwardAfterFinishing() throws Exception {
    ModelWizard wizard = new ModelWizard.Builder(new DummyStep(new DummyModel())).build();
    try {
      wizard.goForward();
      assertThat(wizard.isFinished()).isTrue();
      wizard.goForward();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackAfterFinishing() throws Exception {
    ModelWizard wizard = new ModelWizard.Builder(new DummyStep(new DummyModel())).build();
    try {
      wizard.goForward();
      assertThat(wizard.isFinished()).isTrue();
      wizard.goBack();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantCancelAfterFinishing() throws Exception {
    ModelWizard wizard = new ModelWizard.Builder(new DummyStep(new DummyModel())).build();
    try {
      wizard.goForward();
      assertThat(wizard.isFinished()).isTrue();
      wizard.cancel();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackIfNoPreviousSteps() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard wizard = new ModelWizard.Builder(new DummyStep(model), new DummyStep(model)).build();
    try {
      wizard.goForward();
      wizard.goBack();
      wizard.goBack();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test
  public void wizardCanSkipOverSteps() throws Exception {
    DummyModel dummyModel = new DummyModel();
    ShouldSkipStep shouldSkipStep = new ShouldSkipStep(dummyModel);

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new DummyStep(dummyModel));
    wizardBuilder.addStep(shouldSkipStep);
    wizardBuilder.addStep(new DummyStep(dummyModel));

    ModelWizard wizard = wizardBuilder.build();
    wizard.goForward();
    wizard.goForward();

    assertThat(wizard.isFinished()).isTrue();
    assertThat(shouldSkipStep.isEntered()).isFalse();

    Disposer.dispose(wizard);
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantContinueIfStepPreventsIt() throws Exception {
    DummyModel dummyModel = new DummyModel();

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new PreventNavigatingForwardStep(dummyModel));
    wizardBuilder.addStep(new DummyStep(dummyModel));

    ModelWizard wizard = wizardBuilder.build();
    try {
      wizard.goForward();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackIfStepPreventsIt() throws Exception {
    DummyModel dummyModel = new DummyModel();

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new DummyStep(dummyModel));
    wizardBuilder.addStep(new PreventNavigatingBackwardStep(dummyModel));

    ModelWizard wizard = wizardBuilder.build();
    try {
      wizard.goForward();
      wizard.goBack();
    }
    finally {
      Disposer.dispose(wizard);
    }
  }

  @Test
  public void stepCanCreateSubsteps() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    // Creates parent, which creates child
    wizardBuilder.addStep(new GrandparentStep(model));

    ModelWizard wizard = wizardBuilder.build();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(GrandparentStep.class);
    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(ParentStep.class);
    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(ChildStep.class);

    Disposer.dispose(wizard);
  }

  @Test
  public void hidingAStepHidesItsSubstepsRecursively() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    // Creates parent, which creates child
    GrandparentStep grandparentStep = new GrandparentStep(model);
    grandparentStep.setShouldSkip();

    // Add at least one visible step or the wizard will be fail to start
    wizardBuilder.addStep(new DummyStep(model));
    wizardBuilder.addStep(grandparentStep);

    ModelWizard wizard = wizardBuilder.build();
    assertThat(wizard.onLastStep().get()).isTrue();

    Disposer.dispose(wizard);
  }

  @Test
  public void finishedWizardsSkipModelsOfHiddenSteps() throws Exception {
    List<RecordFinishedModel> finishList = Lists.newArrayList();
    RecordFinishedModel recordModel = new RecordFinishedModel(finishList);
    RecordFinishedStep recordStep = new RecordFinishedStep(recordModel);
    recordStep.setShouldSkip();

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(recordStep);
    wizardBuilder.addStep(new DummyStep(new DummyModel())); // Ensure we have at least one shown step

    ModelWizard wizard = wizardBuilder.build();
    wizard.goForward();

    assertThat(wizard.isFinished()).isTrue();

    assertThat(finishList).isEmpty();

    Disposer.dispose(wizard);
  }

  @Test
  public void stepGetsDisposedWhenWizardGetsDisposed() throws Exception {
    DisposedStep disposedStep = new DisposedStep(new DummyModel());

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(disposedStep);

    ModelWizard wizard = wizardBuilder.build();
    wizard.goForward();

    assertThat(wizard.isFinished()).isTrue();

    assertThat(disposedStep.isDisposed()).isFalse();
    Disposer.dispose(wizard);
    assertThat(disposedStep.isDisposed()).isTrue();
  }

  @Test
  public void allModelsGetDisposedWhenWizardGetsDisposed() throws Exception {
    DummyModel modelA = new DummyModel();
    DummyModel modelB = new DummyModel();
    DummyModel modelC = new DummyModel();

    DummyStep step1A = new DummyStep(modelA);
    DummyStep step2A = new DummyStep(modelA);
    ShouldSkipStep step3B = new ShouldSkipStep(modelB);
    ShouldSkipStep step4C = new ShouldSkipStep(modelC);
    DummyStep step5C = new DummyStep(modelC);

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(step1A, step2A, step3B, step4C, step5C);
    ModelWizard wizard = wizardBuilder.build();
    wizard.goForward();
    wizard.goForward();
    wizard.goForward();

    assertThat(wizard.isFinished()).isTrue();

    assertThat(modelA.myIsDisposed).isFalse();
    assertThat(modelB.myIsDisposed).isFalse();
    assertThat(modelC.myIsDisposed).isFalse();
    Disposer.dispose(wizard);
    assertThat(modelA.myIsDisposed).isTrue();
    assertThat(modelB.myIsDisposed).isTrue();
    assertThat(modelC.myIsDisposed).isTrue();
  }

  @Test
  public void wizardCanRecoverFromOnProceedingThrowingException() throws Exception {
    DummyModel modelA = new DummyModel();
    DummyModel modelB = new DummyModel();
    DummyModel modelC = new DummyModel();

    DummyStep step1 = new DummyStep(modelA);
    ExceptionThrowingStep step2 = new ExceptionThrowingStep(modelB).throwOnProceeding(true);
    DummyStep step3 = new DummyStep(modelC);

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(step1, step2, step3);
    ModelWizard wizard = wizardBuilder.build();

    final ModelWizardStep<?>[] badStep = {null};
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardAdvanceError(@NotNull Exception e) {
        badStep[0] = ((FakeStepException)e).getStep();
      }
    });

    wizard.goForward();
    assertThat(badStep[0]).isNull();
    try {
      wizard.goForward();
      fail(); // Guarantees that a exception is thrown, as expected.
    }
    catch (FakeStepException ignored) {
    }

    assertThat(badStep[0]).isEqualTo(step2);
    assertThat(wizard.getCurrentStep()).isEqualTo(step2);

    step2.throwOnProceeding(false);
    wizard.goForward();
    assertThat(wizard.getCurrentStep()).isEqualTo(step3);

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardCanRecoverFromOnEnteringThrowingException() throws Exception {
    DummyModel modelA = new DummyModel();
    DummyModel modelB = new DummyModel();
    DummyModel modelC = new DummyModel();

    DummyStep step1 = new DummyStep(modelA);
    ExceptionThrowingStep step2 = new ExceptionThrowingStep(modelB).throwOnEntering(true);
    DummyStep step3 = new DummyStep(modelC);

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(step1, step2, step3);
    ModelWizard wizard = wizardBuilder.build();

    final ModelWizardStep<?>[] badStep = {null};
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardAdvanceError(@NotNull Exception e) {
        badStep[0] = ((FakeStepException)e).getStep();
      }
    });

    try {
      wizard.goForward();
      fail(); // Guarantees that a exception is thrown, as expected.
    }
    catch (FakeStepException ignored) {
    }

    assertThat(badStep[0]).isEqualTo(step2);
    assertThat(wizard.getCurrentStep()).isEqualTo(step1);

    step2.throwOnEntering(false);
    wizard.goForward();
    assertThat(wizard.getCurrentStep()).isEqualTo(step2);

    Disposer.dispose(wizard);
  }

  @Test
  public void wizardStaysClosedEvenIfModelThrowsException() throws Exception {
    DummyModel modelA = new DummyModel();
    DummyExceptionModel modelB = new DummyExceptionModel();
    DummyModel modelC = new DummyModel();

    DummyStep step1 = new DummyStep(modelA);
    DummyStep step2 = new DummyStep(modelB);
    DummyStep step3 = new DummyStep(modelC);

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder(step1, step2, step3);
    ModelWizard wizard = wizardBuilder.build();
    final boolean[] onFinished = {false};
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
        onFinished[0] = true;
      }
    });

    wizard.goForward();
    wizard.goForward();

    try {
      wizard.goForward();
      fail();
    }
    catch (FakeModelException e) {
      assertThat(e.getModel()).isEqualTo(modelB);
    }

    assertThat(onFinished[0]).isTrue();

    Disposer.dispose(wizard);
  }

  private static class DummyModel extends WizardModel {
    private boolean myIsFinished;
    private boolean myIsSkipped;
    private boolean myIsDisposed;

    @Override
    public void handleFinished() {
      assertThat(myIsFinished).isFalse(); // This is only ever called once
      assertThat(myIsSkipped).isFalse(); // Skipped and finished are mutually exclusive
      myIsFinished = true;
    }

    @Override
    protected void handleSkipped() {
      assertThat(myIsSkipped).isFalse(); // This is only ever called once
      assertThat(myIsFinished).isFalse(); // Skipped and finished are mutually exclusive
      myIsSkipped = true;
    }

    @Override
    public void dispose() {
      myIsDisposed = true;
    }
  }

  private static class FakeModelException extends RuntimeException {
    private final WizardModel myModel;

    public FakeModelException(WizardModel model) {
      myModel = model; }

    public WizardModel getModel() {
      return myModel;
    }
  }


  /**
   * Extends {@link DummyModel} so it can be used with {@link DummyStep}
   */
  private static class DummyExceptionModel extends DummyModel {
    @Override
    public void handleFinished() {
      super.handleFinished();
      throw new FakeModelException(this);
    }
  }

  /**
   * None of these tests show UI so we just stub out the UI related methods in this helper base
   * class.
   */
  private static abstract class NoUiStep<M extends WizardModel> extends ModelWizardStep<M> {
    public NoUiStep(@NotNull M model) {
      super(model, "");
    }

    @NotNull
    @Override
    protected final JComponent getComponent() {
      return new JPanel();
    }
  }

  private static class DummyStep extends NoUiStep<DummyModel> {
    public DummyStep(@NotNull DummyModel model) {
      super(model);
    }
  }

  private static class ShouldSkipStep extends NoUiStep<DummyModel> {
    private boolean myEntered;

    public ShouldSkipStep(@NotNull DummyModel model) {
      super(model);
    }

    @Override
    protected boolean shouldShow() {
      return false;
    }

    @Override
    protected void onEntering() {
      myEntered = true; // Should never get called!
    }

    public boolean isEntered() {
      return myEntered;
    }
  }

  private static class PreventNavigatingForwardStep extends NoUiStep<DummyModel> {
    public PreventNavigatingForwardStep(@NotNull DummyModel model) {
      super(model);
    }

    @NotNull
    @Override
    protected ObservableBool canGoForward() {
      return BooleanExpressions.alwaysFalse();
    }
  }

  private static class PreventNavigatingBackwardStep extends NoUiStep<DummyModel> {
    public PreventNavigatingBackwardStep(@NotNull DummyModel model) {
      super(model);
    }

    @Override
    protected boolean canGoBack() {
      return false;
    }
  }

  private static class DisposedStep extends NoUiStep<DummyModel> {
    private boolean myDisposed;

    public DisposedStep(@NotNull DummyModel model) {
      super(model);
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }

    public boolean isDisposed() {
      return myDisposed;
    }
  }

  private static class RecordFinishedModel extends WizardModel {
    private final List<RecordFinishedModel> myRecordInto;

    public RecordFinishedModel(List<RecordFinishedModel> recordInto) {
      myRecordInto = recordInto;
    }

    @Override
    public void handleFinished() {
      myRecordInto.add(this);
    }
  }

  private static class RecordFinishedStep extends NoUiStep<RecordFinishedModel> {
    private boolean myShouldShow = true;

    public RecordFinishedStep(@NotNull RecordFinishedModel model) {
      super(model);
    }

    @Override
    protected boolean shouldShow() {
      return myShouldShow;
    }

    public void setShouldSkip() {
      myShouldShow = false;
    }
  }

  private static class PersonModel extends WizardModel {
    private String myName;
    private int myAge;
    private boolean myIsFinished;

    public int getAge() {
      return myAge;
    }

    public void setAge(int age) {
      myAge = age;
    }

    public String getName() {
      return myName;
    }

    public void setName(String name) {
      myName = name;
    }

    public boolean isFinished() {
      return myIsFinished;
    }

    @Override
    public void handleFinished() {
      myIsFinished = true;
    }
  }

  private static class OccupationModel extends WizardModel {
    private String myTitle;
    private boolean myIsFinished;

    public String getTitle() {
      return myTitle;
    }

    public void setTitle(String title) {
      myTitle = title;
    }

    public boolean isFinished() {
      return myIsFinished;
    }

    @Override
    public void handleFinished() {
      myIsFinished = true;
    }
  }

  private static class NameStep extends NoUiStep<PersonModel> {
    private final String myName;

    public NameStep(PersonModel model, String name) {
      super(model);
      myName = name; // Normally, this would be set in some UI, but this is just a test
    }

    @Override
    protected void onEntering() {
      getModel().setName(myName);
    }
  }

  private static class AgeStep extends NoUiStep<PersonModel> {
    private final int myAge;

    public AgeStep(PersonModel model, int age) {
      super(model);
      myAge = age; // Normally, this would be set in some UI, but this is just a test
    }

    @Override
    protected void onEntering() {
      getModel().setAge(myAge);
    }
  }

  private static class TitleStep extends NoUiStep<OccupationModel> {
    private final String myTitle;

    public TitleStep(OccupationModel model, String title) {
      super(model);
      myTitle = title; // Normally, this would be set in some UI, but this is just a test
    }

    @Override
    protected void onEntering() {
      getModel().setTitle(myTitle);
    }
  }

  private static class ChildStep extends NoUiStep<DummyModel> {
    protected ChildStep(@NotNull DummyModel model) {
      super(model);
    }
  }

  private static class ParentStep extends NoUiStep<DummyModel> {
    protected ParentStep(@NotNull DummyModel model) {
      super(model);
    }

    @NotNull
    @Override
    protected Collection<? extends ModelWizardStep> createDependentSteps() {
      return Collections.singletonList(new ChildStep(getModel()));
    }
  }

  private static class GrandparentStep extends NoUiStep<DummyModel> {
    @Nullable private List<ParentStep> myParentSteps;
    private boolean myShouldShow = true;

    protected GrandparentStep(@NotNull DummyModel model) {
      super(model);
    }

    @NotNull
    @Override
    protected Collection<? extends ModelWizardStep> createDependentSteps() {
      myParentSteps = Collections.singletonList(new ParentStep(getModel()));
      return myParentSteps;
    }

    @Override
    protected boolean shouldShow() {
      return myShouldShow;
    }

    public void setShouldSkip() {
      myShouldShow = false;
    }
  }

  private static class FakeStepException extends RuntimeException {
    private final ModelWizardStep<?> myStep;

    public FakeStepException(ModelWizardStep<?> step) {
      myStep = step;
    }

    public ModelWizardStep<?> getStep() {
      return myStep;
    }
  }

  private static class ExceptionThrowingStep extends NoUiStep<DummyModel> {
    private boolean myThrowOnProceeding;
    private boolean myThrowOnEntering;

    protected ExceptionThrowingStep(@NotNull DummyModel model) {
      super(model);
    }

    public ExceptionThrowingStep throwOnProceeding(boolean throwOnProceeding) {
      myThrowOnProceeding = throwOnProceeding;
      return this;
    }

    public ExceptionThrowingStep throwOnEntering(boolean throwOnEntering) {
      myThrowOnEntering = throwOnEntering;
      return this;
    }

    @Override
    protected void onProceeding() {
      if (myThrowOnProceeding) {
        throw new FakeStepException(this);
      }
    }

    @Override
    protected void onEntering() {
      if (myThrowOnEntering) {
        throw new FakeStepException(this);
      }
    }
  }
}