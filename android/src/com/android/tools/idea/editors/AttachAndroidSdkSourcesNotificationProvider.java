/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors;

import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Lists;
import com.intellij.ProjectTopics;
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;
import static org.jetbrains.android.sdk.AndroidSdkUtils.updateSdkSourceRoot;

/**
 * Notifies users that the android SDK class they opened doesn't have a source file associated with it, and provide two links: one to
 * open SDK manager to download the source, another to update the SDK information cached in the IDE once source code is downloaded.
 */
public class AttachAndroidSdkSourcesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("add sdk sources to class");

  private final Project myProject;

  public AttachAndroidSdkSourcesNotificationProvider(@NotNull Project project, @NotNull final EditorNotifications notifications) {
    myProject = project;
    myProject.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        notifications.updateAllNotifications();
      }
    });
  }

  @Override
  @NotNull
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() != JavaClassFileType.INSTANCE) {
      return null;
    }

    // Locate the java source of the class file, if not found, then it might come from a SDK.
    if (JavaEditorFileSwapper.findSourceFile(myProject, file) == null) {
      JdkOrderEntry jdkOrderEntry = findAndroidSdkEntryForFile(file);

      if (jdkOrderEntry == null) {
        return null;
      }
      final Sdk sdk = jdkOrderEntry.getJdk();

      String sdkHome = sdk.getHomePath();
      if (sdkHome == null) {
        return null;
      }

      if (sdk.getRootProvider().getFiles(OrderRootType.SOURCES).length > 0) {
        return null;
      }

      final AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
      if (platform == null) {
        return null;
      }
      EditorNotificationPanel panel = new EditorNotificationPanel();

      panel.setText("Sources for '" + jdkOrderEntry.getJdkName() + "' not found.");

      panel.createActionLabel("Download", new Runnable() {
        @Override
        public void run() {
          List<String> requested = Lists.newArrayList();
          requested.add(DetailsTypes.getSourcesPath(platform.getApiVersion()));

          ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myProject, requested);
          if (dialog != null && dialog.showAndGet()) {
            updateSdkSourceRoot(sdk);
          }
        }
      });

      panel.createActionLabel("Refresh (if already downloaded)", new Runnable() {
        @Override
        public void run() {
          updateSdkSourceRoot(sdk);
        }
      });
      return panel;
    }
    return null;
  }

  @Nullable
  private JdkOrderEntry findAndroidSdkEntryForFile(@NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(myProject);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof JdkOrderEntry) {
        JdkOrderEntry jdkOrderEntry = (JdkOrderEntry) entry;
        if (isAndroidSdk(jdkOrderEntry.getJdk())) {
          return jdkOrderEntry;
        }
      }
    }
    return null;
  }
}
