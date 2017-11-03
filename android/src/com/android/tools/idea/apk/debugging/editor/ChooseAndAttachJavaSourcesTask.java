/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.debugging.editor;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.ApkFacetConfiguration;
import com.android.tools.idea.apk.debugging.DexSourceFiles;
import com.android.tools.idea.apk.debugging.ExternalSourceFolders;
import com.android.tools.idea.util.FileOrFolderChooser;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.ApkDebugProject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.stats.AnonymizerUtil.anonymizeUtf8;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.APK_DEBUG;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.APK_DEBUG_ATTACH_JAVA_SOURCES;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFiles;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createMultipleJavaPathDescriptor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

class ChooseAndAttachJavaSourcesTask implements Runnable {
  @NotNull private final String myClassFqn;
  @NotNull private final Module myModule;
  @NotNull private final EditorNotifications myEditorNotifications;
  @NotNull private final DumbService myDumbService;
  @NotNull private final DexSourceFiles myDexSourceFiles;
  @NotNull private final FileOrFolderChooser myFileOrFolderChooser;

  ChooseAndAttachJavaSourcesTask(@NotNull String classFqn, @NotNull Module module, @NotNull DexSourceFiles dexSourceFiles) {
    this(classFqn, module, DumbService.getInstance(module.getProject()), EditorNotifications.getInstance(module.getProject()),
         dexSourceFiles, project -> {
        FileChooserDescriptor descriptor = createMultipleJavaPathDescriptor();
        descriptor.setTitle("Attach Sources");
        //noinspection DialogTitleCapitalization
        descriptor.setDescription("Select directories in which Java sources are located");
        return chooseFiles(descriptor, module.getProject(), null);
      });
  }

  @VisibleForTesting
  ChooseAndAttachJavaSourcesTask(@NotNull String classFqn,
                                 @NotNull Module module,
                                 @NotNull DumbService dumbService,
                                 @NotNull EditorNotifications editorNotifications,
                                 @NotNull DexSourceFiles dexSourceFiles,
                                 @NotNull FileOrFolderChooser fileOrFolderChooser) {
    myClassFqn = classFqn;
    myModule = module;
    myDumbService = dumbService;
    myEditorNotifications = editorNotifications;
    myDexSourceFiles = dexSourceFiles;
    myFileOrFolderChooser = fileOrFolderChooser;
  }

  @Override
  public void run() {
    VirtualFile[] chosenFiles = myFileOrFolderChooser.choose(myModule.getProject());
    if (chosenFiles.length > 0) {

      // Send event to usage tracker
      ApkFacet facet = ApkFacet.getInstance(myModule);
      if (facet != null && !ApplicationManager.getApplication().isUnitTestMode()) {
        ApkDebugProject.Builder project = ApkDebugProject.newBuilder();
        project.setPackageId(anonymizeUtf8(facet.getConfiguration().APP_PACKAGE));

        AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
        // @formatter:off
        event.setCategory(APK_DEBUG)
             .setKind(APK_DEBUG_ATTACH_JAVA_SOURCES)
             .setApkDebugProject(project);
        // @formatter:on

        UsageTracker.getInstance().log(event);
      }

      ModifiableRootModel moduleModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
      ExternalSourceFolders sourceFolders = new ExternalSourceFolders(moduleModel);
      sourceFolders.addSourceFolders(chosenFiles, () -> {
        if (facet != null) {
          storeJavaSourceFolderPaths(facet, chosenFiles);
        }
        ApplicationManager.getApplication().runWriteAction(moduleModel::commit);
        // Wait until the new source folders are indexed. Otherwise PsiManager won't find the Java class.
        myDumbService.smartInvokeLater(() -> myDexSourceFiles.navigateToJavaFile(myClassFqn));
        myEditorNotifications.updateAllNotifications();
      });
    }
  }

  private static void storeJavaSourceFolderPaths(@NotNull ApkFacet facet, @NotNull VirtualFile[] chosenFiles) {
    ApkFacetConfiguration configuration = facet.getConfiguration();
    for (VirtualFile file : chosenFiles) {
      File path = virtualToIoFile(file);
      configuration.JAVA_SOURCE_FOLDER_PATHS.add(path.getPath());
    }
  }
}
