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

import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.collect.Maps;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Uses {@link DialogWrapper} to display a wizard in a dialog
 */
public class DialogWrapperHost extends DialogWrapper implements DynamicWizardHost {
  // Action References. myCancelAction and myHelpAction are inherited
  protected Action myPreviousAction = new PreviousAction();
  protected Action myNextAction = new NextAction();
  protected Action myFinishAction = new FinishAction();

  private DynamicWizard myWizard;
  private TallImageComponent myIcon = new TallImageComponent(null);
  private AtomicReference<ProgressIndicator> myCurrentProgressIndicator = new AtomicReference<ProgressIndicator>();

  private JPanel myCenterPanel;

  // UI references
  private Map<Action, JButton> myActionToButtonMap = Maps.newHashMapWithExpectedSize(5);

  public DialogWrapperHost(@Nullable Project project, IdeModalityType modalityType) {
    super(project, true, modalityType);
  }

  public DialogWrapperHost(@Nullable Project project) {
    super(project);
  }

  @Override
  public void init(@NotNull DynamicWizard wizard) {
    Window window = getWindow();
    if (window == null) {
      assert ApplicationManager.getApplication().isUnitTestMode();
    } else {
      window.setMinimumSize(WizardConstants.DEFAULT_WIZARD_WINDOW_SIZE);
    }
    myWizard = wizard;
    super.init();
    // Clear out the large border
    Container centerRootPanel = myCenterPanel.getParent();
    if (centerRootPanel != null) {
      Container rootPanel = centerRootPanel.getParent();
      if (rootPanel instanceof JPanel) {
        // Clear out default borders, we'll set our own later
        ((JPanel)rootPanel).setBorder(JBUI.Borders.empty());
      }
    }
  }

  @Override
  public void close(@NotNull CloseAction action) {
    if (action == CloseAction.FINISH) {
      doOKAction();
    }
    else if (action == CloseAction.CANCEL) {
      dialogWrapperCancel();
    }
  }

  private void dialogWrapperCancel() {
    super.doCancelAction();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    JComponent focusedComponent = myWizard.getPreferredFocusedComponent();
    return focusedComponent == null ? getNextButton() : focusedComponent;
  }

  @Override
  public void shakeWindow() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DialogEarthquakeShaker.shake((JDialog)getPeer().getWindow());
    }
  }

  /**
   * ACTIONS
   */
  @NotNull
  @Override
  protected Action[] createActions() {
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
  public void setIcon(@Nullable Icon icon) {
    myIcon.setIcon(icon);
    myIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
  }

  @Override
  public void runSensitiveOperation(@NotNull final ProgressIndicator progressIndicator,
                                    boolean cancellable, @NotNull final Runnable operation) {
    final Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (!myCurrentProgressIndicator.compareAndSet(null, progressIndicator)) {
      throw new IllegalStateException("Submitting an operation while another is in progress.");
    }
    final JRootPane rootPane = getRootPane();
    rootPane.setDefaultButton(null);
    updateButtons(false, false, true, false);

    Task.Backgroundable task = new Task.Backgroundable(null, myWizard.getWizardActionDescription(), cancellable) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        operation.run();
        // this can't be done in onSuccess because the ModalityState needs to be set
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            updateButtons(false, false, false, true);
            myCurrentProgressIndicator.set(null);
          }
        }, ModalityState.stateForComponent(myWizard.getContentPane()));
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator);
  }

  @Override
  protected final JButton createJButtonForAction(Action action) {
    // Save a reference to the created button so that we can access them
    // later to enable/disable
    JButton button = super.createJButtonForAction(action);
    myActionToButtonMap.put(action, button);
    return button;
  }

  @Override
  protected void doHelpAction() {
    // TODO: Implement
  }

  /**
   * Create the south panel that serves as the container for the wizard buttons.
   * The base class does all the heavy lifting already, we just adjust the margins here.
   */
  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JPanel southPanel = (JPanel)super.createSouthPanel();
    assert southPanel != null;
    southPanel.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    return southPanel;
  }

  @Override
  public final void doCancelAction() {
    ProgressIndicator indicator = myCurrentProgressIndicator.get();
    if (indicator != null) {
      indicator.cancel();
    } else {
      myWizard.doCancelAction();
    }
  }

  /**
   * Update the buttons for the wizard
   * @param canGoPrev whether the previous button is enabled
   * @param canGoNext whether the next button is enabled
   * @param canCancel whether the cancel button is enabled
   * @param canFinish if this is set to true and the current path is the last non-optional path, the canFinish
   */
  @Override
  public void updateButtons(boolean canGoPrev, boolean canGoNext, boolean canCancel, boolean canFinish) {
    JButton prev = getPreviousButton();
    if (prev != null) {
      prev.setEnabled(canGoPrev);
    }
    JButton next = getNextButton();
    if (next != null) {
      next.setEnabled(canGoNext);
    }

    getFinishButton().setEnabled(canFinish);
    getCancelButton().setEnabled(canCancel);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getRootPane().setDefaultButton(canFinish ? getFinishButton() : getNextButton());
    }
  }

  /**
   * Create the center panel that will serve as a container for the UI components of each step.
   */
  @Override
  @Nullable
  protected final JComponent createCenterPanel() {
    myCenterPanel = new JPanel(new BorderLayout());
    myCenterPanel.add(myWizard.getContentPane(), BorderLayout.CENTER);
    myCenterPanel.add(myIcon, BorderLayout.WEST);
    return myCenterPanel;
  }

  @Nullable
  protected JButton getNextButton() {
    return myActionToButtonMap.get(myNextAction);
  }

  @Nullable
  protected JButton getPreviousButton() {
    return myActionToButtonMap.get(myPreviousAction);
  }

  @NotNull
  protected JButton getFinishButton() {
    return myActionToButtonMap.get(myFinishAction);
  }

  @NotNull
  protected JButton getCancelButton() {
    return myActionToButtonMap.get(myCancelAction);
  }

  @Override
  public void setTitle(String title) {
    super.setTitle(title);
  }

  @Override
  public void setPreferredWindowSize(Dimension dimension) {
    getContentPanel().setPreferredSize(dimension);
  }

  /**
   * Taken from {@link com.intellij.ide.wizard.AbstractWizard}
   */
  public static class TallImageComponent extends OpaquePanel {
    @Nullable private Icon myIcon;

    public TallImageComponent(@Nullable Icon icon) {
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
      int y = myIcon.getIconHeight() - 1;
      while (y < bounds.y + bounds.height) {
        g.drawImage(image, bounds.x, y, bounds.x + bounds.width, y + 1, 0, myIcon.getIconHeight() - 1, bounds.width, myIcon.getIconHeight(),
                    this);

        y++;
      }


      g.drawImage(image, 0, 0, this);
    }

    public void setIcon(@Nullable Icon icon) {
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

  protected class NextAction extends DialogWrapperAction {
    protected NextAction() {
      super(IdeBundle.message("button.wizard.next"));
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.doNextAction();
    }
  }

  protected class PreviousAction extends DialogWrapperAction {
    protected PreviousAction() {
      super(IdeBundle.message("button.wizard.previous"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.doPreviousAction();
    }
  }

  protected class FinishAction extends DialogWrapperAction {
    protected FinishAction() {
      super(IdeBundle.message("button.finish"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.doFinishAction();
    }
  }
}
