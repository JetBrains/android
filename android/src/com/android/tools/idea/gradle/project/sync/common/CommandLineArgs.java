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

import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.project.Project;
import org.codehaus.groovy.runtime.typehandling.ShortTypeHandling;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.builder.ModelBuildScriptClasspathBuilderImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.containers.ContainerUtil.addAll;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.generateInitScript;
import static org.jetbrains.plugins.gradle.util.GradleConstants.INIT_SCRIPT_CMD_OPTION;

public class CommandLineArgs {
  @NotNull private final GradleInitScripts myInitScripts;
  @Nullable private final File myClasspathInitScript;

  public CommandLineArgs(boolean generateClasspathInitScript) {
    this(GradleInitScripts.getInstance(), generateClasspathInitScript);
  }

  @VisibleForTesting
  CommandLineArgs(@NotNull GradleInitScripts initScripts, boolean generateClasspathInitScript) {
    myInitScripts = initScripts;
    if (generateClasspathInitScript) {
      myClasspathInitScript = generateInitScript(false, getToolingExtensionsClasses());
    }
    else {
      myClasspathInitScript = null;
    }
  }

  // This is necessary to obtain the custom models we need to configure Java library modules, given that Gradle own models do not expose
  // all the information Android Studio needs.
  // TODO contribute to Gradle the extra models we need, once that is done, we can remove the dependency on the module
  // 'gradle-tooling-extension-impl'
  @NotNull
  private static Set<Class> getToolingExtensionsClasses() {
    return Sets.newHashSet(SyncAction.class, ExternalSystemException.class, ExternalSystemSourceType.class, ProjectImportAction.class,
                           ModelBuildScriptClasspathBuilderImpl.class, ModelBuilderService.class, Multimap.class, GsonBuilder.class,
                           ShortTypeHandling.class);
  }

  @NotNull
  public List<String> get(@Nullable Project project) {
    List<String> args = new ArrayList<>();

    // TODO: figure out why this is making sync fail.
    if (myClasspathInitScript != null) {
      addAll(args, INIT_SCRIPT_CMD_OPTION, myClasspathInitScript.getPath());
    }

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
