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

import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_LOWER;

import com.android.ide.common.repository.GoogleMavenRepository;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleCoordinate.ArtifactType;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.api.RemotePackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.eclipse.ImportModule;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleLocalCache;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.LintClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to aid in generating Maven URLs for various internal repository files (Support Library, AppCompat, etc).
 */
public class RepositoryUrlManager {
  /**
   * Constant full revision for "anything available"
   */
  public static final String REVISION_ANY = "+";

  private final boolean myForceRepositoryChecksInTests;
  private final Set<String> myPendingNetworkRequests = ConcurrentHashMap.newKeySet();
  private final GoogleMavenRepository myGoogleMavenRepository;
  private final GoogleMavenRepository myCachedGoogleMavenRepository;

  public static RepositoryUrlManager get() {
    return ServiceManager.getService(RepositoryUrlManager.class);
  }

  @SuppressWarnings("unused") // registered as service
  RepositoryUrlManager() {
    this(IdeGoogleMavenRepository.INSTANCE, OfflineIdeGoogleMavenRepository.INSTANCE, false);
  }

  @VisibleForTesting
  public RepositoryUrlManager(GoogleMavenRepository repository, GoogleMavenRepository localRepository, boolean forceRepositoryChecks) {
    myForceRepositoryChecksInTests = forceRepositoryChecks;
    myGoogleMavenRepository = repository;
    myCachedGoogleMavenRepository = localRepository;
  }

  @Nullable
  public String getArtifactStringCoordinate(GoogleMavenArtifactId artifactId, boolean preview) {
    return getArtifactStringCoordinate(artifactId, null, preview);
  }

  @Nullable
  public String getArtifactStringCoordinate(GoogleMavenArtifactId artifactId,
                                            @Nullable Predicate<GradleVersion> filter,
                                            boolean preview) {
    String revision = getLibraryRevision(artifactId.getMavenGroupId(),
                                         artifactId.getMavenArtifactId(),
                                         filter,
                                         preview,
                                         FileOpUtils.create());
    if (revision == null) {
      return null;
    }

    return artifactId.getCoordinate(revision).toString();
  }

  /**
   * Returns the string for the specific version number of the most recent version of the given library
   * (matching the given prefix filter, if any) in one of the Sdk repositories.
   *
   * @param groupId         the group id
   * @param artifactId      the artifact id
   * @param filter          the optional filter constraining acceptable versions
   * @param includePreviews whether to include preview versions of libraries
   * @return
   */
  @Nullable
  public String getLibraryRevision(@NotNull String groupId,
                                   @NotNull String artifactId,
                                   @Nullable Predicate<GradleVersion> filter,
                                   boolean includePreviews,
                                   @NotNull FileOp fileOp) {
    GradleVersion version = findVersion(groupId, artifactId, filter, includePreviews, fileOp);
    return version == null ? null : version.toString();
  }


  /**
   * Returns the string for the specific version number of the most recent version of the given library
   * (matching the given prefix filter, if any)
   *
   * @param groupId         the group id
   * @param artifactId      the artifact id
   * @param filter          the optional filter constraining acceptable versions
   * @param includePreviews whether to include preview versions of libraries
   */
  @Nullable
  public GradleVersion findVersion(@NotNull String groupId,
                                   @NotNull String artifactId,
                                   @Nullable Predicate<GradleVersion> filter,
                                   boolean includePreviews,
                                   @NotNull FileOp fileOp) {
    GradleVersion version;
    // First check the Google maven repository, which has most versions.
    if (ApplicationManager.getApplication().isDispatchThread()) {
      version = myCachedGoogleMavenRepository.findVersion(groupId, artifactId, filter, includePreviews);
      refreshCacheInBackground(groupId, artifactId);
    }
    else {
      version = myGoogleMavenRepository.findVersion(groupId, artifactId, filter, includePreviews);
    }

    if (version != null) {
      return version;
    }

    // Try the repo embedded in AS.
    List<File> paths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    for (File path : paths) {
      if (path != null && path.isDirectory()) {
        GradleCoordinate versionInEmbedded = MavenRepositories.getHighestInstalledVersion(groupId,
                                                                                          artifactId,
                                                                                          path,
                                                                                          filter,
                                                                                          includePreviews,
                                                                                          fileOp);
        if (versionInEmbedded != null) {
          return versionInEmbedded.getVersion();
        }
      }
    }
    return null;
  }

  @NotNull
  public List<GradleCoordinate> findCompileDependencies(@NotNull String groupId,
                                                        @NotNull String artifactId,
                                                        @NotNull GradleVersion version) {
    // First check the Google maven repository, which has most versions.
    List<GradleCoordinate> result;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      result = myCachedGoogleMavenRepository.findCompileDependencies(groupId, artifactId, version);
      refreshCacheInBackground(groupId, artifactId);
    }
    else {
      result = myGoogleMavenRepository.findCompileDependencies(groupId, artifactId, version);
    }
    return result;
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

    String groupId = gradleCoordinate.getGroupId();
    String artifactId = gradleCoordinate.getArtifactId();
    SdkMavenRepository repository = SdkMavenRepository.find(sdkLocation, groupId, artifactId, fileOp);
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
                              String.format("%s-%s.%s", artifactId, gradleCoordinate.getRevision(), artifactType.toString()));

      if (fileOp.isFile(archive)) {
        return archive;
      }
    }

    return null;
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
   * @param coordinate the coordinate whose version we want to resolve
   * @param project    the current project, if known. This is required if you want to
   *                   perform a network lookup of the current best version if we can't
   *                   find a locally cached version of the library
   * @return the resolved coordinate, or null if not successful
   */
  @Nullable
  public GradleCoordinate resolveDynamicCoordinate(@NotNull GradleCoordinate coordinate,
                                                   @Nullable Project project,
                                                   @NotNull AndroidSdkHandler sdkHandler) {
    String version = resolveDynamicCoordinateVersion(coordinate, project, sdkHandler);
    if (version != null) {
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
   * @param project    the current project, if known. This is required if you want to
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
    String revision = coordinate.getRevision();
    if (!revision.endsWith(REVISION_ANY)) {
      // Already resolved. That was easy.
      return revision;
    }

    String versionPrefix = revision.substring(0, revision.length() - 1);
    Predicate<GradleVersion> filter = version -> version.toString().startsWith(versionPrefix);
    String groupId = coordinate.getGroupId();
    String artifactId = coordinate.getArtifactId();

    GoogleMavenRepository bestAvailableGoogleMavenRepo;

    // First check the Google maven repository, which has most versions.
    if (ApplicationManager.getApplication().isDispatchThread()) {
      bestAvailableGoogleMavenRepo = myCachedGoogleMavenRepository;
      refreshCacheInBackground(groupId, artifactId);
    } else {
      bestAvailableGoogleMavenRepo = myGoogleMavenRepository;
    }

    // First check the Google maven repository, which has most versions
    GradleVersion stable = bestAvailableGoogleMavenRepo.findVersion(groupId, artifactId, filter, false);
    if (stable != null) {
      return stable.toString();
    }
    GradleVersion version = bestAvailableGoogleMavenRepo.findVersion(groupId, artifactId, filter, true);
    if (version != null) {
      // Only had preview version; use that (for example, artifacts that haven't been released as stable yet).
      return version.toString();
    }

    File sdkLocation = sdkHandler.getLocation();
    if (sdkLocation != null) {
      // If this coordinate points to an artifact in one of our repositories, mark it with a comment if they don't
      // have that repository available.
      String libraryCoordinate = getLibraryRevision(groupId, artifactId, filter, false, sdkHandler.getFileOp());
      if (libraryCoordinate != null) {
        return libraryCoordinate;
      }

      // If that didn't yield any matches, try again, this time allowing preview platforms.
      // This is necessary if the artifact prefix includes enough of a version where there are
      // only preview matches.
      libraryCoordinate = getLibraryRevision(groupId, artifactId, filter, true, sdkHandler.getFileOp());
      if (libraryCoordinate != null) {
        return libraryCoordinate;
      }
    }

    // Regular Gradle dependency? Look in Gradle cache.
    GradleVersion versionFound = GradleLocalCache.getInstance().findLatestArtifactVersion(coordinate, project, revision);
    if (versionFound != null) {
      return versionFound.toString();
    }

    // Maybe it's available for download as an SDK component.
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    RemotePackage sdkPackage = SdkMavenRepository.findLatestRemoteVersion(coordinate, sdkHandler, filter, progress);
    if (sdkPackage != null) {
      GradleCoordinate found = SdkMavenRepository.getCoordinateFromSdkPath(sdkPackage.getPath());
      if (found != null) {
        return found.getRevision();
      }
    }

    // Perform network lookup to resolve current best version, if possible.
    if (project != null) {
      LintClient client = new LintIdeClient(project);
      GradleVersion latest = GradleDetector.getLatestVersionFromRemoteRepo(client, coordinate, filter, coordinate.isPreview());
      if (latest != null) {
        String latestString = latest.toString();
        if (latestString.startsWith(versionPrefix)) {
          return latestString;
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
                                                              @NotNull FileOp fileOp) {
    List<GradleCoordinate> result = new ArrayList<>(dependencies.size());
    String supportFilter = findExistingExplicitVersion(dependencies.values());
    if (supportFilter == null) {
      supportFilter = supportLibVersionFilter;
    }

    for (String key : dependencies.keySet()) {
      GradleCoordinate highest = Collections.max(dependencies.get(key), COMPARE_PLUS_LOWER);

      // For test consistency, don't depend on installed SDK state while testing
      if (myForceRepositoryChecksInTests || !ApplicationManager.getApplication().isUnitTestMode()) {
        // If this coordinate points to an artifact in one of our repositories, check to see if there is a static version
        // that we can add instead of a plus revision.
        String revision = highest.getRevision();
        if (revision.endsWith(REVISION_ANY)) {
          revision = revision.length() > 1 ? revision.substring(0, revision.length() - 1) : null;
          if (ImportModule.SUPPORT_GROUP_ID.equals(highest.getGroupId()) ||
              ImportModule.CORE_KTX_GROUP_ID.equals(highest.getGroupId())) {
            if (revision == null) {
              revision = supportFilter;
            }
          }
          String prefix = revision;
          Predicate<GradleVersion> filter = prefix != null ? version -> version.toString().startsWith(prefix) : null;

          String version = null;
          // 1 - Latest specific (ie support lib version filter level) stable version
          if (filter != null) {
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), filter, false, fileOp);
          }
          // 2 - Latest specific (ie support lib version filter level) preview version
          if (version == null && filter != null) {
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), filter, true, fileOp);
          }
          // 3 - Latest stable version
          if (version == null) {
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), null, false, fileOp);
          }
          // 4 - Latest preview version
          if (version == null) {
            version = getLibraryRevision(highest.getGroupId(), highest.getArtifactId(), null, true, fileOp);
          }
          // 5 - No version found
          if (version != null) {
            String libraryCoordinate = highest.getId() + ":" + version;
            GradleCoordinate available = GradleCoordinate.parseCoordinateString(libraryCoordinate);
            if (available != null && COMPARE_PLUS_LOWER.compare(available, highest) >= 0) {
              highest = available;
            }
          }
        }
      }
      result.add(highest);
    }
    return result;
  }

  @Nullable
  public Predicate<GradleVersion> findExistingSupportVersionFilter(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    GradleVersion highest = null;
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    for (GoogleMavenArtifactId artifactId : GoogleMavenArtifactId.values()) {
      // Note: Only the old style support library have version dependencies, so explicitly check the group ID:
      if (artifactId.isPlatformSupportLibrary() && artifactId.getMavenGroupId().equals(ImportModule.SUPPORT_GROUP_ID)) {
        GradleCoordinate coordinate = moduleSystem.getResolvedDependency(artifactId.getCoordinate(REVISION_ANY));
        GradleVersion version = coordinate != null ? coordinate.getVersion() : null;
        if (version != null) {
          if (highest == null || version.compareTo(highest) > 0) {
            highest = version;
          }
        }
      }
    }
    if (highest == null) {
      AndroidModuleInfo info = AndroidModuleInfo.getInstance(module);
      AndroidVersion compileSdkVersion = info != null ? info.getBuildSdkVersion() : null;
      if (compileSdkVersion == null) {
        return null;
      }
      String prefix = compileSdkVersion.getApiLevel() + ".";
      return version -> version.toString().startsWith(prefix);
    }
    GradleVersion found = highest;
    String raw = highest.toString();
    if (highest.isPreview() || highest.isSnapshot() || !raw.endsWith(REVISION_ANY)) {
      return version -> version.equals(found);
    }
    String prefix = raw.substring(0, raw.length() - 1);
    return version -> version.toString().startsWith(prefix);
  }


  @Nullable
  public GradleVersion findHighestAndroidxSupportVersion(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    // Take from the resolved versions
    GradleVersion highest = null;
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    for (GoogleMavenArtifactId artifactId : GoogleMavenArtifactId.values()) {
      // Only consider Androidx
      if (artifactId.isAndroidxPlatformLibrary()) {
        GradleCoordinate coordinate = moduleSystem.getResolvedDependency(artifactId.getCoordinate(REVISION_ANY));
        GradleVersion version = coordinate != null ? coordinate.getVersion() : null;
        if (version != null) {
          if (highest == null || version.compareTo(highest) > 0) {
            highest = version;
          }
        }
      }
    }
    return highest;
  }

  @Nullable
  public Predicate<GradleVersion> findExistingAndroidxSupportVersionFilter(@Nullable GradleVersion highest) {
    if (highest == null) {
      return null;
    }
    String raw = highest.toString();
    if (highest.isPreview() || highest.isSnapshot() || !raw.endsWith(REVISION_ANY)) {
      return version -> version.equals(highest);
    }
    String prefix = raw.substring(0, raw.length() - 1);
    return version -> version.toString().startsWith(prefix);
  }

  private void refreshCacheInBackground(@NotNull String groupId, @NotNull String artifactId) {
    String searchKey = String.format("%s:%s", groupId, artifactId);
    if (myPendingNetworkRequests.add(searchKey)) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          // We don't care about the result, just the side effect of updating the cache.
          // This will only make a network request if there is no cache or it has expired.
          myGoogleMavenRepository.findVersion(groupId, artifactId, (Predicate<GradleVersion>)null, true);
        }
        finally {
          myPendingNetworkRequests.remove(searchKey);
        }
      });
    }
  }

  @Nullable
  private static String findExistingExplicitVersion(@NotNull Collection<GradleCoordinate> dependencies) {
    Optional<GradleCoordinate> highest = dependencies.stream()
      .filter(coordinate -> ImportModule.SUPPORT_GROUP_ID.equals(coordinate.getGroupId()))
      .max(COMPARE_PLUS_LOWER);
    if (!highest.isPresent()) {
      return null;
    }
    String version = highest.get().getRevision();
    if (version.endsWith(REVISION_ANY)) {
      return version.length() > 1 ? version.substring(0, version.length() - 1) : null;
    }
    return version;
  }
}
