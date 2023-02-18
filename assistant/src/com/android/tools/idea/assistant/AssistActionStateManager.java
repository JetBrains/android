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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.view.StatefulButtonMessage;
import com.google.common.collect.Lists;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Extension point to add state to action buttons. State should generally be based off of data available from a from an independent source.
 */
public abstract class AssistActionStateManager {
  public static ExtensionPointName<AssistActionStateManager> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.assistant.actionStateManager");

  /**
   * Gets the opaque id for a state manager. Corresponds to the stateManager field in {@code TutorialBundle.Action} and is used to identify
   * which extension to use.
   */
  @NotNull
  public abstract String getId();

  /**
   * Allows you to initialize your instance of your manager if necessary.
   */
  public abstract void init(@NotNull Project project, @NotNull ActionData actionData);

  /**
   * Returns the current state of the action and any presentation data.
   */
  @NotNull
  public abstract AssistActionState getState(@NotNull Project project, @NotNull ActionData actionData);

  /**
   * Gets the display for a given action button when the action may not be completed. For example, if the action adds a dependency, this
   * would confirm that the dependency has already been added. It may also be used for things like permanent failures.
   *
   * When null and isCompletable returns false, defaults to disabling the button.
   */
  @Nullable
  public abstract StatefulButtonMessage getStateDisplay(@NotNull Project project,
                                                        @NotNull ActionData actionData,
                                                        @Nullable/*ignored if null*/ String message);

  /**
   * Causes state buttons to recheck their state. Affects all buttons within the assistant.
   */
  public void refreshDependencyState(@NotNull Project project) {
    project.getMessageBus().syncPublisher(StatefulButtonNotifier.BUTTON_STATE_TOPIC).stateUpdated();
  }


  /**
   * Refresh the currently visible TutorialCard and set the view to the desired step within the view.
   */
  public void refreshTutorialCardView(@NotNull Project project, int stepNumberToStartAt) {
    project.getMessageBus().syncPublisher(TutorialCardRefreshNotifier.TUTORIAL_CARD_TOPIC).stateUpdated(stepNumberToStartAt);
  }

  public static List<Module> getAndroidModules(@NotNull Project project) {
    return Lists.newArrayList(ModuleManager.getInstance(project).getModules())
      .stream()
      .filter(module -> AndroidFacet.getInstance(module) != null)
      .collect(Collectors.toList());
  }
}
