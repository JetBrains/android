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

import com.android.tools.idea.rendering.ProjectResources;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  @Override
  public void actionPerformed(AnActionEvent e) {
    tryUpdateConfiguration();
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
        ConfigurationMatcher matcher = new ConfigurationMatcher(clone, ProjectResources.get(module, true), file);
        VirtualFile best = matcher.getBestFileMatch();
        if (best != null && !best.equals(file)) {
          // Switch files, and leave this configuration alone
          pickedBetterMatch(best);
          return;
        }
      }

      updateConfiguration(configuration);
    }
  }

  protected void pickedBetterMatch(@NotNull VirtualFile file) {
    // Switch files, and leave this configuration alone
    Module module = myRenderContext.getModule();
    assert module != null;
    Project project = module.getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, -1);
    FileEditorManager.getInstance(project).openEditor(descriptor, true);
  }

  @Override
  public boolean changed(int flags) {
    myFlags |= flags;
    return true;
  }

  protected abstract void updateConfiguration(@NotNull Configuration configuration);
}
