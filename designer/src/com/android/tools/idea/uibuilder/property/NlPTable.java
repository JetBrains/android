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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableModel;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.Hint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;

public class NlPTable extends PTable {
  public NlPTable(@NotNull PTableModel model) {
    super(model);
  }

  public NlPTable(@NotNull PTableModel model, @NotNull CopyPasteManager copyPasteManager) {
    super(model, copyPasteManager);
  }

  // The method editingCanceled is called from IDEEventQueue.EditingCanceller when a child component
  // of a JTable receives a KeyEvent for the VK_ESCAPE key.
  // However we do NOT want to stop editing the cell if our editor currently is showing completion
  // results. The completion lookup is supposed to consume the key event but it cannot do that here
  // because of the preprocessing performed in IDEEventQueue.
  @Override
  @SuppressWarnings("deprecation")  // For CompletionProgressIndicator
  public void editingCanceled(@Nullable ChangeEvent event) {
    CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
    if (process instanceof CompletionProgressIndicator) {
      Hint hint = ((CompletionProgressIndicator)process).getLookup();
      if (hint != null) {
        hint.hide();
        return;
      }
    }
    super.editingCanceled(event);
  }
}
