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
package com.android.tools.idea.assistant.datamodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An action represents a single button and its behaviors.
 *
 * TODO: Investigate if we should change to an abstract class and override equals/hashCode
 * since this is used as keys in one or more maps. So far instance comparisons seem
 * sufficient but there may be cases where this is problematic.
 *
 * Note that we can't use java8's default methods in interfaces for this as these methods
 * would be blown away by the implementing object already having them being present.
 */
public interface ActionData {

  /**
   * Gets the label to be used on the action button.
   */
  @NotNull
  String getLabel();

  /**
   * Gets the key used to map a {@code StatefulButton} to a {@code AssistActionStateManager}.
   */
  @Nullable
  String getKey();

  /**
   * Gets an optional argument to be passed to the {@code AssistActionHandler}, allowing you to differentiate
   * between buttons of the same kind. For example, a button that adds permissions may use this to determine
   * which permissions to add.
   */
  @Nullable("not required")
  String getActionArgument();

  /**
   * Gets a message to display instead of the button when the action is considered complete.
   *
   * This should be considered deprecated as the varied states may correlate to a variety of messages.
   * Instead override {@code AssistActionStateManager#getStateDisplay} to return the appropriate message.
   */
  @Deprecated
  String getSuccessMessage();

  /**
   * Gets whether the button should be highlighted like the default action in dialogs
   */
  boolean isHighlighted();
}
