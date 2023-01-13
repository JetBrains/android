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
package com.android.tools.idea.gradle.project.sync.common;

import static com.android.builder.model.AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED;
import static com.android.builder.model.InjectedProperties.PROPERTY_INVOKED_FROM_IDE;
import static com.android.builder.model.InjectedProperties.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL;
import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createJvmArg;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.intellij.util.ArrayUtil.toStringArray;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommandLineArgs {
  private static Key<String[]> GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY = Key.create("gradle.sync.command.line.options");

  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleInitScripts myInitScripts;

  public CommandLineArgs() {
    this(IdeInfo.getInstance(), GradleInitScripts.getInstance());
  }

  @VisibleForTesting
  CommandLineArgs(@NotNull IdeInfo ideInfo,
                  @NotNull GradleInitScripts initScripts) {
    myIdeInfo = ideInfo;
    myInitScripts = initScripts;
  }

  @NotNull
  public List<String> get(@Nullable Project project) {
    List<String> args = new ArrayList<>();

    myInitScripts.addAndroidStudioToolingPluginInitScriptCommandLineArg(args);

    // http://b.android.com/201742, let's make sure the daemon always runs in headless mode.
    args.add("-Djava.awt.headless=true");

    if (project != null) {
      String[] extraOptions = project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY);
      if (extraOptions != null) {
        project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, null);
        Collections.addAll(args, extraOptions);
      }
      var buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
      Collections.addAll(args, buildConfiguration.getCommandLineOptions());
    }

    // Always add the --stacktrace option to aid in the debugging of any issues in sync.
    args.add("--stacktrace");

    // These properties tell the Android Gradle plugin that we are performing a sync and not a build.
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY, true));
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_ADVANCED, true));
    args.add(createProjectProperty(PROPERTY_INVOKED_FROM_IDE, true));
    // Sent to plugin starting with Studio 3.0
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_VERSIONED, MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD));

    // Skip download of source and javadoc jars during Gradle sync, this flag only has effect on AGP 3.5.
    //noinspection deprecation AGP 3.6 and above do not download sources at all.
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD, true));

    args.add(createProjectProperty("idea.gradle.do.not.build.tasks", GradleExperimentalSettings.getInstance().SKIP_GRADLE_TASKS_LIST));
    if (myIdeInfo.isAndroidStudio()) {
      // This property customizes GradleProjectBuilder, with "omit_all_tasks" the builder will skip task realization and return
      // GradleProject model with empty task list. The task list in GradleProject is not used by IDE and thus should always be omitted.
      // This property exists since Gradle 6.1, and has no effect on prior versions of Gradle.
      args.add(createJvmArg("org.gradle.internal.GradleProjectBuilderOptions", "omit_all_tasks"));
    }
    if (project != null) {
      Boolean refreshExternalNativeModels = project.getUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY);
      if (refreshExternalNativeModels != null) {
        project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, null);
        args.add(createProjectProperty(PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, refreshExternalNativeModels));
      }
    }

    Application application = ApplicationManager.getApplication();
    boolean isTestingMode = GuiTestingService.isInTestingMode();
    if (isTestingMode) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      application.putUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, toStringArray(args));
    }

    if (StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get() && !isTestingMode) {
      myInitScripts.addLocalMavenRepoInitScriptCommandLineArg(args);
    }
    return args;
  }
}
