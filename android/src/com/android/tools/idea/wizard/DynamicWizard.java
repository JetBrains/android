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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;

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
 *
 */
public abstract class DynamicWizard extends DialogWrapper implements ScopedStateStore.ScopedStoreListener {
  Logger LOG = Logger.getInstance(DynamicWizard.class);

  // A queue of updates used to throttle the update() function.
  private final MergingUpdateQueue myUpdateQueue;
  // Used by update() to ensure that multiple updates are not invoked simultaneously.
  private boolean myUpdateInProgress;
  // A reference to the project context in which this wizard was invoked.
  @Nullable private Project myProject;
  // A reference to the module context in which this wizard was invoked.
  @Nullable private Module myModule;
  // The name of this wizard for display to the user
  protected String myName;
  // List of the paths that this wizard contains. Paths can be optional or required.
  protected ArrayList<DynamicWizardPath> myPaths = Lists.newArrayList();
  // The current path
  protected DynamicWizardPath myCurrentPath;
  // An iterator to keep track of the user's progress through the paths.
  protected PathIterator myPathListIterator = new PathIterator(myPaths);
  // Action References. myCancelAction and myHelpAction are inherited
  protected Action myPreviousAction = new PreviousAction();
  protected Action myNextAction = new NextAction();
  protected Action myFinishAction = new FinishAction();

  protected TallImageComponent myIcon;
  private boolean myIsInitialized = false;

  // UI references
  private Map<Action, JButton> myActionToButtonMap = Maps.newHashMapWithExpectedSize(5);

  //private Icon myIcon;
  private JPanel myContentPanel;
  private Map<JComponent, String> myComponentToIdMap = Maps.newHashMap();
  private ScopedStateStore myState;

  public DynamicWizard(@Nullable Project project, @Nullable Module module, @NotNull String name) {
    super(project);
    myProject = project;
    myModule = module;
    myName = name;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myUpdateQueue = null;
    } else {
      myUpdateQueue = new MergingUpdateQueue("wizard", 100, true, null, getDisposable(), null, false);
    }
    myState = new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, this);
    myIcon = new TallImageComponent(null);
  }

  /**
   * Create the center panel that will serve as a container for the UI components of each step.
   */
  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    myContentPanel = new JPanel(new CardLayout());
    panel.add(myContentPanel, BorderLayout.CENTER);
    panel.add(myIcon, BorderLayout.WEST);
    return panel;
  }

  /**
   * Call update with rate throttling
   */
  @Override
  public <T> void invokeUpdate(@Nullable Key<T> changedKey) {
    if (myUpdateQueue != null) {
      myUpdateQueue.cancelAllUpdates();
      myUpdateQueue.queue(new Update("update") {
        @Override
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              update();
            }
          });
        }
      });
    } else {
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
      myState.clearRecentUpdates();
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
   * Declare any finishing actions that will take place at the completion of the wizard.
   * This function is called inside of a {@link WriteCommandAction}.
   */
  public abstract void performFinishingActions();

  /**
   * Get the project context which this wizard is operating under.
   * If the this wizard is a global one, the function returns null.
   */
  @Nullable
  protected final Project getProject() {
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
   * @param canFinishCurrentPath if this is set to true and the current path is the last non-optional path, the canFinish
   *                             button will be enabled.
   */
  public final void updateButtons(boolean canGoPrev, boolean canGoNext, boolean canFinishCurrentPath) {
    if (!myIsInitialized) {
      // Buttons were not yet created
      return;
    }
    getPreviousButton().setEnabled(canGoPrev && hasPrevious());
    getNextButton().setEnabled(canGoNext && hasNext());

    boolean canFinish = canFinishCurrentPath && canFinish();
    getFinishButton().setEnabled(canFinish);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getRootPane().setDefaultButton(canFinish ? getFinishButton() : getNextButton());
    }
  }

  /**
   * Add the given path to the end of this wizard.
   */
  protected final void addPath(@NotNull DynamicWizardPath path) {
    myPaths.add(path);
    // If this is the first visible path, select it
    if (myCurrentPath == null && path.isPathVisible()) {
      myCurrentPath = path;
    }
    // Rebuild the iterator to avoid concurrent modification exceptions
    myPathListIterator = new PathIterator(myPaths);
    path.attachToWizard(this);
  }

  /**
   * @return the total number of visible steps in this wizard.
   */
  public final int getVisibleStepCount() {
    int sum = 0;
    for (DynamicWizardPath path : myPaths) {
      sum += path.getVisibleStepCount();
    }
    return sum;
  }

  /**
   * @return the currently visible step, or null if no step is visible.
   */
  @Nullable
  public final DynamicWizardStep getCurrentStep() {
    if (myCurrentPath != null) {
      return myCurrentPath.getCurrentStep();
    }
    return null;
  }

  @Override
  public void init() {
    super.init();
    if (myCurrentPath != null) {
      myCurrentPath.onPathStarted(true);
      showStep(myCurrentPath.myCurrentStep);
    }
    myIsInitialized = true;
    updateButtons(hasPrevious(), hasNext(), canFinish());
  }

  @NotNull
  protected final JButton getNextButton() {
    return myActionToButtonMap.get(myNextAction);
  }

  @NotNull
  protected final JButton getPreviousButton() {
    return myActionToButtonMap.get(myPreviousAction);
  }

  @NotNull
  protected final JButton getHelpButton() {
    return myActionToButtonMap.get(myHelpAction);
  }

  @NotNull
  protected final JButton getCancelButton() {
    return myActionToButtonMap.get(myCancelAction);
  }

  @NotNull
  protected final JButton getFinishButton() {
    return myActionToButtonMap.get(myFinishAction);
  }

  protected void showStep(@NotNull DynamicWizardStep step) {
    JComponent component = step.getComponent();
    Icon icon = step.getIcon();

    if (icon != null) {
      myIcon.setIcon(icon);
      myIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
    }

    // Store a reference to this component.
    String id = myComponentToIdMap.get(component);
    if (id == null) {
      id = Joiner.on(':').join(myName, myCurrentPath.getPathName(), step.getStepName());
      myComponentToIdMap.put(component, id);
      myContentPanel.add(component, id);
    }
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, id);

    JComponent focusedComponent = step.getPreferredFocusedComponent();
    if (focusedComponent != null) {
      IdeFocusManager.findInstanceByComponent(focusedComponent).requestFocus(focusedComponent, false);
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
   * ACTIONS
   */

  @NotNull
  @Override
  protected final Action[] createActions() {
    if (getHelpId() == null) {
      if (SystemInfo.isMac) {
        return new Action[]{getCancelAction(), myPreviousAction, myNextAction, myFinishAction};
      }

      return new Action[]{myPreviousAction, myNextAction, getCancelAction(), myFinishAction};
    }
    else {
      if (SystemInfo.isMac) {
        return new Action[]{getHelpAction(), getCancelAction(), myPreviousAction, myNextAction, myFinishAction};
      }
      return new Action[]{myPreviousAction, myNextAction, getCancelAction(), myFinishAction, getHelpAction()};
    }
  }

  @Override
  protected final JButton createJButtonForAction(Action action) {
    // Save a reference to the created button so that we can access them
    // later to enable/disable
    JButton button =  super.createJButtonForAction(action);
    myActionToButtonMap.put(action, button);
    return button;
  }

  /**
   * Commit the current step and move to the next step. Subclasses should rarely need to override
   * this method.
   */
  protected void doNextAction() {
    assert myCurrentPath != null;
    if (!myCurrentPath.canGoNext()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        DialogEarthquakeShaker.shake((JDialog)getPeer().getWindow());
      }
      return;
    }

    DynamicWizardStep newStep;
    if (!myCurrentPath.hasNext() && myPathListIterator.hasNext()) {
      myCurrentPath = myPathListIterator.next();
      myCurrentPath.onPathStarted(true /* fromBeginning */);
      newStep = myCurrentPath.getCurrentStep();
    } else if (myCurrentPath.hasNext()) {
      newStep = myCurrentPath.next();
    } else {
      doFinishAction();
      return;
    }
    if (newStep != null) {
      showStep(newStep);
    }
  }

  /**
   * Find and go to the previous step. Subclasses should rarely need to override
   * this method.
   */
  protected void doPreviousAction() {
    assert myCurrentPath != null;
    if (!myCurrentPath.canGoPrevious()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        DialogEarthquakeShaker.shake((JDialog)getPeer().getWindow());
      }
      return;
    }

    DynamicWizardStep newStep;
    if ((myCurrentPath == null || !myCurrentPath.hasPrevious()) && myPathListIterator.hasPrevious()) {
      myCurrentPath = myPathListIterator.previous();
      myCurrentPath.onPathStarted(false /* fromBeginning */);
      newStep = myCurrentPath.getCurrentStep();
    } else if (myCurrentPath.hasPrevious()) {
      newStep = myCurrentPath.previous();
    } else {
      doCancelAction();
      return;
    }
    if (newStep != null) {
      showStep(newStep);
    }
  }

  /**
   * Complete the wizard, doing any finishing actions that have been queued up during the wizard flow
   * inside a write action and a command. Subclasses should rarely need to override
   * this method.
   */
  protected void doFinishAction() {
    super.doOKAction();
    new WriteCommandAction<Void>(myProject, (PsiFile) null) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        for (DynamicWizardPath path : myPaths) {
          if (path.isPathVisible()) {
            path.performFinishingActions();
          }
        }
        performFinishingActions();
      }
    }.execute();
  }

  @Override
  protected void doHelpAction() {
    // TODO: Implement
  }

  /**
   * @return the scoped state store associate with this wizard as a whole
   */
  public final ScopedStateStore getState() {
    return myState;
  }

  protected class NextAction extends DialogWrapperAction {
    protected NextAction() {
      super(IdeBundle.message("button.wizard.next"));
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    protected void doAction(ActionEvent e) {
      doNextAction();
    }
  }

  protected class PreviousAction extends DialogWrapperAction {
    protected PreviousAction() {
      super(IdeBundle.message("button.wizard.previous"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      doPreviousAction();
    }
  }

  protected class FinishAction extends DialogWrapperAction {
    protected FinishAction() {
      super(IdeBundle.message("button.finish"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      doFinishAction();
    }
  }

  protected static class PathIterator {

    private int myCurrentIndex;
    private ArrayList<DynamicWizardPath> myList;

    public PathIterator(ArrayList<DynamicWizardPath> list) {
      myList = list;
      myCurrentIndex = 0;
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
        DynamicWizardPath path = myList.get(i);
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
    public DynamicWizardPath next() {
      do {
        myCurrentIndex++;
      } while(myCurrentIndex < myList.size() && !myList.get(myCurrentIndex).isPathVisible());
      if (myCurrentIndex < myList.size()) {
        return myList.get(myCurrentIndex);
      } else {
        return null;
      }
    }

    /**
     * Go back to the last visible path and return it, or null if there are no previous visible paths
     */
    @Nullable
    public DynamicWizardPath previous() {
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

  /**
   * Taken from {@link AbstractWizard}
   */
  public static class TallImageComponent extends OpaquePanel {
    private Icon myIcon;

    public TallImageComponent(Icon icon) {
      myIcon = icon;
    }

    @Override
    protected void paintChildren(Graphics g) {
      if (myIcon == null) return;

      paintIcon(g);
    }

    public void paintIcon(Graphics g) {
      if (myIcon == null) {
        return;
      }
      final BufferedImage image = UIUtil.createImage(myIcon.getIconWidth(), myIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D gg = image.createGraphics();
      myIcon.paintIcon(this, gg, 0, 0);

      final Rectangle bounds = g.getClipBounds();
      int y = myIcon.getIconHeight()-1;
      while (y < bounds.y + bounds.height) {
        g.drawImage(image,
                    bounds.x, y, bounds.x + bounds.width, y + 1,
                    0, myIcon.getIconHeight() - 1, bounds.width, myIcon.getIconHeight(), this);

        y++;
      }


      g.drawImage(image, 0, 0, this);
    }

    public void setIcon(Icon icon) {
      myIcon = icon;
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myIcon != null ? myIcon.getIconWidth() : 0, 0);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(myIcon != null ? myIcon.getIconWidth() : 0, 0);
    }
  }
}
