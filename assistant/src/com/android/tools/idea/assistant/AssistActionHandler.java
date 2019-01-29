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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to provide custom action handling.
 */
public interface AssistActionHandler {
  ExtensionPointName<AssistActionHandler> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.assistant.actionHandler");

  /**
   * Returns a unique id for the action. Used in mapping from an {@see ActionData}
   * instance to the handler.
   */
  @NotNull
  String getId();

  /**
   * Handles an action of a given type as identified by {@code getId}.
   * TODO: Determine what other signals, if any, are necessary.
   * TODO: Determine how we might want to handle callbacks.
   *
   * @param actionData Configuration data for performing the action.
   * @param project    The project context for the action.
   */
  void handleAction(@NotNull ActionData actionData, @NotNull Project project);
}
