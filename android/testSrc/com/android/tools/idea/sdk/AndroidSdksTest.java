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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.testing.Sdks;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
import static org.jetbrains.android.sdk.AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidSdks}.
 */
public class AndroidSdksTest extends IdeaTestCase {
  @Mock IdeInfo myIdeInfo;

  private Sdk myJdk;
  private AndroidSdks myAndroidSdks;
  private File mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);

    mySdkPath = getSdk();

    Sdks.allowAccessToSdk(getTestRootDisposable());

    Jdks jdks = Jdks.getInstance();
    myJdk = jdks.chooseOrCreateJavaSdk();
    assertNotNull(myJdk);

    myAndroidSdks = new AndroidSdks(jdks, myIdeInfo);
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

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, myJdk, true /* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    assertSame(myJdk, androidData.getJavaSdk());

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

    Map<String, VirtualFile> docRootsByName = getSdkRootsByName(sdk, JavadocOrderRootType.getInstance());
    assertThat(docRootsByName).hasSize(1); // offline docs folder

    VirtualFile docsFolder = docRootsByName.get("reference");
    File expectedDocsFolderPath = new File(mySdkPath, join("docs", docsFolder.getName()));
    assertAbout(file()).that(virtualToIoFile(docsFolder)).isEquivalentAccordingToCompareTo(expectedDocsFolderPath);
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

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, myJdk, false /* do *not* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    assertSame(myJdk, androidData.getJavaSdk());

    AndroidPlatform androidPlatform = androidData.getAndroidPlatform();
    assertNotNull(androidPlatform);
    assertSame(target, androidPlatform.getTarget());

    SdkModificator sdkModificator = sdk.getSdkModificator();

    VirtualFile[] classesRoots = sdkModificator.getRoots(CLASSES);
    assertThat(classesRoots).isEmpty();

    VirtualFile[] sourcesRoots = sdkModificator.getRoots(SOURCES);
    assertThat(sourcesRoots).isEmpty();
  }

  public void testCreateSdkAddingRoots() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = "testSdk";

    Sdk sdk = myAndroidSdks.create(target, mySdkPath, name, myJdk, true /* add roots */);
    assertNotNull(sdk);

    assertEquals(name, sdk.getName());
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    assertSame(myJdk, androidData.getJavaSdk());

    AndroidPlatform androidPlatform = androidData.getAndroidPlatform();
    assertNotNull(androidPlatform);
    assertSame(target, androidPlatform.getTarget());

    SdkModificator sdkModificator = sdk.getSdkModificator();

    VirtualFile[] classesRoots = sdkModificator.getRoots(CLASSES);
    assertThat(classesRoots).isNotEmpty();

    VirtualFile[] sourcesRoots = sdkModificator.getRoots(SOURCES);
    assertThat(sourcesRoots).isNotEmpty();

    sdkModificator.commitChanges();
  }

  public void testCreateSdkWithoutSpecifyingJdk() {
    Sdk sdk = myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, true);

    assertNotNull(sdk);
    verifyCorrectPath(sdk);

    AndroidSdkAdditionalData androidData = getAndroidSdkAdditionalData(sdk);
    Sdk jdk = androidData.getJavaSdk();
    assertNotNull(jdk);
    assertEquals(myJdk.getHomePath(), jdk.getHomePath());
  }

  private void verifyCorrectPath(@NotNull Sdk androidSdk) {
    String sdkHomePath = androidSdk.getHomePath();
    assertNotNull(sdkHomePath);
    assertAbout(file()).that(new File(sdkHomePath)).isEquivalentAccordingToCompareTo(mySdkPath);
  }

  public void testCreateSdkWithNullJdk() {
    Jdks jdks = mock(Jdks.class);
    myAndroidSdks = new AndroidSdks(jdks, myIdeInfo);

    when(jdks.chooseOrCreateJavaSdk()).thenReturn(null);

    Sdk sdk = myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, true);
    assertNull(sdk);
  }

  public void testChooseNameForNewLibrary() {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    String name = myAndroidSdks.chooseNameForNewLibrary(target);
    assertEquals("Android " + target.getVersion().toString() + " Platform", name);
  }

  @NotNull
  private AndroidSdkAdditionalData getAndroidSdkAdditionalData(@NotNull Sdk sdk) {
    // Indirectly tests AndroidSdks#getAndroidSdkAdditionalData
    AndroidSdkAdditionalData data = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
    assertNotNull(data);
    return data;
  }

  public void testTryToChooseAndroidSdk() {
    myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, myJdk, false /* do *not* add roots */);

    AndroidSdkData sdkData = myAndroidSdks.tryToChooseAndroidSdk();
    assertSame(getSdkData(mySdkPath), sdkData);
  }

  public void testTryToChooseSdkHandler() {
    myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, myJdk, false /* do *not* add roots */);

    AndroidSdkHandler sdkHandler = myAndroidSdks.tryToChooseSdkHandler();
    AndroidSdkData sdkData = getSdkData(mySdkPath);
    assertNotNull(sdkData);
    assertSame(sdkData.getSdkHandler(), sdkHandler);
  }

  public void testReplaceLibraries() {
    Sdk sdk = myAndroidSdks.create(findLatestAndroidTarget(mySdkPath), mySdkPath, myJdk, true /* add roots */);
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
    Sdk sdk = myAndroidSdks.create(target, mySdkPath, myJdk, false /* do *not* add roots */);
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
    assertTrue(myAndroidSdks.needsAnnotationsJarInClasspath(target));
  }

  public void testNeedsAnnotationsJarInClasspathWithApiLevelGreaterThan15() {
    IAndroidTarget target = createTargetWithApiLevel(16);
    assertFalse(myAndroidSdks.needsAnnotationsJarInClasspath(target));
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

  public void testHasValidDocsWithValidLocalDocs() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of(), true);

    // prebuilt SDK (with docs) - docs root points to local docs - valid
    assertTrue(myAndroidSdks.hasValidDocs(sdk, target));
  }

  public void testHasValidDocsWithInvalidLocalDocs() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of(), false);

    // random dir (no docs) - docs root points to local docs - invalid
    assertFalse(myAndroidSdks.hasValidDocs(sdk, target));
  }

  public void testHasValidDocsWithInvalidRemoteDocs() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of("https://www.google.com"), false);

    // random dir (no docs) - docs root points to random web address - invalid
    assertFalse(myAndroidSdks.hasValidDocs(sdk, target));
  }

  public void testHasValidDocsWithValidRemoteDocs() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of(DEFAULT_EXTERNAL_DOCUMENTATION_URL), false);

    // random dir (no docs) - docs root points to web docs - valid
    assertTrue(myAndroidSdks.hasValidDocs(sdk, target));
  }

  public void testHasValidDocsWithValidAndInvalidRemoteDocs() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of("https://www.google.com", DEFAULT_EXTERNAL_DOCUMENTATION_URL), false);

    // random dir (no docs) - docs root points to random website + web docs - valid
    assertTrue(myAndroidSdks.hasValidDocs(sdk, target));
  }

  public void testRefreshDocsIn() throws Exception {
    IAndroidTarget target = findLatestAndroidTarget(mySdkPath);
    Sdk sdk = setUpSdkWithDocsRoots(target, ImmutableList.of("https://www.google.com"), true);
    assertFalse(myAndroidSdks.hasValidDocs(sdk, target));

    myAndroidSdks.refreshDocsIn(sdk);
    assertTrue(myAndroidSdks.hasValidDocs(sdk, target));
  }

  @NotNull
  private Sdk setUpSdkWithDocsRoots(@NotNull IAndroidTarget target,
                                    @NotNull Collection<String> docsRootUrls,
                                    boolean hasLocalDocs) throws Exception {
    Sdk sdk = myAndroidSdks.create(target, mySdkPath, "testSdk", myJdk, true);
    assertNotNull(sdk);

    if (!hasLocalDocs || !docsRootUrls.isEmpty()) {
      SdkModificator sdkModificator = sdk.getSdkModificator();
      if (!hasLocalDocs) {
        sdkModificator.setHomePath(createTempDir("sdk-root").getPath());
      }
      if (!docsRootUrls.isEmpty()) {
        OrderRootType javadocType = JavadocOrderRootType.getInstance();
        sdkModificator.removeRoots(javadocType);
        for (String url : docsRootUrls) {
          sdkModificator.addRoot(VirtualFileManager.getInstance().findFileByUrl(url), javadocType);
        }
      }
      sdkModificator.commitChanges();
    }

    return sdk;
  }
}
