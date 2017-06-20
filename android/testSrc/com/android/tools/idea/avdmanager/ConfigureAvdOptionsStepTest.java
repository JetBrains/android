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

package com.android.tools.idea.avdmanager;

import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;

import java.io.File;
import java.util.Map;

import static com.android.sdklib.internal.avd.GpuMode.*;
import static com.android.sdklib.repository.targets.SystemImage.*;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.gpuOtherMode;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.isGoogleApiTag;
import static com.google.common.truth.Truth.assertThat;

public class ConfigureAvdOptionsStepTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private AvdInfo myMarshmallowAvdInfo;
  private AvdInfo myPreviewAvdInfo;
  private Map<String, String> myPropertiesMap = Maps.newHashMap();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // Marshmallow image (API 23)
    String marshmallowPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgMarshmallow = new FakePackage.FakeLocalPackage(marshmallowPath);
    DetailsTypes.SysImgDetailsType detailsMarshmallow =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsMarshmallow.setTag(IdDisplay.create("google_apis", "Google APIs"));
    detailsMarshmallow.setAbi("x86");
    detailsMarshmallow.setVendor(IdDisplay.create("google", "Google"));
    detailsMarshmallow.setApiLevel(23);
    pkgMarshmallow.setTypeDetails((TypeDetails) detailsMarshmallow);
    pkgMarshmallow.setInstalledPath(new File(SDK_LOCATION, "23-marshmallow-x86"));
    fileOp.recordExistingFile(new File(pkgMarshmallow.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Nougat Preview image (still API 23)
    String NPreviewPath = "system-images;android-N;google_apis;x86";
    FakePackage.FakeLocalPackage pkgNPreview = new FakePackage.FakeLocalPackage(NPreviewPath);
    DetailsTypes.SysImgDetailsType detailsNPreview =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsNPreview.setTag(IdDisplay.create("google_apis", "Google APIs"));
    detailsNPreview.setAbi("x86");
    detailsNPreview.setVendor(IdDisplay.create("google", "Google"));
    detailsNPreview.setApiLevel(23);
    detailsNPreview.setCodename("N"); // Setting a code name is the key!
    pkgNPreview.setTypeDetails((TypeDetails) detailsNPreview);
    pkgNPreview.setInstalledPath(new File(SDK_LOCATION, "n-preview-x86"));
    fileOp.recordExistingFile(new File(pkgNPreview.getLocation(), SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgMarshmallow, pkgNPreview));

    RepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(new File(SDK_LOCATION), new File(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage marshmallowImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(marshmallowPath, progress).getLocation());
    ISystemImage NPreviewImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(NPreviewPath, progress).getLocation());

    myMarshmallowAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", marshmallowImage, myPropertiesMap);
    myPreviewAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", NPreviewImage, myPropertiesMap);
  }

  public void testIsGoogleApiTag() throws Exception {
    assertThat(isGoogleApiTag(GOOGLE_APIS_TAG)).isTrue();
    assertThat(isGoogleApiTag(TV_TAG)).isTrue();
    assertThat(isGoogleApiTag(WEAR_TAG)).isTrue();

    assertThat(isGoogleApiTag(DEFAULT_TAG)).isFalse();
    assertThat(isGoogleApiTag(GOOGLE_APIS_X86_TAG)).isFalse();
    assertThat(isGoogleApiTag(GLASS_TAG)).isFalse();
  }

  public void testGpuOtherMode() throws Exception {
    assertEquals(SWIFT, gpuOtherMode(23, true, true, true));
    assertEquals(SWIFT, gpuOtherMode(23, true, true, false));

    assertEquals(OFF, gpuOtherMode(22, false, true, false));
    assertEquals(OFF, gpuOtherMode(22, true, true, false));
    assertEquals(OFF, gpuOtherMode(22, true, false, false));
    assertEquals(OFF, gpuOtherMode(23, true, false, false));

    assertEquals(OFF, gpuOtherMode(22, true, true, true));
    assertEquals(OFF, gpuOtherMode(23, true, false, true));
    assertEquals(OFF, gpuOtherMode(23, false, false, true));
  }

  public void testUpdateSystemImageData() throws Exception {
    ensureSdkManagerAvailable();
    AvdOptionsModel optionsModel = new AvdOptionsModel(myMarshmallowAvdInfo);

    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel);
    Disposer.register(getTestRootDisposable(), optionsStep);

    optionsStep.updateSystemImageData();
    Icon icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    String iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for non-preview API: " + iconUrl, iconUrl.endsWith("Marshmallow_32.png"));

    optionsModel = new AvdOptionsModel(myPreviewAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel);
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    // For an actual Preview, the URL will be Default_32.png, but
    // we now know that N-Preview became Nougat.
    assertTrue("Wrong icon fetched for Preview API: " + iconUrl, iconUrl.endsWith("Nougat_32.png"));
  }
}
