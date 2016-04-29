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
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.LintClient;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;

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
  /** The path ID of the design support library. */
  public static final String DESIGN = "design";

  /** The path ID of the gridlayout library. */
  public static final String GRID_LAYOUT_ID_V7 = "gridlayout-v7";

  /** The path ID of the mediarouter library*/
  public static final String MEDIA_ROUTER_ID_V7 = "mediarouter-v7";

  /** The path ID of the Play Services library */
  public static final String PLAY_SERVICES_ID = "play-services";

  /** The path ID of the Ads Play Services library */
  public static final String PLAY_SERVICES_ADS_ID = "play-services-ads";

  /** The path ID of the Wearable Play Services library */
  public static final String PLAY_SERVICES_WEARABLE_ID = "play-services-wearable";

  /** The path ID of the Maps Play Services library */
  public static final String PLAY_SERVICES_MAPS_ID = "play-services-maps";

  /** The path ID of the wearable support library */
  public static final String SUPPORT_WEARABLE_ID = "wearable";

  /** The path ID of the cardview library*/
  public static final String CARDVIEW_ID_V7 = "cardview-v7";

  /** The path ID of the leanback library*/
  public static final String LEANBACK_ID_V17 = "leanback-v17";

  /** The path ID of the palette library*/
  public static final String PALETTE_ID_V7 = "palette-v7";

  /** The path ID of the recyclerview library*/
  public static final String RECYCLER_VIEW_ID_V7 = "recyclerview-v7";

  /** The path ID of the support-annotations library*/
  public static final String SUPPORT_ANNOTATIONS = "support-annotations";

  /** The path ID of the compatibility library (which was its id for releases 1-3). */
  public static final String COMPATIBILITY_ID = "compatibility";

  /** Internal Maven Repository settings */
  /** Constant full revision for "anything available" */
  public static final String REVISION_ANY = "0.0.+";

  private static final String SUPPORT_BASE_COORDINATE = "com.android.support:%s:%s";
  private static final String GOOGLE_BASE_COORDINATE = "com.google.android.gms:%s:%s";
  private static final String GOOGLE_SUPPORT_BASE_COORDINATE = "com.google.android.support:%s:%s";

  private static final String SUPPORT_REPOSITORY_BASE_PATH = "%s/extras/android/m2repository/com/android/support/%s/";

  private static final String GOOGLE_REPOSITORY_BASE_PATH = "%s/extras/google/m2repository/com/google/android/gms/%s/";

  private static final String GOOGLE_SUPPORT_REPOSITORY_BASE_PATH = "%s/extras/google/m2repository/com/google/android/support/%s/";

  // e.g. 18.0.0/appcompat-v7-18.0.0
  private static final String MAVEN_REVISION_PATH = "%2$s/%1$s-%2$s";

  public static final String MAVEN_METADATA_FILE_NAME = "maven-metadata.xml";


  public static final String HELP_COMMENT =
    "// You must install or update the %1$s Repository through the SDK manager to use this dependency.";

  /** Model of our internal extras repository */
  private static final RangeMap<Integer, String> SUPPORT_LIBRARY_EXTENSIONS = ImmutableRangeMap.<Integer, String>builder()
    .put(Range.closed(1, 19), SdkConstants.DOT_JAR)
    .put(Range.atLeast(20), SdkConstants.DOT_AAR)
    .build();
  public static final Map<String, RepositoryLibrary> EXTRAS_REPOSITORY = new ImmutableMap.Builder<String, RepositoryLibrary>()
    .put(SUPPORT_ANNOTATIONS, new RepositoryLibrary(SUPPORT_ANNOTATIONS, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_JAR))
    .put(SUPPORT_ID_V4,
         new RepositoryLibrary(SUPPORT_ID_V4, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SUPPORT_LIBRARY_EXTENSIONS))
    .put(SUPPORT_ID_V13, new RepositoryLibrary(SUPPORT_ID_V13, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE,
                                               SUPPORT_LIBRARY_EXTENSIONS))
    .put(APP_COMPAT_ID_V7, new RepositoryLibrary(APP_COMPAT_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(DESIGN, new RepositoryLibrary(DESIGN, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(GRID_LAYOUT_ID_V7, new RepositoryLibrary(GRID_LAYOUT_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(MEDIA_ROUTER_ID_V7, new RepositoryLibrary(MEDIA_ROUTER_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PLAY_SERVICES_ID, new RepositoryLibrary(PLAY_SERVICES_ID, GOOGLE_REPOSITORY_BASE_PATH, GOOGLE_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PLAY_SERVICES_ADS_ID, new RepositoryLibrary(PLAY_SERVICES_ADS_ID, GOOGLE_REPOSITORY_BASE_PATH, GOOGLE_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PLAY_SERVICES_WEARABLE_ID, new RepositoryLibrary(PLAY_SERVICES_WEARABLE_ID, GOOGLE_REPOSITORY_BASE_PATH, GOOGLE_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PLAY_SERVICES_MAPS_ID, new RepositoryLibrary(PLAY_SERVICES_MAPS_ID, GOOGLE_REPOSITORY_BASE_PATH, GOOGLE_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(CARDVIEW_ID_V7, new RepositoryLibrary(CARDVIEW_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(PALETTE_ID_V7, new RepositoryLibrary(PALETTE_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(RECYCLER_VIEW_ID_V7, new RepositoryLibrary(RECYCLER_VIEW_ID_V7, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(LEANBACK_ID_V17, new RepositoryLibrary(LEANBACK_ID_V17, SUPPORT_REPOSITORY_BASE_PATH, SUPPORT_BASE_COORDINATE, SdkConstants.DOT_AAR))
    .put(SUPPORT_WEARABLE_ID, new RepositoryLibrary(SUPPORT_WEARABLE_ID, GOOGLE_SUPPORT_REPOSITORY_BASE_PATH, GOOGLE_SUPPORT_BASE_COORDINATE,
                                                    SdkConstants.DOT_AAR))
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
    return getLibraryCoordinate(libraryId, null, true);
  }

  /**
   * Returns the string for the specific version number of the most recent version of the given library
   * (matching the given prefix filter, if any) in one of the Sdk repositories.
   *
   * @param groupId the group id
   * @param artifactId the artifact id
   * @param filterPrefix a prefix, if any
   * @param includePreviews whether to include preview versions of libraries
   * @return
   */
  @Nullable
  public String getLibraryCoordinate(String groupId, String artifactId, @Nullable String filterPrefix, boolean includePreviews) {
    SdkMavenRepository repository = SdkMavenRepository.getByGroupId(groupId);
    if (repository == null) {
      return null;
    }
    AndroidSdkData sdk = tryToChooseAndroidSdk();
    if (sdk == null) {
      return null;
    }

    File sdkLocation = sdk.getLocation();
    File repo = repository.getRepositoryLocation(sdkLocation, false);
    if (repo == null) {
      return null;
    }

    GradleCoordinate max = repository.getHighestInstalledVersion(sdk.getLocation(), groupId, artifactId, filterPrefix, includePreviews);
    if (max == null) {
      return null;
    }

    return max.getRevision();
  }

  /**
   * Calculate the coordinate pointing to the highest valued version of the given library we
   * have available in our repository.
   * @param libraryId the id of the library to find
   * @param filterPrefix an optional prefix libraries must match; e.g. if the prefix is "18." then only coordinates
   *           in version 18.x will be considered
   * @return a maven coordinate for the requested library or null if we don't support that library
   * @deprecated Use {@link #getLibraryCoordinate(String, String, String, boolean)} instead. This method only takes
   *   an artifact id, which may <b>not</b> be unique across group id's, and besides, the below method relies on a hardcoded
   *   list of libraries in each repository, which gets obsolete all the time as new repositories are added. The method
   *   above however, does not rely on a table like that and should continue to work as new libraries are added.
   */
  @Nullable
  @Deprecated
  public String getLibraryCoordinate(String libraryId, @Nullable String filterPrefix, boolean includePreviews) {
    // Check to see if this is a URL we support:
    if (!EXTRAS_REPOSITORY.containsKey(libraryId)) {
      return null;
    }

    AndroidSdkData sdk = tryToChooseAndroidSdk();
    if (sdk == null) {
      return null;
    }

    // Read the support repository and find the latest version available
    String sdkLocation = sdk.getLocation().getPath();
    RepositoryLibrary library = EXTRAS_REPOSITORY.get(libraryId);

    File supportMetadataFile = new File(String.format(library.basePath, sdkLocation, library.id), MAVEN_METADATA_FILE_NAME);
    if (!fileExists(supportMetadataFile)) {
      return String.format(library.baseCoordinate, library.id, REVISION_ANY);
    }

    String version = getLatestVersionFromMavenMetadata(supportMetadataFile, filterPrefix, includePreviews);

    return version != null ? String.format(library.baseCoordinate, library.id, version) : null;
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
    String sdkLocation = sdk.getLocation().getPath();
    String artifactId = gradleCoordinate.getArtifactId();
    String revision = gradleCoordinate.getRevision();
    RepositoryLibrary library = EXTRAS_REPOSITORY.get(artifactId);

    File path = new File(String.format(library.basePath, sdkLocation, library.id));
    String revisionPath = String.format(MAVEN_REVISION_PATH, library.id, revision) +
                          library.getArchiveExtension(gradleCoordinate.getMajorVersion());

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
   * @param includePreviews if false, preview versions of the library will not be returned
   * @return the string representing the highest version found in the file or "0.0.0" if no versions exist in the file
   */
  @Nullable
  public String getLatestVersionFromMavenMetadata(@NotNull final File metadataFile,
                                                  @Nullable final String filterPrefix,
                                                  final boolean includePreviews) {
    String xml = readTextFile(metadataFile);
    final List<GradleCoordinate> versions = Lists.newLinkedList();
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
            inVersionTag = false;
            String revision = new String(ch, start, length);
            //noinspection StatementWithEmptyBody
            if (!includePreviews && "5.2.08".equals(revision) && metadataFile.getPath().contains(PLAY_SERVICES_ID)) {
              // This version (despite not having -rcN in its version name is actually a preview
              // (See https://code.google.com/p/android/issues/detail?id=75292)
              // Ignore it
            } else if (filterPrefix == null || revision.startsWith(filterPrefix)) {
              versions.add(GradleCoordinate.parseVersionOnly(revision));
            }
          }
        }
      });
    } catch (Exception e) {
      LOG.warn(e);
    }

    if (versions.isEmpty()) {
      return REVISION_ANY;
    } else if (includePreviews) {
      return GRADLE_COORDINATE_ORDERING.max(versions).getRevision();
    } else {
      try {
        return GRADLE_COORDINATE_ORDERING.max(Iterables.filter(versions, IS_NOT_PREVIEW)).getRevision();
      } catch (NoSuchElementException e) {
        return null;
      }
    }
  }

  /**
   * Checks the given Gradle coordinate, and if it contains a dynamic dependency, returns
   * a new Gradle coordinate with the dynamic dependency replaced with a specific version.
   * This tries looking at local caches, to pick the best version that Gradle would use
   * without hitting the network, but (if a {@link Project} is provided) it can also fall
   * back to querying the network for the latest version. Note that this works not just
   * for a completely generic version (e.g. "+", but for more specific version filters like
   * 23.+ and 23.1.+ as well.
   * <p/>
   * Note that in some cases the method may return null -- such as the case for unknown
   * artifacts not found on disk or on the network, or for valid artifacts but where
   * there is no local cache and the network query is not successful.
   *
   * @param coordinate the coordinate whose version we want to resolve
   * @param project    the current project, if known. This is equired if you want to
   *                   perform a network lookup of the current best version if we can't
   *                   find a locally cached version of the library
   * @return the resolved coordinate, or null if not successful
   */
  @Nullable
  public GradleCoordinate resolveDynamicCoordinate(@NotNull GradleCoordinate coordinate, @Nullable Project project) {
    String version = resolveDynamicCoordinateVersion(coordinate, project);
    if (version != null && coordinate.getGroupId() != null && coordinate.getArtifactId() != null) {
      List<GradleCoordinate.RevisionComponent> revisions = GradleCoordinate.parseRevisionNumber(version);
      if (!revisions.isEmpty()) {
        return new GradleCoordinate(coordinate.getGroupId(), coordinate.getArtifactId(), revisions, coordinate.getArtifactType());
      }
    }

    return null;
  }

  /**
   * Checks the given Gradle coordinate, and if it contains a dynamic dependency, returns
   * the specific version that Gradle would use during a build.
   * <p>
   * This tries looking at local caches, to pick the best version that Gradle would use
   * without hitting the network, but (if a {@link Project} is provided) it can also fall
   * back to querying the network for the latest version. Note that this works not just
   * for a completely generic version (e.g. "+", but for more specific version filters like
   * 23.+ and 23.1.+ as well.
   * <p>
   * Note that in some cases the method may return null -- such as the case for unknown
   * artifacts not found on disk or on the network, or for valid artifacts but where
   * there is no local cache and the network query is not successful.
   *
   * @param coordinate the coordinate whose version we want to resolve
   * @param project    the current project, if known. This is equired if you want to
   *                   perform a network lookup of the current best version if we can't
   *                   find a locally cached version of the library
   * @return the string version number, or null if not successful
   */
  @Nullable
  public String resolveDynamicCoordinateVersion(@NotNull GradleCoordinate coordinate, @Nullable Project project) {
    String filter = coordinate.getRevision();
    if (!filter.endsWith("+")) {
      // Already resolved. That was easy.
      return filter;
    }
    filter = filter.substring(0, filter.length() - 1);

    // If this coordinate points to an artifact in one of our repositories, mark it will a comment if they don't
    // have that repository available.
    String libraryCoordinate = getLibraryCoordinate(coordinate.getGroupId(), coordinate.getArtifactId(), filter, false);
    if (libraryCoordinate != null) {
      return libraryCoordinate;
    }

    // If that didn't yield any matches, try again, this time allowing preview platforms.
    // This is necessary if the artifact filter includes enough of a version where there are
    // only preview matches.
    libraryCoordinate = getLibraryCoordinate(coordinate.getGroupId(), coordinate.getArtifactId(), filter, true);
    if (libraryCoordinate != null) {
      return libraryCoordinate;
    }

    // Regular Gradle dependency? Look in Gradle cache
    GradleCoordinate found = GradleUtil.findLatestVersionInGradleCache(coordinate, filter, project);
    if (found != null) {
      return found.getRevision();
    }

    // Perform network lookup to resolve current best version, if possible
    if (project != null) {
      LintClient client = new IntellijLintClient(project);
      Revision latest = GradleDetector.getLatestVersionFromRemoteRepo(client, coordinate, coordinate.isPreview());
      if (latest != null) {
        String version = latest.toShortString();
        if (version.startsWith(filter)) {
          return version;
        }
      }
    }

    return null;
  }

  /**
   * Evaluates to true iff the given Revision is not a preview version
   */
  private static final Predicate<GradleCoordinate> IS_NOT_PREVIEW = new Predicate<GradleCoordinate>() {
    @Override
    public boolean apply(GradleCoordinate input) {
      return !input.isPreview();
    }
  };

  private static final Ordering<GradleCoordinate> GRADLE_COORDINATE_ORDERING = new Ordering<GradleCoordinate>() {
    @Override
    public int compare(GradleCoordinate left, GradleCoordinate right) {
      return GradleCoordinate.COMPARE_PLUS_LOWER.compare(left, right);
    }
  };

  @Nullable
  protected AndroidSdkData tryToChooseAndroidSdk() {
    return AndroidSdkUtils.tryToChooseAndroidSdk();
  }

  @Nullable
  protected String readTextFile(File file) {
    return TemplateUtils.readTextFromDisk(file);
  }

  protected boolean fileExists(@NotNull File file) {
    return file.exists();
  }

  public static class RepositoryLibrary {
    public final String id;
    public final String basePath;
    public final String baseCoordinate;
    private final RangeMap<Integer, String> myArchiveExtensions;

    private RepositoryLibrary(String id, String basePath, String baseCoordinate, String archiveExtension) {
      this.id = id;
      this.basePath = basePath;
      this.baseCoordinate = baseCoordinate;
      myArchiveExtensions = TreeRangeMap.create();
      myArchiveExtensions.put(Range.<Integer>all(), archiveExtension);
    }

    private RepositoryLibrary(String id, String basePath, String baseCoordinate, RangeMap<Integer, String> archiveExtensions) {
      this.id = id;
      this.basePath = basePath;
      this.baseCoordinate = baseCoordinate;
      myArchiveExtensions = archiveExtensions;
    }

    @NotNull
    public String getArchiveExtension(int revision) {
      String extension = myArchiveExtensions.get(revision);
      return extension == null ? "" : extension;
    }
  }
}
