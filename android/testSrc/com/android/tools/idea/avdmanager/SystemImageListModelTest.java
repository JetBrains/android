/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.avdmanager;

import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.AndroidTestCase;

public class SystemImageListModelTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private SystemImageDescription myKnownApiImageDescription;
  private SystemImageDescription myDeprecatedApiImageDescription;
  private SystemImageDescription myJustDeprecatedApiImageDescription;
  private SystemImageDescription myPreviewApiImageDescription;
  private SystemImageDescription myUnknownApiImageDescription;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // Image with known API level
    String knownApiPath = "system-images;android-26;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgKnownApi = new FakePackage.FakeLocalPackage(knownApiPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsKnownApi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsKnownApi.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsKnownApi.setApiLevel(26);
    pkgKnownApi.setTypeDetails((TypeDetails) detailsKnownApi);
    fileOp.recordExistingFile(pkgKnownApi.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image with deprecated API level (Honeycomb)
    String deprecatedApiPath = "system-images;android-13;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgDeprecatedApi = new FakePackage.FakeLocalPackage(deprecatedApiPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsDeprecatedApi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsDeprecatedApi.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsDeprecatedApi.setApiLevel(13);
    pkgDeprecatedApi.setTypeDetails((TypeDetails) detailsDeprecatedApi);
    fileOp.recordExistingFile(pkgDeprecatedApi.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image with deprecated API level (whatever is most-recently deprecated)
    String justDeprecatedApiPath = "system-images;android-97;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgJustDeprecatedApi = new FakePackage.FakeLocalPackage(justDeprecatedApiPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsJustDeprecatedApi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsJustDeprecatedApi.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsJustDeprecatedApi.setApiLevel(SdkVersionInfo.LOWEST_ACTIVE_API - 1);
    pkgJustDeprecatedApi.setTypeDetails((TypeDetails) detailsJustDeprecatedApi);
    fileOp.recordExistingFile(pkgJustDeprecatedApi.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Preview image (with unknown API level)
    String PreviewApiPath = "system-images;android-98;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgPreviewApi = new FakePackage.FakeLocalPackage(PreviewApiPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsPreviewApi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPreviewApi.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsPreviewApi.setApiLevel(98);
    detailsPreviewApi.setCodename("Zwieback-preview"); // The codename makes it a "preview"
    pkgPreviewApi.setTypeDetails((TypeDetails) detailsPreviewApi);
    fileOp.recordExistingFile(pkgPreviewApi.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Non-preview image with unknown API level
    // (This shouldn't happen.)
    String unknownApiPath = "system-images;android-99;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgUnknownApi = new FakePackage.FakeLocalPackage(unknownApiPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsUnknownApi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsUnknownApi.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsUnknownApi.setApiLevel(99);
    // (Leaving codename null)
    pkgUnknownApi.setTypeDetails((TypeDetails) detailsUnknownApi);
    fileOp.recordExistingFile(pkgUnknownApi.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgKnownApi, pkgDeprecatedApi, pkgJustDeprecatedApi, pkgPreviewApi, pkgUnknownApi));

    RepoManager mgr = new FakeRepoManager(fileOp.toPath(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(fileOp.toPath(SDK_LOCATION), fileOp.toPath(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage knownApiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(knownApiPath, progress).getLocation());
    ISystemImage deprecatedApiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(deprecatedApiPath, progress).getLocation());
    ISystemImage justDeprecatedApiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(justDeprecatedApiPath, progress).getLocation());
    ISystemImage previewApiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(PreviewApiPath, progress).getLocation());
    ISystemImage unknownApiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(unknownApiPath, progress).getLocation());

    myKnownApiImageDescription = new SystemImageDescription(knownApiImage);
    myDeprecatedApiImageDescription = new SystemImageDescription(deprecatedApiImage);
    myJustDeprecatedApiImageDescription = new SystemImageDescription(justDeprecatedApiImage);
    myPreviewApiImageDescription = new SystemImageDescription(previewApiImage);
    myUnknownApiImageDescription = new SystemImageDescription(unknownApiImage);
  }

  public void testReleaseDisplayName() {
    assertEquals("Oreo", SystemImageListModel.releaseDisplayName(myKnownApiImageDescription));
    assertEquals("Honeycomb (Deprecated)", SystemImageListModel.releaseDisplayName(myDeprecatedApiImageDescription));
    assertEquals("Zwieback-preview", SystemImageListModel.releaseDisplayName(myPreviewApiImageDescription));
    assertEquals("API 99", SystemImageListModel.releaseDisplayName(myUnknownApiImageDescription));

    String justDeprecatedName = SystemImageListModel.releaseDisplayName(myJustDeprecatedApiImageDescription);
    assertTrue("'" + justDeprecatedName + "' should say '(Deprecated)'",
               SystemImageListModel.releaseDisplayName(myJustDeprecatedApiImageDescription).endsWith(" (Deprecated)"));
  }
}
