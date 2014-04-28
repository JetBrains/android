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

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.repository.FullRevision;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
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

/**
 * Helper class to aid in generating Maven URLs for various internal repository files (Support Library, AppCompat, etc).
 */
public class RepositoryUrlManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.RepositoryUrlManager");

  /** The tag used by the maven metadata file to describe versions */
  public static final String TAG_VERSION = "version";

  /** The path ID of the support library. */
  public static final String SUPPORT_ID_V4 = "support-v4";
  /** The path ID of the support library. */
  public static final String SUPPORT_ID_V13 = "support-v13";
  /** The path ID of the appcompat library. */
  public static final String APP_COMPAT_ID_V7 = "appcompat-v7";

  /** The path ID of the gridlayout library. */
  public static final String GRID_LAYOUT_ID_V7 = "gridlayout-v7";

  /** The path ID of the mediarouter library*/
  public static final String MEDIA_ROUTER_ID_V7 = "mediarouter-v7";

  /** The path ID of the Play Services library */
  public static final String PLAY_SERVICES_ID = "play-services";

  /** The path ID of the compatibility library (which was its id for releases 1-3). */
  public static final String COMPATIBILITY_ID = "compatibility";

  /** Internal Maven Repository settings */
  /** Constant full revision for "anything available" */
  public static final String REVISION_ANY = "0.0.+";

  private static final String SUPPORT_BASE_COORDINATE = "com.android.support:%s:%s";
  private static final String GOOGLE_BASE_COORDINATE = "com.google.android.gms:%s:%s";

  private static final String SUPPORT_REPOSITORY_BASE_PATH = "%s/extras/android/m2repository/com/android/support/%s/";

  private static final String GOOGLE_REPOSITORY_BASE_PATH = "%s/extras/google/m2repository/com/google/android/gms/%s/";

  // e.g. 18.0.0/appcompat-v7-18.0.0
  private static final String MAVEN_REVISION_PATH = "%2$s/%1$s-%2$s";

  public static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";


  public static final String HELP_COMMENT =
    "// You must install or update the %1$s Repository through the SDK manager to use this dependency.";

  /** Model of our internal extras repository */
  public static final Map<String, RepositoryLibrary> EXTRAS_REPOSITORY = new ImmutableMap.Builder<String, RepositoryLibrary>()
    .put(SUPPORT_ID_V4, new RepositoryLibrary(SUPPORT_ID_V4, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_JAR))
    .put(SUPPORT_ID_V13, new RepositoryLibrary(SUPPORT_ID_V13, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_JAR))
    .put(APP_COMPAT_ID_V7, new RepositoryLibrary(APP_COMPAT_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(GRID_LAYOUT_ID_V7, new RepositoryLibrary(GRID_LAYOUT_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(MEDIA_ROUTER_ID_V7, new RepositoryLibrary(MEDIA_ROUTER_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PLAY_SERVICES_ID, new RepositoryLibrary(PLAY_SERVICES_ID, GOOGLE_REPOSITORY_BASE_PATH, GOOGLE_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .build();


  public static RepositoryUrlManager get() {
    return new RepositoryUrlManager();
  }

  @VisibleForTesting
  RepositoryUrlManager() {}

  /**
   * Calculate the coordinate pointing to the highest valued version of the given library we
   * have available in our repository.
   * @param libraryId the id of the library to find
   * @return a maven coordinate for the requested library or null if we don't support that library
   */
  @Nullable
  public String getLibraryCoordinate(String libraryId) {
    // Check to see if this is a URL we support:
    if (!EXTRAS_REPOSITORY.containsKey(libraryId)) {
      return null;
    }

    AndroidSdkData sdk = tryToChooseAndroidSdk();
    if (sdk == null) {
      return null;
    }

    // Read the support repository and find the latest version available
    String sdkLocation = sdk.getPath();
    RepositoryLibrary library = EXTRAS_REPOSITORY.get(libraryId);

    File supportMetadataFile = new File(String.format(library.basePath, sdkLocation, library.id), MAVEN_METADATA_FILE_NAME);
    if (!fileExists(supportMetadataFile)) {
      return String.format(library.baseCoordinate, library.id, REVISION_ANY);
    }

    String version = getLatestVersionFromMavenMetadata(supportMetadataFile);

    return String.format(library.baseCoordinate, library.id, version);
  }

  /**
   * Get the file on the local filesystem that corresponds to the given maven coordinate.
   * @param gradleCoordinate the coordinate to retrieve an archive file for
   * @return a file pointing at the archive for the given coordinate or null if no SDK is configured
   */
  @Nullable
  public File getArchiveForCoordinate(GradleCoordinate gradleCoordinate) {
    AndroidSdkData sdk = tryToChooseAndroidSdk();

    if (sdk == null) {
      return null;
    }

    // Get the parameters to include in the path
    String sdkLocation = sdk.getPath();
    String artifactId = gradleCoordinate.getArtifactId();
    String revision = gradleCoordinate.getFullRevision();
    RepositoryLibrary library = EXTRAS_REPOSITORY.get(artifactId);

    File path = new File(String.format(library.basePath, sdkLocation, library.id));
    String revisionPath = String.format(MAVEN_REVISION_PATH, library.id, revision) + library.archiveExtension;

    return new File(path, revisionPath);
  }

  /**
   * Returns true iff this class knows how to handle the given library id (Maven Artifact Id)
   * @param libraryId the library id to test
   * @return true iff this class supports the given library
   */
  public static boolean supports(String libraryId) {
    return EXTRAS_REPOSITORY.containsKey(libraryId);
  }

  /**
   * Parses a Maven metadata file and returns a string of the highest found version
   * @param metadataFile the files to parse
   * @return the string representing the highest version found in the file or "0.0.0" if no versions exist in the file
   */
  private String getLatestVersionFromMavenMetadata(File metadataFile) {
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
      return REVISION_ANY;
    } else {
      return Collections.max(versions).toString();
    }
  }

  /**
   * Get a helpful comment about how to install the parent repository for the given coordinate
   */
  public static String getHelpComment(@NotNull GradleCoordinate coordinate) {
    String repositoryName = coordinate.getArtifactId().equals(PLAY_SERVICES_ID) ? "Google" : "Support";
    return String.format(HELP_COMMENT, repositoryName);
  }

  @Nullable
  protected AndroidSdkData tryToChooseAndroidSdk() {
    return AndroidSdkUtils.tryToChooseAndroidSdk();
  }

  @Nullable
  protected String readTextFile(File file) {
    return TemplateUtils.readTextFile(file);
  }

  protected boolean fileExists(@NotNull File file) {
    return file.exists();
  }

  private static class RepositoryLibrary {
    public final String id;
    public final String basePath;
    public final String baseCoordinate;
    public final String archiveExtension;


    private RepositoryLibrary(String id, String basePath, String baseCoordinate, String archiveExtension) {
      this.id = id;
      this.basePath = basePath;
      this.baseCoordinate = baseCoordinate;
      this.archiveExtension = archiveExtension;
    }
  }
}
