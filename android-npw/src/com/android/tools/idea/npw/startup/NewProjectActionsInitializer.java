/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.startup;

import com.android.tools.idea.npw.actions.AndroidImportModuleAction;
import com.android.tools.idea.npw.actions.AndroidNewModuleAction;
import com.android.tools.idea.npw.actions.AndroidNewModuleInGroupAction;
import com.android.tools.idea.npw.actions.AndroidNewProjectAction;
import com.android.tools.idea.startup.Actions;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import org.jetbrains.annotations.NotNull;

public class NewProjectActionsInitializer implements ActionConfigurationCustomizer {
  @Override
  public void customize(@NotNull ActionManager actionManager) {
    setUpNewProjectActions(actionManager);
    setUpWelcomeScreenActions(actionManager);
  }

  private static void setUpNewProjectActions(ActionManager actionManager) {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    Actions.replaceAction(actionManager, "NewProject", new AndroidNewProjectAction());
    Actions.replaceAction(actionManager, "NewModule", new AndroidNewModuleAction());
    Actions.replaceAction(actionManager, "NewModuleInGroup", new AndroidNewModuleInGroupAction());
    Actions.replaceAction(actionManager, "ImportModule", new AndroidImportModuleAction());
  }

  private static void setUpWelcomeScreenActions(ActionManager actionManager) {
    // Update the Welcome Screen actions
    Actions.replaceAction(actionManager, "WelcomeScreen.CreateNewProject", new AndroidNewProjectAction("New Project"));
  }
}
