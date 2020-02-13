/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.openapi.extensions.ExtensionPointName;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Navigation listener for Assistant Panel.
 */
public interface AssistNavListener {

  ExtensionPointName<AssistNavListener> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.assistant.navlistener");

  /**
   * Returns a prefix of the keys the listener should subscribe to.
   */
  @NotNull
  String getIdPrefix();

  /**
   * Triggered when navigation related action is performed.
   *
   * @param id The navigation action id.
   * @param e  The action event.
   */
  void onActionPerformed(String id, @NotNull ActionEvent e);
}
