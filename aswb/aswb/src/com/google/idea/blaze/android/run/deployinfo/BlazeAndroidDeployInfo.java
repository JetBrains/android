/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.deployinfo;

import static java.util.Collections.emptyList;

import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Info about the deployment phase. */
public class BlazeAndroidDeployInfo {
  private final ParsedManifest mainAppMergedManifest;
  @Nullable private final ParsedManifest appUnderTestMergedManifest;
  private final ImmutableList<File> apksToDeploy;
  private final ImmutableList<File> symbolFiles;
  private final ImmutableList<ApkInfo> apkInfos;

  /**
   * Note: Not every deployment has a test target, so {@param testTargetMergedManifest} can be null.
   */
  private BlazeAndroidDeployInfo(
    ParsedManifest mainAppMergedManifest,
    @Nullable ParsedManifest appUnderTestMergedManifest,
    List<? extends File> apksToDeploy,
    List<? extends File> symbolFiles,
    List<? extends ApkInfo> apkInfos) {
    this.mainAppMergedManifest = mainAppMergedManifest;
    this.appUnderTestMergedManifest = appUnderTestMergedManifest;
    this.apksToDeploy = ImmutableList.copyOf(apksToDeploy);
    this.symbolFiles = ImmutableList.copyOf(symbolFiles);
    this.apkInfos = ImmutableList.copyOf(apkInfos);
  }

  public record ManifestWithApks(ParsedManifest manifest, List<? extends File> apks){}

  public static BlazeAndroidDeployInfo createBlazeAndroidDeployInfo(
    ManifestWithApks mainAppManifestAndApks,
    @Nullable ManifestWithApks appUnderTestManifestAndApks,
    List<? extends File> symbolFiles) throws ApkProvisionException {

    ImmutableList.Builder<ApkInfo> apkInfoBuilder = ImmutableList.<ApkInfo>builder();
    apkInfoBuilder.addAll(getInfos(mainAppManifestAndApks));
    if (appUnderTestManifestAndApks != null) {
      apkInfoBuilder.addAll(getInfos(appUnderTestManifestAndApks));
    }

    ImmutableList<ApkInfo> apkInfos = apkInfoBuilder.build();

    var mainAppManifest = mainAppManifestAndApks.manifest();
    var appUnderTestManifest = appUnderTestManifestAndApks != null ? appUnderTestManifestAndApks.manifest() : null;
    var apksToDeploy = ImmutableList.copyOf(Iterables.concat(mainAppManifestAndApks.apks(), appUnderTestManifestAndApks != null
                                                                                            ? appUnderTestManifestAndApks.apks()
                                                                                            : emptyList()));
    return new BlazeAndroidDeployInfo(mainAppManifest, appUnderTestManifest, apksToDeploy, symbolFiles, apkInfos);
  }

  /**
   * Returns parsed manifest of the main target for this deployment. During normal app deployment,
   * the main target is the android_binary that builds the app itself. During instrumentation tests
   * the main target is the android_binary/android_test target responsible for instrumenting the
   * app, while the merged manifest of the app under test can be obtained through {@link
   * BlazeAndroidDeployInfo#getAppUnderTestMergedManifest()}.
   */
  public ManifestParser.ParsedManifest getMainAppMergedManifest() {
    return mainAppMergedManifest;
  }

  /**
   * Returns the primary application ID for the app being launched (either an android_binary app or
   * a test instrumentation app).
   */
  @NotNull
  public String getMainAppPackageName() throws ApkProvisionException {
    if (mainAppMergedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in merged manifest.");
    }
    return mainAppMergedManifest.packageName;
  }

  /**
   * Returns the application ID of the app under test for instrumentation tests.
   *
   * <p>If {@link BlazeAndroidDeployInfo#getAppUnderTestMergedManifest()} is null (i.e., the
   * test app is testing itself), this falls back to {@link
   * BlazeAndroidDeployInfo#getMainAppPackageName()}.
   */
  @Nullable
  public String getAppUnderTestPackageName() throws ApkProvisionException {
    if (appUnderTestMergedManifest == null) {
      return null;
    }
    if (appUnderTestMergedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in merged manifest.");
    }
    return appUnderTestMergedManifest.packageName;
  }

  /**
   * Returns parsed manifest of the app under test during an instrumentation test. This method
   * returns null in all other scenarios.
   */
  @Nullable
  public ManifestParser.ParsedManifest getAppUnderTestMergedManifest() {
    return appUnderTestMergedManifest;
  }

  /** Returns the full list of apks to deploy, if any. */
  public ImmutableList<File> getApksToDeploy() {
    return apksToDeploy;
  }

  /**
   * Returns a list of {@link ApkInfo}s to deploy. This includes the main apk and any split apks.
   */
  public ImmutableList<ApkInfo> getApkInfos() {
    return apkInfos;
  }

  private static ImmutableList<ApkInfo> getInfos(ManifestWithApks apks) throws ApkProvisionException {
    var packageName = apks.manifest().packageName;
    if (packageName == null) {
      throw new ApkProvisionException("No application id in merged manifest.");
    }
    ImmutableList.Builder<ApkInfo> apkInfos = ImmutableList.builder();
    for (File apk : apks.apks()) {
      apkInfos.add(new ApkInfo(apk, packageName));
    }
    return apkInfos.build();
  }


  /** Returns the full list of C++ symbol files to provide to LLDB to symbolize debugging. */
  public ImmutableList<File> getSymbolFiles() {
    return symbolFiles;
  }
}
