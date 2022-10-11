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
package com.android.tools.idea.gradle.structure.configurables.ui;

import static icons.StudioIcons.Shell.Filetree.ANDROID_MODULE;

import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ui.LabeledComboBoxAction;
import com.android.utils.HtmlBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class ModulesComboBoxAction extends LabeledComboBoxAction {
  @NotNull private final PsContext myContext;
  @NotNull private final BasePerspectiveConfigurable myBasePerspective;

  public ModulesComboBoxAction(@NotNull PsContext context,
                               @NotNull BasePerspectiveConfigurable basePerspective) {
    super("Module: ");
    myContext = context;
    myBasePerspective = basePerspective;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    PsModule selectedModule = myBasePerspective.getSelectedModule();
    presentation.setIcon(selectedModule != null ? selectedModule.getIcon() : ANDROID_MODULE);
    presentation.setText(selectedModule != null ? selectedModule.getName() : null);
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();

    for (PsModule module : myBasePerspective.getExtraModules()) {
      group.add(new ModuleAction(module));
    }
    group.addSeparator();

    myContext.getProject().getModules().stream()
      .filter(PsModule::isDeclared)
      .forEach(module -> group.add(new ModuleAction(module)));
    return group;
  }

  private class ModuleAction extends DumbAwareAction {
    @NotNull private final String myModuleGradlePath;

    ModuleAction(@NotNull PsModule module) {
      super(fullPath(module), "", module.getIcon());
      myModuleGradlePath = module.getGradlePath();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myBasePerspective.selectModule(myModuleGradlePath);
    }
  }

  @NotNull
  private static String fullPath(@NotNull PsModule module) {
    String gradlePath = module.getGradlePath();
    if (module instanceof PsAllModulesFakeModule) {
      return module.getName();
    }
    else {
      String parentPath = GradleUtil.getParentModulePath(gradlePath);
      return new HtmlBuilder()
        .openHtmlBody()
        .add(parentPath)
        .add(":")
        .addBold(module.getName())
        .closeHtmlBody()
        .getHtml();
    }
  }
}
