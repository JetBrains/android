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
package com.android.tools.idea.actions;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.util.FileExtensions;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import java.io.IOException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;

/**
 * Action to create the main Sample Data directory
 */
public class CreateSampleDataDirectory extends AnAction {
  private static final Logger LOG = Logger.getInstance(CreateSampleDataDirectory.class);

  @SuppressWarnings("UnusedDeclaration")
  public CreateSampleDataDirectory() {
    super(AndroidBundle.message("new.sampledata.dir.action.title"), AndroidBundle.message("new.sampledata.dir.action.description"),
          PlatformIcons.FOLDER_ICON);
  }

  @Nullable
  private static Module getModuleFromSelection(@NotNull DataContext dataContext) {
    Module[] modules = MODULE_CONTEXT_ARRAY.getData(dataContext);

    if (modules != null && modules.length > 0) {
      return modules[0];
    } else {
      return PlatformCoreDataKeys.MODULE.getData(dataContext);
    }
  }

  private static boolean isActionVisibleForModule(@Nullable Module module) {
    if (module == null) return false;

    PathString sampleDataDirPath = ProjectSystemUtil.getModuleSystem(module).getSampleDataDirectory();
    if (sampleDataDirPath == null) return false;

    // Only display if the directory doesn't exist already
    VirtualFile sampleDataDir = FileExtensions.toVirtualFile(sampleDataDirPath);
    return sampleDataDir == null || !sampleDataDir.exists();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Module module = getModuleFromSelection(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(isActionVisibleForModule(module));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module module = getModuleFromSelection(e.getDataContext());
    assert module != null; // Needs to exist or the action wouldn't be visible

    WriteCommandAction.writeCommandAction(module.getProject())
      .withName(AndroidBundle.message("new.sampledata.dir.action.title"))
      .withGlobalUndo()
      .run(
        () -> {
          try {
            ProjectSystemUtil.getModuleSystem(module).getOrCreateSampleDataDirectory();
          }
          catch (IOException ex) {
            LOG.warn("Unable to create sample data directory for module " + module.getName(), ex);
          }
        }
      );
  }
}
