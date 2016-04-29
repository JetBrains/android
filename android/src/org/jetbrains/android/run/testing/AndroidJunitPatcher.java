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
package org.jetbrains.android.run.testing;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathsList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Implementation of {@link com.intellij.execution.JUnitPatcher} that removes android.jar from the class path. It's only applicable to
 * JUnit run configurations if the selected test artifact is "unit tests". In this case, the mockable android.jar is already in the
 * dependencies (taken from the model).
 */
public class AndroidJunitPatcher extends JUnitPatcher {
  @Override
  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    if (module == null) {
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return;
    }

    AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
    if (androidModel == null) {
      return;
    }

    // Modify the class path only if we're dealing with the unit test artifact.
    if (!androidModel.getSelectedTestArtifactName().equals(AndroidProject.ARTIFACT_UNIT_TEST)) {
      return;
    }

    final PathsList classPath = javaParameters.getClassPath();

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return;
    }

    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (!(data instanceof AndroidSdkAdditionalData)) {
      return;
    }

    final AndroidPlatform platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();
    if (platform == null) {
      return;
    }

    classPath.remove(platform.getTarget().getPath(IAndroidTarget.ANDROID_JAR));

    // Move the mockable android jar to the end.
    String mockableJarPath = null;
    for (String path : classPath.getPathList()) {
      if (new File(FileUtil.toSystemDependentName(path)).getName().startsWith("mockable-android")) {
        // PathsList stores strings - use the one that's actually stored there.
        mockableJarPath = path;
        break;
      }
    }

    if (mockableJarPath != null) {
      classPath.remove(mockableJarPath);
      classPath.addTail(mockableJarPath);
    }
  }
}
