/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.xml.XmlTag;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnableInstantAppsSupportDialog extends DialogWrapper {
  private JComboBox<Module> myBaseModuleCombo;
  private JPanel myMainPanel;

  @Nullable private Module myBaseModule;

  protected EnableInstantAppsSupportDialog(@NotNull Module selectedModule) {
    super(false);

    myBaseModuleCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(@NotNull JList list, Object value, int idx, boolean isSelected, boolean hasFocus) {
        Module module = (Module)value;
        if (module == null) {
          setText(message("android.wizard.module.config.new.base.missing"));
        }
        else {
          setIcon(ModuleType.get(module).getIcon());
          setText(module.getName());
        }
        return this;
      }
    });

    AndroidProjectInfo.getInstance(selectedModule.getProject()).getAllModulesOfProjectType(PROJECT_TYPE_APP)
      .stream()
      .filter(module -> AndroidModuleModel.get(module) != null)
      .forEach(module -> myBaseModuleCombo.addItem(module));

    myBaseModuleCombo.setSelectedItem(selectedModule);

    setTitle(message("android.wizard.module.enable.instant"));

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    myBaseModule = (Module)myBaseModuleCombo.getSelectedItem();
    return myBaseModule == null ? new ValidationInfo(message("android.wizard.module.new.dynamic.select.base"), myBaseModuleCombo) : null;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    assert myBaseModule != null; // We should not have a null base module after validation

    AndroidFacet facet = AndroidFacet.getInstance(myBaseModule);
    if (facet == null) {
      return;
    }

    final Manifest manifest = Manifest.getMainManifest(facet);
    if (manifest == null) {
      return;
    }

    final XmlTag manifestTag = manifest.getXmlTag();
    if (manifestTag == null) {
      return;
    }

    EnableInstantAppsSupportAction.addInstantAppSupportToManifest(manifestTag);
  }
}
