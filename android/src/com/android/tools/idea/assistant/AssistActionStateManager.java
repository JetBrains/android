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

import com.android.tools.idea.assistant.view.StatefulButtonMessage;
import com.android.tools.idea.structure.services.DeveloperService;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to add state to action buttons. State should generally be based off of data available from a {@link DeveloperService}
 * instance or from an independent source. You _may_ get the containing project from the DeveloperService instance in order to instantate a
 * project scoped service but this is generally discouraged. Please reach out to the Android Studio team about adding capabilities before
 * directly accessing the Project object.
 */
public interface AssistActionStateManager {
  ExtensionPointName<AssistActionStateManager> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.structure.services.actionStateManager");

  /**
   * Gets the opaque id for a state manager. Corresponds to the stateManager field in {@code TutorialBundle.Action} and is used to identify
   * which extension to use.
   *
   * TODO: Convert TutorialBundle.Action to a POJO and move it to this package.
   */
  @NotNull
  String getId();

  /**
   * Allows you to initialize your instance of your manager if necessary.
   *
   * TODO: Consider whether all calls need to provide some other argument in case the implementing party wants to have a configuration map
   * inside their manager.
   *
   * @param developerService
   */
  void init(@NotNull DeveloperService developerService);

  /**
   * Returns true if the action may still be completed. If false, {@link getStateDisplay} is called
   */
  boolean isCompletable(@NotNull DeveloperService developerService);

  /**
   * Gets the display for a given action button when the action may not be completed. For example, if the action adds a dependency, this
   * would confirm that the dependency has already been added. It may also be used for things like permanent failures.
   */
  @Nullable("When null and isCompletable returns false, defaults to disabling the button.")
  StatefulButtonMessage getStateDisplay(@NotNull DeveloperService developerService,
                                        @Nullable("ignored if null") String message);
}
