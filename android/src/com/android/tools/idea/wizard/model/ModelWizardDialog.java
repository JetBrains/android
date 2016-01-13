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

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.ObservableString;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.android.tools.idea.ui.properties.swing.EnabledProperty;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions.not;

/**
 * A dialog container which drives an underlying {@link ModelWizard}, decorating it with UI.
 * <p/>
 * Note that the dialog owns responsibility for starting the wizard. If you start it externally
 * first, this dialog will throw an exception on {@link #show()}.
 */
public final class ModelWizardDialog extends DialogWrapper {

  @SuppressWarnings("NullableProblems") // Always NotNull but initialized indirectly in constructor
  @NotNull
  private ModelWizard myWizard;

  private BindingsManager myBindings = new BindingsManager();
  private ListenerManager myListeners = new ListenerManager();

  @Nullable private CustomLayout myCustomLayout;

  public ModelWizardDialog(@NotNull ModelWizard wizard,
                           @NotNull String title,
                           @Nullable CustomLayout customLayout,
                           @Nullable Project project,
                           @NotNull IdeModalityType modalityType) {
    super(project, true, modalityType);
    init(wizard, title, customLayout);
  }

  public ModelWizardDialog(@NotNull ModelWizard wizard,
                           @NotNull String title,
                           @NotNull Component parent,
                           @Nullable CustomLayout customLayout) {
    super(parent, true);
    init(wizard, title, customLayout);
  }

  private void init(@NotNull ModelWizard wizard, @NotNull String title, @Nullable CustomLayout customLayout) {
    Disposer.register(getDisposable(), wizard);
    myWizard = wizard;
    myCustomLayout = customLayout;
    setTitle(title);

    init();

    if (customLayout != null) {
      Disposer.register(wizard, customLayout);
    }
  }

  @Override
  protected void dispose() {
    super.dispose();
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  /**
   * {@inheritDoc}
   * <p/>
   * As a side effect, this starts the wizard.
   */
  @Override
  public void show() {
    // TODO: Why is this necessary? Why is DialogWrapper ignoring setSize unless I do this?
    getContentPanel().setMinimumSize(getSize());
    super.show();
  }

  @NotNull
  @Override
  protected DialogStyle getStyle() {
    return DialogStyle.COMPACT; // Remove padding from this dialog, we'll fill it in ourselves
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel wizardContent = myWizard.getContentPanel();
    JPanel centerPanel;
    if (myCustomLayout != null) {
      centerPanel = myCustomLayout.decorate(myWizard.title(), wizardContent);
    }
    else {
      centerPanel = wizardContent;
    }
    return centerPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    NextAction nextAction = new NextAction();
    PreviousAction prevAction = new PreviousAction();
    FinishAction finishAction = new FinishAction();

    if (getHelpId() == null) {
      if (SystemInfo.isMac) {
        return new Action[]{getCancelAction(), prevAction, nextAction, finishAction};
      }

      return new Action[]{prevAction, nextAction, getCancelAction(), finishAction};
    }
    else {
      if (SystemInfo.isMac) {
        return new Action[]{getHelpAction(), getCancelAction(), prevAction, nextAction, finishAction};
      }
      return new Action[]{prevAction, nextAction, getCancelAction(), finishAction, getHelpAction()};
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myWizard.getPreferredFocusComponent();
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    final JButton button = super.createJButtonForAction(action);

    if (action instanceof ModelWizardDialogAction) {
      ModelWizardDialogAction wizardAction = (ModelWizardDialogAction)action;
      myBindings.bind(new EnabledProperty(button), wizardAction.shouldBeEnabled());
      myListeners.listenAndFire(wizardAction.shouldBeDefault(), new Consumer<Boolean>() {
        @Override
        public void consume(Boolean isDefault) {
          JRootPane rootPane = getRootPane();
          if (rootPane != null && isDefault) {
            rootPane.setDefaultButton(button);
          }
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
    JPanel decorate(@NotNull ObservableString title, @NotNull JPanel innerPanel);
  }

  /**
   * The model wizard exposes various boolean properties representing its current navigable state.
   * By associating actions with those properties, we can easily bind UI buttons to them.
   */
  private abstract class ModelWizardDialogAction extends DialogWrapperAction {
    public ModelWizardDialogAction(@NotNull String name) {
      super(name);
    }

    @NotNull
    public abstract ObservableBool shouldBeEnabled();

    @NotNull
    public ObservableBool shouldBeDefault() {
      return BooleanExpressions.alwaysFalse();
    }
  }

  private final class NextAction extends ModelWizardDialogAction {
    NextAction() {
      super(IdeBundle.message("button.wizard.next"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.goForward();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myWizard.canGoForward().and(not(myWizard.onLastStep()));
    }

    @NotNull
    @Override
    public ObservableBool shouldBeDefault() {
      return not(myWizard.onLastStep());
    }
  }

  private final class PreviousAction extends ModelWizardDialogAction {
    PreviousAction() {
      super(IdeBundle.message("button.wizard.previous"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.goBack();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myWizard.canGoBack();
    }
  }

  private final class FinishAction extends ModelWizardDialogAction {
    FinishAction() {
      super(IdeBundle.message("button.finish"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myWizard.goForward();
      doOKAction();
    }

    @Override
    @NotNull
    public ObservableBool shouldBeEnabled() {
      return myWizard.onLastStep().and(myWizard.canGoForward());
    }

    @NotNull
    @Override
    public ObservableBool shouldBeDefault() {
      return myWizard.onLastStep();
    }
  }
}
