/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ModelWizardTest {

  @Test
  public void wizardCanProgressThroughAllStepsAsExpected() throws Exception {
    PersonModel personModel = new PersonModel();
    OccupationModel occupationModel = new OccupationModel();
    ModelWizard wizard = new ModelWizard();

    wizard.addStep(new NameStep(personModel, "John Doe"));
    wizard.addStep(new AgeStep(personModel, 25));
    wizard.addStep(new TitleStep(occupationModel, "Code Monkey"));

    assertThat(wizard.hasStarted()).isFalse();

    ListenableFuture<Boolean> result = wizard.start();
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance()); // Lets wizard properties update
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);
    assertThat(wizard.hasStarted()).isTrue();
    assertThat(wizard.canGoBack().get()).isFalse();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isFalse();

    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());  // Lets wizard properties update
    assertThat(wizard.canGoBack().get()).isTrue();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isFalse();

    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(TitleStep.class);
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());  // Lets wizard properties update
    assertThat(wizard.canGoBack().get()).isTrue();
    assertThat(wizard.canGoForward().get()).isTrue();
    assertThat(wizard.onLastStep().get()).isTrue();

    wizard.goForward();
    SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());  // Lets wizard properties update
    assertThat(wizard.isFinished()).isTrue();
    assertThat(wizard.canGoBack().get()).isFalse();
    assertThat(wizard.canGoForward().get()).isFalse();
    assertThat(wizard.onLastStep().get()).isFalse();

    assertThat(personModel.isFinished()).isTrue();
    assertThat(occupationModel.isFinished()).isTrue();

    assertThat(personModel.getName()).isEqualTo("John Doe");
    assertThat(personModel.getAge()).isEqualTo(25);
    assertThat(occupationModel.getTitle()).isEqualTo("Code Monkey");

    assertThat(result.get()).isTrue();
  }

  @Test
  public void wizardCanGoForwardAndBack() throws Exception {
    ModelWizard wizard = new ModelWizard();

    PersonModel personModel = new PersonModel();
    DummyModel dummyModel = new DummyModel();
    OccupationModel occupationModel = new OccupationModel();
    wizard.addStep(new NameStep(personModel, "John Doe"));
    wizard.addStep(new ShouldSkipStep(dummyModel));
    wizard.addStep(new AgeStep(personModel, 25));
    wizard.addStep(new TitleStep(occupationModel, "Code Monkey"));

    wizard.start();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);
    wizard.goForward(); // Skips skippable step
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    wizard.goForward();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(TitleStep.class);
    wizard.goBack(); // Skips skippable step
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(AgeStep.class);
    wizard.goBack();
    assertThat(wizard.getCurrentStep().getClass()).isEqualTo(NameStep.class);
  }

  @Test
  public void wizardCanBeCancelled() throws Exception {
    ModelWizard wizard = new ModelWizard();

    wizard.addStep(new DummyStep(new DummyModel()));

    ListenableFuture<Boolean> result = wizard.start();
    assertThat(wizard.isFinished()).isFalse();

    wizard.cancel();
    assertThat(wizard.isFinished()).isTrue();

    assertThat(result.get()).isFalse();
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

    // Add dummy model so we can add at least one step
    ModelWizard wizard = new ModelWizard(step1, step2, step3, step4);
    wizard.addStep(extraStep1);
    wizard.addStep(extraStep3);

    wizard.start();
    wizard.goForward(); // Step1
    wizard.goForward(); // Step2
    wizard.goForward(); // Step3
    wizard.goForward(); // Step4
    wizard.goForward(); // ExtraStep1
    wizard.goForward(); // ExtraStep3

    assertThat(finishList).containsExactly(step1.getModel(), step2.getModel(), step3.getModel(), step4.getModel());
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantAddStepAfterStarting() throws Exception {
    PersonModel personModel = new PersonModel();
    ModelWizard wizard = new ModelWizard(new NameStep(personModel, "Dummy Name"));
    wizard.start();

    wizard.addStep(new AgeStep(personModel, 25));
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantStartWithoutAnySteps() throws Exception {
    ModelWizard modelWizard = new ModelWizard();
    modelWizard.start();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantStartAfterAlreadyStarted() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));
    modelWizard.start();

    modelWizard.start();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoForwardBeforeStarting() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));

    modelWizard.goForward();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackBeforeStarting() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));

    modelWizard.goBack();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantCancelBeforeStarting() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));

    modelWizard.cancel();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoForwardAfterFinishing() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));
    modelWizard.start();
    modelWizard.goForward();

    assertThat(modelWizard.isFinished()).isTrue();
    modelWizard.goForward();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackAfterFinishing() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));
    modelWizard.start();
    modelWizard.goForward();

    assertThat(modelWizard.isFinished()).isTrue();
    modelWizard.goBack();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantCancelAfterFinishing() throws Exception {
    ModelWizard modelWizard = new ModelWizard(new DummyStep(new DummyModel()));
    modelWizard.start();
    modelWizard.goForward();

    assertThat(modelWizard.isFinished()).isTrue();
    modelWizard.cancel();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantGoBackIfNoPreviousSteps() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard modelWizard = new ModelWizard(new DummyStep(model), new DummyStep(model));
    modelWizard.start();
    modelWizard.goForward();
    modelWizard.goBack();

    modelWizard.goBack();
  }

  @Test
  public void wizardCanSkipOverSteps() throws Exception {
    DummyModel dummyModel = new DummyModel();
    ShouldSkipStep shouldSkipStep = new ShouldSkipStep(dummyModel);

    ModelWizard modelWizard = new ModelWizard();
    modelWizard.addStep(new DummyStep(dummyModel));
    modelWizard.addStep(shouldSkipStep);
    modelWizard.addStep(new DummyStep(dummyModel));

    modelWizard.start();
    modelWizard.goForward();
    modelWizard.goForward();

    assertThat(modelWizard.isFinished()).isTrue();
    assertThat(shouldSkipStep.isEntered()).isFalse();
  }

  @Test(expected = IllegalStateException.class)
  public void wizardCantContinueIfStepPreventsIt() throws Exception {
    DummyModel dummyModel = new DummyModel();
    ModelWizard modelWizard = new ModelWizard();
    modelWizard.addStep(new PreventProceedingStep(dummyModel));
    modelWizard.addStep(new DummyStep(dummyModel));
    modelWizard.start();
    modelWizard.goForward();
  }

  @Test
  public void stepCanCreateSubsteps() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard modelWizard = new ModelWizard();
    // Creates parent, which creates child
    modelWizard.addStep(new GrandparentStep(model));

    modelWizard.start();
    assertThat(modelWizard.getCurrentStep().getClass()).isEqualTo(GrandparentStep.class);
    modelWizard.goForward();
    assertThat(modelWizard.getCurrentStep().getClass()).isEqualTo(ParentStep.class);
    modelWizard.goForward();
    assertThat(modelWizard.getCurrentStep().getClass()).isEqualTo(ChildStep.class);
  }

  @Test
  public void hidingAStepHidesItsSubstepsRecursively() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard modelWizard = new ModelWizard();
    // Creates parent, which creates child
    GrandparentStep grandparentStep = new GrandparentStep(model);
    grandparentStep.setShouldSkip();

    // Add at least one visible step or the wizard will be fail to start
    modelWizard.addStep(new DummyStep(model));
    modelWizard.addStep(grandparentStep);

    modelWizard.start();
    assertThat(modelWizard.onLastStep().get()).isTrue();
  }

  @Test(expected = IllegalStateException.class)
  public void startingAWizardWithNoVisibleStepsThrowsException() throws Exception {
    DummyModel model = new DummyModel();
    ModelWizard modelWizard = new ModelWizard();
    // Creates parent, which creates child
    GrandparentStep grandparentStep = new GrandparentStep(model);
    grandparentStep.setShouldSkip();

    modelWizard.addStep(grandparentStep);
    modelWizard.start();
  }

  @Test
  public void finishedWizardsSkipModelsOfHiddenSteps() throws Exception {
    List<RecordFinishedModel> finishList = Lists.newArrayList();
    RecordFinishedModel recordModel = new RecordFinishedModel(finishList);
    RecordFinishedStep recordStep = new RecordFinishedStep(recordModel);
    recordStep.setShouldSkip();
    ModelWizard modelWizard = new ModelWizard(recordStep);
    modelWizard.addStep(new DummyStep(new DummyModel())); // Ensure we have at least one shown step

    modelWizard.start();
    modelWizard.goForward();

    assertThat(modelWizard.isFinished()).isTrue();

    assertThat(finishList).isEmpty();
  }

  @Test
  public void stepGetsDisposedWhenWizardGetsDisposed() throws Exception {
    DisposedStep disposedStep = new DisposedStep(new DummyModel());
    ModelWizard wizard = new ModelWizard(disposedStep);
    wizard.start();
    wizard.goForward();

    assertThat(wizard.isFinished()).isTrue();

    Disposer.dispose(wizard);

    assertThat(disposedStep.isDisposed()).isTrue();

  }

  private static class DummyModel extends WizardModel {
    @Override
    public void handleFinished() {
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
    protected void onEnter() {
      myEntered = true; // Should never get called!
    }

    public boolean isEntered() {
      return myEntered;
    }
  }

  private static class PreventProceedingStep extends NoUiStep<DummyModel> {
    public PreventProceedingStep(@NotNull DummyModel model) {
      super(model);
    }

    @NotNull
    @Override
    protected ObservableBool canProceed() {
      return BooleanExpressions.alwaysFalse();
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
    protected void onEnter() {
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
    protected void onEnter() {
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
    protected void onEnter() {
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
}