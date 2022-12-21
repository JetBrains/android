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

import static org.jetbrains.android.sdk.AndroidSdkUtils.updateSdkSourceRoot;

import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.codeEditor.JavaEditorFileSwapper;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that the android SDK class they opened doesn't have a source file associated with it, and provide two links: one to
 * open SDK manager to download the source, another to update the SDK information cached in the IDE once source code is downloaded.
 */
public class AttachAndroidSdkSourcesNotificationProvider implements EditorNotificationProvider {
  private final Project myProject;

  public AttachAndroidSdkSourcesNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return null;
    }

    // Locate the java source of the class file, if not found, then it might come from a SDK.
    if (JavaEditorFileSwapper.findSourceFile(myProject, file) != null) {
      return null;
    }
    JdkOrderEntry jdkOrderEntry = findAndroidSdkEntryForFile(file);

    if (jdkOrderEntry == null) {
      return null;
    }
    Sdk sdk = jdkOrderEntry.getJdk();
    if (sdk == null) {
      return null;
    }

    String sdkHome = sdk.getHomePath();
    if (sdkHome == null) {
      return null;
    }
    if (sdk.getRootProvider().getFiles(OrderRootType.SOURCES).length > 0) {
      return null;
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform == null) {
      return null;
    }

    return fileEditor -> {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);

      panel.setText("Sources for '" + jdkOrderEntry.getJdkName() + "' not found.");
      panel.createActionLabel("Download", () -> {
        List<String> requested = new ArrayList<>();
        requested.add(DetailsTypes.getSourcesPath(platform.getApiVersion()));

        ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myProject, requested);
        if (dialog != null && dialog.showAndGet()) {
          updateSdkSourceRoot(sdk);
        }
      });
      panel.createActionLabel("Refresh (if already downloaded)", () -> updateSdkSourceRoot(sdk));
      return panel;
    };
  }

  @Nullable
  private JdkOrderEntry findAndroidSdkEntryForFile(@NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof JdkOrderEntry) {
        JdkOrderEntry jdkOrderEntry = (JdkOrderEntry) entry;
        Sdk jdk = jdkOrderEntry.getJdk();
        if (jdk != null && AndroidSdks.getInstance().isAndroidSdk(jdk)) {
          return jdkOrderEntry;
        }
      }
    }
    return null;
  }
}
