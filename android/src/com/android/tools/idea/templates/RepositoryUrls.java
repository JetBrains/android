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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.MavenCoordinate;
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.sdklib.repository.FullRevision;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateUtils.readTextFile;

/**
 * Helper class to aid in generating Maven URLs for various internal repository files (Support Library, AppCompat, etc).
 */
public class RepositoryUrls {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.RepositoryUrls");

  /** The tag used by the maven metadata file to describe versions */
  public static final String TAG_VERSION = "version";

  /** The vendor ID of the support library. */
  public static final String VENDOR_ID = "android";
  /** The path ID of the support library. */
  public static final String SUPPORT_ID = "support";
  /** The path ID of the appcompat library. */
  public static final String APP_COMPAT_ID = "appcompat";
  /** The path ID of the appcompat library. */
  public static final String GRID_LAYOUT_ID = "gridlayout";
  /** The path ID of the compatibility library (which was its id for releases 1-3). */
  public static final String COMPATIBILITY_ID = "compatibility";

  /** Internal Maven Repository settings */
  private static final String SUPPORT_BASE_URL = "com.android.support:%s-%s:%s";

  private static final String MIN_VERSION_VALUE = "0.0.0";

  private static final String SUPPORT_REPOSITORY_BASE_PATH = "%s/extras/android/m2repository/com/android/support/%s/";

  // e.g. 18.0.0/appcompat-v7-18.0.0.aar
  private static final String MAVEN_REVISION_PATH = "%s/%s-%s.aar";

  private static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";

  private static final String HIGHEST_KNOWN_API_FULL_REVISION  = new FullRevision(SdkVersionInfo.HIGHEST_KNOWN_API).toString();

  public static final Map<String, String> SUPPORT_REPOSITORY_DEFAULTS = ImmutableMap.of(
    SUPPORT_ID, String.format(SUPPORT_BASE_URL, SUPPORT_ID, "v4", HIGHEST_KNOWN_API_FULL_REVISION),
    APP_COMPAT_ID, String.format(SUPPORT_BASE_URL, APP_COMPAT_ID, "v7", HIGHEST_KNOWN_API_FULL_REVISION),
    GRID_LAYOUT_ID, String.format(SUPPORT_BASE_URL, GRID_LAYOUT_ID, "v7", HIGHEST_KNOWN_API_FULL_REVISION)
  ); // TODO: Add other libraries here (Cloud SDK, Play Services, YouTube, AdMob, etc).

  /**
   * Calculate the correct version of the support library and generate the corresponding maven URL
   * @param minApiLevel the minimum api level specified by the template (-1 if no minApiLevel specified)
   * @param revision the version of the support library (should be v13 or v4)
   * @return a maven url for the android support library
   */
  @Nullable
  public static String getLibraryUrl(String libraryId, String revision) {
    // Check to see if this is a URL we support:
    if (!SUPPORT_REPOSITORY_DEFAULTS.containsKey(libraryId)) {
      return null;
    }

    if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
      throw new IllegalStateException("No SDK Available. Please configure your SDK in the Project Structure dialog.");
    }

    // Read the support repository and find the latest version available
    String sdkLocation = AndroidSdkUtils.tryToChooseAndroidSdk().getLocation();
    String path = String.format(SUPPORT_REPOSITORY_BASE_PATH, sdkLocation, libraryId + "-" + revision) + MAVEN_METADATA_FILE_NAME;
    path = FileUtil.toSystemDependentName(path);
    File supportMetadataFile = new File(path);
    if (!supportMetadataFile.exists()) {
      return SUPPORT_REPOSITORY_DEFAULTS.get(libraryId);
    }

    String version = getLatestVersionFromMavenMetadata(supportMetadataFile);

    return String.format(SUPPORT_BASE_URL, libraryId, revision, version);
  }

  /**
   * Get the file on the local filesystem that corresponds to the given maven coordinate.
   * @param mavenCoordinate the coordinate to retrieve an archive file for
   * @return a file pointing at the archive for the given coordinate
   */
  public static File getArchiveForCoordinate(MavenCoordinate mavenCoordinate) {
    String sdkLocation = AndroidSdkUtils.tryToChooseAndroidSdk().getLocation();
    String path = String.format(SUPPORT_REPOSITORY_BASE_PATH, sdkLocation, mavenCoordinate.getArtifactId());
    path = FileUtil.toSystemDependentName(path);
    String revisionPath = String.format(MAVEN_REVISION_PATH, mavenCoordinate.getFullRevision(), mavenCoordinate.getArtifactId(),
                                        mavenCoordinate.getFullRevision());
    revisionPath = FileUtil.toSystemDependentName(revisionPath);

    return new File(path, revisionPath);
  }

  /**
   * Returns true iff this class knows how to handle the given library id (Maven Artifact Id)
   * @param libraryId the library id to test
   * @return true iff this class supports the given library
   */
  public static boolean supports(String libraryId) {
    return SUPPORT_REPOSITORY_DEFAULTS.containsKey(libraryId);
  }

  /**
   * Parses a Maven metadata file and returns a string of the highest found version
   * @param metadataFile the files to parse
   * @return the string representing the highest version found in the file or "0.0.0" if no versions exist in the file
   */
  private static String getLatestVersionFromMavenMetadata(File metadataFile) {
    String xml = readTextFile(metadataFile);
    final List<FullRevision> versions = new LinkedList<FullRevision>();
    try {
      SAXParserFactory.newInstance().newSAXParser().parse(new ByteArrayInputStream(xml.getBytes()), new DefaultHandler() {
        boolean inVersionTag = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
          if (qName.equals(TAG_VERSION)) {
            inVersionTag = true;
          }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
          // Get the version and compare it to the current known max version
          if (inVersionTag) {
            versions.add(FullRevision.parseRevision(new String(ch, start, length)));
            inVersionTag = false;
          }
        }
      });
    } catch (Exception e) {
      LOG.warn(e);
    }

    if (versions.isEmpty()) {
      return MIN_VERSION_VALUE;
    } else {
      return Collections.max(versions).toString();
    }
  }
}
