/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.cpp;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Joiner;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * Step for configuring native (C++) related parameters in new project wizard
 */
public class ConfigureCppSupportStep extends ModelWizardStep<NewProjectModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JBScrollPane myRoot;
  private JPanel myRootPanel;
  private JComboBox<CppStandardType> myCppStandardCombo;
  private JBLabel myIconLabel;
  private HyperlinkLabel myDocumentationLink;

  public ConfigureCppSupportStep(@NotNull NewProjectModel model) {
    super(model, message("android.wizard.activity.add.cpp"));

    myIconLabel.setIcon(AndroidIcons.Wizards.CppConfiguration);
    myDocumentationLink.setHyperlinkText(message("android.wizard.activity.add.cpp.docslinktext"));
    myDocumentationLink.setHyperlinkTarget("https://developer.android.com/ndk/guides/cpp-support.html");

    myRoot = StudioWizardStepPanel.wrappedWithVScroll(myRootPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRoot);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myCppStandardCombo.setModel(new CollectionComboBoxModel<>(Arrays.asList(CppStandardType.values())));
    OptionalProperty<CppStandardType> cppStandard = new OptionalValueProperty<>(CppStandardType.DEFAULT);
    myBindings.bindTwoWay(new SelectedItemProperty<>(myCppStandardCombo), cppStandard);

    myListeners.listenAll(cppStandard).withAndFire(() -> {
      final ArrayList<Object> flags = new ArrayList<>();
      flags.add(cppStandard.getValueOr(CppStandardType.DEFAULT).getCompilerFlag());

      getModel().getCppFlags().set(Joiner.on(' ').skipNulls().join(flags));
    });
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRoot;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myCppStandardCombo;
  }

  @Override
  protected boolean shouldShow() {
    return getModel().getEnableCppSupport().get();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
