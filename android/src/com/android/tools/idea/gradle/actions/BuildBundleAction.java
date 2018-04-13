/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.utils.HtmlBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.exportSignedPackage.ChooseBundleOrApkStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

public class BuildBundleAction extends DumbAwareAction {
  private static final String ACTION_TEXT = "Build Bundle(s)";

  public BuildBundleAction() {
    super(ACTION_TEXT);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get() &&
                      isProjectBuildWithGradle(project);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (isProjectBuildWithGradle(project)) {
      List<Module> appModules = DynamicAppUtils.getModulesSupportingBundleTask(project);
      if (!appModules.isEmpty()) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        gradleBuildInvoker.add(new GoToBundleLocationTask(project, appModules, ACTION_TEXT));
        Module[] modulesToBuild = appModules.toArray(new Module[appModules.size()]);
        gradleBuildInvoker.bundle(modulesToBuild, Collections.emptyList(), new OutputBuildAction(getModuleGradlePaths(appModules)));
      } else {
        promptUserForGradleUpdate(project);
      }
    }
  }

  private static void promptUserForGradleUpdate(Project project) {
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    builder.add("Building Bundles requires you to update to the latest version of the Android Gradle Plugin.");
    builder.newline();
    builder.addLink("Learn More", ChooseBundleOrApkStep.DOC_URL);
    builder.newline();
    builder.newline();
    builder.add("Bundles allow you to support multiple device configurations from a single build artifact.");
    builder.newline();
    builder.add("App stores that support the bundle format use it to build and sign your APKs for you, and");
    builder.newline();
    builder.add("serve those APKs to users as needed.");
    builder.newline();
    builder.newline();
    builder.closeHtmlBody();
    final int updateButtonIndex = 1;
    int result = Messages.showDialog(project,
                                     builder.getHtml(),
                                     "Update the Android Gradle Plugin",
                                     new String[]{Messages.CANCEL_BUTTON, "Update"},
                                     updateButtonIndex /* Default button */,
                                     AllIcons.General.WarningDialog);

    if (result == updateButtonIndex) {
      GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
      GradleVersion pluginVersion = GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
      AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
      updater.updatePluginVersion(pluginVersion, gradleVersion);
    }
  }

  @NotNull
  private static List<String> getModuleGradlePaths(@NotNull List<Module> modules) {
    List<String> gradlePaths = new ArrayList<>();
    for (Module module : modules) {
      String gradlePath = getGradlePath(module);
      if (gradlePath != null) {
        gradlePaths.add(gradlePath);
      }
    }
    return gradlePaths;
  }

  private static boolean isProjectBuildWithGradle(@Nullable Project project) {
    return project != null &&
           GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }
}
