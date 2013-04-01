/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.intellij.openapi.actionSystem.*;

public class AndroidIdeSpecificInitializer implements Runnable {
  public static final String NEW_NEW_PROJECT_WIZARD = "android.newProjectWizard";

  @Override
  public void run() {
    if (Boolean.getBoolean(NEW_NEW_PROJECT_WIZARD)) {
      fixNewProjectActions();
    }
  }

  private static void fixNewProjectActions() {
    // TODO: This is temporary code. We should build out our own menu set and welcome screen exactly how we want. In the meantime,
    // unregister IntelliJ's version of the project actions and manually register our own.

    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
  }

  private static void replaceAction(String actionId, AnAction newAction) {
    ActionManager am = ActionManager.getInstance();
    AnAction oldAction = am.getAction(actionId);
    newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
    am.unregisterAction(actionId);
    am.registerAction(actionId, newAction);
  }
}
