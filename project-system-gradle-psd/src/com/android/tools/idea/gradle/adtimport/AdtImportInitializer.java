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
package com.android.tools.idea.gradle.adtimport;

import static com.intellij.icons.AllIcons.ToolbarDecorator.Import;

import com.android.tools.idea.gradle.adtimport.actions.AndroidImportProjectAction;
import com.android.tools.idea.startup.Actions;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import org.jetbrains.annotations.NotNull;

public final class AdtImportInitializer implements ActionConfigurationCustomizer {
  @Override
  public void customize(@NotNull ActionManager actionManager) {
    Actions.replaceAction(actionManager, "ImportProject",
                          new AndroidImportProjectAction("Import Project...", null, null));
    Actions.replaceAction(actionManager, "WelcomeScreen.ImportProject",
                          new AndroidImportProjectAction("Import Project (Gradle, Eclipse ADT, etc.)", null, Import));
  }
}
