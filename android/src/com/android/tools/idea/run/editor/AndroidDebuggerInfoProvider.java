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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface whose implementations provide AndroidDebugger related information for a given RunConfiguration.
 *
 * <p>This interface is exposed publicly as an extension point of Android plugin. Any IntelliJ plugin
 * that defines its own run configurations may supply their implementation of this provider by registering it
 * to {@link #EP_NAME} from their plugin.xml file.
 */
public interface AndroidDebuggerInfoProvider {

  ExtensionPointName<AndroidDebuggerInfoProvider> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.run.editor.androidDebuggerInfoProvider");

  /**
   * @return true if this AndroidDebuggerInfoProvider can be used to obtain debugger information for the given project.
   */
  boolean supportsProject(@NotNull Project project);

  /**
   * @return all AndroidDebugger implementations registered for the that are supported by the provided run configuration.
   */
  @NotNull
  List<AndroidDebugger> getAndroidDebuggers(@NotNull RunConfiguration configuration);

  /**
   * @return the AndroidDebugger that is selected by the user for the provided run configuration.
   */
  @Nullable
  AndroidDebugger getSelectedAndroidDebugger(@NotNull RunConfiguration configuration);

  /**
   * @return extra information about the debugger selected by the user for the provided run configuration (e.g., additional symbol
   * directories added explicitly by the user)
   */
  @Nullable
  AndroidDebuggerState getSelectedAndroidDebuggerState(@NotNull RunConfiguration configuration);
}
