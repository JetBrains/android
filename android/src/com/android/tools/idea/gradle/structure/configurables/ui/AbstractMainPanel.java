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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;

public abstract class AbstractMainPanel extends JPanel implements Disposable, Place.Navigator {
  @NotNull private final PsProject myProject;
  @NotNull private final PsContext myContext;

  private boolean myShowModulesDropDown;
  private JComponent myModulesToolbar;
  private History myHistory;

  protected AbstractMainPanel(@NotNull PsContext context) {
    this(context, Collections.emptyList());
  }

  protected AbstractMainPanel(@NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(new BorderLayout());
    myProject = context.getProject();
    myContext = context;

    myShowModulesDropDown = PsUISettings.getInstance().MODULES_LIST_MINIMIZE;
    if (myShowModulesDropDown) {
      createAndAddModulesAction(extraTopModules);
    }
    PsUISettings.getInstance().addListener(settings -> {
      if (settings.MODULES_LIST_MINIMIZE != myShowModulesDropDown) {
        myShowModulesDropDown = settings.MODULES_LIST_MINIMIZE;
        if (myShowModulesDropDown) {
          createAndAddModulesAction(extraTopModules);
        }
        else {
          removeModulesAction();
        }
      }
    }, this);
  }

  private void createAndAddModulesAction(@NotNull List<PsModule> extraTopModules) {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new ModulesComboBoxAction(myContext, extraTopModules));

    AnAction restoreModuleListAction = new DumbAwareAction("Restore 'Modules' List", "", AllIcons.Actions.MoveTo2) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        PsUISettings settings = PsUISettings.getInstance();
        settings.MODULES_LIST_MINIMIZE = myShowModulesDropDown = false;
        settings.fireUISettingsChanged();
        removeModulesAction();
      }
    };
    actions.add(restoreModuleListAction);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    myModulesToolbar = toolbar.getComponent();
    myModulesToolbar.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    add(myModulesToolbar, BorderLayout.NORTH);
  }

  private void removeModulesAction() {
    if (myModulesToolbar != null) {
      remove(myModulesToolbar);
      myModulesToolbar = null;
      revalidateAndRepaint(this);
    }
  }

  @NotNull
  protected PsProject getProject() {
    return myProject;
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @Override
  public void setHistory(History history) {
    myHistory = history;
  }

  @Nullable
  protected History getHistory() {
    return myHistory;
  }
}
