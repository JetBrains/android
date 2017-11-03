/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.mockito.Mockito;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tests for the local repository utility class
 */
public class RepositoryUrlManagerTest extends AndroidGradleTestCase {
  private static final File SDK_DIR = new File("/sdk");
  private static final File ANDROID_HOME = new File("/android-home");

  private RepositoryUrlManager myRepositoryUrlManager;
  private MockFileOp myFileOp;
  private AndroidSdkHandler mySdkHandler;
  private AndroidSdkData mySdk;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileOp = new MockFileOp();
    myRepositoryUrlManager = new RepositoryUrlManager(true /* force repository checks */);
    mySdkHandler = new AndroidSdkHandler(SDK_DIR, ANDROID_HOME, myFileOp);
    mySdk = Mockito.mock(AndroidSdkData.class);
    Mockito.when(mySdk.getLocation()).thenReturn(SDK_DIR);

    String[] paths = new String[]{
      // Android repository
      "extras/android/m2repository/com/android/support/appcompat-v7/18.0.0/appcompat-v7-18.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.0.0/appcompat-v7-19.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.0.1/appcompat-v7-19.0.1.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.1.0/appcompat-v7-19.1.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/20.0.0/appcompat-v7-20.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/21.0.0/appcompat-v7-21.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/21.0.2/appcompat-v7-21.0.2.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/22.0.0-alpha1/appcompat-v7-22.0.0-alpha1.aar",
      "extras/android/m2repository/com/android/support/cardview-v7/21.0.0/cardview-v7-21.0.0.aar",
      "extras/android/m2repository/com/android/support/cardview-v7/21.0.2/cardview-v7-21.0.2.aar",
      "extras/android/m2repository/com/android/support/support-v13/20.0.0/support-v13-20.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v13/21.0.0/support-v13-21.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v13/21.0.2/support-v13-21.0.2.aar",
      "extras/android/m2repository/com/android/support/support-v4/13.0.0/support-v4-13.0.0.jar", // JARs were used before 19.0.0
      "extras/android/m2repository/com/android/support/support-v4/19.0.1/support-v4-19.0.1.aar",
      "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0-rc1.aar",
      "extras/android/m2repository/com/android/support/support-v4/21.0.0/support-v4-21.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v4/21.0.2/support-v4-21.0.2.aar",
      "extras/android/m2repository/com/android/support/constraint/constraint-layout/1.0.0-alpha7/constraint-layout-1.0.0-alpha7.aar",

      // Google repository
      "extras/google/m2repository/com/google/android/gms/play-services/3.1.36/play-services-3.1.36.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/3.1.59/play-services-3.1.59.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/3.2.25/play-services-3.2.25.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/3.2.65/play-services-3.2.65.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/4.0.30/play-services-4.0.30.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/4.1.32/play-services-4.1.32.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/4.2.42/play-services-4.2.42.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/4.3.23/play-services-4.3.23.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/4.4.52/play-services-4.4.52.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/5.0.89/play-services-5.0.89.aar",
      "extras/google/m2repository/com/google/android/gms/play-services/5.2.08/play-services-5.2.08.aar",
      "extras/google/m2repository/com/google/android/gms/play-services-wearable/5.0.77/play-services-wearable-5.0.77.aar",
      "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.11/play-services-wearable-6.1.11.aar",
      "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.71/play-services-wearable-6.1.71.aar",
      "extras/google/m2repository/com/google/android/support/wearable/1.0.0/wearable-1.0.0.aar",
      "extras/google/m2repository/com/google/android.wearable/wearable/2.0.0-alpha2/wearable-2.0.0-alpha2.aar",
    };

    for (String path : paths) {
      myFileOp.createNewFile(new File(SDK_DIR, path));
    }
  }

  /** Common boilerplate code for invoking getLibraryRevision. */
  private String getLibraryRevision(SupportLibrary library, boolean preview) {
    return getLibraryRevision(library, preview, null);
  }

  private String getLibraryRevision(SupportLibrary library, boolean preview, Predicate<GradleVersion> filter) {
    return myRepositoryUrlManager.getLibraryRevision(library.getGroupId(), library.getArtifactId(), filter, preview, SDK_DIR, myFileOp);
  }

  public void testGetLibraryRevision() throws Exception {
    // Check missing Maven metadata file. We should fall back to scanning the files.
    assertEquals("26.0.0-beta1", getLibraryRevision(SupportLibrary.SUPPORT_V4, true));

    // Set up our fake file contents for the "maven-metadata.xml" file
    myFileOp.recordExistingFile(new File(SDK_DIR, "extras/android/m2repository/com/android/support/support-v4/maven-metadata.xml").getAbsolutePath(),
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><versioning>" +
                                "<version>13.0.0</version> <version>19.0.1</version> <version>20.0.0-rc1</version>" +
                                "</versioning>");

    assertEquals("25.4.0", getLibraryRevision(SupportLibrary.SUPPORT_V4, false));
    assertEquals("26.0.0-beta1", getLibraryRevision(SupportLibrary.SUPPORT_V4, true));
  }

  /** Checks {@link SupportLibrary} values against a real SDK, to make sure the paths are correct. */
  public void testGetLibraryRevision_realSdk() throws Exception {
    // the first library from each group, representing a distinct SDK component, of SupportLibrary values
    SupportLibrary libraryFromAndroidRepo = SupportLibrary.SUPPORT_ANNOTATIONS;
    SupportLibrary libraryFromGoogleRepo = SupportLibrary.PLAY_SERVICES;
    for (SupportLibrary library : ImmutableList.of(libraryFromAndroidRepo, libraryFromGoogleRepo)) {
      assertNotNull("Can't find latest version of " + library,
                    myRepositoryUrlManager.getLibraryRevision(library.getGroupId(),
                                                              library.getArtifactId(),
                                                              null,
                                                              false,
                                                              TestUtils.getSdk(),
                                                              FileOpUtils.create()));
    }
  }

  public void testGetLibraryRevision_thirdPartyLibrary() throws Exception {
    assertNull(myRepositoryUrlManager.getLibraryRevision("com.actionbarsherlock",
                                                         "actionbarsherlock",
                                                         null,
                                                         false,
                                                         SDK_DIR,
                                                         myFileOp));
  }

  public void testGetLibraryRevision_SdkOnly() throws Exception {
    assertNull(getLibraryRevision(SupportLibrary.SUPPORT_V4, true, v -> v.getMajor() == 24));
  }

  public void testGetLibraryRevision_missingSdk() throws Exception {
    myFileOp.deleteFileOrFolder(SDK_DIR);
    assertNull(getLibraryRevision(SupportLibrary.SUPPORT_V4, true, v -> v.getMajor() == 24));
  }

  public void testGetLibraryRevision_offlineIndex() throws Exception {
    myFileOp.deleteFileOrFolder(SDK_DIR);
    assertEquals("26.0.0-beta1", getLibraryRevision(SupportLibrary.SUPPORT_V4, true));
  }

  /** @see com.android.ide.common.repository.MavenRepositories#isPreview(GradleCoordinate) */
  public void testGetLibraryRevision_playServices_preview() throws Exception {
    // Check without metadata file.
    assertEquals("5.0.89", getLibraryRevision(SupportLibrary.PLAY_SERVICES, false));
    assertEquals("5.2.08", getLibraryRevision(SupportLibrary.PLAY_SERVICES, true));

    // Check with metadata file.
    myFileOp.recordExistingFile(new File(SDK_DIR, "extras/google/m2repository/com/google/android/gms/play-services/maven-metadata.xml").getAbsolutePath(),
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><versioning>" +
                                "<version>5.0.89</version> <version>5.2.08</version>" +
                                "</versioning>");

    assertEquals("5.0.89", getLibraryRevision(SupportLibrary.PLAY_SERVICES, false));
    assertEquals("5.2.08", getLibraryRevision(SupportLibrary.PLAY_SERVICES, true));
  }

  public void testGetArchiveForCoordinate() throws Exception {
    checkGetArchiveForCoordinate("com.android.support:support-v4:20.0.0",
                                 "/extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0.aar");

    checkGetArchiveForCoordinate("com.android.support:support-v4:13.0.0",
                                 "/extras/android/m2repository/com/android/support/support-v4/13.0.0/support-v4-13.0.0.jar");

    // Unknown version:
    checkGetArchiveForCoordinate("com.android.support:support-v4:20.4.0", null);

    // Unknown library
    checkGetArchiveForCoordinate("com.google.guava:guava:19.0", null);
  }

  private void checkGetArchiveForCoordinate(String coordinateString, String path) {
    GradleCoordinate supportCoordinate = GradleCoordinate.parseCoordinateString(coordinateString);
    assertNotNull(supportCoordinate);
    File expectedFile = null;
    if (path != null) {
      expectedFile = new File(SDK_DIR, path.replace('/', File.separatorChar));
    }
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(supportCoordinate, SDK_DIR, myFileOp));
  }

  public void testGetArchiveForCoordinate_missingSdk() throws Exception {
    myFileOp.deleteFileOrFolder(SDK_DIR);
    checkGetArchiveForCoordinate("com.android.support:support-v4:20.0.0", null);
  }

  public void testResolvedCoordinate() throws Exception {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNotNull(coordinate);
    assertEquals("4.4.52", resolveDynamicCoordinateVersion(coordinate));
    assertEquals(GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.4.52"),
                 resolveDynamicCoordinate(coordinate));

    coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+@aar");
    assertNotNull(coordinate);
    assertEquals("4.4.52", resolveDynamicCoordinateVersion(coordinate));
    assertEquals(GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.4.52@aar"),
                 resolveDynamicCoordinate(coordinate));

    coordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:+");
    assertNotNull(coordinate);
    assertEquals("25.4.0", resolveDynamicCoordinateVersion(coordinate));

    // Make sure already resolved coordinates are handled correctly
    coordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:1.2.3");
    assertNotNull(coordinate);
    assertEquals("1.2.3", resolveDynamicCoordinateVersion(coordinate));

    coordinate = GradleCoordinate.parseCoordinateString("my.group.id:my.bogus.artifact:+");
    assertNotNull(coordinate);
    assertNull(resolveDynamicCoordinateVersion(coordinate));
  }

  private String resolveDynamicCoordinateVersion(GradleCoordinate coordinate) {
    return myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null, mySdkHandler);
  }

  private GradleCoordinate resolveDynamicCoordinate(GradleCoordinate coordinate) {
    return myRepositoryUrlManager.resolveDynamicCoordinate(coordinate, null, mySdkHandler);
  }

  public void testResolvedCoordinate_sdkMissing() throws Exception {
    myFileOp.deleteFileOrFolder(SDK_DIR);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNull(resolveDynamicCoordinateVersion(coordinate));
  }

  public void testResolvedCoordinateLocalFirst() throws Exception {
    RemotePackage pkg = new FakePackage.FakeRemotePackage("extras;m2repository;com;google;android;gms;play-services;4.5.0");
    RepositoryPackages pkgs = new RepositoryPackages(ImmutableList.of(), ImmutableList.of(pkg));
    RepoManager mgr = new FakeRepoManager(pkgs);
    mySdkHandler = new AndroidSdkHandler(SDK_DIR, ANDROID_HOME, myFileOp, mgr);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNotNull(coordinate);
    assertEquals("4.4.52", resolveDynamicCoordinateVersion(coordinate));
  }

  public void testResolvedCoordinateRemoteSdk() throws Exception {
    RemotePackage pkg = new FakePackage.FakeRemotePackage("extras;m2repository;com;google;android;gms;play-services;4.5.0");
    RepositoryPackages pkgs = new RepositoryPackages(ImmutableList.of(), ImmutableList.of(pkg));
    RepoManager mgr = new FakeRepoManager(pkgs);
    mySdkHandler = new AndroidSdkHandler(new File("/emptysdk"), ANDROID_HOME, myFileOp, mgr);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNotNull(coordinate);
    assertEquals("4.5.0", resolveDynamicCoordinateVersion(coordinate));
  }

  public void testResolveDynamicSdkDependencies() {
    Multimap<String, GradleCoordinate> dependencies = HashMultimap.create();
    dependencies.put("com.android.support:appcompat-v7", GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:19.+"));
    dependencies.put("com.android.support:appcompat-v7", GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:+"));
    dependencies.put("com.google.android.support:wearable", GradleCoordinate.parseCoordinateString("com.google.android.support:wearable:+"));
    dependencies.put("com.google.android.gms:play-services", GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:+"));
    dependencies.put("com.google.android.gms:play-services", GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.2.+"));
    List<GradleCoordinate> result = myRepositoryUrlManager.resolveDynamicSdkDependencies(dependencies, "23.", mySdk, myFileOp);
    result.sort(Comparator.comparing(GradleCoordinate::toString));
    assertEquals("com.android.support:appcompat-v7:19.1.0", result.get(0).toString());
    assertEquals("com.google.android.gms:play-services:4.2.42", result.get(1).toString());
    assertEquals("com.google.android.support:wearable:1.0.0", result.get(2).toString());
    assertEquals(3, result.size());
  }

  public void testResolveDynamicSdkDepsAlpha1() {
    Multimap<String, GradleCoordinate> dependencies = HashMultimap.create();
    dependencies.put("com.android.support:appcompat-v7", GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:22.+"));
    List<GradleCoordinate> result = myRepositoryUrlManager.resolveDynamicSdkDependencies(dependencies, "22.", mySdk, myFileOp);
    assertEquals("com.android.support:appcompat-v7:22.0.0-alpha1", result.get(0).toString());
  }

  public void testResolveDynamicSdkDependenciesWithSupportVersionFromFilter() {
    Multimap<String, GradleCoordinate> dependencies = HashMultimap.create();
    dependencies.put("com.android.support:appcompat-v7", GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:+"));
    dependencies.put("com.google.android.support:wearable", GradleCoordinate.parseCoordinateString("com.google.android.support:wearable:0.+"));
    dependencies.put("com.android.support:support-v4", GradleCoordinate.parseCoordinateString("com.android.support:support-v4:+"));
    dependencies.put("com.android.support.constraint:constraint-layout", GradleCoordinate.parseCoordinateString("com.android.support.constraint:constraint-layout:0.+"));
    List<GradleCoordinate> result = myRepositoryUrlManager.resolveDynamicSdkDependencies(dependencies, "20", mySdk, myFileOp);
    result.sort(Comparator.comparing(GradleCoordinate::toString));
    assertEquals("com.android.support.constraint:constraint-layout:1.0.2", result.get(0).toString());
    assertEquals("com.android.support:appcompat-v7:20.0.0", result.get(1).toString());
    assertEquals("com.android.support:support-v4:20.0.0", result.get(2).toString());
    assertEquals("com.google.android.support:wearable:1.0.0", result.get(3).toString());
    assertEquals(4, result.size());
  }

  public void testResolveDynamicSdkDependenciesWithSupportVersionFromExplicitVersion() {
    Multimap<String, GradleCoordinate> dependencies = HashMultimap.create();
    dependencies.put("com.android.support:appcompat-v7", GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:+"));
    dependencies.put("com.google.android.support:wearable", GradleCoordinate.parseCoordinateString("com.google.android.support:wearable:2.0.0-alpha2"));
    dependencies.put("com.android.support:support-v4", GradleCoordinate.parseCoordinateString("com.android.support:support-v4:19.0.1"));
    List<GradleCoordinate> result = myRepositoryUrlManager.resolveDynamicSdkDependencies(dependencies, "20", mySdk, myFileOp);
    result.sort(Comparator.comparing(GradleCoordinate::toString));
    assertEquals("com.android.support:appcompat-v7:19.0.1", result.get(0).toString());
    assertEquals("com.android.support:support-v4:19.0.1", result.get(1).toString());
    assertEquals("com.google.android.support:wearable:2.0.0-alpha2", result.get(2).toString());
    assertEquals(3, result.size());
  }
}
