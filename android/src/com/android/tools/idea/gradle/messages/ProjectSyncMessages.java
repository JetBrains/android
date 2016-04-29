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
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.customizer.dependency.DependencySetupErrors;
import com.android.tools.idea.gradle.customizer.dependency.DependencySetupErrors.MissingModule;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.compatibility.VersionCompatibilityService;
import com.android.tools.idea.gradle.project.compatibility.VersionCompatibilityService.VersionIncompatibilityMessage;
import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.service.notification.errors.UnsupportedGradleVersionErrorHandler;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Verify;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.*;
import static com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler.updateNotification;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtil.toStringArray;

/**
 * Service that collects and displays, in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class ProjectSyncMessages {
  private static final NotificationSource NOTIFICATION_SOURCE = PROJECT_SYNC;

  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemNotificationManager myNotificationManager;

  @NotNull
  public static ProjectSyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSyncMessages.class);
  }

  public int getErrorCount() {
    return myNotificationManager.getMessageCount(null, NOTIFICATION_SOURCE, NotificationCategory.ERROR, GRADLE_SYSTEM_ID);
  }

  public ProjectSyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    myProject = project;
    myNotificationManager = manager;
  }

  public int getMessageCount(@NotNull String groupName) {
    return myNotificationManager.getMessageCount(groupName, NOTIFICATION_SOURCE, null, GRADLE_SYSTEM_ID);
  }

  public boolean isEmpty() {
    return myNotificationManager.getMessageCount(NOTIFICATION_SOURCE, null, GRADLE_SYSTEM_ID) == 0;
  }

  public void reportComponentIncompatibilities() {
    VersionCompatibilityService compatibilityService = VersionCompatibilityService.getInstance();
    List<VersionIncompatibilityMessage> messages = compatibilityService.checkComponentCompatibility(myProject);
    for (VersionIncompatibilityMessage message : messages) {
      add(message.getMessage(), message.getQuickFixes());
    }
    if (!messages.isEmpty()) {
      setHasSyncErrors(myProject, true);
    }
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
          reportUnresolvedDependency(Verify.verifyNotNull(syncIssue.getData()), module, buildFile);
          break;
        default:
          String group = UNHANDLED_SYNC_ISSUE_TYPE;
          String text = syncIssue.getMessage();
          Message.Type severity = syncIssue.getSeverity() == SyncIssue.SEVERITY_ERROR ? Message.Type.ERROR : Message.Type.WARNING;

          Message msg;
          if (buildFile != null) {
            msg = new Message(module.getProject(), group, severity, buildFile, -1, -1, text);
          }
          else {
            msg = new Message(group, severity, NonNavigatable.INSTANCE, text);
          }

          List<NotificationHyperlink> hyperlinks = getHyperlinks(myProject, text);
          add(msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
      }
    }

    if (hasSyncErrors) {
      setHasSyncErrors(myProject, true);
    }
  }

  @NotNull
  private static List<NotificationHyperlink> getHyperlinks(@NotNull Project project, @NotNull String message) {
    String version = UnsupportedGradleVersionErrorHandler.getSupportedGradleVersion(message);
    if (isNotEmpty(version)) {
      return UnsupportedGradleVersionErrorHandler.getQuickFixHyperlinks(project, version);
    }
    return Collections.emptyList();
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
    if (gradleFacet != null && gradleFacet.getGradleModel() != null) {
      GradleModel gradleModel = gradleFacet.getGradleModel();
      return gradleModel.getBuildFile();
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
    else if (dependency.startsWith("com.google.android")) {
      group = UNRESOLVED_ANDROID_DEPENDENCIES;
      hyperlinks.add(new InstallRepositoryHyperlink(SdkMavenRepository.GOOGLE));
    }
    else {
      group = UNRESOLVED_DEPENDENCIES;
      if (isOfflineBuildModeEnabled(myProject)) {
        NotificationHyperlink disableOfflineModeHyperlink = new NotificationHyperlink("disable.gradle.offline.mode", "Disable offline mode and Sync") {
          @Override
          protected void execute(@NotNull Project project) {
            GradleSettings.getInstance(myProject).setOfflineWork(false);
            GradleProjectImporter.getInstance().requestProjectSync(project, null);
          }
        };
        hyperlinks.add(disableOfflineModeHyperlink);
      }
    }

    String text = "Failed to resolve: " + dependency;
    Message msg;
    if (buildFile != null) {
      int lineNumber = -1;
      int column = -1;

      Document document = FileDocumentManager.getInstance().getDocument(buildFile);
      if (document != null) {
        TextRange textRange = findDependency(dependency, document.getText());
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
    if (AndroidStudioInitializer.isAndroidStudio()) {
      GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(dependency);
      if (coordinate != null) {
        hyperlinks.add(new ShowDependencyInProjectStructureHyperlink(module, coordinate));
      }
    }
    add(msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));
  }


  @Nullable
  private static TextRange findDependency(@NotNull final String dependency, @NotNull String contents) {
    return findStringLiteral(dependency, contents, pair -> {
      GroovyLexer lexer = pair.getSecond();
      return TextRange.create(lexer.getTokenStart() + 1, lexer.getTokenEnd() - 1);
    });
  }

  @Nullable
  private static <T> T findStringLiteral(@NotNull String textToSearchPrefix,
                                         @NotNull String fileContents,
                                         @NotNull Function<Pair<String, GroovyLexer>, T> consumer) {
    GroovyLexer lexer = new GroovyLexer();
    lexer.start(fileContents);
    while (lexer.getTokenType() != null) {
      IElementType type = lexer.getTokenType();
      if (type == GroovyTokenTypes.mSTRING_LITERAL) {
        String text = unquoteString(lexer.getTokenText());
        if (text.startsWith(textToSearchPrefix)) {
          return consumer.fun(Pair.create(text, lexer));
        }
      }
      lexer.advance();
    }
    return null;
  }

  public void reportDependencySetupErrors() {
    DependencySetupErrors setupErrors = getDependencySetupErrors(myProject);
    if (setupErrors != null) {
      reportModulesNotFoundErrors(setupErrors);
      setDependencySetupErrors(myProject, null);
    }
  }

  private void reportModulesNotFoundErrors(@NotNull DependencySetupErrors setupErrors) {
    reportModulesNotFoundIssues(MISSING_DEPENDENCIES_BETWEEN_MODULES, setupErrors.getMissingModules());

    for (String dependent : setupErrors.getMissingNames()) {
      String msg = String.format("Module '%1$s' depends on modules that do not have a name.", dependent);
      add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, Message.Type.ERROR, msg));
    }

    for (String dependent : setupErrors.getDependentsOnLibrariesWithoutBinaryPath()) {
      String msg = String.format("Module '%1$s' depends on libraries that do not have a 'binary' path.", dependent);
      add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, Message.Type.ERROR, msg));
    }

    for (DependencySetupErrors.InvalidModuleDependency dependency : setupErrors.getInvalidModuleDependencies()) {
      String msg = String.format("Ignoring dependency of module '%1$s' on module '%2$s'. %3$s",
                                 dependency.dependent, dependency.dependency.getName(), dependency.detail);
      VirtualFile buildFile = getBuildFile(dependency.dependency);
      assert buildFile != null;
      add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, Message.Type.WARNING, new OpenFileDescriptor(dependency.dependency.getProject(), buildFile, 0), msg));
    }

    reportModulesNotFoundIssues(FAILED_TO_SET_UP_DEPENDENCIES, setupErrors.getMissingModulesWithBackupLibraries());
  }

  private void reportModulesNotFoundIssues(@NotNull String groupName, @NotNull List<MissingModule> missingModules) {
    if (!missingModules.isEmpty()) {
      Message.Type severity = Message.Type.ERROR;

      for (MissingModule missingModule : missingModules) {
        List<String> messageLines = Lists.newArrayList();

        StringBuilder text = new StringBuilder();
        text.append(String.format("Unable to find module with Gradle path '%1$s' (needed by module", missingModule.dependencyPath));

        addDependentsToText(text, missingModule.dependentNames);
        text.append(".)");
        messageLines.add(text.toString());

        String backupLibraryName = missingModule.backupLibraryName;
        if (isNotEmpty(backupLibraryName)) {
          severity = Message.Type.WARNING;
          String msg = String.format("Linking to library '%1$s' instead.", backupLibraryName);
          messageLines.add(msg);
        }
        add(new Message(groupName, severity, toStringArray(messageLines)));
      }

      // If the project is really a subset of the project, attempt to find and include missing modules.
      ProjectSubset projectSubset = ProjectSubset.getInstance(myProject);
      String[] selection = projectSubset.getSelection();
      boolean hasSelection = selection != null && selection.length > 0;
      if (severity == Message.Type.ERROR && hasSelection && projectSubset.hasCachedModules()) {
        String msg = "The missing modules may have been excluded from the project subset.";
        add(new Message(groupName, Message.Type.INFO, msg), new IncludeMissingModulesHyperlink(missingModules));
      }
    }
  }

  private static void addDependentsToText(@NotNull StringBuilder text, @NotNull List<String> dependents) {
    assert !dependents.isEmpty();

    if (dependents.size() == 1) {
      text.append(String.format(" '%1$s'", dependents.get(0)));
      return;
    }

    text.append("s: ");
    int i = 0;
    for (String dependent : dependents) {
      if (i++ > 0) {
        text.append(", ");
      }
      text.append(String.format("'%1$s'", dependent));
    }
  }

  public void add(@NotNull final Message message, @NotNull NotificationHyperlink... hyperlinks) {
    Navigatable navigatable = message.getNavigatable();
    String title = message.getGroupName();
    String errorMsg = join(message.getText(), "\n");

    VirtualFile file = message.getFile();
    String filePath = file != null ? virtualToIoFile(file).getPath() : null;

    NotificationCategory category = NotificationCategory.convert(message.getType().getValue());
    NotificationData notification =
      new NotificationData(title, errorMsg, category, NOTIFICATION_SOURCE, filePath, message.getLine(), message.getColumn(), false);
    notification.setNavigatable(navigatable);

    if (hyperlinks.length > 0) {
      updateNotification(notification, myProject, title, errorMsg, hyperlinks);
    }

    myNotificationManager.showNotification(GRADLE_SYSTEM_ID, notification);
  }

  public void removeMessages(@NotNull String... groupNames) {
    for (String groupName : groupNames) {
      myNotificationManager.clearNotifications(groupName, NOTIFICATION_SOURCE, GRADLE_SYSTEM_ID);
    }
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
      List<String> requested = Lists.newArrayList();
      requested.add(myRepository.getPackageId());
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
      if (dialog != null) {
        dialog.setTitle("Install Missing Components");
        if (dialog.showAndGet()) {
          GradleProjectImporter.getInstance().requestProjectSync(project, null);
        }
      }
    }
  }

  /**
   * "Quick Fix" link that attempts to find and include any modules that were not previously included in the project subset.
   */
  private static class IncludeMissingModulesHyperlink extends NotificationHyperlink {
    @NotNull private final Set<String> myModuleGradlePaths;

    IncludeMissingModulesHyperlink(@NotNull List<MissingModule> missingModules) {
      super("include.missing.modules", "Find and include missing modules");
      myModuleGradlePaths = Sets.newHashSetWithExpectedSize(missingModules.size());
      for (MissingModule module : missingModules) {
        myModuleGradlePaths.add(module.dependencyPath);
      }
    }

    @Override
    protected void execute(@NotNull Project project) {
      ProjectSubset.getInstance(project).findAndIncludeModules(myModuleGradlePaths);
    }
  }
}
