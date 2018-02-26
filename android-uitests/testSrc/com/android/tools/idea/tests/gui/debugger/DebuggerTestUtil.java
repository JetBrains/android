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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.jetbrains.annotations.NotNull;

public class DebuggerTestUtil {

  public static final String AUTO = "Auto";
  public static final String DUAL = "Dual";
  public static final String NATIVE = "Native";
  public static final String JAVA = "Java";
  public static final String JAVA_DEBUGGER_CONF_NAME = "app-java";

  public static void setDebuggerType(@NotNull IdeFrameFixture ideFrameFixture,
                       @NotNull String type) {
    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrameFixture.robot())
      .selectDebuggerType(type)
      .clickOk();
  }
}
