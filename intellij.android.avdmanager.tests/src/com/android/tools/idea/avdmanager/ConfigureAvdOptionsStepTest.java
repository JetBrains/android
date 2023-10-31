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
import static com.android.sdklib.repository.targets.SystemImage.ANDROID_TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_X86_TAG;
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
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.observable.BatchInvoker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBLabel;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class ConfigureAvdOptionsStepTest extends AndroidTestCase {

  private AvdInfo myQAvdInfo;
  private AvdInfo myMarshmallowAvdInfo;
  private AvdInfo myPreviewAvdInfo;
  private AvdInfo myZuluAvdInfo;
  private AvdInfo myExtensionsAvdInfo;
  private ISystemImage mySnapshotSystemImage;
  private final Map<String, String> myPropertiesMap = new HashMap<>();
  private Device myFoldable;
  private Device myDesktop;
  private Device myAutomotive;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RepositoryPackages packages = new RepositoryPackages();
    IconLoader.activate();

    Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
    // Q image (API 29)
    String qPath = "system-images;android-29;google_apis;x86";
    FakePackage.FakeLocalPackage pkgQ = new FakePackage.FakeLocalPackage(qPath, sdkRoot.resolve("qSysImg"));
    DetailsTypes.SysImgDetailsType detailsQ =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsQ.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsQ.setAbi("x86");
    detailsQ.setApiLevel(29);
    pkgQ.setTypeDetails((TypeDetails) detailsQ);
    InMemoryFileSystems.recordExistingFile(pkgQ.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Marshmallow image (API 23)
    String marshmallowPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgMarshmallow = new FakePackage.FakeLocalPackage(marshmallowPath, sdkRoot.resolve("mSysImg"));
    DetailsTypes.SysImgDetailsType detailsMarshmallow =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsMarshmallow.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsMarshmallow.setAbi("x86");
    detailsMarshmallow.setApiLevel(23);
    pkgMarshmallow.setTypeDetails((TypeDetails) detailsMarshmallow);
    InMemoryFileSystems.recordExistingFile(pkgMarshmallow.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Preview image
    String previewPath = "system-images;android-ZZZ;google_apis;x86";
    FakePackage.FakeLocalPackage pkgPreview = new FakePackage.FakeLocalPackage(previewPath, sdkRoot.resolve("previewSysImg"));
    DetailsTypes.SysImgDetailsType detailsPreview =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPreview.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsPreview.setAbi("x86");
    detailsPreview.setApiLevel(99);
    detailsPreview.setCodename("Z"); // Setting a code name is the key!
    pkgPreview.setTypeDetails((TypeDetails) detailsPreview);
    InMemoryFileSystems.recordExistingFile(pkgPreview.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image with an unknown API level
    // (This is not supposed to happen. But it does sometimes.)
    String zuluPath = "system-images;android-Z;google_apis;x86";
    FakePackage.FakeLocalPackage pkgZulu = new FakePackage.FakeLocalPackage(zuluPath, sdkRoot.resolve("zSysImg"));
    DetailsTypes.SysImgDetailsType detailsZulu =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsZulu.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsZulu.setAbi("x86");
    detailsZulu.setApiLevel(99);
    pkgZulu.setTypeDetails((TypeDetails)detailsZulu);
    InMemoryFileSystems.recordExistingFile(pkgZulu.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image that contains SDK extensions and is not the base SDK
    String extensionsPath = "system-images;android-32-3;google_apis;x86";
    FakePackage.FakeLocalPackage pkgExtensions = new FakePackage.FakeLocalPackage(extensionsPath, sdkRoot.resolve("extensionSysImg"));
    DetailsTypes.SysImgDetailsType detailsExtensions =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsExtensions.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsExtensions.setAbi("x86");
    detailsExtensions.setApiLevel(32);
    detailsExtensions.setExtensionLevel(3);
    detailsExtensions.setBaseExtension(false);
    pkgExtensions.setTypeDetails((TypeDetails)detailsExtensions);
    InMemoryFileSystems.recordExistingFile(pkgExtensions.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgQ, pkgMarshmallow, pkgPreview, pkgZulu, pkgExtensions));

    RepoManager mgr = new FakeRepoManager(sdkRoot, packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(sdkRoot, sdkRoot.getRoot().resolve("avd"), mgr);

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
    ISystemImage extensionsImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(extensionsPath, progress).getLocation());

    mySnapshotSystemImage = ZuluImage; // Re-use Zulu for the snapshot test

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    myFoldable = devMgr.getDevice("7.6in Foldable", "Generic");
    myDesktop = devMgr.getDevice("desktop_small", "Google");
    myAutomotive = devMgr.getDevice("automotive_1024p_landscape", "Google");

    myQAvdInfo =
      new AvdInfo("name", Paths.get("ini"), Paths.get("folder"), QImage, myPropertiesMap);
    myMarshmallowAvdInfo =
      new AvdInfo("name", Paths.get("ini"), Paths.get("folder"), marshmallowImage, myPropertiesMap);
    myPreviewAvdInfo =
      new AvdInfo("name", Paths.get("ini"), Paths.get("folder"), NPreviewImage, myPropertiesMap);
    myZuluAvdInfo =
      new AvdInfo("name", Paths.get("ini"), Paths.get("folder"), ZuluImage, myPropertiesMap);
    myExtensionsAvdInfo =
      new AvdInfo("name", Paths.get("ini"), Paths.get("folder"), extensionsImage, myPropertiesMap);

    BatchInvoker.setOverrideStrategy(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY);
  }

  @Override
  protected void tearDown() throws Exception {
    BatchInvoker.clearOverrideStrategy();
    IconLoader.deactivate();
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
    assertEquals(SWIFT, gpuOtherMode(23, true, true));

    assertEquals(OFF, gpuOtherMode(22, false, true));
    assertEquals(OFF, gpuOtherMode(22, true, true));
    assertEquals(OFF, gpuOtherMode(22, true, false));
    assertEquals(OFF, gpuOtherMode(23, true, false));

    assertEquals(OFF, gpuOtherMode(23, false, false));
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

  public void testDesktopDevice() {
    ensureSdkManagerAvailable();
    AvdOptionsModel optionsModel = new AvdOptionsModel(myQAvdInfo);
    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    optionsStep.addListeners();
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsModel.device().setNullableValue(myDesktop);

    JBLabel label = optionsStep.getDeviceFrameTitle();
    assertFalse(label.isEnabled());
    label = optionsStep.getSkinDefinitionLabel();
    assertTrue(label.isEnabled());
    JCheckBox box = optionsStep.getDeviceFrameCheckbox();
    assertFalse(box.isEnabled());
    assertFalse(box.isSelected());
    SkinChooser skinChooser = optionsStep.getSkinComboBox();
    assertTrue(skinChooser.isEnabled());
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
    assertEquals("Android API 99 x86", optionsStep.getSystemImageDetailsText());
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for unknown API: " + iconUrl, iconUrl.contains("Default_32.png"));

    optionsModel = new AvdOptionsModel(myExtensionsAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    assertEquals("Android 12L x86 (Extension Level 3)", optionsStep.getSystemImageDetailsText());
  }

  public void testPopulateSnapshotList() throws Exception {
    Path snapAvdDir = InMemoryFileSystems.createInMemoryFileSystemAndFolder("proto_avd");
    AvdInfo snapshotAvdInfo =
      new AvdInfo("snapAvd", Paths.get("ini"), snapAvdDir, mySnapshotSystemImage, myPropertiesMap);
    AvdOptionsModel optionsModel = new AvdOptionsModel(snapshotAvdInfo);

    ConfigureAvdOptionsStep optionsStep = new ConfigureAvdOptionsStep(getProject(), optionsModel, newSkinChooser());
    Disposer.register(getTestRootDisposable(), optionsStep);

    Path snapshotDir = snapAvdDir.resolve("snapshots");
    Files.createDirectories(snapshotDir);
    SnapshotOuterClass.Image.Builder imageBuilder = SnapshotOuterClass.Image.newBuilder();
    SnapshotOuterClass.Image anImage = imageBuilder.build();

    Path snapNewestDir = snapshotDir.resolve("snapNewest");
    Files.createDirectories(snapNewestDir);
    SnapshotOuterClass.Snapshot.Builder newestBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    newestBuilder.addImages(anImage);
    newestBuilder.setCreationTime(1_500_300_000L);
    SnapshotOuterClass.Snapshot protoNewestBuf = newestBuilder.build();
    Path protoNewestFile = snapNewestDir.resolve("snapshot.pb");
    OutputStream protoNewestOutputStream = Files.newOutputStream(protoNewestFile);
    protoNewestBuf.writeTo(protoNewestOutputStream);

    Path snapSelectedDir = snapshotDir.resolve("snapSelected");
    Files.createDirectories(snapSelectedDir);
    SnapshotOuterClass.Snapshot.Builder selectedBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    selectedBuilder.addImages(anImage);
    selectedBuilder.setCreationTime(1_500_200_000L);
    SnapshotOuterClass.Snapshot protoSelectedBuf = selectedBuilder.build();
    Path protoSelectedFile = snapSelectedDir.resolve("snapshot.pb");
    OutputStream protoSelectedOutputStream = Files.newOutputStream(protoSelectedFile);
    protoSelectedBuf.writeTo(protoSelectedOutputStream);

    Path snapOldestDir = snapshotDir.resolve("snapOldest");
    Files.createDirectories(snapOldestDir);
    SnapshotOuterClass.Snapshot.Builder oldestBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    oldestBuilder.addImages(anImage);
    oldestBuilder.setCreationTime(1_500_100_000L);
    SnapshotOuterClass.Snapshot protoOldestBuf = oldestBuilder.build();
    Path protoOldestFile = snapOldestDir.resolve("snapshot.pb");
    OutputStream protoOldestOutputStream = Files.newOutputStream(protoOldestFile);
    protoOldestBuf.writeTo(protoOldestOutputStream);

    Path snapQuickDir = snapshotDir.resolve("default_boot");
    Files.createDirectories(snapQuickDir);
    SnapshotOuterClass.Snapshot.Builder quickBootBuilder = SnapshotOuterClass.Snapshot.newBuilder();
    quickBootBuilder.addImages(anImage);
    quickBootBuilder.setCreationTime(1_500_000_000L);
    SnapshotOuterClass.Snapshot protoQuickBuf = quickBootBuilder.build();
    Path protoQuickFile = snapQuickDir.resolve("snapshot.pb");
    OutputStream protoQuickOutputStream = Files.newOutputStream(protoQuickFile);
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
