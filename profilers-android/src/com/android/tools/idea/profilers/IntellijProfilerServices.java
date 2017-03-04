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

import com.android.tools.idea.profilers.stacktrace.IntellijCodeNavigator;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class IntellijProfilerServices implements IdeProfilerServices {

  private static Logger getLogger() {
    return Logger.getInstance(IntellijProfilerServices.class);
  }

  private final IntellijCodeNavigator myCodeNavigator;
  @NotNull private final Project myProject;

  public IntellijProfilerServices(@NotNull Project project) {
    myProject = project;
    myCodeNavigator = new IntellijCodeNavigator(project);
  }

  @NotNull
  @Override
  public Executor getMainExecutor() {
    return ApplicationManager.getApplication()::invokeLater;
  }

  @NotNull
  @Override
  public Executor getPoolExecutor() {
    return ApplicationManager.getApplication()::executeOnPooledThread;
  }

  @Override
  public void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable) {
    File parentDir = file.getParentFile();
    if (!parentDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      parentDir.mkdirs();
    }
    if (!file.exists()) {
      try {
        if (!file.createNewFile()) {
          getLogger().error("Could not create new file at: " + file.getPath());
          return;
        }
      }
      catch (IOException e) {
        getLogger().error(e);
      }
    }

    try (FileOutputStream fos = new FileOutputStream(file)) {
      fileOutputStreamConsumer.accept(fos);
    }
    catch (IOException e) {
      getLogger().error(e);
    }

    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
    if (virtualFile != null) {
      virtualFile.refresh(true, false, postRunnable);
    }
  }

  @NotNull
  @Override
  public CodeNavigator getCodeNavigator() {
    return myCodeNavigator;
  }


  /**
   * Note - this opens the Run Configuration dialog which is modal and blocking until the dialog closes.
   */
  @Override
  public void enableAdvancedProfiling() {
    // Attempts to find the AndroidRunConfiguration to enable the profiler state validation.
    AndroidRunConfigurationBase androidConfiguration = null;
    RunManager runManager = RunManager.getInstance(myProject);
    if (runManager != null) {
      RunnerAndConfigurationSettings configurationSettings = runManager.getSelectedConfiguration();
      if (configurationSettings != null && configurationSettings.getConfiguration() instanceof AndroidRunConfigurationBase) {
        androidConfiguration = (AndroidRunConfigurationBase)configurationSettings.getConfiguration();
        androidConfiguration.getProfilerState().setCheckAdvancedProfiling(true);
      }
    }

    EditConfigurationsDialog dialog = new EditConfigurationsDialog(myProject);
    dialog.show();

    if (androidConfiguration != null) {
      androidConfiguration.getProfilerState().setCheckAdvancedProfiling(false);
    }
  }
}
