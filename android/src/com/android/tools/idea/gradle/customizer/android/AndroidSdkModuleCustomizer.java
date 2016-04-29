/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

/**
 * Sets an Android SDK to a module imported from an {@link AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer<AndroidGradleModel> {
  private static final Logger LOG = Logger.getInstance(AndroidSdkModuleCustomizer.class);

  /**
   * Sets an Android SDK to the given module only if:
   * <ol>
   * <li>the given module was created by importing an {@code AndroidProject}</li>
   * <li>there is a matching Android SDK already defined in IDEA</li>
   * </ol>
   *
   * @param project        project that owns the module to customize.
   * @param modelsProvider modifiable IDE models provider to customize. The caller is responsible to commit the changes to models
   *                       and the customizer should not call commit on the models.
   * @param androidModel   the imported Android model.
   */
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable AndroidGradleModel androidModel) {
    if (androidModel == null) {
      return;
    }
    File androidSdkHomePath = IdeSdks.getAndroidSdkPath();
    // Android SDK may be not configured in IntelliJ
    if (androidSdkHomePath == null) {
      LOG.warn("Path to Android SDK not set");

      List<Sdk> sdks = IdeSdks.getEligibleAndroidSdks();
      LOG.warn("# of eligible SDKs: " + sdks.size());
      for (Sdk sdk : sdks) {
        LOG.info("sdk: " + sdk.toString());
      }
      return;
    }

    final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    LanguageLevel languageLevel = androidModel.getJavaLanguageLevel();
    if (languageLevel != null) {
      moduleModel.getModuleExtension(LanguageLevelModuleExtensionImpl.class).setLanguageLevel(languageLevel);
    }

    AndroidProject androidProject = androidModel.getAndroidProject();
    String compileTarget = androidProject.getCompileTarget();

    Sdk sdk = findSuitableAndroidSdk(compileTarget);
    if (sdk == null) {
      sdk = tryToCreateAndroidSdk(androidSdkHomePath, compileTarget);

      if (sdk == null) {
        // If SDK was not created, this might be an add-on.
        sdk = findMatchingSdkForAddon(androidProject);
      }
    }

    if (sdk != null) {
      moduleModel.setSdk(sdk);
      String sdkPath = sdk.getHomePath();
      if (sdkPath == null) {
        sdkPath = "<path not set>";
      }
      LOG.info("Setting SDK in the module model: " + sdk.getName() + " @ " + sdkPath);
      return;
    }

    String text = String.format("Module '%1$s': platform '%2$s' not found.", module.getName(), compileTarget);
    LOG.warn(text);

    Message msg = new Message(FAILED_TO_SET_UP_SDK, Message.Type.ERROR, text);
    ProjectSyncMessages.getInstance(project).add(msg);
  }

  @Nullable
  private static Sdk findMatchingSdkForAddon(@NotNull AndroidProject androidProject) {
    File androidJarPath = null;
    Collection<String> bootClasspath = androidProject.getBootClasspath();
    for (String entry : bootClasspath) {
      File file = new File(entry);
      if (FN_FRAMEWORK_LIBRARY.equals(file.getName())) {
        androidJarPath = file;
        break;
      }
    }
    // We only know the path of android.jar. Now we are going to find an existing Android SDK that uses the same android.jar.
    // If the boot classpath has more than 1 element (something besides android.jar), we are dealing with an add-on.
    if (androidJarPath != null && bootClasspath.size() > 1) {
      // This is an add-on
      for (Sdk sdk : getAllAndroidSdks()) {
        for (VirtualFile sdkFile : sdk.getRootProvider().getFiles(CLASSES)) {
          // We need to convert the VirtualFile to java.io.File, because the path of the VirtualPath is using 'jar' protocol and it won't
          // match the path returned by AndroidProject#getBootClasspath().
          File sdkFilePath = virtualToIoFile(sdkFile);
          if (filesEqual(sdkFilePath, androidJarPath)) {
            return sdk;
          }
        }
      }
    }
    return null;
  }
}
