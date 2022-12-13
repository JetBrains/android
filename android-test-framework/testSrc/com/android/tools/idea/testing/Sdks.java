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
package com.android.tools.idea.testing;

import static com.android.tools.sdk.AndroidSdkData.getSdkData;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.PathUtil.toSystemIndependentName;
import static org.junit.Assert.assertNotNull;

import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.android.tools.sdk.AndroidSdkData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.util.ArrayUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Sdks {
  private Sdks() {
  }

  @NotNull
  public static Sdk addLatestAndroidSdk(@NotNull Disposable parentDisposable, @NotNull Module module) {
    //otherwise sdk will be added in ModuleRootModificationUtil.setModuleSdk; proper parentDisposable should be provided anyway
    boolean addToSdkTable = !ApplicationManager.getApplication().isUnitTestMode();
    Sdk androidSdk = createLatestAndroidSdk(parentDisposable, "SDK", addToSdkTable);
    Disposer.register(parentDisposable, () -> WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk(androidSdk)));
    ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
    return androidSdk;
  }

  public static Sdk createLatestAndroidSdk() {
    return createLatestAndroidSdk(null, "SDK", true);
  }

  public static Sdk createLatestAndroidSdk(@Nullable Disposable parentDisposable, String name, boolean addToSdkTable) {
    String sdkPath = TestUtils.getSdk().toString();
    String platformDir = TestUtils.getLatestAndroidPlatform();

    Sdk sdk = ProjectJdkTable.getInstance().createSdk(name, AndroidSdkType.getInstance());
    if (addToSdkTable) {
      ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk));
      if (parentDisposable != null) {
        Disposer.register(parentDisposable, () -> WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk(sdk)));
      }
    }

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(toSystemIndependentName(sdkPath));

    VirtualFile androidJar = JarFileSystem.getInstance().findFileByPath(sdkPath + "/platforms/" + platformDir + "/android.jar!/");
    sdkModificator.addRoot(androidJar, OrderRootType.CLASSES);

    VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/platforms/" + platformDir + "/data/res");
    sdkModificator.addRoot(resFolder, OrderRootType.CLASSES);

    VirtualFile androidSrcFolder = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/sources/" + platformDir);
    if (androidSrcFolder != null) {
      sdkModificator.addRoot(androidSrcFolder, OrderRootType.SOURCES);
    }

    VirtualFile docsFolder = LocalFileSystem.getInstance().findFileByPath(sdkPath + "/docs/reference");
    if (docsFolder != null) {
      sdkModificator.addRoot(docsFolder, JavadocOrderRootType.getInstance());
    }

    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    AndroidSdkData sdkData = getSdkData(sdkPath);
    assertNotNull(sdkData);
    IAndroidTarget foundTarget = null;
    IAndroidTarget[] targets = sdkData.getTargets();
    for (IAndroidTarget target : targets) {
      if (target.getLocation().contains(platformDir)) {
        foundTarget = target;
        break;
      }
    }
    assertNotNull(foundTarget);
    data.setBuildTarget(foundTarget);
    sdkModificator.setSdkAdditionalData(data);
    ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
    return sdk;
  }

  @NotNull
  public static IAndroidTarget findLatestAndroidTarget(@NotNull File sdkPath) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    assertNotNull(sdkData);
    IAndroidTarget[] targets = sdkData.getTargets(false /* do not include add-ons */);
    assertThat(targets).isNotEmpty();

    // Use the latest platform, which is checked-in as a full SDK. Older platforms may not be checked in full, to save space.
    Optional<IAndroidTarget> found =
      Arrays.stream(targets).filter(target -> target.hashString().equals(TestUtils.getLatestAndroidPlatform())).findFirst();

    IAndroidTarget target = found.isPresent() ? found.get() : null;
    assertNotNull(target);
    return target;
  }

  public static void allowAccessToSdk(Disposable disposable) {
    String[] paths = JavaSdk.getInstance().suggestHomePaths().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
    VfsRootAccess.allowRootAccess(disposable, paths);
  }
}
