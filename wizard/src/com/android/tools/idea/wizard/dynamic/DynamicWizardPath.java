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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * DynamicWizardPath
 * A DynamicWizardPath is the modular portion of the workflow. It is responsible for maintaining a list of steps and for
 * advancing through them. A path must provide a name, and a function which executes any actions that should take place when
 * the path has finished. Additionally, each path can override {@link #isPathVisible()} to set whether the path is shown to the user,
 * and {@link #isPathRequired()} to signify whether the path is optional. All optional paths should be placed at the end of a
 * wizard and the wizard will signify that it can finish if the only paths left in its workflow are optional ones.

 * Each path consists of a linear progression of
 * steps which may be hidden or shown individually. The path itself can be hidden or shown as a whole.
 * Paths are meant to represent sequential portions of a wizard flow as well as branches. For example, consider
 * a workflow where the user begins in Path A, and then based on choices made during the steps in Path A, will be presented with
 * either Path B1 or Path B2. Regardless of which path the user has taken, the wizard finishes with Path C. Logically, this flow
 * would be represented by the following:
 * <pre>
 *   Start --> Path A ---- Path B1 ----\
 *                    \--- Path B2 ----- Path C --- > Finish
 * </pre>
 *
 * In code, this would be represented by a wizard containing a list of the following paths:
 * <pre>[ Path A, Path B1, Path B2, Path C]</pre>
 * Paths B1 and B2 would contain logic in their isPathVisible() function to implement the branching.
 */
public abstract class DynamicWizardPath implements ScopedStateStore.ScopedStoreListener, AndroidStudioWizardPath {
  // List of steps in this path
  protected List<DynamicWizardStep> mySteps = new ArrayList<>();
  // Reference to the parent wizard
  protected DynamicWizard myWizard;
  // The index of the current step in the path
  protected int myCurrentStepIndex;
  // The current step
  protected DynamicWizardStep myCurrentStep;
  // Whether the current path is in a good state
  protected boolean myIsValid;
  // State store
  protected ScopedStateStore myState;
  // Update queue used to throttle updates
  @Nullable private MergingUpdateQueue myUpdateQueue;
  // Used by update() to ensure that multiple updates are not invoked simultaneously.
  private boolean myUpdateInProgress;
  // Set to true after #init() was invoked
  private boolean myIsInitialized = false;

  public DynamicWizardPath() {
    myState = new ScopedStateStore(ScopedStateStore.Scope.PATH, null, this);
  }

  /**
   * Attach this path to a {@link DynamicWizard}, linking it to that wizard's state.
   */
  @Override
  public final void attachToWizard(@NotNull DynamicWizard wizard) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode()) {
      application.assertIsDispatchThread();
    }
    myWizard = wizard;
    myUpdateQueue = wizard.getUpdateQueue();
    Map<String, Object> myCurrentValues = myState.flatten();
    myState = new ScopedStateStore(ScopedStateStore.Scope.PATH, myWizard.getState(), this);
    for (String keyName : myCurrentValues.keySet()) {
      myState.put(myState.createKey(keyName, Object.class), myCurrentValues.get(keyName));
    }
    init();
    myIsInitialized = true;
  }

  @Nullable
  @Override
  public DynamicWizard getWizard() {
    return myWizard;
  }

  /**
   * Set up this path. Addition of steps and other instantiations should be done here.
   */
  protected abstract void init();

  /**
   * Add a new step to the end of this path.
   */
  public final void addStep(@NotNull DynamicWizardStep step) {
    mySteps.add(step);
    step.attachToPath(this);
  }

  /**
   * @return the scoped state store associated with this path.
   */
  public final ScopedStateStore getState() {
    return myState;
  }

  @VisibleForTesting
  public final void setState(@NotNull ScopedStateStore overrideState) {
    myState = overrideState;
  }

  /**
   * @return the number of visible steps currently in this path.
   */
  @Override
  public final int getVisibleStepCount() {
    int sum = 0;
    for (DynamicWizardStep step : mySteps) {
      if (step.isStepVisible()) {
        sum++;
      }
    }
    return sum;
  }

  /**
   * @return the current step object for this path, or null if this path has not yet been started, or has already ended.
   */
  @Override
  @Nullable
  public final DynamicWizardStep getCurrentStep() {
    return myCurrentStep;
  }

  @Override
  public List<DynamicWizardStep> getAllSteps() {
    return mySteps;
  }

  /**
   * Initialize the path, including setting the iterator to the correct location (just before the beginning, or just after the end).
   * Any additional state setup should be done here.
   * @param fromBeginning Whether the path is being started from the beginning or from the end. If true, the path will be initialized
   *                      to the beginning of the path. If false, it will be initialized to its ending state.
   */
  @Override
  public void onPathStarted(boolean fromBeginning) {
    if (mySteps.isEmpty() || getVisibleStepCount() == 0) {
      return;
    }
    myCurrentStep = null;
    if (fromBeginning) {
      myCurrentStepIndex = -1;
      myCurrentStep = next();
    } else {
      myCurrentStepIndex = mySteps.size();
      myCurrentStep = previous();
    }
  }

  /**
   * Updating: Whenever our state store is changed, this method is invoked.
   * This update method is rate throttled.
   * The update method serves to update values that depend on other values.
   */

  /**
   * Call update with rate throttling. Subclasses should generally not need to override this method.
   */
  @Override
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    if (myUpdateQueue != null) {
      myUpdateQueue.queue(new PathUpdate());
    } else {
      // If we don't have a queue (ie we're not attached to a wizard) then just update immediately
      update();
    }
  }

  /**
   * Call the update steps in order, as well as any parent updates required by the scope.
   */
  private void update() {
    if (myIsInitialized && !myUpdateInProgress) {
      try {
        myUpdateInProgress = true;
        deriveValues(myState.getRecentUpdates());
        myIsValid = validate();
      } finally {
        myUpdateInProgress = false;
        updateButtons();
      }
    }
  }

  /**
   * The first step in the update cycle. Takes the list of changed variables and uses them to recalculate any variables
   * which may depend on those changed values.
   * @param modified set of the changed keys
   */
  public void deriveValues(Set<? extends Key> modified) {

  }

  /**
   * Validate the current state and return true if the current path is in a good state and the wizard can continue.
   * Most subclasses will want to override this function and add custom validation.
   * @return true if the current input is complete and consistent and the wizard can continue.
   */
  public boolean validate() {
    return true;
  }

  /**
   * Called on every update by the wizard.
   * Subclasses should rarely need to override this method. It is preferred
   * that subclasses override validate() and rely on the update method to set the value
   * used by this determination. Note that the default implementation of this method will
   * return true even if there are no more steps in the path, thus allowing the wizard to
   * call this and get an answer which is the same for progress within the path or from this
   * path to the next path in the wizard.
   * @return true if the user can progress to the next step in this path.
   */
  @Override
  public boolean canGoNext() {
    return (myCurrentStep == null || myCurrentStep.canGoNext()) && myIsValid;
  }

  /**
   * Called on every update by the wizard.
   * Subclasses should rarely need to override this method. Note that the default implementation of this method will
   * return true even if there are no more steps in the path, thus allowing the wizard to
   * call this and get an answer which is the same for progress backwards within the path or from this
   * path to the previous path in the wizard.
   * @return true if the user should be allowed to go back through this path.
   */
  @Override
  public boolean canGoPrevious() {
    return myCurrentStep == null || myCurrentStep.canGoPrevious();
  }

  /**
   * @return true iff this path has more visible steps following its current step.
   */
  @Override
  public final boolean hasNext() {
    if (myCurrentStepIndex >= mySteps.size() - 1) {
      return false;
    }
    for (int i = myCurrentStepIndex + 1; i < mySteps.size(); i++) {
      if (mySteps.get(i).isStepVisible()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true iff this path has more visible steps previous to its current step
   */
  @Override
  public final boolean hasPrevious() {
    if (myCurrentStepIndex == 0) {
      return false;
    }
    for (int i = myCurrentStepIndex - 1; i >= 0; i--) {
      if (mySteps.get(i).isStepVisible()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return the next visible step in this path or null if there are no following visible steps
   */
  @Override
  @Nullable
  public final DynamicWizardStep next() {
    if (myCurrentStep != null && (!myCurrentStep.canGoNext() || !myCurrentStep.commitStep())) {
      return myCurrentStep;
    }

    do {
      myCurrentStepIndex++;
    } while (myCurrentStepIndex < mySteps.size() && !mySteps.get(myCurrentStepIndex).isStepVisible());

    if (myCurrentStepIndex < mySteps.size()) {
      myCurrentStep = mySteps.get(myCurrentStepIndex);
      myCurrentStep.onEnterStep();
      myCurrentStep.invokeUpdate(null);
      invokeUpdate(null);
    } else {
      myCurrentStep = null;
    }
    return myCurrentStep;
  }

  @Override
  public boolean readyToLeavePath() {
    return myCurrentStep == null || myCurrentStep.commitStep();
  }

  /**
   * @return the previous visible step in this path or null if there are no previous visible steps
   */
  @Override
  @Nullable
  public final DynamicWizardStep previous() {
    if (myCurrentStep != null && !myCurrentStep.canGoPrevious()) {
      return myCurrentStep;
    }
    do {
      myCurrentStepIndex--;
    } while (myCurrentStepIndex >= 0 && !mySteps.get(myCurrentStepIndex).isStepVisible());
    if (myCurrentStepIndex >= 0) {
      myCurrentStep = mySteps.get(myCurrentStepIndex);
      myCurrentStep.onEnterStep();
      myCurrentStep.invokeUpdate(null);
      invokeUpdate(null);
    } else {
      myCurrentStep = null;
    }
    return myCurrentStep;
  }

  /**
   * Determine whether this path is visible as part of the wizard flow.
   * Subclasses which implement branching must override this function.
   * @return true if this path should be shown to the user.
   */
  @Override
  public boolean isPathVisible() {
    return true;
  }

  /**
   * Determine whether this path is optional or required.
   * Optional paths should be added to the wizard flow AFTER all required paths.
   * Once all remaining paths in the wizard are optional, the wizard's finish button
   * will be enabled.
   * @return true if this path is required, false if it is optional.
   */
  @Override
  public boolean isPathRequired() {
    return true;
  }

  /**
   * Do the checks needed before performing the finishing actions.
   * @return false to abort, true to do the finishing actions.
   */
  @Override
  public boolean canPerformFinishingActions() {
    return true;
  }

  /**
   * This string is used by the wizard framework to uniquely identify this path
   * and will not be shown to the user.
   * @return the name of this path.
   */
  @NotNull
  public abstract String getPathName();

  /**
   * Get the project context which this wizard is operating under.
   * If the this wizard is a global one, this function returns null.
   */
  @Nullable
  protected final Project getProject() {
    return myWizard != null ? myWizard.getProject() : null;
  }

  /**
   * Get the module context which this wizard is operating under.
   * If the this wizard is a global one or project-scoped, the function returns null.
   */
  @Nullable
  protected final Module getModule() {
    return myWizard != null ? myWizard.getModule() : null;
  }

  /**
   * Converts the given text to an HTML message if necessary, and then displays it to the user.
   * @param errorMessage the message to display
   */
  @Override
  public final void setErrorHtml(String errorMessage) {
    if (myCurrentStep != null) {
      myCurrentStep.setErrorHtml(errorMessage);
    }
  }

  /**
   * Ask the wizard to update the buttons to match the current state.
   */
  protected final void updateButtons() {
    if (myWizard != null && myCurrentStep != null) {
      // We update with the step's ability to go to the next step or the previous step in this path.
      // The path can finish when it has run out of steps.
      myWizard.updateButtons(myCurrentStep.canGoPrevious(), myCurrentStep.canGoNext(), true, !hasNext() && myCurrentStep.canGoNext());
    }
  }

  @Override
  public void updateCurrentStep() {
    if (getCurrentStep() != null) {
      getCurrentStep().invokeUpdate(null);
    }
  }

  @Override
  public boolean containsStep(@NotNull String stepName, boolean visibleOnly) {
    for (DynamicWizardStep step : mySteps) {
      if (visibleOnly && !step.isStepVisible()) {
        continue;
      }
      if (stepName.equals(step.getStepName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void navigateToNamedStep(@NotNull String stepName, boolean requireVisible) {
    for (DynamicWizardStep step : mySteps) {
      if (requireVisible && !step.isStepVisible()) {
        continue;
      }
      if (stepName.equals(step.getStepName())) {
        myCurrentStep = step;
        myCurrentStepIndex = mySteps.indexOf(step);
        myCurrentStep.onEnterStep();
        myCurrentStep.invokeUpdate(null);
        invokeUpdate(null);
        return;
      }
    }
  }

  /**
   * @return update queue if there is one
   */
  @Nullable
  public MergingUpdateQueue getUpdateQueue() {
    return myUpdateQueue;
  }

  private class PathUpdate extends Update {
    public PathUpdate() {
      super(DynamicWizardPath.this);
    }

    @Override
    public void run() {
      update();
    }
  }
}
