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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.view.StatefulButtonMessage;
import com.android.tools.idea.structure.services.DeveloperService;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to add state to action buttons. State should generally be based off of data available from a {@link DeveloperService}
 * instance or from an independent source. You _may_ get the containing project from the DeveloperService instance in order to instantiate a
 * project scoped service but this is generally discouraged. Please reach out to the Android Studio team about adding capabilities before
 * directly accessing the Project object.
 */
public interface AssistActionStateManager {
  ExtensionPointName<AssistActionStateManager> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.assistant.actionStateManager");

  /**
   * Gets the opaque id for a state manager. Corresponds to the stateManager field in {@code TutorialBundle.Action} and is used to identify
   * which extension to use.
   */
  @NotNull
  String getId();

  /**
   * Allows you to initialize your instance of your manager if necessary.
   */
  void init(@NotNull Project project, @NotNull ActionData actionData);

  /**
   * Returns the current state of the action and any presentation data.
   */
  ActionState getState(@NotNull Project project, @NotNull ActionData actionData);

  /**
   * Gets the display for a given action button when the action may not be completed. For example, if the action adds a dependency, this
   * would confirm that the dependency has already been added. It may also be used for things like permanent failures.
   *
   * When null and isCompletable returns false, defaults to disabling the button.
   */
  @Nullable
  StatefulButtonMessage getStateDisplay(@NotNull Project project,
                                        @NotNull ActionData actionData,
                                        @Nullable/*ignored if null*/ String message);

  enum ActionState {
    INCOMPLETE, // Action is not complete.
    COMPLETE, // Action is complete and may not be run again.
    PARTIALLY_COMPLETE, // Action is complete for a subset of cases and may still be run again.
    IN_PROGRESS,// TODO: Use this to disable the button and indicate the state is being determined.
    ERROR, // There is a pre-existing error condition that prevents completion.
    NOT_APPLICABLE // [stub-used in future] Similar to INCOMPLETE but action completion doesn't make sense. e.g. Trigger debug event.
  }
}
