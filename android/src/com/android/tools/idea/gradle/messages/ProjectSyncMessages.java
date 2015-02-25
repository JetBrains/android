/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.messages;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.structure.gradle.AndroidProjectSettingsService;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_ANDROID_DEPENDENCIES;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_DEPENDENCIES;
import static com.android.tools.idea.gradle.util.Projects.setHasSyncErrors;

/**
 * Service that collects and displays, in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class ProjectSyncMessages {
  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemNotificationManager myNotificationManager;

  @NotNull
  public static ProjectSyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSyncMessages.class);
  }

  public int getErrorCount() {
    return myNotificationManager
      .getMessageCount(null, NotificationSource.PROJECT_SYNC, NotificationCategory.ERROR, GradleConstants.SYSTEM_ID);
  }

  public ProjectSyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    myProject = project;
    myNotificationManager = manager;
  }

  public void removeMessages(@NotNull String groupName) {
    myNotificationManager.clearNotifications(groupName, NotificationSource.PROJECT_SYNC, GradleConstants.SYSTEM_ID);
  }

  public int getMessageCount(@NotNull String groupName) {
    return myNotificationManager.getMessageCount(groupName, NotificationSource.PROJECT_SYNC, null, GradleConstants.SYSTEM_ID);
  }

  public void add(@NotNull final Message message, @NotNull NotificationHyperlink... hyperlinks) {
    Navigatable navigatable = message.getNavigatable();
    String title = message.getGroupName();
    String errorMsg = StringUtil.join(message.getText(), "\n");

    VirtualFile file = message.getFile();
    String filePath = file != null ? FileUtil.toSystemDependentName(file.getPath()) : null;

    NotificationData notification =
      new NotificationData(title, errorMsg, NotificationCategory.convert(message.getType().getValue()), NotificationSource.PROJECT_SYNC,
                           filePath, message.getLine(), message.getColumn(), false);
    notification.setNavigatable(navigatable);

    if (hyperlinks.length > 0) {
      AbstractSyncErrorHandler.updateNotification(notification, myProject, title, errorMsg, hyperlinks);
    }

    myNotificationManager.showNotification(GradleConstants.SYSTEM_ID, notification);
  }

  public boolean isEmpty() {
    return myNotificationManager.getMessageCount(NotificationSource.PROJECT_SYNC, null, GradleConstants.SYSTEM_ID) == 0;
  }

  public void reportSyncIssues(@NotNull Collection<SyncIssue> syncIssues, @NotNull Module module) {
    if (syncIssues.isEmpty()) {
      return;
    }

    boolean hasSyncErrors = false;
    VirtualFile buildFile = getBuildFile(module);

    for (SyncIssue syncIssue : syncIssues) {
      if (syncIssue.getSeverity() == SyncIssue.SEVERITY_ERROR) {
        hasSyncErrors = true;
      }
      switch (syncIssue.getType()) {
        case SyncIssue.TYPE_UNRESOLVED_DEPENDENCY:
          reportUnresolvedDependency(syncIssue.getData(), module, buildFile);
          break;
        default:
          String group = UNHANDLED_SYNC_ISSUE_TYPE;
          String text = syncIssue.getMessage();
          Message.Type severity = syncIssue.getType() == SyncIssue.SEVERITY_ERROR ? Message.Type.ERROR : Message.Type.WARNING;

          Message msg;
          if (buildFile != null) {
            msg = new Message(module.getProject(), group, severity, buildFile, -1, -1, text);
          }
          else {
            msg = new Message(group, severity, NonNavigatable.INSTANCE, text);
          }
          add(msg);
      }
    }

    if (hasSyncErrors) {
      setHasSyncErrors(myProject, true);
    }
  }

  public void reportUnresolvedDependencies(@NotNull Collection<String> unresolvedDependencies, @NotNull Module module) {
    if (unresolvedDependencies.isEmpty()) {
      return;
    }
    VirtualFile buildFile = getBuildFile(module);
    for (String dep : unresolvedDependencies) {
      reportUnresolvedDependency(dep, module, buildFile);
    }
    setHasSyncErrors(myProject, true);
  }

  @Nullable
  private static VirtualFile getBuildFile(@NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null && gradleFacet.getGradleProject() != null) {
      IdeaGradleProject gradleProject = gradleFacet.getGradleProject();
      return gradleProject.getBuildFile();
    }
    return null;
  }

  private void reportUnresolvedDependency(@NotNull String dependency, @NotNull Module module, @Nullable VirtualFile buildFile) {
    List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
    String group;
    if (dependency.startsWith("com.android.support")) {
      group = UNRESOLVED_ANDROID_DEPENDENCIES;
      hyperlinks.add(new InstallRepositoryHyperlink(SdkMavenRepository.ANDROID));
    }
    else if (dependency.startsWith("com.google.android.gms")) {
      group = UNRESOLVED_ANDROID_DEPENDENCIES;
      hyperlinks.add(new InstallRepositoryHyperlink(SdkMavenRepository.GOOGLE));
    }
    else {
      group = UNRESOLVED_DEPENDENCIES;
    }

    String text = "Failed to resolve: " + dependency;
    Message msg;
    if (buildFile != null) {
      int lineNumber = -1;
      int column = -1;

      Document document = FileDocumentManager.getInstance().getDocument(buildFile);
      if (document != null) {
        TextRange textRange = GradleUtil.findDependency(dependency, document.getText());
        if (textRange != null) {
          lineNumber = document.getLineNumber(textRange.getStartOffset());
          if (lineNumber > -1) {
            int lineStartOffset = document.getLineStartOffset(lineNumber);
            column = textRange.getStartOffset() - lineStartOffset;
          }
        }
      }

      msg = new Message(module.getProject(), group, Message.Type.ERROR, buildFile, lineNumber, column, text);
      String hyperlinkText = lineNumber > -1 ? "Show in File": "Open File";
      hyperlinks.add(new OpenFileHyperlink(buildFile.getPath(), hyperlinkText, lineNumber, column));
    }
    else {
      msg = new Message(group, Message.Type.ERROR, NonNavigatable.INSTANCE, text);
    }
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency);
      if (coordinate != null) {
        hyperlinks.add(new ShowDependencyInProjectStructureHyperlink(module, coordinate));
      }
    }
    add(msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
  }

  private static class ShowDependencyInProjectStructureHyperlink extends NotificationHyperlink {
    @NotNull private final Module myModule;
    @NotNull private final GradleCoordinate myDependency;

    ShowDependencyInProjectStructureHyperlink(@NotNull Module module, @NotNull GradleCoordinate dependency) {
      super("open.dependency.in.project.structure", "Show in Project Structure dialog");
      myModule = module;
      myDependency = dependency;
    }

    @Override
    protected void execute(@NotNull Project project) {
      ProjectSettingsService service = ProjectSettingsService.getInstance(project);
      if (service instanceof AndroidProjectSettingsService) {
        ((AndroidProjectSettingsService)service).openAndSelectDependency(myModule, myDependency);
      }
    }
  }

  private static class InstallRepositoryHyperlink extends NotificationHyperlink {
    @NotNull private final SdkMavenRepository myRepository;

    InstallRepositoryHyperlink(@NotNull SdkMavenRepository repository) {
      super("install.m2.repo", "Install Repository and sync project");
      myRepository = repository;
    }

    @Override
    protected void execute(@NotNull Project project) {
      List<IPkgDesc> requested = Lists.newArrayList();
      requested.add(myRepository.getPackageDescription());
      SdkQuickfixWizard wizard = new SdkQuickfixWizard(project, null, requested);
      wizard.init();
      wizard.setTitle("Install Missing Components");
      if (wizard.showAndGet()) {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      }
    }
  }
}
