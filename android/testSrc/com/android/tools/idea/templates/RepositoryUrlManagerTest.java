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

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the local repository utility class
 */
public class RepositoryUrlManagerTest extends TestCase {

  private RepositoryUrlManager myRepositoryUrlManager;
  private AndroidSdkData mockSdkData;
  private String myFileText;
  private boolean myFileExists = true;
  private File mSdkDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mockSdkData = mock(AndroidSdkData.class);
    when(mockSdkData.getLocation()).thenReturn(getMockSupportLibraryInstallation());

    myRepositoryUrlManager = new RepositoryUrlManager() {
      @Nullable
      @Override
      public AndroidSdkData tryToChooseAndroidSdk() {
        return mockSdkData;
      }

      @Nullable
      @Override
      public String readTextFile(File file) {
        return myFileText;
      }

      @Override
      public boolean fileExists(@NotNull File file) {
        return myFileExists;
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();

    if (mSdkDir != null) {
      FileUtil.delete(mSdkDir);
      mSdkDir = null;
    }
  }

  /** Creates a mock SDK installation structure, containing a fixed set of dependencies */
  private File getMockSupportLibraryInstallation() {
    if (mSdkDir == null) {
      // Make fake SDK "installation" such that we can predict the set
      // of Maven repositories discovered by this test
      mSdkDir = Files.createTempDir();

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
        "extras/android/m2repository/com/android/support/support-v4/20.0.0/support-v4-20.0.0.aar",
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
        "extras/google/m2repository/com/google/android/gms/play-services/6.1.11/play-services-6.1.11.aar",
        "extras/google/m2repository/com/google/android/gms/play-services/6.1.71/play-services-6.1.71.aar",
        "extras/google/m2repository/com/google/android/gms/play-services-wearable/5.0.77/play-services-wearable-5.0.77.aar",
        "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.11/play-services-wearable-6.1.11.aar",
        "extras/google/m2repository/com/google/android/gms/play-services-wearable/6.1.71/play-services-wearable-6.1.71.aar",
        "extras/google/m2repository/com/google/android/support/wearable/1.0.0/wearable-1.0.0.aar"
      };

      for (String path : paths) {
        File file = new File(mSdkDir, path.replace('/', File.separatorChar));
        File parent = file.getParentFile();
        if (!parent.exists()) {
          boolean ok = parent.mkdirs();
          assertTrue(ok);
        }
        try {
          boolean created = file.createNewFile();
          assertTrue(created);
        } catch (IOException e) {
          fail(e.toString());
        }
      }
    }

    return mSdkDir;
  }

  public void testGetLibraryCoordinate() throws Exception {
    // Check unsupported URL
    assertNull(myRepositoryUrlManager.getLibraryCoordinate("actionbar-sherlock"));

    // Check null SDK
    AndroidSdkData oldMockSdk = mockSdkData;
    mockSdkData = null;
    assertNull(myRepositoryUrlManager.getLibraryCoordinate("support-v4"));
    mockSdkData = oldMockSdk;

    // Check repository doesn't exist
    myFileExists = false;
    String expectedCoordinate = "com.android.support:support-v4:0.0.+";
    assertEquals(expectedCoordinate, myRepositoryUrlManager.getLibraryCoordinate("support-v4"));


    // Set up our fake file contents for the "maven-metadata.xml" file
    myFileText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><versioning>" +
                 "<version>13.0.0</version> <version>19.0.1</version>" +
                 "</versioning>";
    myFileExists = true;

    // Check support library
    assertEquals("com.android.support:support-v4:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("support-v4"));
    assertEquals("com.android.support:support-v13:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("support-v13"));

    // Check AppCompat, GridLayout, and MediaRouter
    assertEquals("com.android.support:appcompat-v7:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("appcompat-v7"));
    assertEquals("com.android.support:gridlayout-v7:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("gridlayout-v7"));
    assertEquals("com.android.support:mediarouter-v7:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("mediarouter-v7"));

    // Check GMS
    assertEquals("com.google.android.gms:play-services:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("play-services"));
  }

  public void testGetLibraryCoordinateReturnsPreview() throws Exception {
    // Set up our fake file contents for the "maven-metadata.xml" file
    myFileText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><versioning>" +
                 "<version>13.0.0</version> <version>19.0.1</version>" +
                 "<version>13.0.0</version> <version>20.0.0-rc1</version>" +
                 "</versioning>";
    myFileExists = true;

    // Check no preview when asked not to return previews
    assertEquals("com.android.support:support-v4:19.0.1", myRepositoryUrlManager.getLibraryCoordinate("support-v4", null, false));

    // Check preview returned when explicitly asked for previews
    assertEquals("com.android.support:support-v4:20.0.0-rc1", myRepositoryUrlManager.getLibraryCoordinate("support-v4", null, true));

    // Check preview returned by default
    assertEquals("com.android.support:support-v4:20.0.0-rc1", myRepositoryUrlManager.getLibraryCoordinate("support-v4"));
  }

  public void testGetArchiveForCoordinate() throws Exception {
    // Check null SDK
    AndroidSdkData oldMockSdk = mockSdkData;
    mockSdkData = null;
    assertNull(myRepositoryUrlManager.getArchiveForCoordinate(mock(GradleCoordinate.class)));
    mockSdkData = oldMockSdk;

    // Check support library
    GradleCoordinate supportCoordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:18.3.1");
    assertNotNull(supportCoordinate);
    File expectedFile = new File(getMockSupportLibraryInstallation().getPath()
                                 + "/extras/android/m2repository/com/android/support/support-v4/18.3.1/support-v4-18.3.1.jar"
                                   .replace('/', File.separatorChar));
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(supportCoordinate));

    // Check AppCompat
    GradleCoordinate appcompatCoordinate = GradleCoordinate.parseCoordinateString("com.android.support:appcompat-v7:19.0.1");
    assertNotNull(appcompatCoordinate);
    expectedFile = new File(getMockSupportLibraryInstallation().getPath()
                            + "/extras/android/m2repository/com/android/support/appcompat-v7/19.0.1/appcompat-v7-19.0.1.aar"
                              .replace('/', File.separatorChar));
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(appcompatCoordinate));

    // Check PlayServices
    GradleCoordinate playservicesCoordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.1.32");
    assertNotNull(playservicesCoordinate);
    expectedFile = new File(getMockSupportLibraryInstallation().getPath()
                            + "/extras/google/m2repository/com/google/android/gms/play-services/4.1.32/play-services-4.1.32.aar"
                              .replace('/', File.separatorChar));
    assertEquals(expectedFile, myRepositoryUrlManager.getArchiveForCoordinate(playservicesCoordinate));
  }

  public void testResolvedCoordinate() throws Exception {
    // Check null SDK
    AndroidSdkData oldMockSdk = mockSdkData;
    mockSdkData = null;
    assertNull(myRepositoryUrlManager.getArchiveForCoordinate(mock(GradleCoordinate.class)));
    mockSdkData = oldMockSdk;

    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+");
    assertNotNull(coordinate);
    assertEquals("4.4.52", myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null));
    assertEquals(GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.4.52"),
                 myRepositoryUrlManager.resolveDynamicCoordinate(coordinate, null));

    coordinate = GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.+@aar");
    assertNotNull(coordinate);
    assertEquals("4.4.52", myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null));
    assertEquals(GradleCoordinate.parseCoordinateString("com.google.android.gms:play-services:4.4.52@aar"),
                 myRepositoryUrlManager.resolveDynamicCoordinate(coordinate, null));

    coordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:+");
    assertNotNull(coordinate);
    assertEquals("21.0.2", myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null));

    // Make sure already resolved coordinates are handled correctly
    coordinate = GradleCoordinate.parseCoordinateString("com.android.support:support-v4:1.2.3");
    assertNotNull(coordinate);
    assertEquals("1.2.3", myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null));

    coordinate = GradleCoordinate.parseCoordinateString("my.group.id:my.bogus.artifact:+");
    assertNotNull(coordinate);
    assertNull(myRepositoryUrlManager.resolveDynamicCoordinateVersion(coordinate, null));
  }

  public void testSupports() throws Exception {
    assertTrue(RepositoryUrlManager.supports("support-v4"));
    assertTrue(RepositoryUrlManager.supports("support-v13"));
    assertTrue(RepositoryUrlManager.supports("gridlayout-v7"));
    assertTrue(RepositoryUrlManager.supports("appcompat-v7"));
    assertTrue(RepositoryUrlManager.supports("play-services"));
    assertTrue(RepositoryUrlManager.supports("play-services-wearable"));
    assertTrue(RepositoryUrlManager.supports("support-annotations"));
    assertTrue(RepositoryUrlManager.supports("cardview-v7"));
    assertTrue(RepositoryUrlManager.supports("recyclerview-v7"));
    assertTrue(RepositoryUrlManager.supports("palette-v7"));
    assertTrue(RepositoryUrlManager.supports("leanback-v17"));

    assertFalse(RepositoryUrlManager.supports("actionbar-sherlock"));
  }
}
