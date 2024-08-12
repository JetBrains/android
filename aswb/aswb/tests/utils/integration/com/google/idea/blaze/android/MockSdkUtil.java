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
package com.google.idea.blaze.android;

import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdksCompat;
import com.google.idea.blaze.base.WorkspaceFileSystem;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.containers.MultiMap;
import java.io.File;
import java.io.IOException;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkDataCompat;
import org.jetbrains.android.sdk.AndroidSdkType;

/**
 * Helper to create mock SDKs.
 *
 * <p>Sync tests need register SDKs to {link @ProjectJdkTable} for their test projects since the SDK
 * is needed during sync. However, {link @ProjectJdkTable} will only treat a SDK as valid if it can
 * access expected SDK files with expected content in disk. {link #registerSdkWithJdkTable} is
 * provided to handle all files creation and registration to mock a SDK.
 */
public class MockSdkUtil {

  public static final WorkspacePath SDK_DIR = new WorkspacePath("sdk");
  private static final WorkspacePath PLATFORM_DIR = new WorkspacePath(SDK_DIR, "platforms");
  private static final String TARGET_HASH = "android-%s";

  private MockSdkUtil() {}

  /**
   * Creates a mock SDK and register it in {@link ProjectJdkTable}.
   *
   * <p>All dummy files will be created in disks which are used by {@link
   * com.android.tools.idea.sdk.AndroidSdks#tryToCreate} or {@link
   * com.android.tools.idea.sdk.IdeSdks#getEligibleAndroidSdks}. It helps {@link ProjectJdkTable} to
   * make sure a SDK is valid. All sync test with heavy test fixture are recommended to use this
   * method to improve coverage.
   *
   * @param workspace test file system
   * @param major major version of SDK
   * @return a mock sdk for the given target and name. The sdk is registered with the {@link
   *     ProjectJdkTable}.
   */
  public static Sdk registerSdk(WorkspaceFileSystem workspace, String major) {
    String targetHash = String.format(TARGET_HASH, major);
    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();
    roots.putValue(
        OrderRootType.CLASSES,
        workspace.createFile(new WorkspacePath(PLATFORM_DIR, targetHash + "/android.jar")));
    roots.putValue(
        OrderRootType.CLASSES,
        workspace.createDirectory(new WorkspacePath(PLATFORM_DIR, targetHash + "/data/res")));

    return registerSdk(workspace, major, "0", roots, true);
  }

  /**
   * Creates a mock SDK and register it in {@link ProjectJdkTable}.
   *
   * <p>Same as {link #registerSdk} but provides user ability to customize root content of SDK and
   * dummy files to create. It can be used to mock corrupt {@link ProjectJdkTable}/ missing SDK in
   * local.
   *
   * @param workspace test file system
   * @param major major version of SDK
   * @param minor minor version of SDK
   * @param roots root content of SDK
   * @param createSubFiles whether create subdirectory and files in file system for SDK. Set this to
   *     false would lead to fail to add SDK to Jdk table and cannot retrieve its repo package
   * @return a mock sdk for the given target and name. The sdk is registered with the {@link
   *     ProjectJdkTable}.
   */
  public static Sdk registerSdk(
      WorkspaceFileSystem workspace,
      String major,
      String minor,
      MultiMap<OrderRootType, VirtualFile> roots,
      boolean createSubFiles) {
    String targetHash = String.format(TARGET_HASH, major);
    String sdkName = String.format("Android %s SDK", major);
    WorkspacePath workspacePathToAndroid = new WorkspacePath(PLATFORM_DIR, targetHash);

    if (createSubFiles) {
      workspace.createFile(
          new WorkspacePath(workspacePathToAndroid, "package.xml"),
          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
          "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\"",
          " xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\"",
          " xmlns:ns4=\"http://schemas.android.com/sdk/android/repo/addon2/01\"",
          " xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/repository2/01\"",
          " xmlns:ns6=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\">",
          "<localPackage path=\"platforms;" + targetHash + "\" obsolete=\"false\">",
          "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
              + " xsi:type=\"ns5:platformDetailsType\">",
          "<api-level>",
          major,
          "</api-level>",
          "<layoutlib api=\"15\"/>",
          "</type-details>",
          "<revision>",
          " <major>",
          major,
          " </major>",
          " <minor>",
          minor,
          " </minor>",
          " <micro>",
          "0",
          " </micro>",
          "</revision>",
          "<display-name>",
          sdkName,
          "</display-name>",
          "</localPackage>",
          "</ns2:repository>");
      workspace.createFile(new WorkspacePath(workspacePathToAndroid, "build.prop"));
      workspace.createFile(new WorkspacePath(workspacePathToAndroid, "android.jar"));
      workspace.createDirectory(new WorkspacePath(workspacePathToAndroid, "data/res"));
      workspace.createFile(new WorkspacePath(workspacePathToAndroid, "data/annotations.zip"));
    }
    String sdkHomeDir = workspace.createDirectory(SDK_DIR).getPath();
    AndroidSdkDataCompat.getSdkData(new File(sdkHomeDir), true);

    Sdk sdk = ProjectJdkTable.getInstance().createSdk(sdkName, AndroidSdkType.getInstance());

    var sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkHomeDir);
    sdkModificator.setVersionString(String.format("%s.%s.0", major, minor));
    for (var entry : roots.entrySet()) {
        var rootType = entry.getKey();
        for (var root : entry.getValue()) {
            sdkModificator.addRoot(root, rootType);
        }
    }

    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(sdk);
    createFakeAndroidAnnotation();
    data.setBuildTargetHashString(targetHash);
    sdkModificator.setSdkAdditionalData(data);

    WriteAction.run(sdkModificator::commitChanges);

    EdtTestUtil.runInEdtAndWait(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    () -> {
                      IdeSdksCompat.setAndroidSdkPath(new File(sdkHomeDir), sdk, null);
                    }));
    return AndroidSdks.getInstance().findSuitableAndroidSdk(targetHash);
  }

  /**
   * Create android annotation file since set up android sdk path check existence of
   * androidAnnotations.jar. But it's not used in test. So only create a fake one.
   */
  private static void createFakeAndroidAnnotation() {
    String userHomeDir = System.getProperty("idea.home.path");
    File annotationJar =
        new File(userHomeDir + "/plugins/android/resources/androidAnnotations.jar");
    try {
      annotationJar.getParentFile().mkdirs();
      annotationJar.createNewFile();
      annotationJar.deleteOnExit();
    } catch (IOException e) {
      throw new AssertionError("Fail to create android annotation jar", e);
    }
  }
}
