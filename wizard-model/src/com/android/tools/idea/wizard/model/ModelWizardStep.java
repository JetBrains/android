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

import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * A step is a single page in a wizard. It is responsible for creating a single pane of UI to
 * present to the user, determining if the information on the page is valid in order to progress,
 * and storing the user's data in the target model.
 */
public abstract class ModelWizardStep<M extends WizardModel> implements Disposable {
  @NotNull private final M myModel;
  @NotNull private final String myTitle;
  @Nullable private Icon myIcon;

  protected ModelWizardStep(@NotNull M model, @NotNull String title) {
    myModel = model;
    myTitle = title;
  }

  protected ModelWizardStep(@NotNull M model, @NotNull String title, @NotNull Icon icon) {
    myModel = model;
    myTitle = title;
    myIcon = icon;
  }

  @NotNull
  public final String getTitle() {
    return myTitle;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  /**
   * The step icon (likely shown somewhere in the step's header area). Ideally this should be
   * specified in the constructor, but this method is provided in case an implementing class
   * needs to change the icon on the fly.
   *
   * @param icon If null, no icon is displayed.
   */
  protected final void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  /**
   * Returns the UI component that represents this step.
   */
  @NotNull
  protected abstract JComponent getComponent();

  /**
   * Returns a component which should try and get focus when a step is first entered, or
   * {@code null} if we're OK just letting Swing choose for us. This will likely leave focus where
   * it is, which is probably the wizard's next button.
   */
  @Nullable
  protected JComponent getPreferredFocusComponent() {
    return null;
  }

  @NotNull
  protected final M getModel() {
    return myModel;
  }

  /**
   * When this step is added to a wizard, it is given a chance to add subsequent steps that it is
   * willing to take responsibility for. This is useful for steps which contain toggles that
   * enable/disable following steps.
   */
  @NotNull
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return ImmutableList.of();
  }

  /**
   * Called once, when the wizard first starts up but before any step is shown. This is a good
   * place to set up bindings and handle any UI initialization that needs to interact with the
   * parent wizard.
   * <p/>
   * Note: You should not store a reference to the wizard parameter. It's only meant to be used in
   * the context of this method.
   */
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
  }

  /**
   * Called once when a wizard is completed because the user hit "Finish" for every step, whether
   * it was visited or not.
   */
  public void onWizardFinished() {
  }

  /**
   * Returns {@code true} to indicate the that this step should be shown, or {@code false} if it
   * should be skipped.
   */
  protected boolean shouldShow() {
    return true;
  }

  /**
   * Returns an observable boolean, which when set to {@code true} means the current step is
   * complete and the user can move onto the next step.
   * <p/>
   * The return type is observable so as soon as you switch the value from {@code false} to
   * {@code true}, UI can automatically be notified through a binding.
   */
  @NotNull
  protected ObservableBool canGoForward() {
    return BooleanExpression.ALWAYS_TRUE;
  }

  /**
   * Returns a boolean, which when set to {@code true} means the current step can
   * go back to previous step.
   * <p/>
   * The return type is a primitive and NOT an observable (unlike {@link #canGoForward()}, because
   * it is only called once, shortly after {@link #onEntering()} is called. This method is meant
   * to be overridden by steps that serve as "no turning back" points in their wizard's flow.
   */
  protected boolean canGoBack() {
    return true;
  }

  /**
   * Called on the step just before it is shown. This method is not called when returning back to a
   * step. This is a good time to copy any relevant data out of the model to initialize the UI.
   */
  protected void onEntering() {
  }

  /**
   * Called just before a step is about to move to the next step (or, if this is the last step, to
   * the finished state). This is a good time to copy any relevant data out of the UI into a model.
   */
  protected void onProceeding() {
  }

  @Override
  public void dispose() {
  }

  /**
   * An extra action that should be shown along with the standard wizard navigation actions
   * (forward, back, finish, etc.). For example, in {@code ModelWizardDialog} this action is shown
   * as a button on the left side.
   *
   * @return The action to show. If {@code null} no extra action will be shown.
   */
  @Nullable
  protected Action getExtraAction() {
    return null;
  }

  private static final class BlankModel extends WizardModel {
    @Override
    protected void handleFinished() {
    }
  }

  /**
   * A base class for steps which don't need to store their state in any model, for example a step
   * that can act simply on data passed into its constructor.
   *
   * This should not be something you'll commonly do, and you should almost always prefer
   * inheriting from {@link ModelWizardStep} or consider using another solution entirely. For
   * example, it might be more appropriate to use a standard dialog instead of a ModelWizard with a
   * model-less step.
   *
   * If you do inherit from this, you should document why in a header comment.
   */
  public static abstract class WithoutModel extends ModelWizardStep<BlankModel> {
    protected WithoutModel(@NotNull String title) {
      super(new BlankModel(), title);
    }
  }
}
