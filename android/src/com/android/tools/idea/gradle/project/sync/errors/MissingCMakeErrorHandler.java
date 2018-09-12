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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallCMakeHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.FD_CMAKE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingCMakeErrorHandler extends BaseSyncErrorHandler {

  // Extended version of the "Revision" class with orAbove semantics.
  static class RevisionOrHigher {
    // The numerical revision requested to be installed.
    @NotNull public final Revision revision;
    // If any version above the requested revision can satisfy the request.
    public final boolean orHigher;

    public RevisionOrHigher(@NotNull Revision revision, boolean orHigher) {
      this.revision = revision;
      this.orHigher = orHigher;
    }
  }

  // The default CMake version (sdk-internal) and the equivalent version used when it is reported to the user.
  static private final String FORK_CMAKE_SDK_VERSION = "3.6.4111459";
  static private final String FORK_CMAKE_REPORTED_VERSION = "3.6.0";

  // Parsed default cmake version (sdk-internal and user-facing).
  static private final Revision ourForkCmakeSdkVersion = Revision.parseRevision(FORK_CMAKE_SDK_VERSION);
  static private final Revision ourForkCmakeReportedVersion = Revision.parseRevision(FORK_CMAKE_REPORTED_VERSION);

  @Nullable
  @Override
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String message = rootCause.getMessage();
    if (isNotEmpty(message)) {
      String firstLine = getFirstLineMessage(message);
      if (firstLine.startsWith("Failed to find CMake.") || firstLine.startsWith("Unable to get the CMake version")) {
        updateUsageTracker();
        return "Failed to find CMake.";
      }
      else if (matchesCannotFindCmake(firstLine) || matchesTriedInstall(message)) {
        updateUsageTracker();
        return message;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    String firstLine = getFirstLineMessage(text);
    if (matchesCannotFindCmake(firstLine)) {
      // The user requested a specific version of CMake (or anything above it).
      RevisionOrHigher requestedCmake = extractCmakeVersionFromError(firstLine);
      if (requestedCmake == null) {
        // Cannot find the CMake version in the error string.
        return Collections.emptyList();
      }

      RepoManager sdkManager = getSdkManager();
      Collection<RemotePackage> remoteCmakePackages = sdkManager.getPackages().getRemotePackagesForPrefix(FD_CMAKE);
      Revision foundCmakeVersion = findBestMatch(remoteCmakePackages, requestedCmake);
      if (foundCmakeVersion == null) {
        // No CMake versions in the SDK satisfied the request. Adding a hyperlink is useless as it
        // will only fail to install the package.
        return Collections.emptyList();
      }

      Collection<LocalPackage> localCmakePackages = sdkManager.getPackages().getLocalPackagesForPrefix(FD_CMAKE);
      if (isAlreadyInstalled(localCmakePackages, foundCmakeVersion)) {
        // Failed sanity check. The package is already installed.
        return Collections.emptyList();
      }

      // Version-specific install of CMake.
      return Collections.singletonList(new InstallCMakeHyperlink(foundCmakeVersion));
    }
    else {
      // Generic install of CMake.
      return Collections.singletonList(new InstallCMakeHyperlink());
    }
  }

  /**
   * @param firstLine the first line of the error message returned by gradle sync.
   * @return true if input looks like it is an error about not finding a particular version of CMake.
   **/
  private static boolean matchesCannotFindCmake(@NotNull String firstLine) {
    return firstLine.startsWith("CMake") && firstLine.contains("was not found in PATH or by cmake.dir property");
  }

  /**
   * @param errorMessage the error message
   * @return whether the given error message was generated by the Android Gradle Plugin failing to download the CMake package.
   */
  private static boolean matchesTriedInstall(@NotNull String errorMessage) {
    return (errorMessage.startsWith("Failed to install the following Android SDK packages as some licences have not been accepted.") ||
            errorMessage.startsWith("Failed to install the following SDK components:")) &&
           (errorMessage.contains("CMake") || errorMessage.contains("cmake"));
  }

  /**
   * @param firstLine The line inside which the cmake version will be searched.
   * @return The cmake version included in the error message, null if it's not found or cannot be parsed as a valid revision.
   **/
  @Nullable
  static RevisionOrHigher extractCmakeVersionFromError(@NotNull String firstLine) {
    int startPos = firstLine.indexOf('\'');
    if (startPos == -1) {
      return null;
    }

    int endPos = firstLine.indexOf('\'', startPos + 1);
    if (endPos == -1) {
      return null;
    }

    String version = firstLine.substring(startPos + 1, endPos);
    try {
      return new RevisionOrHigher(
        Revision.parseRevision(version),
        firstLine.contains("'" + version + "' or higher"));
    }
    catch (NumberFormatException e) {
      // Cannot parse version string.
      return null;
    }
  }

  /**
   * Finds whether the requested cmake version can be installed from the SDK.
   *
   * @param cmakePackages  Remote CMake packages available in the SDK.
   * @param requestedCmake The CMake version requested by the user.
   * @return The version that best matches the requested version, null if no match was found.
   */
  @Nullable
  static Revision findBestMatch(@NotNull Collection<RemotePackage> cmakePackages, @NotNull RevisionOrHigher requestedCmake) {
    Revision foundVersion = null;
    for (RemotePackage remotePackage : cmakePackages) {
      Revision remoteCmake = remotePackage.getVersion();

      // If the version in the remote package is the fork version, we use its user friendly equivalent.
      if (remoteCmake.equals(ourForkCmakeSdkVersion)) {
        remoteCmake = ourForkCmakeReportedVersion;
      }

      if (!versionSatisfies(remoteCmake, requestedCmake)) {
        continue;
      }

      if (foundVersion == null) {
        foundVersion = remoteCmake;
        continue;
      }

      if (remoteCmake.compareTo(foundVersion, Revision.PreviewComparison.IGNORE) > 0) {
        // Among all matching Cmake versions, use the highest version one (ignore preview version).
        foundVersion = remoteCmake;
        continue;
      }
    }

    return foundVersion;
  }

  /**
   * @param candidateCmake the cmake version that is available in the SDK.
   * @param requestedCmake the cmake version (or the minimum cmake version) that we are looking for.
   * @return true if the version represented by candidateCmake is a good match for the version represented by requestedCmake. The preview
   * version (i.e., 4th component) is always ignored when performing the matching.
   */
  static boolean versionSatisfies(@NotNull Revision candidateCmake, @NotNull RevisionOrHigher requestedCmake) {
    int result = candidateCmake.compareTo(requestedCmake.revision, Revision.PreviewComparison.IGNORE);
    return (result == 0) || (requestedCmake.orHigher && result >= 0);
  }

  /**
   * @param cmakePackages local CMake installations available in the SDK.
   * @param cmakeVersion  the cmake version that we are looking for.
   * @return true if a package with the given version exists in cmakePackages.
   */
  private static boolean isAlreadyInstalled(@NotNull Collection<LocalPackage> cmakePackages, @NotNull Revision cmakeVersion) {
    for (LocalPackage localCmakePackage : cmakePackages) {
      if (localCmakePackage.getVersion().equals(cmakeVersion)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return The currently available SDK manager.
   */
  @NotNull
  protected RepoManager getSdkManager() {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    StudioLoggerProgressIndicator progressIndicator = new StudioLoggerProgressIndicator(getClass());
    return sdkHandler.getSdkManager(progressIndicator);
  }
}
