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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;

/**
 * This step allows the user to select which type of module they want to create.
 */
public class ChooseModuleTypeStep extends DynamicWizardStepWithHeaderAndDescription {
  private JPanel myPanel;
  private JBList myModuleTypeList;

  public ChooseModuleTypeStep(@Nullable Disposable parentDisposable) {
    super("Choose Module Type", "Select an option below to create your new module", null, parentDisposable);
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    super.init();
    populateModuleTypesList();
  }

  private void populateModuleTypesList() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    List<ModuleType> moduleTypes = Lists.newArrayList();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null) {
        continue;
      }
      if (metadata.getFormFactor() != null) {
        final FormFactor formFactor = FormFactor.get(metadata.getFormFactor());
        if (formFactor == null) {
          continue;
        }
        register(FormFactorUtils.getInclusionKey(formFactor), myModuleTypeList, new ComponentBinding<Boolean, JBList>() {
          private final FormFactor myFormFactor = formFactor;
          @Nullable
          @Override
          public Boolean getValue(@NotNull JBList component) {
            ModuleType type = (ModuleType)component.getSelectedValue();
            return type != null && type.formFactor != null && type.formFactor.equals(myFormFactor);
          }
        });
        moduleTypes.addAll(getModuleTypes(metadata, formFactor));
      } else {
        // TODO: add import paths here
        // moduleTypes.add(new ModuleType("Import Name", false));
      }
    }
    myModuleTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myModuleTypeList.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(moduleTypes)));

    register(SELECTED_MODULE_TYPE_KEY, myModuleTypeList, new ComponentBinding<ModuleType, JBList>() {
      @Override
      public void setValue(@Nullable ModuleType newValue, @NotNull JBList component) {
        component.setSelectedValue(newValue, true);
      }

      @Nullable
      @Override
      public ModuleType getValue(@NotNull JBList component) {
        return (ModuleType)component.getSelectedValue();
      }
    });

    myModuleTypeList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myModuleTypeList);
      }
    });

    if (!myModuleTypeList.isEmpty()) {
      myModuleTypeList.setSelectedIndex(0);
    }
  }

  @Override
  public boolean commitStep() {
    ModuleType selected = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (selected != null) {
      for (Key<?> k : selected.customValues.keySet()) {
        myState.unsafePut(k, selected.customValues.get(k));
      }
    }
    return true;
  }

  @NotNull
  private static Collection<ModuleType> getModuleTypes(@NotNull TemplateMetadata metadata, @NotNull FormFactor formFactor) {
    if (formFactor.equals(FormFactor.MOBILE)) {
      ModuleType androidApplication = new ModuleType(metadata, formFactor, formFactor.toString() + " Application", true);
      androidApplication.customValues.put(WizardConstants.IS_LIBRARY_KEY, false);
      ModuleType androidLibrary = new ModuleType(metadata, formFactor, "Android Library", true);
      androidLibrary.customValues.put(WizardConstants.IS_LIBRARY_KEY, true);
      return ImmutableSet.of(androidApplication, androidLibrary);
    } else {
      return ImmutableSet.of(new ModuleType(metadata, formFactor, metadata.getTitle(), true));
    }
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Choose Module Type Step";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myModuleTypeList;
  }
}
