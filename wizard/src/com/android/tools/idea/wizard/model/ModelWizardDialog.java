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

import static com.android.tools.idea.observable.expressions.bool.BooleanExpressions.not;

import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog container which drives an underlying {@link ModelWizard}, decorating it with UI.
 * <p/>
 * Note that the dialog owns responsibility for starting the wizard. If you start it externally
 * first, this dialog will throw an exception on {@link #show()}.
 */
public final class ModelWizardDialog extends DialogWrapper implements ModelWizard.WizardListener {
  public enum CancellationPolicy {
    ALWAYS_CAN_CANCEL,
    CAN_CANCEL_UNTIL_CAN_FINISH
  }

  private CancellationPolicy myCancellationPolicy = CancellationPolicy.ALWAYS_CAN_CANCEL;

  @SuppressWarnings("NotNullFieldNotInitialized") // Always NotNull but initialized indirectly in constructor
  @NotNull
  private ModelWizard myActiveWizard;

  private final BindingsManager myDialogBindings = new BindingsManager();
  private final BindingsManager myActiveWizardBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final BoolProperty myCanGoBack = new BoolValueProperty();
  private final BoolProperty myCanGoForward = new BoolValueProperty();
  private final BoolProperty myOnFirstStep = new BoolValueProperty();
  private final BoolProperty myOnLastStep = new BoolValueProperty();
  private final OptionalProperty<Action> myExtraAction = new OptionalValueProperty<>();

  private final JPanel mainPanel = new JPanel(new BorderLayout());

  @Nullable private CustomLayout myCustomLayout;
  @Nullable private URL myHelpUrl;

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[]{new StepActionWrapper()};
  }

  public ModelWizardDialog(@NotNull ModelWizard wizard,
                           @NotNull String title,
                           @Nullable CustomLayout customLayout,
                           @Nullable Project project,
                           @Nullable URL helpUrl,
                           @NotNull IdeModalityType modalityType,
                           @NotNull CancellationPolicy cancellationPolicy) {
    super(project, true, modalityType);
    init(wizard, title, customLayout, helpUrl, cancellationPolicy);
  }

  public ModelWizardDialog(@NotNull ModelWizard wizard,
                           @NotNull String title,
                           @NotNull Component parent,
                           @Nullable CustomLayout customLayout,
                           @Nullable URL helpUrl,
                           @NotNull CancellationPolicy cancellationPolicy) {
    super(parent, true);
    init(wizard, title, customLayout, helpUrl, cancellationPolicy);
  }

  private void init(@NotNull ModelWizard wizard,
                    @NotNull String title,
                    @Nullable CustomLayout customLayout,
                    @Nullable URL helpUrl,
                    @NotNull CancellationPolicy cancellationPolicy) {
    myCustomLayout = customLayout;
    myHelpUrl = helpUrl;
    myCancellationPolicy = cancellationPolicy;
    setTitle(title);
    setModelWizard(wizard);


    init();

    if (customLayout != null) {
      Disposer.register(wizard, customLayout);
    }
  }

  private void clearActiveWizard() {
    // Only null while initialized indirectly in constructor
    //noinspection ConstantConditions
    if (myActiveWizard != null) {
      myActiveWizardBindings.releaseAll();
      myActiveWizard.removeResultListener(this);
      Disposer.dispose(myActiveWizard);
    }
  }

  /**
   * Disposes the current wizard and replaced it by the specified one.
   * @param wizard The new wizard to use.
   */
  public void setModelWizard(@NotNull ModelWizard wizard) {
    clearActiveWizard();

    myActiveWizard = wizard;
    myActiveWizard.addResultListener(this);

    myActiveWizardBindings.bind(myCanGoBack, myActiveWizard.canGoBack());
    myActiveWizardBindings.bind(myCanGoForward, myActiveWizard.canGoForward());
    myActiveWizardBindings.bind(myOnFirstStep, myActiveWizard.onFirstStep());
    myActiveWizardBindings.bind(myOnLastStep, myActiveWizard.onLastStep());
    myActiveWizardBindings.bind(myExtraAction, myActiveWizard.getExtraAction());

    mainPanel.removeAll();
    mainPanel.add(myActiveWizard.getContentPanel(), BorderLayout.CENTER);
  }

  @Override
  protected void dispose() {
    super.dispose();
    myDialogBindings.releaseAll();
    myListeners.releaseAll();
    clearActiveWizard();
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT; // Remove padding from this dialog, we'll fill it in ourselves
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myCustomLayout == null ? mainPanel : myCustomLayout.decorate(myActiveWizard.getTitleHeader(), mainPanel);
  }

  @Override
  protected void doHelpAction() {
    if (getHelpAction().isEnabled()) {
      // This should never be called unless myHelpUrl is non-null (see createActions)
      assert myHelpUrl != null;
      BrowserUtil.browse(myHelpUrl);
    }
  }

  @Override
  public void doCancelAction() {
    myActiveWizard.cancel();
    // DON'T call super.doCancelAction - that's triggered by onWizardFinished
  }

  @Override
  public void doOKAction() {
    // OK doesn't work directly. This dialog only closes when the underlying wizard closes.
    // super.doOKAction is triggered by onWizardFinished
  }

  @Override
  public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
    // Only progress when we know the underlying wizard is done. Call the super methods directly
    // since we stubbed out our local overrides.
    if (result.isFinished()) {
      super.doOKAction();
    }
    else {
      super.doCancelAction();
    }
  }

  @Override
  public void onWizardAdvanceError(@NotNull Exception e) {
    DialogEarthquakeShaker.shake(getWindow());
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    NextAction nextAction = new NextAction();
    PreviousAction prevAction = new PreviousAction();
    FinishAction finishAction = new FinishAction();
    CancelAction cancelAction = new CancelAction(myCancellationPolicy);
    getHelpAction().setEnabled(myHelpUrl != null);

    if (myHelpUrl == null) {
      if (SystemInfo.isMac) {
        return new Action[]{cancelAction, prevAction, nextAction, finishAction};
      }

      return new Action[]{prevAction, nextAction, cancelAction, finishAction};
    }
    else {
      if (SystemInfo.isMac) {
        return new Action[]{getHelpAction(), cancelAction, prevAction, nextAction, finishAction};
      }
      return new Action[]{prevAction, nextAction, cancelAction, finishAction, getHelpAction()};
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myActiveWizard.getPreferredFocusComponent();
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    final JButton button = super.createJButtonForAction(action);

    if (action instanceof ModelWizardDialogAction) {
      ModelWizardDialogAction wizardAction = (ModelWizardDialogAction)action;
      myDialogBindings.bind(new EnabledProperty(button), wizardAction.shouldBeEnabled());
      myDialogBindings.bind(new VisibleProperty(button), wizardAction.shouldBeVisible());
      myListeners.listenAndFire(wizardAction.shouldBeDefault(), isDefault -> {
        JRootPane rootPane = getRootPane();
        if (rootPane != null && isDefault) {
          rootPane.setDefaultButton(button);
        }
      });
    }

    return button;
  }

  /**
   * A layout provider which, if set, gives the wizard dialog a custom look and feel.
   * <p/>
   * By default, a wizard dialog simply displays the contents of a wizard, undecorated. However,
   * a custom look and feel lets you inject a custom theme and titlebar into your wizard.
   */
  public interface CustomLayout extends Disposable {
    @NotNull
    JPanel decorate(@NotNull ModelWizard.TitleHeader titleHeader, @NotNull JPanel innerPanel);

    Dimension getDefaultPreferredSize();

    Dimension getDefaultMinSize();
  }

  /**
   * The model wizard exposes various boolean properties representing its current navigable state.
   * By associating actions with those properties, we can easily bind UI buttons to them.
   */
  private abstract class ModelWizardDialogAction extends DialogWrapperAction {
    private ModelWizardDialogAction(@NotNull String name) {
      super(name);
    }

    @NotNull
    public abstract ObservableBool shouldBeEnabled();

    @NotNull
    public ObservableBool shouldBeVisible() {
      return ObservableBool.TRUE;
    }

    @NotNull
    public ObservableBool shouldBeDefault() {
      return ObservableBool.FALSE;
    }
  }

  private final class NextAction extends ModelWizardDialogAction {
    NextAction() {
      super(IdeBundle.message("button.wizard.next"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myActiveWizard.goForward();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myCanGoForward.and(not(myOnLastStep));
    }

    @NotNull
    @Override
    public ObservableBool shouldBeDefault() {
      return not(myOnLastStep);
    }

    @NotNull
    public ObservableBool shouldBeVisible() {
      return not(myOnFirstStep.and(myOnLastStep));
    }
  }

  private final class PreviousAction extends ModelWizardDialogAction {
    PreviousAction() {
      super(IdeBundle.message("button.wizard.previous"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myActiveWizard.goBack();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myCanGoBack;
    }

    @Override
    @NotNull
    public ObservableBool shouldBeVisible() {
      return not(myOnFirstStep.and(myOnLastStep));
    }
  }

  private final class FinishAction extends ModelWizardDialogAction {
    FinishAction() {
      super(IdeBundle.message("button.finish"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myActiveWizard.goForward();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myOnLastStep.and(myCanGoForward);
    }

    @NotNull
    @Override
    public ObservableBool shouldBeDefault() {
      return myOnLastStep;
    }
  }

  private final class CancelAction extends ModelWizardDialogAction {
    private final CancellationPolicy myCancellationPolicy;

    private CancelAction(@NotNull CancellationPolicy cancellationPolicy) {
      super(IdeBundle.message("button.cancel"));
      myCancellationPolicy = cancellationPolicy;
    }

    @Override
    protected void doAction(ActionEvent e) {
      doCancelAction();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      switch (myCancellationPolicy) {
        case CAN_CANCEL_UNTIL_CAN_FINISH:
          return not(myOnLastStep.and(myCanGoForward));
        case ALWAYS_CAN_CANCEL:
        default:
          return ObservableBool.TRUE;
      }
    }
  }

  /**
   * A {@link ModelWizardDialogAction} that behaves (in terms of name, enabled status, and actual action implementation) like the
   * {@link ModelWizardStep#getExtraAction() extra action} of the current step in our wizard. If the current step has no extra action,
   * {@link #shouldBeVisible()} will be false.
   */
  private final class StepActionWrapper extends ModelWizardDialogAction {
    private final BoolValueProperty myEnabled = new BoolValueProperty(false);
    private PropertyChangeListener myActionListener;


    @NotNull
    @Override
    public ObservableBool shouldBeVisible() {
      return myExtraAction.isPresent();
    }

    private StepActionWrapper() {
      super("");

      InvalidationListener extraActionChangedListener = new InvalidationListener() {
        Action myActiveAction = null;

        @Override
        public void onInvalidated() {
          if (myActiveAction != null && myActionListener != null) {
            myActiveAction.removePropertyChangeListener(myActionListener);
          }
          myActiveAction = myExtraAction.getValueOrNull();
          if (myActiveAction != null) {
            StepActionWrapper.this.putValue(NAME, myActiveAction.getValue(NAME));
            myActionListener = evt -> {
              if ("enabled".equals(evt.getPropertyName())) {
                myEnabled.set((Boolean)evt.getNewValue());
              }
            };
            myActiveAction.addPropertyChangeListener(myActionListener);
            myEnabled.set(myActiveAction.isEnabled());
          }
          else {
            myActionListener = null;
          }
        }
      };
      myExtraAction.addListener(extraActionChangedListener);
      extraActionChangedListener.onInvalidated();
    }

    @NotNull
    @Override
    public ObservableBool shouldBeEnabled() {
      return myEnabled;
    }

    @Override
    protected void doAction(ActionEvent e) {
      assert myExtraAction.get().isPresent();
      myExtraAction.getValue().actionPerformed(e);
    }
  }
}
