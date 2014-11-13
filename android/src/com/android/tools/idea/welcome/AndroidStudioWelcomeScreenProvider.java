/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.WelcomeScreenProvider;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Shows a wizard first time Android Studio is launched
 */
public final class AndroidStudioWelcomeScreenProvider implements WelcomeScreenProvider {
  public static final String SYSTEM_PROPERTY_DISABLE_WIZARD = "disable.android.first.run";

  @Nullable
  @Override
  public WelcomeScreen createWelcomeScreen(JRootPane rootPane) {
    return new WelcomeScreenHost();
  }

  @Override
  public boolean isAvailable() {
    AndroidFirstRunPersistentData instance = AndroidFirstRunPersistentData.getInstance();
    return !AndroidPlugin.isGuiTestingMode()  && !Boolean.getBoolean(SYSTEM_PROPERTY_DISABLE_WIZARD) && !instance.isSdkUpToDate();
  }
}
