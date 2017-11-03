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
package com.android.tools.idea.logcat.output;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class LogcatOutputConfigurableProvider extends DebuggerConfigurableProvider {
  private static final String ID = "logcatOutputSettingsConfigurable";
  private static final String DISPLAY_NAME = "Logcat output";
  /**
   * Banner message to display before displaying first line of captured logcat output.
   */
  public static final String BANNER_MESSAGE = "Capturing and displaying logcat messages from application. " +
                                              "This behavior can be disabled in the \"" + DISPLAY_NAME + "\" section " +
                                              "of the \"Debugger\" settings page.";

  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    if (LogcatOutputConfigurableUi.shouldShow() && category == DebuggerSettingsCategory.GENERAL) {
      return ImmutableList.of(SimpleConfigurable.create(ID, DISPLAY_NAME, LogcatOutputConfigurableUi.class,
                                                        LogcatOutputSettings.getInstance()));
    }
    return Collections.emptyList();
  }
}