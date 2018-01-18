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
package com.android.tools.idea.gradle.project.settings;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import org.jetbrains.annotations.Nullable;

public class AndroidStudioGradleIdeSettingsConfigurableProvider extends ConfigurableProvider {
  public AndroidStudioGradleIdeSettingsConfigurableProvider() {
  }

  @Override
  @Nullable
  public Configurable createConfigurable() {
    return IdeInfo.getInstance().isAndroidStudio() ? new AndroidStudioGradleIdeSettingsConfigurable() : null;
  }
}
