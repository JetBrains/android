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
package com.android.tools.idea.npw.cpp;

import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Joiner;
import com.intellij.openapi.Disposable;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBLabel;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * Step for configuring native (C++) related parameters in new project wizard
 */
public class ConfigureCppSupportStep extends DynamicWizardStepWithDescription {
  private JPanel myPanel;
  private JComboBox<CppStandardType> myCppStandardCombo;
  private JCheckBox myExceptionSupportCheck;
  private JBLabel myIconLabel;
  private JComboBox<RuntimeLibraryType> myRuntimeLibraryCombo;
  private JCheckBox myRttiSupportCheck;
  private JBLabel myRuntimeLibraryLabel;

  public ConfigureCppSupportStep(@NotNull Disposable disposable) {
    super(disposable);
    myPanel.setBorder(createBodyBorder());
    setBodyComponent(myPanel);
    myIconLabel.setIcon(AndroidIcons.Wizards.CppConfiguration);
  }

  @Override
  public void init() {
    super.init();

    //noinspection unchecked
    myRuntimeLibraryCombo.setModel(new CollectionComboBoxModel<>(Arrays.asList(RuntimeLibraryType.values())));
    //noinspection unchecked
    myCppStandardCombo.setModel(new CollectionComboBoxModel<>(Arrays.asList(CppStandardType.values())));

    // Connect widgets to wizard keys
    register(WizardConstants.CPP_RUNTIME_LIBRARY_TYPE_KEY, myRuntimeLibraryCombo);
    register(WizardConstants.CPP_STANDARD_TYPE_KEY, myCppStandardCombo);
    register(WizardConstants.CPP_ENABLE_EXCEPTIONS_KEY, myExceptionSupportCheck);
    register(WizardConstants.CPP_ENABLE_RTTI_KEY, myRttiSupportCheck);

    // Put default values for selected C++ stardard version and runtime library type
    myState.put(WizardConstants.CPP_RUNTIME_LIBRARY_TYPE_KEY, RuntimeLibraryType.GABIXX);
    myState.put(WizardConstants.CPP_STANDARD_TYPE_KEY, CppStandardType.DEFAULT);

    // Convert runtime library type enum to string for consumption inside Freemarker templates
    registerValueDeriver(WizardConstants.CPP_RUNTIME_LIBRARY_TYPE_STRING_KEY, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
        return makeSetOf(WizardConstants.CPP_RUNTIME_LIBRARY_TYPE_KEY);
      }

      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable String currentValue) {
        final RuntimeLibraryType type = state.get(WizardConstants.CPP_RUNTIME_LIBRARY_TYPE_KEY);
        return type == null ? null : type.getGradleName();
      }
    });

    // Combine standard combo box and checkboxes into line of parameters passed to C++ compiler
    registerValueDeriver(WizardConstants.CPP_FLAGS_KEY, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
        return makeSetOf(WizardConstants.CPP_STANDARD_TYPE_KEY, WizardConstants.CPP_ENABLE_EXCEPTIONS_KEY, WizardConstants.CPP_ENABLE_RTTI_KEY);
      }

      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable String currentValue) {
        final ArrayList<Object> flags = new ArrayList<>();
        flags.add(state.getNotNull(WizardConstants.CPP_STANDARD_TYPE_KEY, CppStandardType.DEFAULT).getCompilerFlag());
        flags.add(state.getNotNull(WizardConstants.CPP_ENABLE_RTTI_KEY, false) ? "-frtti" : null);
        flags.add(state.getNotNull(WizardConstants.CPP_ENABLE_EXCEPTIONS_KEY, false) ? "-fexceptions" : null);
        return Joiner.on(' ').skipNulls().join(flags);
      }
    });

    // TODO: un-hide UI components once dsl support would be available
    myRuntimeLibraryCombo.setVisible(false);
    myRuntimeLibraryLabel.setVisible(false);
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Customize C++ Support";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Customize C++ Support";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCppStandardCombo;
  }
}
