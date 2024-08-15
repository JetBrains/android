/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.qsync;


import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.google.idea.sdkcompat.kotlin.KotlinCompat;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;

/** Supports Kotlin. */
public class BlazeKotlinQuerySyncPlugin implements BlazeQuerySyncPlugin {

  @Override
  public void updateProjectSettingsForQuerySync(
      Project project, Context<?> context, ProjectViewSet projectViewSet) {
    if (!isKotlinProject(projectViewSet)) {
      return;
    }

    // Set jvm-target from java language level
    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_11);
    setProjectJvmTarget(project, javaLanguageLevel);
  }

  private static void setProjectJvmTarget(Project project, LanguageLevel javaLanguageLevel) {
    K2JVMCompilerArguments k2JVMCompilerArguments =
        (K2JVMCompilerArguments)
            KotlinCompat.unfreezeSettings(
                Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project).getSettings());

    String javaVersion = javaLanguageLevel.toJavaVersion().toString();
    k2JVMCompilerArguments.setJvmTarget(javaVersion);
    Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project)
        .setSettings(k2JVMCompilerArguments);
  }

  private static boolean isKotlinProject(ProjectViewSet projectViewSet) {
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    return workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN);
  }
}
