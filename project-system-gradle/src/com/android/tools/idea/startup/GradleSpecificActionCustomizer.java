/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.startup;

import static com.intellij.openapi.actionSystem.Anchor.AFTER;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COMPILE;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_COMPILE_PROJECT;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_MAKE_MODULE;

import com.android.tools.idea.actions.AndroidActionGroupRemover;
import com.android.tools.idea.actions.AndroidOpenFileAction;
import com.android.tools.idea.actions.CreateLibraryFromFilesAction;
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction;
import com.android.tools.idea.gradle.actions.AssembleIdeaModuleAction;
import com.android.utils.Pair;
import com.intellij.ide.projectView.actions.MarkRootGroup;
import com.intellij.ide.projectView.impl.MoveModuleToGroupTopLevel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.NotNull;

public class GradleSpecificActionCustomizer implements ActionConfigurationCustomizer {

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    setUpNewProjectActions(actionManager);
    setUpWelcomeScreenActions(actionManager);
    disableUnsupportedAction(actionManager);
    replaceProjectPopupActions(actionManager);
    setUpMakeActions(actionManager);
    setUpGradleViewToolbarActions(actionManager);
  }

  private static void disableUnsupportedAction(ActionManager actionManager) {
    actionManager.unregisterAction("LoadUnloadModules"); // private LoadUnloadModulesActionKt.ACTION_ID
  }

  // The original actions will be visible only on plain IDEA projects.
  private static void setUpMakeActions(ActionManager actionManager) {
    // 'Build' > 'Make Project' action
    Actions.hideAction(actionManager, "CompileDirty");

    // 'Build' > 'Make Modules' action
    // We cannot simply hide this action, because of a NPE.
    Actions.replaceAction(actionManager, ACTION_MAKE_MODULE, new AssembleIdeaModuleAction());

    // 'Build' > 'Rebuild' action
    Actions.hideAction(actionManager, ACTION_COMPILE_PROJECT);

    // 'Build' > 'Compile Modules' action
    Actions.hideAction(actionManager, ACTION_COMPILE);

    // Additional 'Build' action from com.jetbrains.cidr.execution.build.CidrBuildTargetAction
    Actions.hideAction(actionManager, "Build");
    Actions.hideAction(actionManager, "Groovy.CheckResources.Rebuild");
    Actions.hideAction(actionManager, "Groovy.CheckResources.Make");
    Actions.hideAction(actionManager, "Groovy.CheckResources");
    Actions.hideAction(actionManager, "CompileFile");
  }

  private static void setUpGradleViewToolbarActions(ActionManager actionManager) {
    Actions.hideAction(actionManager, "ExternalSystem.RefreshAllProjects");
    Actions.hideAction(actionManager, "ExternalSystem.SelectProjectDataToImport");
    Actions.hideAction(actionManager, "ExternalSystem.DetachProject");
    Actions.hideAction(actionManager, "ExternalSystem.AttachProject");
  }

  private static void setUpNewProjectActions(ActionManager actionManager) {
    // Unregister IntelliJ's version of the project actions and manually register our own.
    Actions.replaceAction(actionManager, "OpenFile", new AndroidOpenFileAction());
    Actions.replaceAction(actionManager, "CreateLibraryFromFile", new CreateLibraryFromFilesAction());

    Actions.hideAction(actionManager, "AddFrameworkSupport");
    Actions.hideAction(actionManager, "BuildArtifact");
  }

  private static void setUpWelcomeScreenActions(ActionManager actionManager) {
    // Update the Welcome Screen actions
    Actions.replaceAction(actionManager, "WelcomeScreen.OpenProject", new AndroidOpenFileAction("Open"));
    Actions.replaceAction(actionManager, "WelcomeScreen.Configure.ProjectStructure", new AndroidTemplateProjectStructureAction("Default Project Structure..."));
    Actions.replaceAction(actionManager, "TemplateProjectStructure", new AndroidTemplateProjectStructureAction("Default Project Structure..."));
  }

  private static void replaceProjectPopupActions(ActionManager actionManager) {
    Deque<Pair<DefaultActionGroup, AnAction>> stack = new ArrayDeque<>();
    stack.add(Pair.of(null, actionManager.getAction("ProjectViewPopupMenu")));
    while (!stack.isEmpty()) {
      Pair<DefaultActionGroup, AnAction> entry = stack.pop();
      DefaultActionGroup parent = entry.getFirst();
      AnAction action = entry.getSecond();
      if (action instanceof DefaultActionGroup) {
        DefaultActionGroup actionGroup = (DefaultActionGroup)action;
        for (AnAction child : actionGroup.getChildActionsOrStubs()) {
          stack.push(Pair.of(actionGroup, child));
        }
      }

      if (action instanceof MoveModuleToGroupTopLevel) {
        parent.remove(action, actionManager);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Move Module to Group"),
                   new Constraints(AFTER, "OpenModuleSettings"), actionManager);
      }
      else if (action instanceof MarkRootGroup) {
        parent.remove(action, actionManager);
        parent.add(new AndroidActionGroupRemover((ActionGroup)action, "Mark Directory As"),
                   new Constraints(AFTER, "OpenModuleSettings"), actionManager);
      }
    }
  }
}
