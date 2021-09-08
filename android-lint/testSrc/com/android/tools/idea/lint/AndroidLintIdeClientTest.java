/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.lint;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.lint.common.LintIgnoredResult;
import com.android.tools.idea.lint.common.LintResult;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.PlatformLookupKt;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;

public class AndroidLintIdeClientTest extends TestCase {
  public void testFindCompilationTarget() {
    MockFileOp fop = new MockFileOp();
    LocalPackage platformPackage = getLocalPlatformPackage(fop, "23", 23);
    LocalPackage previewPlatform = getLocalPlatformPackage(fop, "O", 26);
    LocalPackage addOnPlatform =
      getLocalAddOnPackage(
        fop, "google_apis", "Google APIs", "google", "Google Inc.", 23);

    RepositoryPackages packages = new RepositoryPackages();
    packages.setLocalPkgInfos(
      ImmutableList.of(platformPackage, previewPlatform, addOnPlatform));
    RepoManager mgr = new FakeRepoManager(null, packages);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(null, null, fop, mgr);
    com.intellij.openapi.project.Project ideaProject = mock(com.intellij.openapi.project.Project.class);
    LintResult result = new LintIgnoredResult();
    LintClient client = new AndroidLintIdeClient(ideaProject, result) {
      @Override
      public AndroidSdkHandler getSdk() {
        return sdkHandler;
      }
    };
    Project platformProject = mock(Project.class);
    when(platformProject.getBuildTargetHash()).thenReturn("android-23");
    when(platformProject.isAndroidProject()).thenReturn(true);
    Project previewProject = mock(Project.class);
    when(previewProject.getBuildTargetHash()).thenReturn("android-O");
    Project addOnProject = mock(Project.class);
    when(addOnProject.getBuildTargetHash()).thenReturn("Google Inc.:Google APIs:23");

    IAndroidTarget platformTarget = client.getCompileTarget(platformProject);
    assertThat(platformTarget).isNotNull();
    assertEquals("android-23", AndroidTargetHash.getTargetHashString(platformTarget));

    when(previewProject.isAndroidProject()).thenReturn(true);
    IAndroidTarget previewTarget = client.getCompileTarget(previewProject);
    assertThat(previewTarget).isNotNull();
    assertEquals("android-O", AndroidTargetHash.getTargetHashString(previewTarget));

    if (PlatformLookupKt.SUPPORTS_ADD_ONS) {
      when(addOnProject.isAndroidProject()).thenReturn(true);
      IAndroidTarget addOnTarget = client.getCompileTarget(addOnProject);
      assertThat(addOnTarget).isNotNull();
      assertEquals(
        "Google Inc.:Google APIs:23",
        AndroidTargetHash.getTargetHashString(addOnTarget));
    }

    // Don't return IAndroidTargets for non-Android projects
    when(previewProject.isAndroidProject()).thenReturn(false);
    previewTarget = client.getCompileTarget(previewProject);
    assertThat(previewTarget).isNull();
  }

  @NonNull
  private static LocalPackage getLocalPlatformPackage(MockFileOp fop, String version, int api) {
    fop.recordExistingFile("/sdk/platforms/android-" + version + "/build.prop", "");
    FakePackage.FakeLocalPackage local =
      new FakePackage.FakeLocalPackage("platforms;android-" + version, fop);

    DetailsTypes.PlatformDetailsType platformDetails =
      AndroidSdkHandler.getRepositoryModule()
        .createLatestFactory()
        .createPlatformDetailsType();
    platformDetails.setApiLevel(api);
    if (!Character.isDigit(version.charAt(0))) {
      platformDetails.setCodename(version);
    }
    local.setTypeDetails((TypeDetails) platformDetails);
    return local;
  }

  @SuppressWarnings("SameParameterValue")
  @NonNull
  private static LocalPackage getLocalAddOnPackage(
    MockFileOp fop,
    String tag,
    String tagDisplay,
    String vendor,
    String vendorDisplay,
    int version) {
    // MUST also have platform target of the same version
    fop.recordExistingFile(
      "/sdk/add-ons/addon-" + tag + "-" + vendor + "-" + version + "/source.properties",
      "");
    FakePackage.FakeLocalPackage local =
      new FakePackage.FakeLocalPackage(
        "add-ons;addon-" + tag + "-" + vendor + "-" + version, fop);

    DetailsTypes.AddonDetailsType addOnDetails =
      AndroidSdkHandler.getAddonModule().createLatestFactory().createAddonDetailsType();
    addOnDetails.setVendor(IdDisplay.create(vendor, vendorDisplay));
    addOnDetails.setTag(IdDisplay.create(tag, tagDisplay));
    addOnDetails.setApiLevel(version);
    local.setTypeDetails((TypeDetails) addOnDetails);
    return local;
  }
}