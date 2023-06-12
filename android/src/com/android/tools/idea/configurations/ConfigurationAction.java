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
package com.android.tools.idea.configurations;

import static com.android.SdkConstants.FD_RES_LAYOUT;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ConfigurationAction extends AnAction implements ConfigurationListener, Toggleable {
  private static final String FILE_ARROW = " \u2192 ";
  protected final ConfigurationHolder myRenderContext;
  private int myFlags;

  public ConfigurationAction(@NotNull ConfigurationHolder renderContext) {
    this(renderContext, null, null);
  }

  public ConfigurationAction(@NotNull ConfigurationHolder renderContext, @Nullable String title) {
    this(renderContext, title, null);
  }

  public ConfigurationAction(@NotNull ConfigurationHolder renderContext, @Nullable String title, @Nullable Icon icon) {
    super(title, null, icon);
    myRenderContext = renderContext;
  }

  protected void updatePresentation(@NotNull Presentation presentation) {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // Regular actions invoke this method before performing the action. We do so as well since the analytics subsystem hooks into
    // this event to monitor invoked actions.
    ActionUtil.performDumbAwareWithCallbacks(this, e, () -> {
      tryUpdateConfiguration();
      updatePresentation(e.getPresentation());
    });
  }

  /**
   * Performs a try update of the configuration.
   * If the update needs a change of layout file, this method makes it happen.
   * Otherwise, it simply updates the configuration
   */
  protected void tryUpdateConfiguration() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      // See if after switching we need to switch files.
      Configuration clone = configuration.clone();
      myFlags = 0;
      clone.addListener(this);
      updateConfiguration(clone, false /*commit*/);
      clone.removeListener(this);

      boolean affectsFileSelection = (myFlags & MASK_FILE_ATTRS) != 0;
      // Get the resources of the file's project.
      if (affectsFileSelection) {
        VirtualFile file = myRenderContext.getConfiguration().getFile();
        if (file != null) {
          ConfigurationMatcher matcher = new ConfigurationMatcher(clone, file);
          List<VirtualFile> matchingFiles = matcher.getBestFileMatches();
          if (!matchingFiles.isEmpty() && !matchingFiles.contains(file)) {
            // Switch files, and leave this configuration alone.
            pickedBetterMatch(matchingFiles.get(0), file);
            ConfigurationManager configurationManager = configuration.getConfigurationManager();
            updateConfiguration(configurationManager.getConfiguration(matchingFiles.get(0)), true /*commit*/);
            return;
          }
        }
      }

      updateConfiguration(configuration, true /*commit*/);
    }
  }

  protected void pickedBetterMatch(@NotNull VirtualFile file, @NotNull VirtualFile old) {
    // Switch files, and leave this configuration alone
    Configuration configuration = myRenderContext.getConfiguration();
    assert configuration != null;
    Project project = configuration.getConfigModule().getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, -1);
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
    FileEditorWithProvider previousSelection = manager.getSelectedEditorWithProvider(old);
    manager.openEditor(descriptor, true);
    if (previousSelection != null) {
      manager.setSelectedEditor(file, previousSelection.getProvider().getEditorTypeId());
    }
  }

  @Override
  public boolean changed(int flags) {
    myFlags |= flags;
    return true;
  }

  protected abstract void updateConfiguration(@NotNull Configuration configuration, boolean commit);

  public static boolean isBetterMatchLabel(@NotNull String label) {
    return label.contains(FILE_ARROW);
  }

  public static String getBetterMatchLabel(@NotNull String prefix, @NotNull VirtualFile better, @Nullable VirtualFile file) {
    StringBuilder sb = new StringBuilder();
    sb.append(prefix);
    sb.append(FILE_ARROW);
    String folderName = better.getParent().getName();
    if (folderName.equals(FD_RES_LAYOUT)) {
      if (file != null && !Objects.equals(file.getParent(), better.getParent())) {
        sb.append(FD_RES_LAYOUT);
        sb.append(File.separatorChar);
      }
    }
    else {
      if (folderName.startsWith(FD_RES_LAYOUT)) {
        folderName = folderName.substring(FD_RES_LAYOUT.length() + 1);
      }
      sb.append(folderName);
      sb.append(File.separatorChar);
    }
    sb.append(better.getName());
    return sb.toString();
  }

  public static Icon getBetterMatchIcon() {
    return AllIcons.Actions.CloseDarkGrey;
  }
}
