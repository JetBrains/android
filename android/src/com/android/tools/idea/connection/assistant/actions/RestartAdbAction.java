/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.assistant.AssistActionHandler;
import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Used in Connection Assistant, allows user to restart ADB to scan for new connected Android devices.
 */
public final class RestartAdbAction implements AssistActionHandler {
  public static final String ACTION_ID = "connection.restart.adb";

  @NotNull
  @Override
  public String getId() {
    return ACTION_ID;
  }

  @Override
  public void handleAction(@NotNull ActionData actionData, @NotNull Project project) {
    AndroidDebugBridge adb = AndroidDebugBridge.getBridge();
    if (adb == null) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      adb.restart();
    });
  }
}
