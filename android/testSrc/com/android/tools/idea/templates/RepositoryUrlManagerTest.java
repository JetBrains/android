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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.MockFileOp;
import junit.framework.TestCase;

import java.io.File;

/**
 * Tests for the local repository utility class
 */
public class RepositoryUrlManagerTest extends TestCase {

  private RepositoryUrlManager myRepositoryUrlManager;
  private File mySdkDir;
  private MockFileOp myFileOp;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileOp = new MockFileOp();
    mySdkDir = new File("/sdk-from-setUp");
    myRepositoryUrlManager = new RepositoryUrlManager();

    String[] paths = new String[]{
      // Android repository
      "extras/android/m2repository/com/android/support/appcompat-v7/18.0.0/appcompat-v7-18.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.0.0/appcompat-v7-19.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.0.1/appcompat-v7-19.0.1.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/19.1.0/appcompat-v7-19.1.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/20.0.0/appcompat-v7-20.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/21.0.0/appcompat-v7-21.0.0.aar",
      "extras/android/m2repository/com/android/support/appcompat-v7/21.0.2/appcompat-v7-21.0.2.aar",
      "extras/android/m2repository/com/android/support/cardview-v7/21.0.0/cardview-v7-21.0.0.aar",
      "extras/android/m2repository/com/android/support/cardview-v7/21.0.2/cardview-v7-21.0.2.aar",
      "extras/android/m2repository/com/android/support/support-v13/20.0.0/support-v13-20.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v13/21.0.0/support-v13-21.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v13/21.0.2/support-v13-21.0.2.aar",
      "extras/android/m2repository/com/android/support/support-v4/13.0.0/support-v4-13.0.0.jar", // JARs were used before 19.0.0
      "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0-rc1.aar",
      "extras/android/m2repository/com/android/support/support-v4/21.0.0/support-v4-21.0.0.aar",
      "extras/android/m2repository/com/android/support/support-v4/21.0.2/support-v4-21.0.2.aar",

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
      "extras/google/m2repository/com/google/android/support/wearable/1.0.0/wearable-1.0.0.aar"
    };

    for (String path : paths) {
      myFileOp.createNewFile(new File(mySdkDir, path));
    }
  }

  /** Common boilerplate code for invoking getLibraryRevision. */
  private String getLibraryRevision(SupportLibrary library, boolean preview) {
    return myRepositoryUrlManager.getLibraryRevision(library.getGroupId(), library.getArtifactId(), null, preview, mySdkDir, myFileOp);
  }

  public void testGetLibraryRevision() throws Exception {
    // Check missing Maven metadata file. We should fall back to scanning the files.
    assertEquals("21.0.2", getLibraryRevision(SupportLibrary.SUPPORT_V4, true));

    // Set up our fake file contents for the "maven-metadata.xml" file
    myFileOp.recordExistingFile(new File(mySdkDir, "extras/android/m2repository/com/android/support/support-v4/maven-metadata.xml").getAbsolutePath(),
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><versioning>" +
                                "<version>13.0.0</version> <version>19.0.1</version> <version>20.0.0-rc1</version>" +
                                "</versioning>");

    assertEquals("19.0.1", getLibraryRevision(SupportLibrary.SUPPORT_V4, false));
    assertEquals("20.0.0-rc1", getLibraryRevision(SupportLibrary.SUPPORT_V4, true));
  }

  /** Checks the {@link SupportLibrary} values against a real SDK, to make sure the paths are correct. */
  public void testGetLibraryRevision_allKnownLibraries_realSdk() throws Exception {
    for (SupportLibrary library : SupportLibrary.values()) {
      assertNotNull("Can't find latest version of " + library,
                    myRepositoryUrlManager.getLibraryRevision(library.getGroupId(),
                                                              library.getArtifactId(),
                                                              null,
                                                              false,
                                                              new File(System.getenv(SdkConstants.ANDROID_HOME_ENV)),
                                                              FileOpUtils.create()));
    }
  }

  public void testGetLibraryRevision_thirdPartyLibrary() throws Exception {
    assertNull(myRepositoryUrlManager.getLibraryRevision("com.actionbarsherlock",
                                                         "actionbarsherlock",
                                                         null,
                                                         false,
                                                         mySdkDir,
                                                         myFileOp));
  }

  public void testGetLibraryRevision_missingSdk() throws Exception {
    myFileOp.deleteFileOrFolder(mySdkDir);
    assertNull(getLibraryRevision(SupportLibrary.SUPPORT_V4, true));
  }

  /** @see com.android.ide.common.repository.MavenRepositories#isPreview(com.android.ide.common.repository.GradleCoordinate) */
  public void testGetLibraryRevision_playServices_preview() throws Exception {
    // Check without metadata file.
    assertEquals("5.0.89", getLibraryRevision(SupportLibrary.PLAY_SERVICES, false));
    assertEquals("5.2.08", getLibraryRevision(SupportLibrary.PLAY_SERVICES, true));

    // Check with metadata file.
    myFileOp.recordExistingFile(new File(mySdkDir, "extras/google/m2repository/com/google/android/gms/play-services/maven-metadata.xml").getAbsolutePath(),
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
      expectedFile = new File(mySdkDir, path.replace('/', File.separatorChar));
    }
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(supportCoordinate, mySdkDir, myFileOp));
  }

  public void testGetArchiveForCoordinate_missingSdk() throws Exception {
    myFileOp.deleteFileOrFolder(mySdkDir);
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
    assertEquals("21.0.2", resolveDynamicCoordinateVersion(coordinate));

    // Make sure already resolved coordinates are handled correctly
    coordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:1.2.3");
    assertNotNull(coordinate);
    assertEquals("1.2.3", resolveDynamicCoordinateVersion(coordinate));

    coordinate = GradleCoordinate.parseCoordinateString("my.group.id:my.bogus.artifact:+");
    assertNotNull(coordinate);
    assertNull(resolveDynamicCoordinateVersion(coordinate));
  }

  private String resolveDynamicCoordinateVersion(GradleCoordinate coordinate) {
    return myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null, mySdkDir, myFileOp);
  }

  private GradleCoordinate resolveDynamicCoordinate(GradleCoordinate coordinate) {
    return myRepositoryUrlManager.resolveDynamicCoordinate(coordinate, null, mySdkDir, myFileOp);
  }

  public void testResolvedCoordinate_sdkMissing() throws Exception {
    myFileOp.deleteFileOrFolder(mySdkDir);
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNull(resolveDynamicCoordinateVersion(coordinate));
  }

}
