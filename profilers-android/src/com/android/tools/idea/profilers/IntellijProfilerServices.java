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

import com.android.tools.idea.diagnostics.exception.NoPiiException;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilingConfigService;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilingConfigurationsDialog;
import com.android.tools.idea.profilers.stacktrace.IntellijCodeNavigator;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.profilers.FeatureConfig;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerPreferences;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class IntellijProfilerServices implements IdeProfilerServices {

  private static Logger getLogger() {
    return Logger.getInstance(IntellijProfilerServices.class);
  }

  private final IntellijCodeNavigator myCodeNavigator;
  private final StudioFeatureTracker myFeatureTracker = new StudioFeatureTracker();

  @NotNull private final Project myProject;
  @NotNull private final TemporaryProfilerPreferences myTemporaryPreferences;

  public IntellijProfilerServices(@NotNull Project project) {
    myProject = project;
    myCodeNavigator = new IntellijCodeNavigator(project, myFeatureTracker);
    myTemporaryPreferences = new TemporaryProfilerPreferences();
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

  @NotNull
  @Override
  public FeatureTracker getFeatureTracker() {
    return myFeatureTracker;
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

  @NotNull
  @Override
  public FeatureConfig getFeatureConfig() {
    return new FeatureConfig() {
      @Override
      public boolean isAtraceEnabled() {
        return StudioFlags.PROFILER_USE_ATRACE.get();
      }

      @Override
      public boolean isCpuCaptureFilterEnabled() {
        return StudioFlags.PROFILER_CPU_CAPTURE_FILTER.get();
      }

      @Override
      public boolean isEnergyProfilerEnabled() {
        return StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get();
      }

      @Override
      public boolean isJniReferenceTrackingEnabled() {
        return StudioFlags.PROFILER_TRACK_JNI_REFS.get();
      }

      @Override
      public boolean isJvmtiAgentEnabled() {
        return StudioFlags.PROFILER_USE_JVMTI.get();
      }

      @Override
      public boolean isLiveAllocationsEnabled() {
        return StudioFlags.PROFILER_USE_JVMTI.get() && StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get();
      }

      @Override
      public boolean isMemoryCaptureFilterEnabled() {
        return StudioFlags.PROFILER_MEMORY_CAPTURE_FILTER.get();
      }

      @Override
      public boolean isMemorySnapshotEnabled() {
        return StudioFlags.PROFILER_MEMORY_SNAPSHOT.get();
      }

      @Override
      public boolean isNetworkRequestPayloadEnabled() {
        return StudioFlags.PROFILER_NETWORK_REQUEST_PAYLOAD.get();
      }

      @Override
      public boolean isNetworkThreadViewEnabled() {
        return StudioFlags.PROFILER_SHOW_THREADS_VIEW.get();
      }

      @Override
      public boolean isSessionsEnabled() {
        return StudioFlags.PROFILER_SHOW_SESSIONS.get();
      }

      @Override
      public boolean isSimplePerfEnabled() {
        return StudioFlags.PROFILER_USE_SIMPLEPERF.get();
      }

      @Override
      public boolean isExportCpuTraceEnabled() {
        return StudioFlags.PROFILER_EXPORT_CPU_TRACE.get();
      }
    };
  }

  @NotNull
  @Override
  public ProfilerPreferences getTemporaryProfilerPreferences() {
    return myTemporaryPreferences;
  }

  @Override
  public void openCpuProfilingConfigurationsDialog(CpuProfilerConfigModel model, int deviceLevel,
                                                   Consumer<ProfilingConfiguration> dialogCallback) {
    CpuProfilingConfigurationsDialog dialog = new CpuProfilingConfigurationsDialog(myProject,
                                                                                   deviceLevel,
                                                                                   model,
                                                                                   dialogCallback,
                                                                                   myFeatureTracker);
    dialog.show();
  }

  @Override
  public void openParseLargeTracesDialog(Runnable yesCallback, Runnable noCallback) {
    int dialogResult = Messages.showYesNoDialog(myProject,
                                                "The trace file generated is large, and Android Studio may become unresponsive while " +
                                                "it parses the data. Do you want to continue?\n\n" +
                                                "Warning: If you select \"No\", Android Studio discards the trace data and you will need " +
                                                "to capture a new method trace.",
                                                "Trace File Too Large",
                                                Messages.getWarningIcon());
    if (dialogResult == Messages.YES) {
      yesCallback.run();
    }
    else {
      noCallback.run();
    }
  }

  @Override
  public List<ProfilingConfiguration> getCpuProfilingConfigurations() {
    return CpuProfilingConfigService.getInstance(myProject).getConfigurations();
  }

  @NotNull
  @Override
  public String getApplicationId() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null) {
        return model.getApplicationId();
      }
    }
    throw new IllegalStateException("No Android module found for the project.");
  }

  @Override
  public boolean isNativeProfilingConfigurationPreferred() {
    // File extensions that we consider native. We can add more later if we feel that's necessary.
    ImmutableList<String> nativeExtensions = ImmutableList.of("c", "cc", "cpp", "cxx", "c++", "h", "hh", "hpp", "hxx", "h++");
    // If the user is viewing at least one (IntelliJ allows the user to view multiple files at the same time) native file,
    // we want to give preference to a native CPU profiling configuration.
    return Arrays.stream(FileEditorManager.getInstance(myProject).getSelectedFiles())
      .anyMatch(file -> {
        String extension = file.getExtension();
        return extension != null && nativeExtensions.contains(extension.toLowerCase());
      });
  }

  @Override
  public void showErrorBalloon(@NotNull String title, @NotNull String body, @NotNull String url, @NotNull String urlText) {
    OpenUrlHyperlink hyperlink = new OpenUrlHyperlink(url, urlText);
    AndroidNotification.getInstance(myProject)
      .showBalloon(title, body, NotificationType.ERROR, AndroidNotification.BALLOON_GROUP, false, hyperlink);
  }

  @Override
  public void reportNoPiiException(@NotNull Throwable ex) {
    getLogger().error(new NoPiiException(ex));
  }
}
