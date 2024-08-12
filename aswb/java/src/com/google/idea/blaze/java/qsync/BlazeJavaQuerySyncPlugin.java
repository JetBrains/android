/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.google.idea.blaze.java.sync.projectstructure.Jdks;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;

/** Sync support for Java. */
public class BlazeJavaQuerySyncPlugin implements BlazeQuerySyncPlugin {

  @Override
  public void updateProjectSettingsForQuerySync(
      Project project, Context<?> context, ProjectViewSet projectViewSet) {
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    if (!workspaceLanguageSettings.isWorkspaceType(WorkspaceType.JAVA)) {
      return;
    }

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_11);
    Sdk currentSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk sdk = Jdks.chooseOrCreateJavaSdk(currentSdk, javaLanguageLevel);

    if (sdk == null) {
      String msg =
          String.format(
              "Unable to find a JDK %1$s installed.\n", javaLanguageLevel.getPresentableText());
      IssueOutput.error(msg).submit(context);
      return;
    }

    LanguageLevel currentLanguageLevel =
        LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    if (sdk != currentSdk || javaLanguageLevel != currentLanguageLevel) {
      setProjectSdkAndLanguageLevel(project, sdk, javaLanguageLevel);
    }
  }

  private static void setProjectSdkAndLanguageLevel(
      final Project project, final Sdk sdk, final LanguageLevel javaLanguageLevel) {
    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
          rootManager.setProjectSdk(sdk);
          LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
          ext.setLanguageLevel(javaLanguageLevel);
        });
  }
}
