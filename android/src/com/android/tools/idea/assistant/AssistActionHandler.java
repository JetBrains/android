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

import com.android.tools.idea.structure.services.DeveloperService;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point to provide custom action handling.
 */
public interface AssistActionHandler {
  ExtensionPointName<AssistActionHandler> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.structure.services.actionHandler");

  @NotNull
  String getId();

  /**
   * Handles an action of a given type as identified by {@code getId}.
   * TODO: Determine what other signals, if any, are necessary.
   * TODO: Determine how we might want to handle callbacks.
   * TODO: Decouple actionArgument from the value used to retrieve DeveloperService instances.
   *
   * @param actionArgument An opaque argument associated with the given button. May be a number or key or anything that allows you to
   *                       complete the desired action.
   * @param service        A module scoped service instance. May be used for things like installing dependencies.
   */
  void handleAction(@Nullable("Arguments are optional") String actionArgument, @NotNull DeveloperService service);

}
