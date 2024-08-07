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
package com.google.idea.blaze.base.lang;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.qsync.NotSupportedWithQuerySyncException;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Detects usage of files in supported but inactive languages, and offers to add them to the project
 * view.
 */
public class AdditionalLanguagesHelper
    extends EditorNotifications.Provider<EditorNotificationPanel> {

  private static final Key<EditorNotificationPanel> KEY = Key.create("add additional language");

  // avoid notifying more than once per project per language.
  private final Set<LanguageClass> notifiedLanguages = Sets.newHashSet();
  private final Project project;
  private final EditorNotifications notifications;

  AdditionalLanguagesHelper(Project project) {
    this.project = project;
    this.notifications = EditorNotifications.getInstance(project);

    for (LanguageClass langauge : LanguageClass.values()) {
      if (PropertiesComponent.getInstance(project).getBoolean(propertyKey(langauge))) {
        notifiedLanguages.add(langauge);
      }
    }
  }

  private void suppressNotifications(LanguageClass language) {
    PropertiesComponent.getInstance(project).setValue(propertyKey(language), true);
    notifiedLanguages.add(language);
    notifications.updateAllNotifications();
  }

  private static String propertyKey(LanguageClass language) {
    return "additional_languages_helper_suppressed_" + language.getName();
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
    if (Blaze.getProjectType(project).equals(ProjectType.UNKNOWN)) {
      return null;
    }

    String ext = file.getExtension();
    if (ext == null) {
      return null;
    }
    LanguageClass language = LanguageClass.fromExtension(ext);
    if (language == null || notifiedLanguages.contains(language)) {
      return null;
    }
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      // TODO(b/260643753)
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    WorkspaceLanguageSettings settings = projectData.getWorkspaceLanguageSettings();
    if (settings.isLanguageActive(language)) {
      return null;
    }
    if (!LanguageSupport.supportedLanguagesForWorkspaceType(settings.getWorkspaceType())
        .contains(language)) {
      return null;
    }

    String langName = language.getName();
    String message =
        String.format(
            "Do you want to enable %s plugin %s support?",
            Blaze.buildSystemName(project), langName);

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(message);
    panel.createActionLabel(
        String.format("Enable %s support", langName),
        () -> {
          enableLanguageSupport(project, ImmutableList.of(language));
          suppressNotifications(language);
        });
    panel.createActionLabel("Don't show again", () -> suppressNotifications(language));
    return panel;
  }

  /**
   * Adds the specified languages to the project view's 'additional_languages' section, and
   * installs/enables any other required plugins.
   */
  public static void enableLanguageSupport(Project project, List<LanguageClass> languages) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      throw new NotSupportedWithQuerySyncException(
          "Additional languages not applicable to querysync.");
    }
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ListSection<LanguageClass> existingSection =
                  builder.getLast(AdditionalLanguagesSection.KEY);
              builder.replace(
                  existingSection,
                  ListSection.update(AdditionalLanguagesSection.KEY, existingSection)
                      .addAll(languages));
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();

    ImmutableSet<String> requiredPlugins =
        Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
            .map(syncPlugin -> syncPlugin.getRequiredExternalPluginIds(languages))
            .flatMap(Collection::stream)
            .collect(toImmutableSet());
    PluginUtils.installOrEnablePlugins(requiredPlugins);

    BlazeSyncManager.getInstance(project)
        .requestProjectSync(
            BlazeSyncParams.builder()
                .setTitle("Sync")
                .setSyncMode(SyncMode.INCREMENTAL)
                .setSyncOrigin("added_language_support")
                .setAddProjectViewTargets(true)
                .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
                .build());
  }
}
