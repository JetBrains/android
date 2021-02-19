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
package com.android.tools.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.PlatformUtils.getPlatformPrefix;

@Service
public final class IdeInfo {
  @NotNull
  public static IdeInfo getInstance() {
    return ApplicationManager.getApplication().getService(IdeInfo.class);
  }

  public boolean isAndroidStudio() {
    return "AndroidStudio".equals(getPlatformPrefix());
  }

  public boolean isGameTools() {
    return isGameTool();
  }

  // TODO: Remove static method to support dependency injection.
  public static boolean isGameTool() {
    return "AndroidGameDevelopmentTools".equals(getPlatformPrefix());
  }
}
