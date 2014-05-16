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

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;

/**
 * A step in a wizard path.
 */
public abstract class DynamicWizardStep extends ScopedDataBinder implements Step {

  // Reference to the parent path.
  protected DynamicWizardPath myPath;


  // Used by update() to ensure multiple update steps are not run at the same time
  private boolean myUpdateInProgress;
  // used by update() to save whether this step is valid and the wizard can progress.
  private boolean myIsValid;

  public DynamicWizardStep() {
    myState = new ScopedStateStore(STEP, null, this);
  }

  /**
   * Attach this step to the given path, linking the state store.
   */
  public final void attachToPath(@NotNull DynamicWizardPath path) {
    myPath = path;
    Map<String, Object> myCurrentValues = myState.flatten();
    myState = new ScopedStateStore(STEP, myPath.getState(), this);
    for (String keyName : myCurrentValues.keySet()) {
      myState.put(myState.createKey(keyName, Object.class), myCurrentValues.get(keyName));
    }
    init();
  }

  /**
   * Set up this step. UI initialization should be done here.
   */
  public abstract void init();

  /**
   * Get the project context which this wizard is operating under.
   * If the this wizard is a global one, this function returns null.
   */
  @Nullable
  protected final Project getProject() {
    return myPath != null ? myPath.getProject() : null;
  }

  /**
   * Get the module context which this wizard is operating under.
   * If the this wizard is a global one or project-scoped, the function returns null.
   */
  @Nullable
  protected final Module getModule() {
    return myPath != null ? myPath.getModule() : null;
  }

  /**
   * Optionally add an icon to the left side of the screen.
   * @return An icon to be displayed on the left side of the wizard.
   */
  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  /**
   * Legacy compatibility
   */
  @Deprecated
  @Override
  public final void _init() {
    onEnterStep();
  }

  /**
   * Legacy compatibility
   */
  @Deprecated
  @Override
  public final void _commit(boolean finishChosen) throws CommitStepException {
    commitStep();
  }

  /**
   * Called when the step is entered.
   * The step should do any initialization/update work here to prepare for user interaction.
   */
  public void onEnterStep() {
    invokeUpdate(null);
  }

  /**
   * Called when the user tries to advance to the next step.
   * Any data commitment or actions taken by the step should be declared in this function.
   * @return true if the step was committed successfully and the wizard can progress. false if the
   * wizard should remain on this step.
   */
  public boolean commitStep() {
    return true;
  }

  /**
   * Determine whether this step is visible to the user. Visibility is updated automatically whenever
   * a parameter changes.
   * @return true if this step should be visible to the user. false otherwise.
   */
  public boolean isStepVisible() {
    return true;
  }


  /**
   * Updating: Whenever the user updates the value of a UI element, the update() function is called.
   * Each update cycle consists of three steps:
   * 1) updateModel (updates the model state to match the UI state)
   * 2) deriveValues (updates values that depend on other values)
   * 3) validate (checks the given input for correctness and consistency)
   */

  /**
   * update this step. Will invoke the parent path's update function if the
   * scope is PATH or WIZARD. Should generally not be overridden.
   */
  @Override
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    super.invokeUpdate(changedKey);
    update();
    if (myPath != null) {
      myPath.updateButtons();
    }
  }

  /**
   * Call the three update steps in order. Will not fire if an update is already in progress.
   */
  private void update() {
    if (!myUpdateInProgress) {
      myUpdateInProgress = true;
      updateModelFromUI();
      deriveValues(myState.getRecentUpdates());
      myIsValid = validate();
      myState.clearRecentUpdates();
      myUpdateInProgress = false;
    }
  }

  /**
   * If a UI element is registered against a key/scope pair, the listener for that UI element will
   * automatically update the model state every time the value is changed. If additional work is
   * necessary for pulling values from the UI and inserting them into the model it may be done here.
   * Most subclasses should not have a reason to override this part of the cycle.
   */
  public void updateModelFromUI() {

  }

  /**
   * The second step in the update cycle. Takes the list of changed variables and uses them to recalculate any variables
   * which may depend on those changed values. Alternatively, a {@link ScopedDataBinder.ValueDeriver} may be registered
   * which will be called to update the value associated with a single key.
   * @param modified set of the changed keys
   */
  public void deriveValues(Set<Key> modified) {

  }

  /**
   * Third step in the update cycle.
   * Validate the current input and return true if the current step is in a good state and the wizard can continue.
   * This function is automatically called whenever the user changes the value of a UI element.
   * Most subclasses will want to override this function and add custom validation.
   * @return true if the current input is complete and consistent and the wizard can continue.
   */
  public boolean validate() {
    return true;
  }

  /**
   * Called indirectly on every update by the wizard's updateButtons method.
   * Subclasses should rarely need to override this method. It is preferred
   * that subclasses override validate() and rely on the update method to set the value
   * used by this determination.
   * @return true if the user can progress to the next step in this path.
   */
  public boolean canGoNext() {
    return myIsValid;
  }

  /**
   * Called indirectly on every update by the wizard's updateButtons method.
   * Subclasses should rarely need to override this method.
   * @return true if the user should be allowed to go back through this path.
   */
  public boolean canGoPrevious() {
    return true;
  }

  /**
   * Converts the given text to an HTML message if necessary, and then displays it to the user.
   * @param errorMessage the message to display
   */
  public final void setErrorHtml(@Nullable String errorMessage) {
    if (errorMessage != null && !errorMessage.startsWith("<html>")) {
      errorMessage = "<html>" + errorMessage + "</html>";
    }
    getMessageLabel().setText(errorMessage);
  }

  @Override
  public String toString() {
    return getStepName();
  }

  /**
   * Retrieve the UI for this step. UI initialization and construction should NOT be
   * done in this function. This function should only return an already created and
   * initialized component.
   * @return A container which contains all user interface elements for this step.
   */
  @Override
  @NotNull
  public abstract JComponent getComponent();

  /**
   * Must return a label that can be used to display messages to users.
   * @return a JLabel (or descendent) used to display errors and information to the user.
   */
  @NotNull
  public abstract JLabel getMessageLabel();

  /**
   * @return the name of the current step to be displayed to the user
   */
  @NotNull
  public abstract String getStepName();
}
