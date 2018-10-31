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
package com.android.tools.idea.npw.dynamicapp;

import static com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBLabel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigureModuleDownloadOptionsStep extends ModelWizardStep<DynamicFeatureModel> {
  @NotNull
  private final ValidatorPanel myValidatorPanel;
  @NotNull
  private final BindingsManager myBindings = new BindingsManager();
  @NotNull
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  @SuppressWarnings("unused") private JBLabel myFeatureTitleLabel;
  private JTextField myFeatureTitle;
  @SuppressWarnings("unused") private JCheckBox myFusingCheckBox;
  private JComboBox<DownloadInstallType> myInstallationOptionCombo;

  public ConfigureModuleDownloadOptionsStep(@NotNull DynamicFeatureModel model) {
    super(model, message("android.wizard.module.new.dynamic.download.options"));

    myInstallationOptionCombo.setModel(new DefaultComboBoxModel<>(DownloadInstallType.values()));

    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myRootPanel));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFeatureTitle;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  protected boolean shouldShow() {
    return !getModel().instantModule().get();
  }
}
