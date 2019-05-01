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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static com.intellij.openapi.application.TransactionGuard.submitTransaction;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectJdkSetupStep extends ProjectSetupStep {
  @NotNull private final IdeSdks myIdeSdks;
  @NotNull private final Jdks myJdks;
  @NotNull private final IdeInfo myIdeInfo;

  @SuppressWarnings("unused") // Invoked by IDEA.
  public ProjectJdkSetupStep() {
    this(IdeSdks.getInstance(), Jdks.getInstance(), IdeInfo.getInstance());
  }

  @VisibleForTesting
  ProjectJdkSetupStep(@NotNull IdeSdks ideSdks, @NotNull Jdks jdks, @NotNull IdeInfo ideInfo) {
    myIdeSdks = ideSdks;
    myJdks = jdks;
    myIdeInfo = ideInfo;
  }

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    LanguageLevel javaLangVersion = JDK_1_8;
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk ideJdk;

    Application application = ApplicationManager.getApplication();
    boolean androidStudio = myIdeInfo.isAndroidStudio();
    if (androidStudio) {
      ideJdk = myIdeSdks.getJdk();
    }
    else if (projectJdk == null || !myJdks.isApplicableJdk(projectJdk, javaLangVersion)) {
      ideJdk = myJdks.chooseOrCreateJavaSdk(javaLangVersion);
    }
    else {
      ideJdk = projectJdk;
    }

    if (ideJdk == null) {
      SyncMessage message = new SyncMessage(DEFAULT_GROUP, ERROR, "Unable to find a JDK");
      message.add(myJdks.getWrongJdkQuickFixes(project));

      GradleSyncMessages.getInstance(project).report(message);
      return;
    }

    String homePath = ideJdk.getHomePath();
    if (homePath != null) {
      Runnable task = () -> application.runWriteAction(() -> myJdks.setJdk(project, ideJdk));
      submitTransaction(project, task);
    }
  }

  @Override
  public boolean invokeOnFailedSync() {
    return true;
  }
}
