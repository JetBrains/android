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

import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.HyperlinkLabel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that the Android SDK file they opened doesn't have a source file associated with it, and provides a link to download the
 * source via the SDK Manager.
 */
public class AttachAndroidSdkSourcesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("add sdk sources to class");

  public static final Key<List<AndroidVersion>> REQUIRED_SOURCES_KEY = Key.create("sources to download");

  private final Project myProject;

  public AttachAndroidSdkSourcesNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  @UiThread
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.get()) {
      // If the displayed file has user data indicating this banner panel should be displayed for particular sources, just use that.
      List<AndroidVersion> requiredSources = file.getUserData(REQUIRED_SOURCES_KEY);
      if (requiredSources != null && !requiredSources.isEmpty()) {
        return createPanel(fileEditor, requiredSources, null);
      }
    }

    // Check whether the file is a class file in the SDK. This can happen when the user browses to the source of an SDK file.
    return createNotificationPanelForClassFiles(file, fileEditor);
  }

  private EditorNotificationPanel createNotificationPanelForClassFiles(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return null;
    }

    if (JavaEditorFileSwapper.findSourceFile(myProject, file) != null) {
      // Since the java source was found, no need to download sources.
      return null;
    }

    // Since the java source was not found, it might come from an Android SDK.
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

    AndroidVersion apiVersion = platform.getApiVersion();
    Runnable refresh = () -> updateSdkSourceRoot(sdk);
    if (StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.get()) {
      return createPanel(fileEditor, ImmutableList.of(apiVersion), refresh);
    }
    else {
      String title = "Sources for '" + jdkOrderEntry.getJdkName() + "' not found.";
      return createLegacyPanel(fileEditor, title, apiVersion, refresh);
    }
  }

  @NotNull
  private MyEditorNotificationPanel createPanel(
    @NotNull FileEditor fileEditor,
    @NotNull List<AndroidVersion> requestedSourceVersions,
    @Nullable Runnable refreshAfterDownload) {
    MyEditorNotificationPanel panel = new MyEditorNotificationPanel(fileEditor);
    panel.setText("Android SDK sources not found.");
    panel.createAndAddLink("Download SDK Sources", () -> {
      List<String> sourcesPaths =
        requestedSourceVersions.stream().map(DetailsTypes::getSourcesPath).collect(ImmutableList.toImmutableList());
      ModelWizardDialog dialog = createSdkDownloadDialog(sourcesPaths);
      if (dialog != null && dialog.showAndGet()) {
        if (refreshAfterDownload != null) {
          refreshAfterDownload.run();
        }
      }
    });
    return panel;
  }

  private MyEditorNotificationPanel createLegacyPanel(
    @NotNull FileEditor fileEditor,
    @NotNull String title,
    @NotNull AndroidVersion requestedSourceVersion,
    @NotNull Runnable refresh) {
    MyEditorNotificationPanel panel = new MyEditorNotificationPanel(fileEditor);
    panel.setText(title);
    panel.createAndAddLink("Download", () -> {
      String sourcesPath = DetailsTypes.getSourcesPath(requestedSourceVersion);
      ModelWizardDialog dialog = createSdkDownloadDialog(ImmutableList.of(sourcesPath));
      if (dialog != null && dialog.showAndGet()) {
        refresh.run();
      }
    });
    panel.createAndAddLink("Refresh (if already downloaded)", refresh);
    return panel;
  }

  @VisibleForTesting
  protected ModelWizardDialog createSdkDownloadDialog(List<String> requestedPaths) {
    return SdkQuickfixUtils.createDialogForPaths(myProject, requestedPaths);
  }

  @Nullable
  private JdkOrderEntry findAndroidSdkEntryForFile(@NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
      if (entry instanceof JdkOrderEntry) {
        JdkOrderEntry jdkOrderEntry = (JdkOrderEntry)entry;
        Sdk jdk = jdkOrderEntry.getJdk();
        if (jdk != null && AndroidSdks.getInstance().isAndroidSdk(jdk)) {
          return jdkOrderEntry;
        }
      }
    }
    return null;
  }

  @VisibleForTesting
  static class MyEditorNotificationPanel extends EditorNotificationPanel {
    private final Map<String, Runnable> myLinks = new HashMap<>();

    private MyEditorNotificationPanel(@Nullable FileEditor fileEditor) {
      super(fileEditor);
    }

    private void createAndAddLink(@NlsContexts.LinkLabel String text, @NotNull Runnable action) {
      // Despite the name, `createActionLabel` both creates the label and adds it to the panel.
      HyperlinkLabel label = createActionLabel(text, action);

      // This collection is just for tracking for test purposes.
      myLinks.put(label.getText(), action);
    }

    @VisibleForTesting
    Map<String, Runnable> getLinks() {
      return myLinks;
    }
  }
}
