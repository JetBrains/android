/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.Token;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface MemorySettingsToken<P extends AndroidProjectSystem> extends Token {
  ExtensionPointName<MemorySettingsToken<AndroidProjectSystem>>
    EP_NAME = new ExtensionPointName<>("com.android.tools.idea.memorysettings.memorySettingsToken");

  BuildSystemComponent createBuildSystemComponent(P projectSystem);

  int getRecommendedXmxFor(P projectSystem);
}
