/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.tools.idea.rendering.AppResourceRepository;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

abstract class ConfigurationAction extends AnAction implements ConfigurationListener {
  protected final RenderContext myRenderContext;
  private int myFlags;

  public ConfigurationAction(@NotNull RenderContext renderContext, @NotNull String title) {
    this(renderContext, title, null);
  }

  public ConfigurationAction(@NotNull RenderContext renderContext, @NotNull String title, @Nullable Icon icon) {
    super(title, null, icon);
    myRenderContext = renderContext;
  }

  protected void updatePresentation() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    tryUpdateConfiguration();
    updatePresentation();
    myRenderContext.requestRender();
  }

  protected void tryUpdateConfiguration() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      // See if after switching we need to switch files
      Configuration clone = configuration.clone();
      myFlags = 0;
      clone.addListener(this);
      updateConfiguration(clone);
      clone.removeListener(this);

      boolean affectsFileSelection = (myFlags & MASK_FILE_ATTRS) != 0;
      // get the resources of the file's project.
      if (affectsFileSelection) {
        Module module = myRenderContext.getModule();
        assert module != null;
        VirtualFile file = myRenderContext.getVirtualFile();
        assert file != null;
        ConfigurationMatcher matcher = new ConfigurationMatcher(clone, AppResourceRepository.getAppResources(module, true), file);
        VirtualFile best = matcher.getBestFileMatch();
        if (best != null && !best.equals(file)) {
          // Switch files, and leave this configuration alone
          pickedBetterMatch(best, file);
          AndroidFacet facet = AndroidFacet.getInstance(module);
          assert facet != null;
          updateConfiguration(facet.getConfigurationManager().getConfiguration(best));
          return;
        }
      }

      updateConfiguration(configuration);
    }
  }

  protected void pickedBetterMatch(@NotNull VirtualFile file, @NotNull VirtualFile old) {
    // Switch files, and leave this configuration alone
    Module module = myRenderContext.getModule();
    assert module != null;
    Project project = module.getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, -1);
    FileEditorManager manager = FileEditorManager.getInstance(project);
    FileEditor selectedEditor = manager.getSelectedEditor(old);
    List<FileEditor> editors = manager.openEditor(descriptor, true);

    // Switch to the same type of editor (XML or Layout Editor) in the target file
    if (selectedEditor instanceof AndroidDesignerEditor) {
      manager.setSelectedEditor(file, AndroidDesignerEditorProvider.ANDROID_DESIGNER_ID);
    } else if (selectedEditor != null) {
      manager.setSelectedEditor(file, TextEditorProvider.getInstance().getEditorTypeId());

      // Proactively switch to the new editor right away in the layout XML preview, if applicable
      if (!editors.isEmpty()) {
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor && editor.getComponent().isShowing()) {
            AndroidLayoutPreviewToolWindowManager previewManager = AndroidLayoutPreviewToolWindowManager.getInstance(project);
            previewManager.notifyFileShown((TextEditor)editor, true);
            break;
          }
        }
      }
    }
  }

  @Override
  public boolean changed(int flags) {
    myFlags |= flags;
    return true;
  }

  protected abstract void updateConfiguration(@NotNull Configuration configuration);
}
