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
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.tools.adtui.swing.FakeUi;
import com.google.common.collect.ImmutableList;

import java.awt.Dimension;
import java.util.function.Predicate;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;

/**
 * Tests for {@link SystemImagePreview}
 *
 */
public class SystemImagePreviewTest extends AndroidTestCase {
  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private SystemImageDescription mMarshmallowImageDescr;
  private SystemImageDescription mPreviewImageDescr;
  private SystemImageDescription mWearOsImageDescr;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // Marshmallow image (API 23)
    String marshmallowPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgMarshmallow = new FakePackage.FakeLocalPackage(marshmallowPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsMarshmallow =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsMarshmallow.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsMarshmallow.setAbi("x86");
    detailsMarshmallow.setVendor(IdDisplay.create("google", "Google"));
    detailsMarshmallow.setApiLevel(23);
    pkgMarshmallow.setTypeDetails((TypeDetails)detailsMarshmallow);
    fileOp.recordExistingFile(pkgMarshmallow.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Fake preview image
    String previewPath = "system-images;android-ZZZ;google_apis;x86";
    FakePackage.FakeLocalPackage pkgPreview = new FakePackage.FakeLocalPackage(previewPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsPreview =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPreview.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsPreview.setAbi("x86");
    detailsPreview.setVendor(IdDisplay.create("google", "Google"));
    detailsPreview.setApiLevel(99);
    detailsPreview.setCodename("Z"); // Setting a code name is the key!
    pkgPreview.setTypeDetails((TypeDetails)detailsPreview);
    fileOp.recordExistingFile(pkgPreview.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Fake preview image
    String wearOsPath = "system-images;android-wear-cn;android-30;google_apis;x86";
    FakePackage.FakeLocalPackage pkgWearOs = new FakePackage.FakeLocalPackage(wearOsPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsWearOs =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsWearOs.getTags().add(IdDisplay.create("android-wear", "Wear OS Image"));
    detailsWearOs.setAbi("x86");
    detailsWearOs.setVendor(IdDisplay.create("google", "Google"));
    detailsWearOs.setApiLevel(30);
    pkgWearOs.setTypeDetails((TypeDetails)detailsWearOs);
    fileOp.recordExistingFile(pkgWearOs.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgMarshmallow, pkgPreview, pkgWearOs));

    RepoManager mgr = new FakeRepoManager(fileOp.toPath(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(fileOp.toPath(SDK_LOCATION), fileOp.toPath(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage marshmallowImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(marshmallowPath, progress).getLocation());
    ISystemImage previewImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(previewPath, progress).getLocation());
    ISystemImage wearOsImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wearOsPath, progress).getLocation());

    mMarshmallowImageDescr = new SystemImageDescription(marshmallowImage);
    mPreviewImageDescr = new SystemImageDescription(previewImage);
    mWearOsImageDescr = new SystemImageDescription(wearOsImage);
  }

  public void testSetImage() {
    SystemImagePreview imagePreview = new SystemImagePreview(null);

    imagePreview.setImage(mMarshmallowImageDescr);
    JLabel iconLabel = imagePreview.getReleaseIcon();
    assertTrue("No icon fetched for non-preview API", iconLabel != null && iconLabel.getIcon() != null);
    String iconUrl = iconLabel.getIcon().toString();
    assertTrue("Wrong icon fetched for non-preview API", iconUrl.contains("Marshmallow.png"));

    imagePreview.setImage(mPreviewImageDescr);
    iconLabel = imagePreview.getReleaseIcon();
    assertTrue("No icon fetched for Preview API", iconLabel != null && iconLabel.getIcon() != null);
    iconUrl = iconLabel.getIcon().toString();
    assertTrue("Wrong icon fetched for Preview API", iconUrl.contains("Default.png"));
  }

  public void testLocalizedChinaImages() {
    SystemImagePreview imagePreview = new SystemImagePreview(null);
    JPanel rootPanel = new JPanel();
    rootPanel.setSize(new Dimension(500, 500));
    rootPanel.add(imagePreview.getRootPanel());
    FakeUi fakeUi = new FakeUi(rootPanel, 1f, true);

    imagePreview.setImage(mWearOsImageDescr);

    final Predicate<JLabel> chinaLocalizedLabelPredicate =
      label -> label.isShowing() && "The selected image is a localized version of Wear OS for China".equals(label.getText());

    assertNotNull(fakeUi.findComponent(JLabel.class, chinaLocalizedLabelPredicate));

    // Change the image to
    imagePreview.setImage(mPreviewImageDescr);
    assertNull(fakeUi.findComponent(JLabel.class, chinaLocalizedLabelPredicate));
  }
}
