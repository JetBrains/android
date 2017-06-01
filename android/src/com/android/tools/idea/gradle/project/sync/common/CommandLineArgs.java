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

import com.android.java.model.JavaProject;
import com.android.java.model.builder.JavaLibraryPlugin;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.containers.ContainerUtil.addAll;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.writeToFileGradleInitScript;
import static org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION;

public class CommandLineArgs {
  @NotNull private final ApplicationInfo myApplicationInfo;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleInitScripts myInitScripts;

  @Nullable private final File myClasspathInitScript;

  public CommandLineArgs(boolean generateClasspathInitScript) {
    this(ApplicationInfo.getInstance(), IdeInfo.getInstance(), GradleInitScripts.getInstance(), generateClasspathInitScript);
  }

  @VisibleForTesting
  CommandLineArgs(@NotNull ApplicationInfo applicationInfo,
                  @NotNull IdeInfo ideInfo,
                  @NotNull GradleInitScripts initScripts,
                  boolean generateClasspathInitScript) {
    myApplicationInfo = applicationInfo;
    myIdeInfo = ideInfo;
    myInitScripts = initScripts;
    if (generateClasspathInitScript) {
      myClasspathInitScript = getClasspathInitScript();
    }
    else {
      myClasspathInitScript = null;
    }
  }

  // Create init script that applies java library plugin to all projects
  @Nullable
  private static File getClasspathInitScript() {
    String javaPluginClasspath = String.format("classpath files(['%s', '%s'])",
                                               PathManager.getJarPathForClass(JavaProject.class),
                                               PathManager.getJarPathForClass(JavaLibraryPlugin.class));
    String initScriptContent = "initscript{\n" +
                               "    dependencies {\n" +
                               "        " + javaPluginClasspath + "\n" +
                               "    }\n" +
                               "}\n" +
                               "allprojects{\n" +
                               "    apply plugin: " + JavaLibraryPlugin.class.getName() + "\n" +
                               "}\n";

    try {
      return writeToFileGradleInitScript(initScriptContent, "sync.init");
    }
    catch (Exception e) {
      Logger.getInstance(CommandLineArgs.class).warn("Unable to create init script.", e);
      return null;
    }
  }

  @NotNull
  public List<String> get(@Nullable Project project) {
    List<String> args = new ArrayList<>();

    // TODO: figure out why this is making sync fail.
    if (myClasspathInitScript != null) {
      addAll(args, INIT_SCRIPT_CMD_OPTION, myClasspathInitScript.getPath());
    }

    // http://b.android.com/201742, let's make sure the daemon always runs in headless mode.
    args.add("-Djava.awt.headless=true");

    if (project != null) {
      String[] options = project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY);
      if (options != null) {
        project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, null);
        Collections.addAll(args, options);
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

    if (isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      ApplicationManager.getApplication().putUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, toStringArray(args));
    }

    myInitScripts.addLocalMavenRepoInitScriptCommandLineArgTo(args);
    return args;
  }
}
