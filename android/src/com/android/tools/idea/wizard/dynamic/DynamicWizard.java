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

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * DynamicWizard is an evolution of {@link TemplateWizard} that seeks to provide a flexible base for
 * implemented GUI wizards that may involve multiple steps and branches.
 *
 * A DynamicWizard contains a series of {@link DynamicWizardPath}s, in the order that the user is expected
 * to traverse through the wizard. Paths may declare themselves to be visible or invisible (in which case they
 * will be skipped by the wizard), depending on the
 * state of the wizard. Each path contains a series of {@link DynamicWizardStep}s, which also may be visible or invisible, depending
 * on the state of the wizard. The DynamicWizard class is responsible for providing the GUI frame for the wizard and
 * maintaining the state of the wizard buttons. Each path and step is responsible for its own validation and visibility. Each step must
 * provide a {@link JComponent} that serves as the user interface for that step. Each step also provides a title string and optionally
 * provides an {@link Icon} to be displayed on the left hand side of the wizard pane.
 *
 * @deprecated Replaced by {@link ModelWizard}
 */
public abstract class DynamicWizard implements ScopedStateStore.ScopedStoreListener {
  // 42 is an arbitrary number. This constant is for the number of update cycles before
  // we decide there's circular dependency and we cannot settle down the model state.
  public static final int MAX_UPDATE_ATTEMPTS = 42;
  // TODO: Make this logger private and create new loggers for anywhere that complains
  public static Logger LOG = Logger.getInstance(DynamicWizard.class);

  // A queue of updates used to throttle the update() function.
  private final MergingUpdateQueue myUpdateQueue;
  // Used by update() to ensure that multiple updates are not invoked simultaneously.
  private boolean myUpdateInProgress;
  // A reference to the project context in which this wizard was invoked.
  @Nullable private Project myProject;
  // A reference to the module context in which this wizard was invoked.
  @Nullable private Module myModule;

  // Wizard "chrome"
  @NotNull protected final DynamicWizardHost myHost;
  // The name of this wizard for display to the user
  protected String myName;
  // List of the paths that this wizard contains. Paths can be optional or required.
  protected ArrayList<AndroidStudioWizardPath> myPaths = Lists.newArrayList();
  // The current path
  protected AndroidStudioWizardPath myCurrentPath;
  // An iterator to keep track of the user's progress through the paths.
  protected PathIterator myPathListIterator = new PathIterator(myPaths);
  private boolean myIsInitialized = false;
  protected ScopedStateStore myState;
  private JPanel myContentPanel = new JPanel(new CardLayout());
  private Map<JComponent, String> myComponentToIdMap = Maps.newHashMap();

  public DynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name) {
    this(project, module, name, new DialogWrapperHost(project));
  }

  public DynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name, @NotNull DynamicWizardHost host) {
    myHost = host;
    myProject = project;
    myModule = module;
    myName = name;
    myHost.setTitle(name);
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) {
      myUpdateQueue = null;
    } else {
      myUpdateQueue = new MergingUpdateQueue("wizard", 100, true, MergingUpdateQueue.ANY_COMPONENT, myHost.getDisposable(), null, true);
    }
    myState = new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, this);
  }

  public void init() {
    myHost.init(this);
    myIsInitialized = true;

    if (myUpdateQueue != null) {
      int guard = 0;
      // Keep processing updates until model state settles down.
      // In some cases, circular dependencies may turn this into endless loop. This is coding
      // error so we need to detect it and report to developer.
      while (!myUpdateQueue.isEmpty()) {
        myUpdateQueue.flush();
        guard++;
        if (guard >= MAX_UPDATE_ATTEMPTS) {
          throw new IllegalStateException("Circular dependencies detected. Model state cannot be settled down.");
        }
      }
    }
    Step step = showNextStep(null);
    assert step != null;
  }

  /**
   * @return update queue that other components may use to submit their updates.
   */
  @Nullable
  public final MergingUpdateQueue getUpdateQueue() {
    return myUpdateQueue;
  }

  /**
   * Call update with rate throttling
   */
  @Override
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    if (myUpdateQueue != null) {
      myUpdateQueue.queue(new WizardUpdate());
    }
    else {
      // If we're not running in a context, just update immediately
      update();
    }
  }

  /**
   * Updating: Whenever a path's update method is called with a WIZARD scope,
   * it will invoke the parent Wizard's update method. This update method is rate throttled.
   */

  /**
   * Call the update steps in order. Will not fire if an update is already in progress.
   */
  private void update() {
    if (!myUpdateInProgress) {
      myUpdateInProgress = true;
      deriveValues(myState.getRecentUpdates());
      myUpdateInProgress = false;
    }
  }

  /**
   * Takes the list of changed variables and uses them to recalculate any variables
   * which may depend on those changed values.
   * @param modified map of the keys of the changed objects in the state store to their scopes.
   */
  public void deriveValues(Set<Key> modified) {

  }

  /**
   * Do the checks needed before performing the finishing actions.
   * @return false to abort, true to do the finishing actions.
   */
  public boolean canPerformFinishingActions() {
    return true;
  }

  /**
   * Declare any finishing actions that will take place at the completion of the wizard. This will
   * be executed by a worker thread, under progress.
   */
  public abstract void performFinishingActions();

  /**
   * Get the project context which this wizard is operating under.
   * If the this wizard is a global one, the function returns null.
   */
  @Nullable
  public Project getProject() {
    return myProject;
  }

  /**
   * Get the module context which this wizard is operating under.
   * If the this wizard is a global one or project-scoped, the function returns null.
   */
  @Nullable
  protected final Module getModule() {
    return myModule;
  }

  /**
   * Converts the given text to an HTML message if necessary, and then displays it to the user.
   * @param errorMessage the message to display
   */
  public final void setErrorHtml(String errorMessage) {
    if (myCurrentPath != null) {
      myCurrentPath.setErrorHtml(errorMessage);
    }
  }

  /**
   * Update the buttons for the wizard
   * @param canGoPrev whether the previous button is enabled
   * @param canGoNext whether the next button is enabled
   * @param canCancelCurrentPath whether the cancel button is enabled
   * @param canFinishCurrentPath if this is set to true and the current path is the last non-optional path, the canFinish
   */
  public final void updateButtons(boolean canGoPrev, boolean canGoNext, boolean canCancelCurrentPath, boolean canFinishCurrentPath) {
    if (!myIsInitialized) {
      // Buttons were not yet created
      return;
    }
    myHost.updateButtons(canGoPrev && hasPrevious(), canGoNext && hasNext(),
                         canCancelCurrentPath && canCancel(), canFinishCurrentPath && canFinish());
  }

  /**
   * Add the given path to the end of this wizard.
   */
  public final void addPath(@NotNull AndroidStudioWizardPath path) {
    myPaths.add(path);
    path.attachToWizard(this);
    // Rebuild the iterator to avoid concurrent modification exceptions
    myPathListIterator = new PathIterator(myPaths);
  }

  @NotNull
  public final ArrayList<AndroidStudioWizardPath> getAllPaths() {
    return myPaths;
  }

  @Nullable
  public final AndroidStudioWizardPath getCurrentPath() {
    return myCurrentPath;
  }

  /**
   * @return the total number of visible steps in this wizard.
   */
  public final int getVisibleStepCount() {
    int sum = 0;
    for (AndroidStudioWizardPath path : myPaths) {
      sum += path.getVisibleStepCount();
    }
    return sum;
  }

  private void showStep(@NotNull Step step) {
    JComponent component = step.getComponent();
    addStepIfNecessary(step);
    Icon icon = step.getIcon();
    myHost.setIcon(icon);
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, myComponentToIdMap.get(component));

    JComponent focusedComponent = step.getPreferredFocusedComponent();
    if (focusedComponent != null) {
      IdeFocusManager.findInstanceByComponent(focusedComponent).requestFocus(focusedComponent, true);
    }
  }

  /**
   * @return true if the wizard can advance to the next step. Returns false if there is an error
   * on the current step or if there are no more steps. Subclasses should rarely need to override
   * this method.
   */
  protected boolean canGoNext() {
    return myCurrentPath != null && myCurrentPath.canGoNext();
  }

  /**
   * @return true if the wizard can go back to the previous step. Returns false if there is an error
   * on the current step or if there are no more steps prior to the current one.
   * Subclasses should rarely need to override this method.
   */
  protected boolean canGoPrevious() {
    return myCurrentPath != null && myCurrentPath.canGoPrevious();
  }

  /**
   * @return true if the wizard has additional visible steps. Subclasses should rarely need to override
   * this method.
   */
  protected boolean hasNext() {
    return myCurrentPath != null && myCurrentPath.hasNext() || myPathListIterator.hasNext();
  }

  /**
   * @return true if the wizard has previous visible steps
   * Subclasses should rarely need to override this method.
   */
  protected boolean hasPrevious() {
    return myCurrentPath != null && myCurrentPath.hasPrevious() || myPathListIterator.hasPrevious();
  }

  /**
   * @return true if the wizard is in a state in which it can finish. This is defined as being done with the current
   * path and having no required paths remaining. Subclasses should rarely need to override
   * this method.
   */
  protected boolean canFinish() {
    if (!myPathListIterator.hasNext() && (myCurrentPath == null || !myCurrentPath.hasNext())) {
      return true;
    } else if (myCurrentPath != null && myCurrentPath.hasNext()) {
      return false;
    }

    boolean canFinish = true;
    PathIterator remainingPaths = myPathListIterator.getFreshCopy();
    while(canFinish && remainingPaths.hasNext()) {
      canFinish = !remainingPaths.next().isPathRequired();
    }
    return canFinish;
  }

  /**
   * @return true iff the current step is the last one in the wizard (required or optional)
   */
  protected final boolean isLastStep() {
    if (myCurrentPath != null) {
      return !myPathListIterator.hasNext() && !myCurrentPath.hasNext();
    } else {
      return !myPathListIterator.hasNext();
    }
  }

  /**
   * Commit the current step and move to the next step. Subclasses should rarely need to override
   * this method.
   */
  public final void doNextAction() {
    if (!canAdvance()) {
      myHost.shakeWindow();
      return;
    }
    Step newStep = showNextStep(myCurrentPath);
    if (newStep == null) {
      doFinishAction();
    }
  }

  @Nullable
  private Step showNextStep(@Nullable AndroidStudioWizardPath path) {
    Step newStep = null;
    if (path != null) {
      newStep = path.next();
    }
    if (newStep == null) {
      newStep = null;
      while (myPathListIterator.hasNext() && newStep == null) {
        myCurrentPath = myPathListIterator.next();
        assert myCurrentPath != null;
        myCurrentPath.onPathStarted(true /* fromBeginning */);
        newStep = myCurrentPath.getCurrentStep();
      }
    }
    if (newStep != null) {
      showStep(newStep);
    }
    return newStep;
  }

  /**
   * Test if current step and/or path are ok with moving to a next step or completing the wizard.
   */
  private boolean canAdvance() {
    if (myCurrentPath == null) {
      return true;
    }
    else if (myCurrentPath.canGoNext()) {
      return myCurrentPath.hasNext() || myCurrentPath.readyToLeavePath();
    }
    else {
      return false;
    }
  }

  /**
   * Find and go to the previous step. Subclasses should rarely need to override
   * this method.
   */
  public final void doPreviousAction() {
    assert myCurrentPath != null;
    if (!myCurrentPath.canGoPrevious()) {
      myHost.shakeWindow();
      return;
    }

    Step newStep = myCurrentPath.previous();
    if (newStep == null) {
      while (myPathListIterator.hasPrevious() && newStep == null) {
        myCurrentPath = myPathListIterator.previous();
        assert myCurrentPath != null;
        myCurrentPath.onPathStarted(false /* fromBeginning */);
        newStep = myCurrentPath.getCurrentStep();
      }
    }
    if (newStep != null) {
      showStep(newStep);
    }
    else {
      LOG.error("Stepped into Path " + myCurrentPath + " which returned a null step");
    }
  }

  /**
   * Complete the wizard, doing any finishing actions that have been queued up during the wizard flow,
   * with a progress indicator. Subclasses should rarely need to override this method.
   */
  public void doFinishAction() {
    if (!checkFinish()) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
        try {
          doFinish();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }, getProgressTitle(), false, getProject(), getProgressParentComponent());
  }

  protected boolean checkFinish() {
    if (myCurrentPath != null && !myCurrentPath.readyToLeavePath()) {
      myHost.shakeWindow();
      return false;
    }
    myHost.close(DynamicWizardHost.CloseAction.FINISH);
    return true;
  }

  /**
   * The component that should be the parent of the progress window created on wizard
   * completion. Null by default: the main window will be used.
   * Subclasses should override this if the wizard is kicked off from a window other than the main
   * Studio window; otherwise the progress bar will be beneath that window.
   */
  @Nullable
  public JComponent getProgressParentComponent() {
    return null;
  }

  @NotNull
  protected abstract String getProgressTitle();

  /**
   * Cancel the wizard
   */
  public void doCancelAction() {
    myHost.close(DynamicWizardHost.CloseAction.CANCEL);
  }

  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DEFAULT;
  }

  @Nullable
  public final JComponent getPreferredFocusedComponent() {
    Step currentStep = myCurrentPath.getCurrentStep();
    if (currentStep != null) {
      return currentStep.getPreferredFocusedComponent();
    }
    else {
      return null;
    }
  }

  protected abstract String getWizardActionDescription();

  /**
   * @return the scoped state store associate with this wizard as a whole
   */
  public final ScopedStateStore getState() {
    return myState;
  }

  private void prepareForShow() {
    assert myIsInitialized : "Must call init() before showing!";
    // All steps must be included so the window can be sized correctly
    for (AndroidStudioWizardPath path : myPaths) {
      for (Step step : path.getAllSteps()) {
        addStepIfNecessary(step);
      }
    }

    SwingUtilities.getWindowAncestor(myContentPanel).pack();
  }

  private void addStepIfNecessary(Step step) {
    JComponent component = step.getComponent();
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = String.valueOf(myComponentToIdMap.size());
      myComponentToIdMap.put(component, id);
      myContentPanel.add(component, id);
    }
  }

  public void show() {
    prepareForShow();
    myHost.show();
  }

  @NotNull
  public Disposable getDisposable() {
    return myHost.getDisposable();
  }

  public boolean showAndGet() {
    prepareForShow();
    return myHost.showAndGet();
  }

  public final Component getContentPane() {
    return myContentPanel;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }

  public void setTitle(String title) {
    myHost.setTitle(title);
  }

  /**
   * Returns true if a step with the given name exists in this wizard's current configuration.
   * If visibleOnly is set to true, only visible steps (that are part of visible paths) will
   * be considered.
   */
  public boolean containsStep(@NotNull String stepName, boolean visibleOnly) {
    for (AndroidStudioWizardPath path : myPaths) {
      if (visibleOnly && !path.isPathVisible()) {
        continue;
      }
      if (path.containsStep(stepName, visibleOnly)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Navigates this wizard to the step with the given name if it exists. If not, this function
   * is a no-op. If the requireVisible parameter is set to true, then only currently visible steps (which
   * are part of currently visible paths) will be considered.
   */
  public void navigateToNamedStep(@NotNull String stepName, boolean requireVisible) {
    for (AndroidStudioWizardPath path : myPaths) {
      if ((!requireVisible || path.isPathVisible()) && path.containsStep(stepName, requireVisible)) {
        myCurrentPath = path;
        myPathListIterator.myCurrentIndex = myPathListIterator.myList.indexOf(myCurrentPath);
        myCurrentPath.navigateToNamedStep(stepName, requireVisible);
        showStep(myCurrentPath.getCurrentStep());
        return;
      }
    }
  }

  public boolean canCancel() {
    return true;
  }

  @Nullable
  public Icon getIcon() {
    return AndroidIcons.Wizards.NewProjectMascotGreen;
  }

  protected static class PathIterator {

    private int myCurrentIndex;
    private ArrayList<AndroidStudioWizardPath> myList;

    public PathIterator(ArrayList<AndroidStudioWizardPath> list) {
      myList = list;
      myCurrentIndex = -1;
    }

    /**
     * @return a copy of this iterator
     */
    public PathIterator getFreshCopy() {
      PathIterator toReturn = new PathIterator(myList);
      toReturn.myCurrentIndex = myCurrentIndex;
      return toReturn;
    }

    /**
     * @return true iff there are more visible paths with steps following the current location
     */
    public boolean hasNext() {
      if (myCurrentIndex >= myList.size() - 1) {
        return false;
      }
      for (int i = myCurrentIndex + 1; i < myList.size(); i++) {
        AndroidStudioWizardPath path = myList.get(i);
        if (path.isPathVisible() && path.getVisibleStepCount() > 0) {
          return true;
        }
      }
      return false;
    }

    /**
     * @return true iff this path has more visible steps previous to its current step
     */
    public boolean hasPrevious() {
      if (myCurrentIndex <= 0) {
        return false;
      }
      for (int i = myCurrentIndex - 1; i >= 0; i--) {
        if (myList.get(i).isPathVisible()) {
          return true;
        }
      }
      return false;
    }

    /**
     * Advance to the next visible path and return it, or null if there are no following visible paths
     * @return the next path
     */
    @Nullable
    public AndroidStudioWizardPath next() {
      while (myCurrentIndex < (myList.size() - 1)) {
        AndroidStudioWizardPath next = myList.get(++myCurrentIndex);
        if (next.isPathVisible()) {
          return next;
        }
      }
      return null;
    }

    /**
     * Go back to the last visible path and return it, or null if there are no previous visible paths
     */
    @Nullable
    public AndroidStudioWizardPath previous() {
      do {
        myCurrentIndex--;
      } while(myCurrentIndex >= 0 && !myList.get(myCurrentIndex).isPathVisible());
      if (myCurrentIndex >= 0) {
        return myList.get(myCurrentIndex);
      } else {
        return null;
      }
    }
  }

  protected void doFinish() throws IOException {
    for (AndroidStudioWizardPath path : myPaths) {
      if (path.isPathVisible() && !path.canPerformFinishingActions()) {
        return;
      }
    }
    if (!canPerformFinishingActions()) {
      return;
    }
    for (AndroidStudioWizardPath path : myPaths) {
      if (path.isPathVisible()) {
        path.performFinishingActions();
      }
    }
    performFinishingActions();
  }

  private class WizardUpdate extends Update {
    public WizardUpdate() {
      super("Wizard Update");
    }

    @NotNull
    @Override
    public Object[] getEqualityObjects() {
      return new Object[]{DynamicWizard.this};
    }

    @Override
    public void run() {
      update();
    }
  }
}
