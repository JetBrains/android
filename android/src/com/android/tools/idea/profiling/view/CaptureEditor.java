/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.view;

import com.android.tools.perflib.analyzer.AnalysisReport;
import com.android.tools.perflib.analyzer.AnalyzerTask;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class CaptureEditor extends UserDataHolderBase implements FileEditor {
  protected CapturePanel myPanel;

  @NotNull
  public abstract DesignerEditorPanelFacade getFacade();

  @NotNull
  public abstract AnalysisReport performAnalysis(@NotNull Set<? extends AnalyzerTask> tasks,
                                                 @NotNull Set<AnalysisReport.Listener> listeners);

  public final CapturePanel getCapturePanel() {
    return myPanel;
  }
}
