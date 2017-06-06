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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.intellij.util.ArrayUtil.toStringArray;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

public class CommandLineArgs {
  @NotNull private final ApplicationInfo myApplicationInfo;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleInitScripts myInitScripts;

  public CommandLineArgs() {
    this(ApplicationInfo.getInstance(), IdeInfo.getInstance(), GradleInitScripts.getInstance());
  }

  @VisibleForTesting
  CommandLineArgs(@NotNull ApplicationInfo applicationInfo,
                  @NotNull IdeInfo ideInfo,
                  @NotNull GradleInitScripts initScripts) {
    myApplicationInfo = applicationInfo;
    myIdeInfo = ideInfo;
    myInitScripts = initScripts;
  }

  @NotNull
  public List<String> get(@NotNull Options options, @Nullable Project project) {
    List<String> args = new ArrayList<>();

    // TODO: figure out why this is making sync fail.
    if (options.applyJavaPlugin) {
      myInitScripts.addApplyJavaLibraryPluginInitScriptCommandLineArg(args);
    }

    // http://b.android.com/201742, let's make sure the daemon always runs in headless mode.
    args.add("-Djava.awt.headless=true");

    if (project != null) {
      String[] extraOptions = project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY);
      if (extraOptions != null) {
        project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, null);
        Collections.addAll(args, extraOptions);
      }
    }

    // These properties tell the Android Gradle plugin that we are performing a sync and not a build.
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY, true));
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_ADVANCED, true));
    args.add(createProjectProperty(PROPERTY_INVOKED_FROM_IDE, true));
    // Sent to plugin starting with Studio 3.0
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_VERSIONED,
                                   NewGradleSync.isEnabled() ? MODEL_LEVEL_4_NEW_DEP_MODEL : MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD));
    if (myIdeInfo.isAndroidStudio()) {
      // Example of version to pass: 2.4.0.6
      args.add(createProjectProperty(PROPERTY_STUDIO_VERSION, myApplicationInfo.getStrictVersion()));
    }

    if (project != null) {
      Boolean refreshExternalNativeModels = project.getUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY);
      if (refreshExternalNativeModels != null) {
        project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, null);
        args.add(createProjectProperty(PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, refreshExternalNativeModels));
      }
    }

    Application application = ApplicationManager.getApplication();
    if (isGuiTestingMode() || application.isUnitTestMode()) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      application.putUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, toStringArray(args));
    }

    if (options.includeLocalMavenRepo) {
      myInitScripts.addLocalMavenRepoInitScriptCommandLineArg(args);
    }
    return args;
  }

  public static class Options {
    boolean applyJavaPlugin;
    boolean includeLocalMavenRepo;

    public void applyJavaPlugin() {
      applyJavaPlugin = true;
    }

    public void includeLocalMavenRepo() {
      includeLocalMavenRepo = true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Options)) {
        return false;
      }
      Options options = (Options)o;
      return applyJavaPlugin == options.applyJavaPlugin &&
             includeLocalMavenRepo == options.includeLocalMavenRepo;
    }

    @Override
    public int hashCode() {
      return Objects.hash(applyJavaPlugin, includeLocalMavenRepo);
    }
  }
}
