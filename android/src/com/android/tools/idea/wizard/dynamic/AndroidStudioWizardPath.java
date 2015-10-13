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

import com.intellij.ide.wizard.Step;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for the wizard paths
 */
public interface AndroidStudioWizardPath {
  /**
   * Initialize the path, including setting the iterator to the correct location (just before the beginning, or just after the end).
   * Any additional state setup should be done here.
   *
   * @param fromBeginning Whether the path is being started from the beginning or from the end. If true, the path will be initialized
   *                      to the beginning of the path. If false, it will be initialized to its ending state.
   */
  void onPathStarted(boolean fromBeginning);

  /**
   * Called when the wizard proceeds past the last visible step of the path.
   */
  boolean readyToLeavePath();

  /**
   * @return the current step object for this path, or null if this path has not yet been started, or has already ended.
   */
  Step getCurrentStep();

  /**
   * Get all steps in the path. This should not be used outside the wizard framework.
   * @return all steps in this path.
   */
  List<? extends Step> getAllSteps();

  /**
   * @return true iff this path has more visible steps following its current step.
   */
  boolean hasNext();

  /**
   * @return true iff this path has more visible steps previous to its current step
   */
  boolean hasPrevious();

  /**
   * Called on every update by the wizard.
   * Subclasses should rarely need to override this method. Note that the default implementation of this method will
   * return true even if there are no more steps in the path, thus allowing the wizard to
   * call this and get an answer which is the same for progress backwards within the path or from this
   * path to the previous path in the wizard.
   *
   * @return true if the user should be allowed to go back through this path.
   */
  boolean canGoPrevious();

  /**
   * Called on every update by the wizard.
   * Subclasses should rarely need to override this method. It is preferred
   * that subclasses override validate() and rely on the update method to set the value
   * used by this determination. Note that the default implementation of this method will
   * return true even if there are no more steps in the path, thus allowing the wizard to
   * call this and get an answer which is the same for progress within the path or from this
   * path to the next path in the wizard.
   *
   * @return true if the user can progress to the next step in this path.
   */
  boolean canGoNext();

  /**
   * Returns true if the path contains a step with the given name.
   */
  boolean containsStep(@NotNull String stepName, boolean visibleOnly);

  /**
   * Set the current step to the step with the given name if it exists.
   * If the step does not exist or is not visible (and visibleOnly is true)
   * then this is a no-op.
   */
  void navigateToNamedStep(@NotNull String stepName, boolean visibleOnly);

  /**
   * Determine whether this path is visible as part of the wizard flow.
   * Subclasses which implement branching must override this function.
   *
   * @return true if this path should be shown to the user.
   */
  boolean isPathVisible();

  /**
   * @return the number of visible steps currently in this path.
   */
  int getVisibleStepCount();

  /**
   * Attach this path to a {@link DynamicWizard}, linking it to that wizard's state.
   */
  void attachToWizard(DynamicWizard dynamicWizard);

  /**
   * Get the wizard that this path is currently attached to
   */
  @Nullable DynamicWizard getWizard();

  /**
   * Determine whether this path is optional or required.
   * Optional paths should be added to the wizard flow AFTER all required paths.
   * Once all remaining paths in the wizard are optional, the wizard's finish button
   * will be enabled.
   *
   * @return true if this path is required, false if it is optional.
   */
  boolean isPathRequired();

  /**
   * @return the next visible step in this path or null if there are no following visible steps
   */
  Step next();

  /**
   * @return the previous visible step in this path or null if there are no previous visible steps
   */
  Step previous();

  /**
   * Do the checks needed before performing the finishing actions.
   * @return false to abort, true to do the finishing actions.
   */
  boolean canPerformFinishingActions();

  /**
   * Declare any finishing actions that will take place at the completion of the wizard.
   * This function is called inside of a {@link WriteCommandAction}.
   *
   * @return <code>true</code> signals that the operation completed succesfully and the wizard may procede.
   */
  boolean performFinishingActions();

  /**
   * Force update on the current wizard step
   */
  void updateCurrentStep();

  /**
   * Sets an error message to display in the wizard UI
   */
  void setErrorHtml(String errorMessage);
}
