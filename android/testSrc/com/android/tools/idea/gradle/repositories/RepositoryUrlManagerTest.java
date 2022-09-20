/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.repositories;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.StubGoogleMavenRepository;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.mockito.Mockito;

/**
 * Tests for the local repository utility class
 */
public class RepositoryUrlManagerTest extends AndroidGradleTestCase {
  private static final String SDK_DIR = "sdk";
  private static final String ANDROID_PREFS_ROOT = "android-home";

  private RepositoryUrlManager myRepositoryUrlManager;
  private final Path myRoot = InMemoryFileSystems.getSomeRoot(InMemoryFileSystems.createInMemoryFileSystem());
  private AndroidSdkHandler mySdkHandler;

  private static final String MASTER_INDEX =
    "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<metadata>\n" +
    "  <com.android.support.constraint/>\n" +
    "  <com.android.support/>\n" +
    "  <com.google.android.gms/>\n" +
    "  <com.google.android.support/>\n" +
    "</metadata>";

  private static final String SUPPORT_GROUP =
   "<?xml version='1.0' encoding='UTF-8'?>\n"+
    "<com.android.support>\n"+
    "  <support-v4 versions=\"26.0.2,26.0.2\"/>\n"+
    "  <appcompat-v7 versions=\"18.0.0,19.0.0,19.0.1,19.1.0,20.0.0,21.0.0,21.0.2,22.0.0-alpha1\"/>\n"+
    "</com.android.support>\n";

  private static final String CONTRAINT_GROUP =
    "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<com.android.support.constraint>\n" +
    "  <constraint-layout-solver versions=\"1.0.2,1.1.0-beta1\"/>\n" +
    "  <constraint-layout versions=\"1.0.2,1.1.0-beta1\"/>\n" +
    "</com.android.support.constraint>";

  private static final String PLAY_SERVICES_GROUP =
    "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<com.google.android.gms>\n" +
    "  <play-services versions=\"4.4.52,11.1.0,11.2.0-beta1\"/>\n" +
    "  <play-services-ads versions=\"11.1.0,11.2.0-beta1\"/>\n" +
    "</com.google.android.gms>\n";

  private static final String WEARABLE_GROUP =
    "<?xml version='1.0' encoding='UTF-8'?>\n" +
    "<com.google.android.support>\n" +
    "  <wearable versions=\"1.0.0,2.0.0-alpha2\"/>\n" +
    "</com.google.android.support>\n";

  private static final Map<String, String> OFFLINE_CACHE =
    ImmutableMap.of("master-index.xml", MASTER_INDEX,
                    "com/google/android/gms/group-index.xml", PLAY_SERVICES_GROUP,
                    "com/google/android/support/group-index.xml", WEARABLE_GROUP,
                    "com/android/support/group-index.xml", SUPPORT_GROUP,
                    "com/android/support/constraint/group-index.xml", CONTRAINT_GROUP);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRepositoryUrlManager = new RepositoryUrlManager(new StubGoogleMavenRepository(OFFLINE_CACHE),
                                                      new StubGoogleMavenRepository(OFFLINE_CACHE),
                                                      true /* force repository checks */,
                                                      true /* ues embedded studio repo */);
    mySdkHandler = new AndroidSdkHandler(myRoot.resolve(SDK_DIR), myRoot.resolve(ANDROID_PREFS_ROOT));
    AndroidSdkData sdk = Mockito.mock(AndroidSdkData.class);
    Mockito.when(sdk.getLocationFile()).thenReturn(new File(SDK_DIR));
  }

  /**
   * Common boilerplate code for invoking getLibraryRevision.
   */
  private String getLibraryRevision(GoogleMavenArtifactId artifactId, boolean preview) {
    return getLibraryRevision(artifactId, preview, null);
  }

  private String getLibraryRevision(GoogleMavenArtifactId artifactId, boolean preview, Predicate<GradleVersion> filter) {
    return myRepositoryUrlManager
      .getLibraryRevision(artifactId.getMavenGroupId(), artifactId.getMavenArtifactId(), filter, preview, myRoot.getFileSystem());
  }

  public void testgetLibraryRevision() {
    // Check missing Maven metadata file. We should fall back to scanning the files.
    assertEquals("26.0.2", getLibraryRevision(GoogleMavenArtifactId.SUPPORT_V4, true));
  }

  public void testgetLibraryRevision_thirdPartyLibrary() {
    assertNull(myRepositoryUrlManager.getLibraryRevision("com.actionbarsherlock",
                                                         "actionbarsherlock",
                                                         null,
                                                         false,
                                                         myRoot.getFileSystem()));
  }

  public void testgetLibraryRevision_SdkOnly() {
    assertNull(getLibraryRevision(GoogleMavenArtifactId.SUPPORT_V4, true, v -> v.getMajor() == 24));
  }

  public void testgetLibraryRevision_missingSdk() throws IOException {
    PathUtils.deleteRecursivelyIfExists(myRoot.resolve(SDK_DIR));
    assertNull(getLibraryRevision(GoogleMavenArtifactId.SUPPORT_V4, true, v -> v.getMajor() == 24));
  }

  public void testgetLibraryRevision_offlineIndex() throws Exception {
    PathUtils.deleteRecursivelyIfExists(myRoot.resolve(SDK_DIR));
    assertEquals("26.0.2", getLibraryRevision(GoogleMavenArtifactId.SUPPORT_V4, true));
  }

  /**
   * @see com.android.ide.common.repository.MavenRepositories#isPreview(GradleCoordinate)
   */
  public void testgetLibraryRevision_playServices_preview() {
    // Check without metadata file.
    assertEquals("11.1.0", getLibraryRevision(GoogleMavenArtifactId.PLAY_SERVICES_ADS, false));
    assertEquals("11.2.0-beta1", getLibraryRevision(GoogleMavenArtifactId.PLAY_SERVICES_ADS, true));
  }

  private void checkGetArchiveForCoordinate(String coordinateString, String path) {
    GradleCoordinate supportCoordinate = GradleCoordinate.parseCoordinateString(coordinateString);
    assertNotNull(supportCoordinate);
    File expectedFile = null;
    if (path != null) {
      expectedFile = new File(SDK_DIR, path.replace('/', File.separatorChar));
    }
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(supportCoordinate, new File(SDK_DIR), myRoot.getFileSystem()));
  }

  public void testGetArchiveForCoordinate_missingSdk() throws Exception {
    PathUtils.deleteRecursivelyIfExists(myRoot.resolve(SDK_DIR));
    checkGetArchiveForCoordinate("com.android.support:support-v4:20.0.0", null);
  }

  public void testResolvedCoordinate() {
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
    assertEquals("26.0.2", resolveDynamicCoordinateVersion(coordinate));

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

  public void testResolvedCoordinateLocalFirst() {
    RemotePackage pkg = new FakePackage.FakeRemotePackage("extras;m2repository;com;google;android;gms;play-services;4.5.0");
    RepositoryPackages pkgs = new RepositoryPackages(ImmutableList.of(), ImmutableList.of(pkg));
    RepoManager mgr = new FakeRepoManager(pkgs);
    mySdkHandler = new AndroidSdkHandler(myRoot.resolve(SDK_DIR), myRoot.resolve(ANDROID_PREFS_ROOT), mgr);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNotNull(coordinate);
    assertEquals("4.4.52", resolveDynamicCoordinateVersion(coordinate));
  }

  public void testResolveDynamicSdkDepsFuture() {
    // Regression test for b/74180487
    String revision = myRepositoryUrlManager.getLibraryRevision(GoogleMavenArtifactId.RECYCLERVIEW_V7.getMavenGroupId(),
                                                                GoogleMavenArtifactId.RECYCLERVIEW_V7.getMavenArtifactId(),
                                                                version -> {
                                                                  return version.getMajor() == 200; // future version
                                                                },
                                                                false,
                                                                FileSystems.getDefault());
    assertNull(revision);
  }
}
