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
package com.android.tools.idea.gradle.project.sync.issues;

import static com.android.ide.common.gradle.model.IdeSyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.util.AndroidGradleUtil.getDisplayNameForModule;
import static com.android.tools.idea.project.messages.MessageType.INFO;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

import com.android.ide.common.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueQuickFix;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.android.tools.idea.util.PositionInFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class provides simple deduplication behaviour for other reporters.
 * It contains several methods that can be overridden to provide more specific behaviour for different types of notifications.
 */
public abstract class SimpleDeduplicatingSyncIssueReporter extends BaseSyncIssuesReporter {
  /**
   * Reporting single sync issues falls back to the result message generation. This method should be overridden
   * in subclasses should different semantics be required.
   */
  @Override
  final void report(@NotNull IdeSyncIssue syncIssue,
                    @NotNull Module module,
                    @Nullable VirtualFile buildFile,
                    @NotNull SyncIssueUsageReporter usageReporter) {
    reportAll(ImmutableList.of(syncIssue), ImmutableMap.of(syncIssue, module),
              buildFile == null ? ImmutableMap.of() : ImmutableMap.of(module, buildFile), usageReporter);
  }

  @Override
  final void reportAll(@NotNull List<IdeSyncIssue> syncIssues,
                       @NotNull Map<IdeSyncIssue, Module> moduleMap,
                       @NotNull Map<Module, VirtualFile> buildFileMap,
                       @NotNull SyncIssueUsageReporter usageReporter) {
    // Group by the deduplication key.
    Map<Object, List<IdeSyncIssue>> groupedIssues = new LinkedHashMap<>();
    for (IdeSyncIssue issue : syncIssues) {
      groupedIssues.computeIfAbsent(getDeduplicationKey(issue), (config) -> new ArrayList<>()).add(issue);
    }

    // Report once for each group, including the list of affected modules.
    for (List<IdeSyncIssue> entry : groupedIssues.values()) {
      if (entry.isEmpty()) {
        continue;
      }
      IdeSyncIssue issue = entry.get(0);
      Module module = moduleMap.get(issue);
      if (module == null) {
        continue;
      }

      List<Module> affectedModules =
        entry.stream().map(moduleMap::get).filter(Objects::nonNull).distinct().sorted(Comparator.comparing(Module::getName))
             .collect(Collectors.toList());
      boolean isError = entry.stream().anyMatch(i -> i.getSeverity() == SEVERITY_ERROR);
      createNotificationDataAndReport(module.getProject(), entry, affectedModules, buildFileMap, isError, usageReporter);
    }
  }

  private void createNotificationDataAndReport(@NotNull Project project,
                                               @NotNull List<IdeSyncIssue> syncIssues,
                                               @NotNull List<Module> affectedModules,
                                               @NotNull Map<Module, VirtualFile> buildFileMap,
                                               boolean isError,
                                               @NotNull SyncIssueUsageReporter usageReporter) {
    GradleSyncMessages messages = GradleSyncMessages.getInstance(project);
    // All errors are displayed as warnings and all warnings displayed as info.
    MessageType type = isError ? WARNING : INFO;

    assert !syncIssues.isEmpty();
    NotificationData notification = setupNotificationData(project, syncIssues, affectedModules, buildFileMap, type);

    StringBuilder builder = new StringBuilder();

    // Add custom links
    final List<NotificationHyperlink> customLinks = getCustomLinks(project, syncIssues, affectedModules, buildFileMap);
    messages
      .updateNotification(notification, notification.getMessage(), customLinks);
    SyncIssueUsageReporterUtils.collect(usageReporter, syncIssues.get(0).getType(), customLinks);
    String message = notification.getMessage().trim();

    // Add links to each of the affected modules
    ArrayList<AndroidSyncIssueQuickFix> buildIssueLinks = new ArrayList<>();
    buildIssueLinks.addAll(ContainerUtil.map(customLinks, it -> new AndroidSyncIssueQuickFix(it)));
    if (shouldIncludeModuleLinks() && !affectedModules.isEmpty()) {
      builder.append("\nAffected Modules: ");
      for (Iterator<Module> it = affectedModules.iterator(); it.hasNext(); ) {
        Module m = it.next();
        if (m != null) {
          NotificationHyperlink link = doCreateModuleLink(project, notification, builder, m, syncIssues, buildFileMap.get(m));
          if (link != null) {
            buildIssueLinks.add(new AndroidSyncIssueQuickFix(link));
          }
          if (it.hasNext()) {
            builder.append(", ");
          }
        }
      }
    }
    message += builder.toString();

    notification.setMessage(message);
    messages.report(notification, buildIssueLinks);
  }

  private NotificationHyperlink doCreateModuleLink(@NotNull Project project,
                                  @NotNull NotificationData notification,
                                  @NotNull StringBuilder builder,
                                  @NotNull Module module,
                                  @NotNull List<IdeSyncIssue> syncIssues,
                                  @Nullable VirtualFile buildFile) {
    if (buildFile == null) {
      // No build file found, just include the name of the module.
      builder.append(getDisplayNameForModule(module));
      return null;
    }
    else {
      OpenFileHyperlink link = createModuleLink(project, module, syncIssues, buildFile);
      builder.append(link.toHtml());
      notification.setListener(link.getUrl(), new QuickFixNotificationListener(project, link));
      return link;
    }
  }


  /**
   * Creates the module link for this SyncIssue, this allows subclasses to link to specific files relevant to the issue. It defaults to
   * simply linking to the module build file.
   *
   * @param project           the project.
   * @param module            the module this link should be created for.
   * @param syncIssues        list of all the sync issues in this group, this list will contain at least one element.
   * @param buildFile         the build file for the provided module.
   */
  @NotNull
  protected OpenFileHyperlink createModuleLink(@NotNull Project project,
                                               @NotNull Module module,
                                               @NotNull List<IdeSyncIssue> syncIssues,
                                               @NotNull VirtualFile buildFile) {

    return new OpenFileHyperlink(buildFile.getPath(), getDisplayNameForModule(module), -1, -1);
  }

  /**
   * @param issue each issue.
   * @return the key that should be used to deduplicate issues, each issue with the same key will be grouped and reported as one, this
   * method should be stateless.
   */
  @NotNull
  protected Object getDeduplicationKey(@NotNull IdeSyncIssue issue) {
    return (issue.getData() == null) ? issue : issue.getData();
  }

  /**
   * @return whether or not links to each of the effected modules should be appended to the SyncIssue message.
   */
  protected boolean shouldIncludeModuleLinks() {
    return true;
  }

  /**
   * Allows reporters to provide custom links for each type of error message.
   *
   * @param project         the project.
   * @param syncIssues      grouped sync issues, these all return the same value from {@link #getDeduplicationKey(SyncIssue)}.
   * @param affectedModules list of origin modules that the issues in syncIssues belong to.
   * @param buildFileMap    a module to build file map, entries are optional.
   * @return a list of hyperlinks to be included in the message displayed to the user.
   */
  @NotNull
  protected List<NotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                       @NotNull List<IdeSyncIssue> syncIssues,
                                                       @NotNull List<Module> affectedModules,
                                                       @NotNull Map<Module, VirtualFile> buildFileMap) {
    return ImmutableList.of();
  }

  /**
   * Allows reporter to customize the sync issue message. Subclasses can call this method to create a basic notification message to
   * mutate or can create them from scratch.
   *
   * @param project         the project.
   * @param syncIssues      grouped sync issues, these all return the same value from {@link #getDeduplicationKey(SyncIssue)}.
   * @param affectedModules list of origin modules that the issues in syncIssues belong to.
   * @param buildFileMap    a module to build file map, entries are optional.
   * @param type            whether or not this group of issues contain errors or warnings.
   * @return a {@link NotificationData} instance with the correct configuration.
   */
  @NotNull
  protected NotificationData setupNotificationData(@NotNull Project project,
                                                   @NotNull List<IdeSyncIssue> syncIssues,
                                                   @NotNull List<Module> affectedModules,
                                                   @NotNull Map<Module, VirtualFile> buildFileMap,
                                                   @NotNull MessageType type) {
    assert !syncIssues.isEmpty();
    GradleSyncMessages messages = GradleSyncMessages.getInstance(project);
    PositionInFile position = null;
    // If we only have one module/file allow us to navigate to it.
    if (affectedModules.size() == 1) {
      VirtualFile file = buildFileMap.get(affectedModules.get(0));
      if (file != null) {
        position = new PositionInFile(file);
      }
    }

    NotificationData data = messages.createNotification(DEFAULT_GROUP, syncIssues.get(0).getMessage(), type.convertToCategory(), position);
    if (position != null) {
      data.setNavigatable(new OpenFileDescriptor(project, position.file, position.line, position.column));
    }
    return data;
  }

  public static int getLineNumberForElement(@NotNull Project project,
                                            @Nullable PsiElement element) {
    return ApplicationManager.getApplication().runReadAction((Computable<Integer>)() -> {
      if (element != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        if (document != null) {
          return document.getLineNumber(element.getTextOffset());
        }
      }
      return -1;
    });
  }
}
