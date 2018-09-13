/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.template;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.platform.NavigationType;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.ui.ComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConfigureNavigationTypeStep extends ModelWizardStep<NewProjectModel> {

  private final NewProjectModuleModel myProjectModuleModel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private JComboBox<NavigationType> myNavigationType;
  private JLabel myNavigationIcon;
  private JLabel myNavigationDetails;

  public ConfigureNavigationTypeStep(@NotNull NewProjectModel model,
                                     @NotNull NewProjectModuleModel projectModuleModel,
                                     @NotNull String title) {
    super(model, title);
    myProjectModuleModel = projectModuleModel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myListeners.receive(getModel().navigationType(), navType -> {
      if (navType.isPresent()) {
        myNavigationIcon.setIcon(navType.get().getIcon());
        myNavigationDetails.setText(navType.get().getDetails());
      }
    });
    myBindings.bind(getModel().navigationType(), new SelectedItemProperty<>(myNavigationType));
  }

  @Override
  protected boolean shouldShow() {
    return myProjectModuleModel.includeNavController().get() &&
           myProjectModuleModel.formFactor().isEqualTo(FormFactor.MOBILE).get();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myNavigationType;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return getModel().navigationType().isPresent();
  }

  private void createUIComponents() {
    myNavigationType = new ComboBox<>(new DefaultComboBoxModel<>(NavigationType.valuesExceptNone()));
    myNavigationType.setToolTipText(message("android.wizard.activity.navigation.configure.tooltip"));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
