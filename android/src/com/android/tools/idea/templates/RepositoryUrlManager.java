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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleCoordinate.ArtifactType;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.eclipse.ImportModule;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleLocalCache;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.LintClient;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.IOUtils;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_LOWER;
import static com.android.tools.idea.templates.SupportLibrary.PLAY_SERVICES;

/**
 * Helper class to aid in generating Maven URLs for various internal repository files (Support Library, AppCompat, etc).
 */
public class RepositoryUrlManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.RepositoryUrlManager");

  /**
   * The tag used by the maven metadata file to describe versions
   */
  public static final String TAG_VERSION = "version";

  /**
   * Constant full revision for "anything available"
   */
  public static final String REVISION_ANY = "0.0.+";

  private static final Ordering<GradleCoordinate> GRADLE_COORDINATE_ORDERING = Ordering.from(COMPARE_PLUS_LOWER);

  private final boolean myForceRepositoryChecksInTests;

  public static RepositoryUrlManager get() {
    return new RepositoryUrlManager(false);
  }

  @VisibleForTesting
  RepositoryUrlManager(boolean forceRepositoryChecks) {
    myForceRepositoryChecksInTests = forceRepositoryChecks;
  }

  @Nullable
  public String getLibraryStringCoordinate(SupportLibrary library, boolean preview) {
    AndroidSdkData sdk = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdk == null) {
      return null;
    }

    String revision = getLibraryRevision(library.getGroupId(),
                                         library.getArtifactId(),
                                         null,
                                         preview,
                                         sdk.getLocation(),
                                         FileOpUtils.create());
    if (revision == null) {
      return null;
    }

    return library.getGradleCoordinate(revision).toString();
  }

  /**
   * Returns the string for the specific version number of the most recent version of the given library
   * (matching the given prefix filter, if any) in one of the Sdk repositories.
   *
   * @param groupId         the group id
   * @param artifactId      the artifact id
   * @param filterPrefix    a prefix, if any
   * @param includePreviews whether to include preview versions of libraries
   * @return
   */
  @Nullable
  public String getLibraryRevision(@NotNull String groupId,
                                   @NotNull String artifactId,
                                   @Nullable String filterPrefix,
                                   boolean includePreviews,
                                   @NotNull File sdkLocation,
                                   @NotNull FileOp fileOp) {
    // Try the new, combined repository first:
    File combinedRepo = FileUtils.join(sdkLocation, FD_EXTRAS, FD_M2_REPOSITORY);
    if (fileOp.isDirectory(combinedRepo)) {
      GradleCoordinate versionInCombined = MavenRepositories.getHighestInstalledVersion(groupId,
                                                                                        artifactId,
                                                                                        combinedRepo,
                                                                                        filterPrefix,
                                                                                        includePreviews,
                                                                                        fileOp);
      if (versionInCombined != null) {
        return versionInCombined.getRevision();
      }
    }

    // Now try the "old" repositories, "google" and "android":
    SdkMavenRepository repository = SdkMavenRepository.find(sdkLocation, groupId, artifactId, fileOp);
    if (repository == null) {
      // Try the repo embedded in AS. We distribute for example the constraint layout there for now.
      List<File> paths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
      for (File path : paths) {
        if (path != null && path.isDirectory()) {
          GradleCoordinate versionInEmbedded = MavenRepositories.getHighestInstalledVersion(groupId,
                                                                                            artifactId,
                                                                                            path,
                                                                                            filterPrefix,
                                                                                            includePreviews,
                                                                                            fileOp);
          if (versionInEmbedded != null) {
            return versionInEmbedded.getRevision();
          }
        }
      }
      return null;
    }

    File repositoryLocation = repository.getRepositoryLocation(sdkLocation, true, fileOp);
    if (repositoryLocation == null) {
      return null;
    }

    // Try using the POM file:
    File mavenMetadataFile = MavenRepositories.getMavenMetadataFile(repositoryLocation, groupId, artifactId);
    if (fileOp.isFile(mavenMetadataFile)) {
      try {
        return getLatestVersionFromMavenMetadata(mavenMetadataFile, filterPrefix, includePreviews, fileOp);
      }
      catch (IOException e) {
        return null;
      }
    }

    // Just scan all the directories:
    GradleCoordinate max = repository.getHighestInstalledVersion(sdkLocation,
                                                                 groupId,
                                                                 artifactId,
                                                                 filterPrefix,
                                                                 includePreviews,
                                                                 fileOp);
    if (max == null) {
      return null;
    }

    return max.getRevision();
  }

  /**
   * Gets the file on the local filesystem that corresponds to the given maven coordinate.
   *
   * @param gradleCoordinate the coordinate to retrieve an archive file for
   * @param sdkLocation      SDK to use
   * @param fileOp           {@link FileOp} used for file operations
   * @return a file pointing at the archive for the given coordinate or null if no SDK is configured
   */
  @Nullable
  public File getArchiveForCoordinate(@NotNull GradleCoordinate gradleCoordinate,
                                      @NotNull File sdkLocation,
                                      @NotNull FileOp fileOp) {
    if (gradleCoordinate.getGroupId() == null || gradleCoordinate.getArtifactId() == null) {
      return null;
    }

    SdkMavenRepository repository = SdkMavenRepository.find(sdkLocation,
                                                            gradleCoordinate.getGroupId(),
                                                            gradleCoordinate.getArtifactId(),
                                                            fileOp);
    if (repository == null) {
      return null;
    }

    File repositoryLocation = repository.getRepositoryLocation(sdkLocation, true, fileOp);
    if (repositoryLocation == null) {
      return null;
    }

    File artifactDirectory = MavenRepositories.getArtifactDirectory(repositoryLocation, gradleCoordinate);
    if (!fileOp.isDirectory(artifactDirectory)) {
      return null;
    }

    for (ArtifactType artifactType : ImmutableList.of(ArtifactType.JAR, ArtifactType.AAR)) {
      File archive = new File(artifactDirectory,
                              String.format("%s-%s.%s",
                                            gradleCoordinate.getArtifactId(),
                                            gradleCoordinate.getRevision(),
                                            artifactType.toString()));

      if (fileOp.isFile(archive)) {
        return archive;
      }
    }

    return null;
  }

  /**
   * Parses a Maven metadata file and returns a string of the highest found version
   *
   * @param metadataFile    the files to parse
   * @param includePreviews if false, preview versions of the library will not be returned
   * @return the string representing the highest version found in the file or "0.0.0" if no versions exist in the file
   */
  @Nullable
  private static String getLatestVersionFromMavenMetadata(@NotNull File metadataFile,
                                                          @Nullable String filterPrefix,
                                                          boolean includePreviews,
                                                          @NotNull FileOp fileOp) throws IOException {
    String xml = fileOp.toString(metadataFile, StandardCharsets.UTF_8);

    List<GradleCoordinate> versions = Lists.newLinkedList();
    try {
      SAXParserFactory.newInstance().newSAXParser().parse(IOUtils.toInputStream(xml), new DefaultHandler() {
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
            if (!includePreviews &&
                "5.2.08".equals(revision) &&
                metadataFile.getPath().contains(PLAY_SERVICES.getArtifactId())) {
              // This version (despite not having -rcN in its version name is actually a preview
              // (See https://code.google.com/p/android/issues/detail?id=75292)
              // Ignore it
            }
            else if (filterPrefix == null || revision.startsWith(filterPrefix)) {
              versions.add(GradleCoordinate.parseVersionOnly(revision));
            }
          }
        }
      });
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    if (versions.isEmpty()) {
      return REVISION_ANY;
    }
    else if (includePreviews) {
      return GRADLE_COORDINATE_ORDERING.max(versions).getRevision();
    }
    else {
      return versions.stream()
        .filter(v -> !v.isPreview())
        .max(GRADLE_COORDINATE_ORDERING)
        .map(GradleCoordinate::getRevision)
        .orElse(null);
    }
  }

  @Nullable
  public GradleCoordinate resolveDynamicCoordinate(@NotNull GradleCoordinate coordinate, @Nullable Project project) {
    return resolveDynamicCoordinate(coordinate, project, AndroidSdks.getInstance().tryToChooseSdkHandler());
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
   * @param coordinate  the coordinate whose version we want to resolve
   * @param project     the current project, if known. This is required if you want to
   *                    perform a network lookup of the current best version if we can't
   *                    find a locally cached version of the library
   * @return the resolved coordinate, or null if not successful
   */
  @Nullable
  public GradleCoordinate resolveDynamicCoordinate(@NotNull GradleCoordinate coordinate,
                                                   @Nullable Project project,
                                                   @NotNull AndroidSdkHandler sdkHandler) {
    String version = resolveDynamicCoordinateVersion(coordinate, project, sdkHandler);
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
    return resolveDynamicCoordinateVersion(coordinate, project, AndroidSdks.getInstance().tryToChooseSdkHandler());
  }

  @Nullable
  @VisibleForTesting
  String resolveDynamicCoordinateVersion(@NotNull GradleCoordinate coordinate,
                                         @Nullable Project project,
                                         @NotNull AndroidSdkHandler sdkHandler) {
    if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) {
      return null;
    }

    String filter = coordinate.getRevision();
    if (!filter.endsWith("+")) {
      // Already resolved. That was easy.
      return filter;
    }
    filter = filter.substring(0, filter.length() - 1);

    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation != null) {
      // If this coordinate points to an artifact in one of our repositories, mark it will a comment if they don't
      // have that repository available.
      String libraryCoordinate = getLibraryRevision(coordinate.getGroupId(),
                                                    coordinate.getArtifactId(),
                                                    filter,
                                                    false,
                                                    sdkLocation,
                                                    sdkHandler.getFileOp());
      if (libraryCoordinate != null) {
        return libraryCoordinate;
      }

      // If that didn't yield any matches, try again, this time allowing preview platforms.
      // This is necessary if the artifact filter includes enough of a version where there are
      // only preview matches.
      libraryCoordinate = getLibraryRevision(coordinate.getGroupId(),
                                             coordinate.getArtifactId(),
                                             filter,
                                             true,
                                             sdkLocation,
                                             sdkHandler.getFileOp());
      if (libraryCoordinate != null) {
        return libraryCoordinate;
      }
    }
    // Regular Gradle dependency? Look in Gradle cache
    GradleVersion versionFound = GradleLocalCache.getInstance().findLatestArtifactVersion(coordinate, project, filter);
    if (versionFound != null) {
      return versionFound.toString();
    }

    // Maybe it's available for download as an SDK component
    RemotePackage sdkPackage = SdkMavenRepository
      .findLatestRemoteVersion(coordinate, sdkHandler, new StudioLoggerProgressIndicator(getClass()));
    if (sdkPackage != null) {
      GradleCoordinate found = SdkMavenRepository.getCoordinateFromSdkPath(sdkPackage.getPath());
      if (found != null) {
        return found.getRevision();
      }
    }

    // Perform network lookup to resolve current best version, if possible
    if (project != null) {
      LintClient client = new LintIdeClient(project);
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
   * Resolves multiple dynamic dependencies on artifacts distributed in the SDK.
   *
   * <p>This method doesn't check any remote repositories, just the already downloaded SDK "extras" repositories.
   */
  public List<GradleCoordinate> resolveDynamicSdkDependencies(@NotNull Multimap<String, GradleCoordinate> dependencies,
                                                              @Nullable String supportLibVersionFilter,
                                                              @NotNull AndroidSdkData sdk,
                                                              @NotNull FileOp fileOp) {
    List<GradleCoordinate> result = Lists.newArrayListWithCapacity(dependencies.size());
    String supportFilter = findExistingExplicitVersion(dependencies.values());
    if (supportFilter != null) {
      supportLibVersionFilter = supportFilter;
    }

    for (String key : dependencies.keySet()) {
      GradleCoordinate highest = Collections.max(dependencies.get(key), COMPARE_PLUS_LOWER);
      if (highest.getGroupId() == null || highest.getArtifactId() == null) {
        return null;
      }

      // For test consistency, don't depend on installed SDK state while testing
      if (myForceRepositoryChecksInTests || !ApplicationManager.getApplication().isUnitTestMode()) {
        // If this coordinate points to an artifact in one of our repositories, check to see if there is a static version
        // that we can add instead of a plus revision.
        String filter = highest.getRevision();
        if (filter.endsWith("+")) {
          filter = filter.length() > 1 ? filter.substring(0, filter.length() - 1) : null;
          boolean includePreviews = false;
          if (filter == null && ImportModule.SUPPORT_GROUP_ID.equals(highest.getGroupId())) {
            filter = supportLibVersionFilter;
            includePreviews = true;
          }
          String version =
            getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), filter, includePreviews, sdk.getLocation(), fileOp);
          if (version == null && filter != null) {
            // No library found at the support lib version filter level, so look for any match
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), null, includePreviews, sdk.getLocation(), fileOp);
          }
          if (version == null && !includePreviews) {
            // Still no library found, check preview versions
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), null, true, sdk.getLocation(), fileOp);
          }
          if (version != null) {
            String libraryCoordinate = highest.getId() + ":" + version;
            GradleCoordinate available = GradleCoordinate.parseCoordinateString(libraryCoordinate);
            if (available != null) {
              File archiveFile = getArchiveForCoordinate(available, sdk.getLocation(), fileOp);
              if (((archiveFile != null && fileOp.exists(archiveFile))
                   // Not a known library hardcoded in RepositoryUrlManager?
                   || SupportLibrary.forGradleCoordinate(available) == null)
                  && COMPARE_PLUS_LOWER.compare(available, highest) >= 0) {
                highest = available;
              }
            }
          }
        }
      }
      result.add(highest);
    }
    return result;
  }

  private static String findExistingExplicitVersion(@NotNull Collection<GradleCoordinate> dependencies) {
    Optional<GradleCoordinate> highest = dependencies.stream()
      .filter(coordinate -> ImportModule.SUPPORT_GROUP_ID.equals(coordinate.getGroupId()))
      .max(COMPARE_PLUS_LOWER);
    if (!highest.isPresent()) {
      return null;
    }
    String version = highest.get().getRevision();
    if (version.endsWith("+")) {
      return version.length() > 1 ? version.substring(0, version.length() - 1) : null;
    }
    return version;
  }
}
