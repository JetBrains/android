/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import static com.android.sdklib.AndroidTargetHash.getTargetHashString;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.Sdks.findLatestAndroidTarget;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.generated.common.v1.LibraryType;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.testing.Sdks;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidSdks}.
 */
public class AndroidSdksTest extends PlatformTestCase {
  @Mock IdeInfo myIdeInfo;

  private AndroidSdks myAndroidSdks;
  private File mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);

    mySdkPath = getSdk().toFile();

    Sdks.allowAccessToSdk(getTestRootDisposable());

    myAndroidSdks = new AndroidSdks(myIdeInfo);
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  public void testTryToCreate() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String hash = getTargetHashString(target);

    Sdk sdk = myAndroidSdks.tryToCreate(mySdkPath, hash);
    assertNotNull(sdk);
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(sdk);
    assertEquals(hash, data.getBuildTargetHashString());
  }

  public void testCreateSdk() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = "testSdk";

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, true /* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    AndroidPlatform androidPlatform = androidData.getAndroidPlatform();
    assertNotNull(androidPlatform);
    assertSame(target, androidPlatform.getTarget());

    File platformPath = new File(mySdkPath, join("platforms", androidData.getBuildTargetHashString()));

    // Check roots added to SDK
    Map<String, VirtualFile> classRootsByName = getSdkRootsByName(sdk, CLASSES);
    assertThat(classRootsByName).hasSize(2); // android.jar and res folder

    VirtualFile androidJar = classRootsByName.get("android.jar");
    File expectedAndroidJarPath = new File(platformPath, androidJar.getName());
    assertAbout(file()).that(virtualToIoFile(androidJar)).isEquivalentAccordingToCompareTo(expectedAndroidJarPath);

    VirtualFile resFolder = classRootsByName.get("res");
    File expectedResFolderPath = new File(platformPath, join("data", resFolder.getName()));
    assertAbout(file()).that(virtualToIoFile(resFolder)).isEquivalentAccordingToCompareTo(expectedResFolderPath);

    /* b/115735357: We don't currently have a valid docs package, nor do we plan to have one again soon.
    Map<String, VirtualFile> docRootsByName = getSdkRootsByName(sdk, JavadocOrderRootType.getInstance());
    assertThat(docRootsByName).hasSize(1); // offline docs folder

    VirtualFile docsFolder = docRootsByName.get("reference");
    File expectedDocsFolderPath = new File(mySdkPath, join("docs", docsFolder.getName()));
    assertAbout(file()).that(virtualToIoFile(docsFolder)).isEquivalentAccordingToCompareTo(expectedDocsFolderPath);
    */
  }

  @NotNull
  private static Map<String, VirtualFile> getSdkRootsByName(@NotNull Sdk androidSdk, @NotNull OrderRootType type) {
    Map<String, VirtualFile> rootsByName = new HashMap<>();
    VirtualFile[] roots = androidSdk.getRootProvider().getFiles(type);
    for (VirtualFile root : roots) {
      rootsByName.put(root.getName(), root);
    }
    return rootsByName;
  }

  public void testCreateSdkWithoutAddingRoots() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = "testSdk";

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, false /* do *not* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    AndroidPlatform androidPlatform = androidData.getAndroidPlatform();
    assertNotNull(androidPlatform);
    assertSame(target, androidPlatform.getTarget());

    VirtualFile[] classesRoots = sdk.getRootProvider().getFiles(CLASSES);
    assertThat(classesRoots).isEmpty();

    VirtualFile[] sourcesRoots = sdk.getRootProvider().getFiles(SOURCES);
    assertThat(sourcesRoots).isEmpty();
  }

  public void testCreateSdkAddingRoots() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = "testSdk";

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, true /* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    AndroidPlatform androidPlatform = androidData.getAndroidPlatform();
    assertNotNull(androidPlatform);
    assertSame(target, androidPlatform.getTarget());

    VirtualFile[] classesRoots = sdk.getRootProvider().getFiles(CLASSES);
    assertThat(classesRoots).isNotEmpty();

    VirtualFile[] sourcesRoots = sdk.getRootProvider().getFiles(SOURCES);
    assertThat(sourcesRoots).isNotEmpty();
  }

  private void verifyCorrectPath(@NotNull Sdk androidSdk) {
    String sdkHomePath = androidSdk.getHomePath();
    assertNotNull(sdkHomePath);
    assertAbout(file()).that(new File(sdkHomePath)).isEquivalentAccordingToCompareTo(mySdkPath);
  }

  public void testCreateSdkForAddonCreatesItsParent() {
    IAndroidTarget parentTarget = findLatestAndroidTarget(mySdkPath);
    String parentHash = getTargetHashString(parentTarget);

    IAndroidTarget addonTarget = mock(IAndroidTarget.class);
    when(addonTarget.getParent()).thenReturn(parentTarget);
    when(addonTarget.getAdditionalLibraries()).thenReturn(Collections.singletonList(new LibraryType()));

    assertNull(myAndroidSdks.findSuitableAndroidSdk(parentHash)); // Parent SDK doesn't exists
    assertNull(myAndroidSdks.create(addonTarget, mySdkPath, false)); // Addon sdk is not created
    assertNotNull(myAndroidSdks.findSuitableAndroidSdk(parentHash)); // Parent SDK is now created
  }

  public void testChooseNameForNewLibrary() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = myAndroidSdks.chooseNameForNewLibrary(target);
    assertEquals("Android " + target.getVersion().toString() + " Platform", name);
  }

  @NotNull
  private AndroidSdkAdditionalData getAndroidSdkAdditionalData(@NotNull Sdk sdk) {
    // Indirectly tests AndroidSdks#getAndroidSdkAdditionalData
    AndroidSdkAdditionalData data = AndroidSdkAdditionalData.from(sdk);
    assertNotNull(data);
    return data;
  }

  public void testTryToChooseAndroidSdk() {
    myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, false /* do *not* add roots */);

    AndroidSdkData sdkData = myAndroidSdks.tryToChooseAndroidSdk();
    assertSame(getSdkData(mySdkPath), sdkData);
  }

  public void testTryToChooseSdkHandler() {
    myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, false /* do *not* add roots */);

    AndroidSdkHandler sdkHandler = myAndroidSdks.tryToChooseSdkHandler();
    AndroidSdkData sdkData = getSdkData(mySdkPath);
    assertNotNull(sdkData);
    assertSame(sdkData.getSdkHandler(), sdkHandler);
  }

  public void testReplaceLibraries() {
    Sdk sdk = myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, true /* add roots */);
    assertNotNull(sdk);

    VirtualFile[] currentLibraries = sdk.getRootProvider().getFiles(CLASSES);
    assertThat(currentLibraries).hasLength(2);

    VirtualFile newLibrary = currentLibraries[0];
    VirtualFile[] newLibraries = {newLibrary};

    myAndroidSdks.replaceLibraries(sdk, newLibraries);
    currentLibraries = sdk.getRootProvider().getFiles(CLASSES);
    assertThat(currentLibraries).hasLength(1);

    assertSame(newLibrary, currentLibraries[0]);
  }

  public void testFindSuitableAndroidSdk() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = myAndroidSdks.create(target, mySdkPath, false /* do *not* add roots */);
    assertNotNull(sdk);

    String hash = getTargetHashString(target);
    Sdk foundSdk = myAndroidSdks.findSuitableAndroidSdk(hash);
    assertNotNull(foundSdk);
    assertEquals(sdk.getHomePath(), foundSdk.getHomePath());

    AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(foundSdk);
    assertEquals(hash, data.getBuildTargetHashString());
  }

  public void testNeedsAnnotationsJarInClasspathWithApiLevel15() {
    IAndroidTarget target = createTargetWithApiLevel(15);
    assertTrue(Annotations.needsAnnotationsJarInClasspath(target));
  }

  public void testNeedsAnnotationsJarInClasspathWithApiLevelGreaterThan15() {
    IAndroidTarget target = createTargetWithApiLevel(16);
    assertFalse(Annotations.needsAnnotationsJarInClasspath(target));
  }

  @NotNull
  private static IAndroidTarget createTargetWithApiLevel(int apiLevel) {
    AndroidVersion version = new AndroidVersion(apiLevel, null);

    IAndroidTarget target = mock(IAndroidTarget.class);
    when(target.getVersion()).thenReturn(version);
    return target;
  }

  public void testSetSdkData() {
    AndroidSdkData data = mock(AndroidSdkData.class);
    myAndroidSdks.setSdkData(data);
    assertSame(data, myAndroidSdks.tryToChooseAndroidSdk());
  }
}
