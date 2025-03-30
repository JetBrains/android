// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.test.testutils;

import com.intellij.openapi.util.SystemInfo;
import java.net.URI;
import java.nio.file.Path;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly;

@SuppressWarnings("UnstableApiUsage")
public class AndroidSdkDownloader {
  private static final String ANDROID_SDK_VERSION = "30.3.0.0";

  public static Path downloadSdk(BuildDependenciesCommunityRoot communityRoot) {
    Path androidSdkRoot = communityRoot.communityRoot.resolve("build/dependencies/build/android-sdk");

    //noinspection SpellCheckingInspection
    BuildDependenciesDownloader.INSTANCE.extractFile(
      downloadAndroidSdk(communityRoot),
      androidSdkRoot.resolve("prebuilts/studio/sdk"),
      communityRoot
    );

    return androidSdkRoot;
  }

  // debug only
  public static void main(String[] args) {
    Path root = downloadSdk(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory());
    System.out.println("Sdk is at " + root);
  }

  private static Path downloadAndroidSdk(BuildDependenciesCommunityRoot communityRoot) {
    URI uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
      "org.jetbrains.intellij.deps.android",
      "android-sdk",
      getOsPrefix() + "." + ANDROID_SDK_VERSION,
      "tar.gz"
    );

    return BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, uri);
  }

  private static String getOsPrefix() {
    if (SystemInfo.isWindows) {
      return "windows";
    }

    if (SystemInfo.isLinux) {
      return "linux";
    }

    if (SystemInfo.isMac) {
      return "darwin";
    }

    throw new IllegalStateException("Unsupported operating system");
  }
}
