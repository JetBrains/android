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

import static com.android.sdklib.internal.avd.GpuMode.OFF;
import static com.android.sdklib.internal.avd.GpuMode.SWIFT;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.repository.targets.SystemImage.ANDROID_TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.WEAR_TAG;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.gpuOtherMode;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.isGoogleApiTag;
import static com.google.common.truth.Truth.assertThat;

import com.android.emulator.snapshot.SnapshotOuterClass;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.tools.idea.observable.BatchInvoker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TemporaryFolder;

public class ConfigureAvdOptionsStepTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private AvdInfo myQAvdInfo;
  private AvdInfo myMarshmallowAvdInfo;
  private AvdInfo myPreviewAvdInfo;
  private AvdInfo myZuluAvdInfo;
  private ISystemImage mySnapshotSystemImage;
  private final Map<String, String> myPropertiesMap = Maps.newHashMap();
  private Device myFoldable;
  private Device myAutomotive;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // Q image (API 29)
    String qPath = "system-images;android-29;google_apis;x86";
    FakePackage.FakeLocalPackage pkgQ = new FakePackage.FakeLocalPackage(qPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsQ =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsQ.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsQ.setAbi("x86");
    detailsQ.setApiLevel(29);
    pkgQ.setTypeDetails((TypeDetails) detailsQ);
    fileOp.recordExistingFile(pkgQ.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Marshmallow image (API 23)
    String marshmallowPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgMarshmallow = new FakePackage.FakeLocalPackage(marshmallowPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsMarshmallow =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsMarshmallow.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsMarshmallow.setAbi("x86");
    detailsMarshmallow.setApiLevel(23);
    pkgMarshmallow.setTypeDetails((TypeDetails) detailsMarshmallow);
    fileOp.recordExistingFile(pkgMarshmallow.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Preview image
    String previewPath = "system-images;android-ZZZ;google_apis;x86";
    FakePackage.FakeLocalPackage pkgPreview = new FakePackage.FakeLocalPackage(previewPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsPreview =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPreview.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsPreview.setAbi("x86");
    detailsPreview.setApiLevel(99);
    detailsPreview.setCodename("Z"); // Setting a code name is the key!
    pkgPreview.setTypeDetails((TypeDetails) detailsPreview);
    fileOp.recordExistingFile(pkgPreview.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image with an unknown API level
    // (This is not supposed to happen. But it does sometimes.)
    String zuluPath = "system-images;android-Z;google_apis;x86";
    FakePackage.FakeLocalPackage pkgZulu = new FakePackage.FakeLocalPackage(zuluPath, fileOp);
    DetailsTypes.SysImgDetailsType detailsZulu =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsZulu.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsZulu.setAbi("x86");
    detailsZulu.setApiLevel(99);
    pkgZulu.setTypeDetails((TypeDetails) detailsZulu);
    fileOp.recordExistingFile(pkgZulu.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgQ, pkgMarshmallow, pkgPreview, pkgZulu));

    RepoManager mgr = new FakeRepoManager(fileOp.toPath(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(fileOp.toPath(SDK_LOCATION), fileOp.toPath(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage QImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(qPath, progress).getLocation());
    ISystemImage marshmallowImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(marshmallowPath, progress).getLocation());
    ISystemImage NPreviewImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(previewPath, progress).getLocation());
    ISystemImage ZuluImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(zuluPath, progress).getLocation());

    mySnapshotSystemImage = ZuluImage; // Re-use Zulu for the snapshot test

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    myFoldable = devMgr.getDevice("7.6in Foldable", "Generic");
    myAutomotive = devMgr.getDevice("automotive_1024p_landscape", "Google");

    myQAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", QImage, myPropertiesMap);
    myMarshmallowAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", marshmallowImage, myPropertiesMap);
    myPreviewAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", NPreviewImage, myPropertiesMap);
    myZuluAvdInfo =
      new AvdInfo("name", new File("ini"), "folder", ZuluImage, myPropertiesMap);

    BatchInvoker.setOverrideStrategy(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY);
  }

  @Override
  protected void tearDown() throws Exception {
    BatchInvoker.clearOverrideStrategy();
    super.tearDown();
  }

  public void testIsGoogleApiTag() {
    assertThat(isGoogleApiTag(GOOGLE_APIS_TAG)).isTrue();
    assertThat(isGoogleApiTag(ANDROID_TV_TAG)).isTrue();
    assertThat(isGoogleApiTag(GOOGLE_TV_TAG)).isTrue();
    assertThat(isGoogleApiTag(WEAR_TAG)).isTrue();

    assertThat(isGoogleApiTag(DEFAULT_TAG)).isFalse();
    assertThat(isGoogleApiTag(GOOGLE_APIS_X86_TAG)).isFalse();
  }

  public void testGpuOtherMode() {
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

  public void testAutomotiveDevice() {
    ensureSdkManagerAvailable();

    //Device without SdCard
    AvdOptionsModel optionsModelNoSdCard = new AvdOptionsModel(myQAvdInfo);
    ConfigureAvdOptionsStep optionsStepNoSdCard = new ConfigureAvdOptionsStep(getProject(), optionsModelNoSdCard, newSkinChooser());
    optionsStepNoSdCard.addListeners();
    Disposer.register(getTestRootDisposable(), optionsStepNoSdCard);
    optionsModelNoSdCard.device().setNullableValue(myAutomotive);

    assertFalse(optionsModelNoSdCard.useBuiltInSdCard().get());
    assertFalse(optionsModelNoSdCard.useExternalSdCard().get());

    // Device with SdCard
    AvdOptionsModel optionsModelWithSdCard = new AvdOptionsModel(myQAvdInfo);
    ConfigureAvdOptionsStep optionsStepWithSdCard = new ConfigureAvdOptionsStep(getProject(), optionsModelWithSdCard, newSkinChooser());
    optionsStepWithSdCard.addListeners();
    Disposer.register(getTestRootDisposable(), optionsStepWithSdCard);
    optionsModelWithSdCard.device().setNullableValue(myFoldable);

    assertTrue(optionsModelWithSdCard.useBuiltInSdCard().get());
    assertFalse(optionsModelWithSdCard.useExternalSdCard().get());
  }

  public void testFoldedDevice() {
    ensureSdkManagerAvailable();
    AvdOptionsModel optionsModel = new AvdOptionsModel(myQAvdInfo);
    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    optionsStep.addListeners();
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsModel.device().setNullableValue(myFoldable);

    JBLabel label = optionsStep.getDeviceFrameTitle();
    assertFalse(label.isEnabled());
    label = optionsStep.getSkinDefinitionLabel();
    assertFalse(label.isEnabled());
    JCheckBox box = optionsStep.getDeviceFrameCheckbox();
    assertFalse(box.isEnabled());
    assertFalse(box.isSelected());
    SkinChooser skinChooser = optionsStep.getSkinComboBox();
    assertFalse(skinChooser.isEnabled());
  }

  public void testUpdateSystemImageData() {
    ensureSdkManagerAvailable();
    AvdOptionsModel optionsModel = new AvdOptionsModel(myMarshmallowAvdInfo);

    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);

    optionsStep.updateSystemImageData();
    Icon icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    String iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for non-preview API: " + iconUrl, iconUrl.contains("Marshmallow_32.png"));

    optionsModel = new AvdOptionsModel(myPreviewAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for Preview API: " + iconUrl, iconUrl.contains("Default_32.png"));

    optionsModel = new AvdOptionsModel(myZuluAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for unknown API: " + iconUrl, iconUrl.contains("Default_32.png"));
  }

  public void testPopulateSnapshotList() throws Exception {
    TemporaryFolder tempFolder = new TemporaryFolder();
    tempFolder.create();
    File snapAvdDir = tempFolder.newFolder("proto_avd");
    AvdInfo snapshotAvdInfo =
      new AvdInfo("snapAvd", new File("ini"), snapAvdDir.getAbsolutePath(), mySnapshotSystemImage, myPropertiesMap);
    AvdOptionsModel optionsModel = new AvdOptionsModel(snapshotAvdInfo);

    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);

    File snapshotDir = new File(snapAvdDir, "snapshots");
    assertThat(snapshotDir.mkdir()).isTrue();
    SnapshotOuterClass.Image.Builder imageBuilder = SnapshotOuterClass.Image.newBuilder();
    SnapshotOuterClass.Image anImage = imageBuilder.build();

    File snapNewestDir = new File(snapshotDir, "snapNewest");
    assertThat(snapNewestDir.mkdir()).isTrue();
    SnapshotOuterClass.Snapshot.Builder newestBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    newestBuilder.addImages(anImage);
    newestBuilder.setCreationTime(1_500_300_000L);
    SnapshotOuterClass.Snapshot protoNewestBuf = newestBuilder.build();
    File protoNewestFile = new File(snapNewestDir, "snapshot.pb");
    OutputStream protoNewestOutputStream = new FileOutputStream(protoNewestFile);
    protoNewestBuf.writeTo(protoNewestOutputStream);

    File snapSelectedDir = new File(snapshotDir, "snapSelected");
    assertThat(snapSelectedDir.mkdir()).isTrue();
    SnapshotOuterClass.Snapshot.Builder selectedBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    selectedBuilder.addImages(anImage);
    selectedBuilder.setCreationTime(1_500_200_000L);
    SnapshotOuterClass.Snapshot protoSelectedBuf = selectedBuilder.build();
    File protoSelectedFile = new File(snapSelectedDir, "snapshot.pb");
    OutputStream protoSelectedOutputStream = new FileOutputStream(protoSelectedFile);
    protoSelectedBuf.writeTo(protoSelectedOutputStream);

    File snapOldestDir = new File(snapshotDir, "snapOldest");
    assertThat(snapOldestDir.mkdir()).isTrue();
    SnapshotOuterClass.Snapshot.Builder oldestBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    oldestBuilder.addImages(anImage);
    oldestBuilder.setCreationTime(1_500_100_000L);
    SnapshotOuterClass.Snapshot protoOldestBuf = oldestBuilder.build();
    File protoOldestFile = new File(snapOldestDir, "snapshot.pb");
    OutputStream protoOldestOutputStream = new FileOutputStream(protoOldestFile);
    protoOldestBuf.writeTo(protoOldestOutputStream);

    File snapQuickDir = new File(snapshotDir, "default_boot");
    assertThat(snapQuickDir.mkdir()).isTrue();
    SnapshotOuterClass.Snapshot.Builder quickBootBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    quickBootBuilder.addImages(anImage);
    quickBootBuilder.setCreationTime(1_500_000_000L);
    SnapshotOuterClass.Snapshot protoQuickBuf = quickBootBuilder.build();
    File protoQuickFile = new File(snapQuickDir, "snapshot.pb");
    OutputStream protoQuickOutputStream = new FileOutputStream(protoQuickFile);
    protoQuickBuf.writeTo(protoQuickOutputStream);

    List<String> snapshotList = optionsStep.getSnapshotNamesList("snapSelected");

    // This list should NOT include 'default_boot'
    assertThat(snapshotList.size()).isEqualTo(3);
    assertThat(snapshotList.get(0)).isEqualTo("snapSelected"); // First because it's selected
    assertThat(snapshotList.get(1)).isEqualTo("snapOldest");   // Next because of creation time
    assertThat(snapshotList.get(2)).isEqualTo("snapNewest");
  }

  private @NotNull SkinChooser newSkinChooser() {
    Executor executor = MoreExecutors.directExecutor();
    return new SkinChooser(getProject(), () -> Futures.immediateFuture(Collections.emptyList()), executor, executor);
  }
}
