/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractMainDependenciesPanel extends AbstractMainPanel {
  protected AbstractMainDependenciesPanel(@NotNull PsContext context,
                                          @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);
  }

  @NotNull
  protected JBSplitter createMainVerticalSplitter() {
    return new OnePixelSplitter(false, "psd.dependencies.main.vertical.splitter.proportion", .75f);
  }
}
