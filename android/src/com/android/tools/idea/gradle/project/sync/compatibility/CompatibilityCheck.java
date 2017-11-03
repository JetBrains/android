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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.tools.idea.project.messages.MessageType;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a compatibility check between two or more IDE components.
 */
class CompatibilityCheck {
  /**
   * The main component to base for compatibility check. For example, for the check "Gradle 2.14.1 requires Android plugin 1.2.3 or newer,"
   * this field will contain the definition of "Gradle 2.14.1".
   */
  @NotNull private final Component myComponent;

  /**
   * The type of message to use in case the compatibility check fails.
   */
  @NotNull private final MessageType myType;

  CompatibilityCheck(@NotNull Component component, @NotNull MessageType type) {
    myComponent = component;
    myType = type;
  }

  @NotNull
  Component getComponent() {
    return myComponent;
  }

  @NotNull
  public MessageType getType() {
    return myType;
  }
}
