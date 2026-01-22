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
  private final ParsedManifest mergedManifest;
  @Nullable private final ParsedManifest testTargetMergedManifest;
  private final ImmutableList<File> apksToDeploy;
  private final ImmutableList<File> symbolFiles;
  private final ImmutableList<ApkInfo> apkInfos;

  /**
   * Note: Not every deployment has a test target, so {@param testTargetMergedManifest} can be null.
   */
  private BlazeAndroidDeployInfo(
    ParsedManifest mergedManifest,
    @Nullable ParsedManifest testTargetMergedManifest,
    List<? extends File> apksToDeploy,
    List<? extends File> symbolFiles,
    List<? extends ApkInfo> apkInfos) {
    this.mergedManifest = mergedManifest;
    this.testTargetMergedManifest = testTargetMergedManifest;
    this.apksToDeploy = ImmutableList.copyOf(apksToDeploy);
    this.symbolFiles = ImmutableList.copyOf(symbolFiles);
    this.apkInfos = ImmutableList.copyOf(apkInfos);
  }

  public record ManifestWithApks(ParsedManifest manifest, List<? extends File> apks){}

  public static BlazeAndroidDeployInfo createBlazeAndroidDeployInfo(
    ManifestWithApks mergedManifestAndApks,
    @Nullable ManifestWithApks testTargetMergedManifestAndApks,
    List<? extends File> symbolFiles) throws ApkProvisionException {

    ImmutableList.Builder<ApkInfo> apkInfoBuilder = ImmutableList.<ApkInfo>builder();
    apkInfoBuilder.addAll(getInfos(mergedManifestAndApks));
    if (testTargetMergedManifestAndApks != null) {
      apkInfoBuilder.addAll(getInfos(testTargetMergedManifestAndApks));
    }

    ImmutableList<ApkInfo> apkInfos = apkInfoBuilder.build();

    var mainManifest = mergedManifestAndApks.manifest();
    var targetMergedManifest = testTargetMergedManifestAndApks != null ? testTargetMergedManifestAndApks.manifest() : null;
    var apksToDeploy = ImmutableList.copyOf(Iterables.concat(mergedManifestAndApks.apks(), testTargetMergedManifestAndApks != null
                                                                                           ? testTargetMergedManifestAndApks.apks()
                                                                                           : emptyList()));
    return new BlazeAndroidDeployInfo(mainManifest, targetMergedManifest, apksToDeploy, symbolFiles, apkInfos);
  }

  /**
   * Returns parsed manifest of the main target for this deployment. During normal app deployment,
   * the main target is the android_binary that builds the app itself. During instrumentation tests
   * the main target is the android_binary/android_test target responsible for instrumenting the
   * app, while the merged manifest of the app under test can be obtained through {@link
   * BlazeAndroidDeployInfo#getTestTargetMergedManifest()}.
   */
  public ManifestParser.ParsedManifest getMergedManifest() {
    return mergedManifest;
  }

  /**
   * Returns the primary application ID for the app being launched (either an android_binary app or
   * a test instrumentation app).
   */
  @NotNull
  public String getMainAppPackageName() throws ApkProvisionException {
    if (mergedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in merged manifest.");
    }
    return mergedManifest.packageName;
  }

  /**
   * Returns the application ID of the app under test for instrumentation tests.
   *
   * <p>If {@link BlazeAndroidDeployInfo#getTestTargetMergedManifest()} is null (i.e., the
   * test app is testing itself), this falls back to {@link
   * BlazeAndroidDeployInfo#getMainAppPackageName()}.
   */
  @Nullable
  public String getAppUnderTestPackageName() throws ApkProvisionException {
    if (testTargetMergedManifest == null) {
      return null;
    }
    if (testTargetMergedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in merged manifest.");
    }
    return testTargetMergedManifest.packageName;
  }

  /**
   * Returns parsed manifest of the app under test during an instrumentation test. This method
   * returns null in all other scenarios.
   */
  @Nullable
  public ManifestParser.ParsedManifest getTestTargetMergedManifest() {
    return testTargetMergedManifest;
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
