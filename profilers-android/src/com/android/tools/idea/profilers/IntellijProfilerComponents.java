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
package com.android.tools.idea.profilers;

import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiClassNavigation;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.common.CodeLocation;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.function.Supplier;

public class IntellijProfilerComponents implements IdeProfilerComponents {
  @Nullable
  private Project myProject;

  public IntellijProfilerComponents(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public JComponent getFileViewer(@Nullable File file) {
    VirtualFile virtualFile = file != null ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
    return getFileViewer(virtualFile, FileEditorProviderManager.getInstance(), myProject);
  }

  @Nullable
  @VisibleForTesting
  static JComponent getFileViewer(@Nullable VirtualFile virtualFile,
                                  @NotNull FileEditorProviderManager fileEditorProviderManager,
                                  @Nullable Project project) {
    if (project != null && virtualFile != null) {
      // TODO: Investigate providers are empty when file download is not finished.
      FileEditorProvider editorProvider = ArrayUtil.getFirstElement(fileEditorProviderManager.getProviders(project, virtualFile));
      return editorProvider != null ? editorProvider.createEditor(project, virtualFile).getComponent() : null;
    }
    return null;
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                           @Nullable Runnable preNavigate) {
    component.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, (DataProvider)dataId -> {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        CodeLocation frame = codeLocationSupplier.get();
        if (frame == null || myProject == null) {
          return null;
        }

        if (frame.getLine() > 0) {
          return PsiClassNavigation.getNavigationForClass(myProject, preNavigate, frame.getClassName(), frame.getLine());
        }
        else {
          return PsiClassNavigation.getNavigationForClass(myProject, preNavigate, frame.getClassName());
        }
      }
      else if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      return null;
    });

    DefaultActionGroup popupGroup = new DefaultActionGroup(new EditMultipleSourcesAction());
    component.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }
    });
  }
}
