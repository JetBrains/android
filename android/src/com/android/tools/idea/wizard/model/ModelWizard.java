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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A wizard that owns a series of {@link ModelWizardStep}s. When finished, it iterates through its
 * steps, queries the {@link WizardModel} they're associated with, and calls their
 * {@link WizardModel#handleFinished()} method.
 * <p/>
 * In this way, users of this framework can design steps which handle the UI logic while putting
 * all non-UI business logic in a data model class.
 *
 * To avoid memory leaks, you must dispose a wizard when you're done with it (although this may be
 * done for you by a wrapping class, such as a model wizard dialog).
 */
public final class ModelWizard implements Disposable {

  private final List<ModelWizardStep> mySteps;
  private final Facade myFacade = new Facade();

  /**
   * When we check if we should show a step, we also check the step's ancestor chain, and make sure
   * all of those should be shown as well. In this way, skipping a parent step automatically will
   * skip any child steps as well (recursively).
   */
  private final Map<ModelWizardStep, ModelWizardStep> myStepOwners = Maps.newHashMap();

  private final BindingsManager myBindings = new BindingsManager();
  private final BoolProperty myCanGoBack = new BoolValueProperty();
  private final BoolProperty myCanGoForward = new BoolValueProperty();
  private final BoolProperty myOnLastStep = new BoolValueProperty();
  private final OptionalProperty<Action> myExtraAction = new OptionalValueProperty<Action>();

  private final Stack<ModelWizardStep> myPrevSteps = new Stack<ModelWizardStep>();

  private final StringProperty myTitle = new StringValueProperty();
  private final JPanel myContentPanel = new JPanel(new CardLayout());

  private final List<ResultListener> myResultListeners = Lists.newArrayListWithExpectedSize(1);

  private int myCurrIndex = -1;

  /**
   * Construct a wizard with all of the steps it can potentially show (although some may be
   * hidden). If a step provides its own dependent steps, those, too, will be automatically added
   * at this time.
   * <p/>
   * A wizard, once constructed, is ready to go and will already be pointing at the first step. The
   * next expected action is for {@link #goForward()} to be called. Most likely, a wrapping UI
   * container, such as {@link ModelWizardDialog}, will be responsible for handling this.
   * <p/>
   * When the wizard is finished, it will iterate through its steps, in order, and run
   * {@link WizardModel#handleFinished()} on each of their associated models.
   * <p/>
   * Note: You don't use this constructor directly - instead, use {@link Builder#build()}.
   *
   * @throws IllegalArgumentException if {@code steps} is empty or none of the steps are visible.
   */
  private ModelWizard(@NotNull Collection<ModelWizardStep> steps) {
    mySteps = Lists.newArrayListWithExpectedSize(steps.size());
    for (ModelWizardStep step : steps) {
      addStep(step);
    }

    if (mySteps.isEmpty()) {
      throw new IllegalStateException("Can't create a wizard with no steps");
    }

    myCanGoForward.addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        if (myCanGoForward.get()) {
          // Make double sure that, when we switch from blocked to can proceed, we check that no
          // no future steps also became visible or hidden at some point. Otherwise, we might think
          // we're on the last step when we're not (or vice versa).
          myOnLastStep.set(isOnLastVisibleStep());
        }
      }
    });

    Set<WizardModel> seenModels = Sets.newHashSet();
    for (ModelWizardStep step : mySteps) {
      Disposer.register(this, step);

      WizardModel model = step.getModel();
      if (seenModels.contains(model)) {
        continue;
      }
      Disposer.register(this, model);
      seenModels.add(model);
    }

    // At this point, we're ready to go! Try to start the wizard, proceeding into the first step
    // if we can.

    for (ModelWizardStep step : mySteps) {
      step.onWizardStarting(myFacade);
    }

    boolean atLeastOneVisibleStep = false;
    for (ModelWizardStep step : mySteps) {
      if (shouldShowStep(step)) {
        atLeastOneVisibleStep = true;
        break;
      }
    }

    if (atLeastOneVisibleStep) {
      goForward(); // Proceed to first step
    }
    else {
      // Normally we'd leave it up to external code to dispose the wizard, but since we're throwing
      // an exception in the constructor, it means the caller won't be able to get a reference to
      // this wizard before the exception interrupts it. So we manually clean things up ourselves.
      Disposer.dispose(this);
      throw new IllegalStateException("Trying to create a wizard but no steps are visible");
    }
  }

  /**
   * Boolean property which is set to {@code true} when there's a previous step we can go back to.
   * <p/>
   * The return type is an observable boolean so a UI can bind a back button to its value.
   */
  @NotNull
  public ObservableBool canGoBack() {
    return myCanGoBack;
  }

  /**
   * Boolean property which is set to {@code true} when there's a step we can move forward to.
   * Note that this can be {@code true} even on the last page - it just indicates that the current
   * step is satisfied with the information it has.
   * <p/>
   * The return type is an observable boolean so a UI can bind a back button to its value.
   */
  @NotNull
  public ObservableBool canGoForward() {
    return myCanGoForward;
  }

  /**
   * Boolean property which is set to {@code true} when the wizard is on the last step.
   * <p/>
   * The return type is an observable boolean so a UI can bind a finish button to its value.
   */
  @NotNull
  public ObservableBool onLastStep() {
    return myOnLastStep;
  }

  /**
   * String property containing the active title of the current wizard on the current step.
   * <p/>
   * This class itself is not responsible for displaying the title; rather, this task is delegated
   * to an external UI.
   * <p/>
   * The return type is an observable string so a UI can bind a label to its value.
   */
  @NotNull
  public ObservableString title() {
    return myTitle;
  }

  /**
   * Returns the component on the current step which wants to have initial focus, if any, or
   * {@code null} otherwise.
   * <p/>
   * This is provided in case the container UI wants to know (as {@link DialogWrapper} does, for
   * example).
   */
  @Nullable
  public JComponent getPreferredFocusComponent() {
    if (isFinished()) {
      return null;
    }

    ModelWizardStep step = mySteps.get(myCurrIndex);
    return step.getPreferredFocusComponent();
  }

  /**
   * Populates the wizard with an additional step (and any dependent steps it may have).
   */
  private void addStep(@NotNull ModelWizardStep<?> step) {
    myContentPanel.add(step.getComponent(), Integer.toString(mySteps.size()));
    mySteps.add(step);

    for (ModelWizardStep subStep : step.createDependentSteps()) {
      myStepOwners.put(subStep, step);
      addStep(subStep);
    }
  }

  /**
   * Returns the currently active step.
   * <p/>
   * Calling this method after a wizard has finished should not be done and has undefined behavior.
   */
  @VisibleForTesting
  @NotNull
  ModelWizardStep getCurrentStep() {
    return mySteps.get(myCurrIndex);
  }

  /**
   * Returns an (optional) action to be shown in addition to the normal wizard actions (next, previous, etc.).
   * This action is provided by the current wizard step.
   */
  @NotNull
  ObservableOptional<Action> getExtraAction() { return myExtraAction; }

  /**
   * Returns the panel that will contain the UI for each step. It is up to an external UI class
   * to decorate this with buttons (next, prev, etc.), titles, icons, etc.
   */
  @NotNull
  public JPanel getContentPanel() {
    return myContentPanel;
  }

  /**
   * Moves the wizard to the next page. If we're currently on the last page, then this action
   * finishes the wizard.
   * <p/>
   * It is an error to call this on a wizard that has already finished.
   */
  public void goForward() {
    ensureWizardIsRunning();

    if (myCurrIndex >= 0) {
      ModelWizardStep currStep = mySteps.get(myCurrIndex);
      if (!currStep.canGoForward().get()) {
        throw new IllegalStateException("Can't call goForward on wizard when the step prevents it");
      }

      myPrevSteps.add(currStep);
      currStep.onProceeding();
    }

    while (true) {
      myCurrIndex++;
      if (myCurrIndex >= mySteps.size()) {
        handleFinished(true);
        break;
      }

      ModelWizardStep step = mySteps.get(myCurrIndex);
      if (shouldShowStep(step)) {
        updateNavigationProperties();
        step.onEntering();
        showCurrentStep();
        break;
      }
    }
  }

  /**
   * Returns the wizard back to the previous page.
   * <p/>
   * It is an error to call this if there are no previous pages to return to or on a wizard that's
   * already finished.
   */
  public void goBack() {
    ensureWizardIsRunning();

    if (myPrevSteps.empty()) {
      throw new IllegalStateException("Calling back on wizard without any previous pages");
    }

    ModelWizardStep currStep = mySteps.get(myCurrIndex);
    if (!currStep.canGoBack()) {
      throw new IllegalStateException("Can't call goBack on wizard when the step prevents it");
    }

    myCurrIndex = mySteps.indexOf(myPrevSteps.pop());
    updateNavigationProperties();
    showCurrentStep();
  }

  /**
   * Cancels the wizard, discarding all work done so far.
   * <p/>
   * It is an error to call this on a wizard that has already finished.
   */
  public void cancel() {
    ensureWizardIsRunning();

    handleFinished(false);
  }

  public void addResultListener(@NotNull ResultListener listener) {
    myResultListeners.add(listener);
  }

  public void removeResultListener(@NotNull ResultListener listener) {
    myResultListeners.remove(listener);
  }

  public boolean isFinished() {
    return myCurrIndex >= mySteps.size();
  }

  private void ensureWizardIsRunning() {
    if (isFinished()) {
      throw new IllegalStateException("Invalid operation attempted after wizard already finished");
    }
  }

  private void handleFinished(boolean success) {
    if (success) {
      Set<WizardModel> seenModels = Sets.newHashSet();
      for (ModelWizardStep step : myPrevSteps) {
        WizardModel model = step.getModel();
        if (seenModels.contains(model)) {
          continue;
        }
        seenModels.add(model);
        model.handleFinished();
      }
    }

    myCurrIndex = mySteps.size(); // Magic value indicates done. See: isFinished
    myPrevSteps.clear();
    myCanGoBack.set(false);
    myCanGoForward.set(false);
    myOnLastStep.set(false);

    // Make a copy of the event list, as a listener may attempt to remove their listener when this
    // is fired.
    List<ResultListener> listenersCopy = Lists.newArrayList(myResultListeners);
    for (ResultListener listener : listenersCopy) {
      listener.onWizardFinished(success);
    }
  }

  private void showCurrentStep() {
    ModelWizardStep step = mySteps.get(myCurrIndex);
    myTitle.set(step.getTitle());
    myExtraAction.setNullableValue(step.getExtraAction());
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, Integer.toString(myCurrIndex));

    JComponent focusedComponent = step.getPreferredFocusComponent();
    if (focusedComponent != null) {
      IdeFocusManager.findInstanceByComponent(focusedComponent).requestFocus(focusedComponent, false);
    }
  }

  /**
   * Update the navigational properties (next, prev, etc.) given the state of the current step.
   * This should only be called if you're already on a step.
   */
  private void updateNavigationProperties() {
    myOnLastStep.set(isOnLastVisibleStep());
    ModelWizardStep step = mySteps.get(myCurrIndex);
    myBindings.bind(myCanGoForward, step.canGoForward());
    myCanGoBack.set(!myPrevSteps.empty() && step.canGoBack());
  }

  private boolean shouldShowStep(ModelWizardStep step) {
    ModelWizardStep currStep = step;
    do {
      if (!currStep.shouldShow()) {
        return false;
      }

      currStep = myStepOwners.get(currStep);
    }
    while (currStep != null);

    return true;
  }

  private boolean isOnLastVisibleStep() {
    float size = mySteps.size();
    boolean currPageIsLast = true;
    for (int i = myCurrIndex + 1; i < size; i++) {
      ModelWizardStep step = mySteps.get(i);
      if (shouldShowStep(step)) {
        currPageIsLast = false;
        break;
      }
    }

    return currPageIsLast;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myResultListeners.clear();
  }

  /**
   * Listener interface which is fired when the wizard is either finished or canceled.
   */
  public interface ResultListener {
    void onWizardFinished(boolean success);
  }

  /**
   * In order to construct a wizard, you must do so through its builder. The builder collects steps
   * and, when the user is ready, can instantiate a new wizard which is already set to the first
   * step.
   */
  public static final class Builder {
    private final List<ModelWizardStep> mySteps;

    /**
     * Builder constructor which, for convenience, accepts some initial steps. You can also call
     * {@link #addStep(ModelWizardStep)} to add additional steps. Once all steps are added, you
     * should {@link #build()} the wizard.
     */
    public Builder(@NotNull ModelWizardStep... steps) {
      mySteps = Lists.newArrayList(steps);
    }

    public Builder addStep(@NotNull ModelWizardStep step) {
      mySteps.add(step);
      return this;
    }

    public ModelWizard build() {
      return new ModelWizard(mySteps);
    }
  }

  /**
   * Class to provide an interface providing some limited subset of wizard functionality, useful
   * to pass to steps so they can modify only the parts of the wizard that they should care about.
   */
  public final class Facade {

    /**
     * Update the properties driving next, back, and last page behavior. This is often handled
     * automatically, but a step may modify a model that will cause a later step to skip itself,
     * and there's no way the wizard can know that, so this method is provided as a way for a step
     * to manually trigger the update.
     */
    public void updateNavigationProperties() {
      if (myCurrIndex < 0) {
        return; // Protects against user calling this method in ModelWizardStep#onWizardStarting
      }
      ModelWizard.this.updateNavigationProperties();
    }

    /**
     * Allows the child step to move the wizard to the next step. If the wizard is on its last
     * step, then this action finishes the wizard.
     *
     * This should be used very sparingly, as normally you should encourage the user to navigate
     * the wizard via the UI and not do it directly. However, this can be useful if you have a
     * UI interaction where it's obvious that the user is making a clear choice and wants to move
     * forward with it, like double-clicking an item from a grid, etc.
     *
     * Because this class is passed to child steps before the wizard has even started, this method
     * will throw an exception if called too early. The step is expected to delay the call at least
     * until the wizard has started, such as on a button press or other UI event.
     *
     * @return {@code true} if the wizard moved forward, {@code false} if progress was blocked
     */
    public boolean goForward() {
      if (myCurrIndex < 0) {
        // Protects against user calling this method directly in ModelWizardStep#onWizardStarting
        throw new IllegalStateException("Attempting to goForward before the wizard has even started");
      }

      if (canGoForward().get()) {
        ModelWizard.this.goForward();
        return true;
      }
      else {
        return false;
      }
    }

    /**
     * Allows the child step to cancel the wizard. This should be used very sparingly, as normally
     * you should encourage the user to cancel the wizard via the UI and not do it directly.
     * However, this can be useful if you need to, say, close the wizard after some timeout passed,
     * or close it if you instead intend to finish the rest of the wizard's work on a background
     * task, etc.
     *
     * Because this class is passed to child steps before the wizard has even started, this method
     * will throw an exception if called too early. The step is expected to delay the call at least
     * until the wizard has started, such as on a button press or other UI event.
     */
    public void cancel() {
      if (myCurrIndex < 0) {
        // Protects against user calling this method directly in ModelWizardStep#onWizardStarting
        throw new IllegalStateException("Attempting to cancel before the wizard has even started");
      }
      ModelWizard.this.cancel();
    }
  }
}
