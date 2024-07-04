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

package com.android.tools.idea.avdmanager.ui;

import static com.android.sdklib.internal.avd.GpuMode.OFF;
import static com.android.sdklib.internal.avd.GpuMode.SWIFT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.emulator.snapshot.SnapshotOuterClass;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.resources.ScreenOrientation;
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
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.avdmanager.skincombobox.Skin;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBox;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.components.JBLabel;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
@RunsInEdt
public final class ConfigureAvdOptionsStepTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.withSdk();

  @Rule
  public final EdtRule edtRule = new EdtRule();

  private ISystemImage mySnapshotSystemImage;

  private DeviceManager myManager;
  private Device myFoldable;
  private Device myDesktop;
  private Device myAutomotive;

  private final Map<String, String> myPropertiesMap = Maps.newHashMap();
  private AvdInfo myQAvdInfo;
  private AvdInfo myMarshmallowAvdInfo;
  private AvdInfo myPreviewAvdInfo;
  private AvdInfo myZuluAvdInfo;
  private AvdInfo myExtensionsAvdInfo;
  private AvdInfo myNoSystemImageAvdInfo;

  @Before
  public void setUp() {
    RepositoryPackages packages = new RepositoryPackages();
    IconLoader.activate();

    Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
    // Q image (API 29)
    String qPath = "system-images;android-29;google_apis;x86";
    FakePackage.FakeLocalPackage pkgQ = new FakePackage.FakeLocalPackage(qPath, sdkRoot.resolve("qSysImg"));
    DetailsTypes.SysImgDetailsType detailsQ =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsQ.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsQ.getAbis().add("x86");
    detailsQ.setApiLevel(29);
    pkgQ.setTypeDetails((TypeDetails)detailsQ);
    InMemoryFileSystems.recordExistingFile(pkgQ.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Marshmallow image (API 23)
    String marshmallowPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgMarshmallow = new FakePackage.FakeLocalPackage(marshmallowPath, sdkRoot.resolve("mSysImg"));
    DetailsTypes.SysImgDetailsType detailsMarshmallow =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsMarshmallow.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsMarshmallow.getAbis().add("x86");
    detailsMarshmallow.setApiLevel(23);
    pkgMarshmallow.setTypeDetails((TypeDetails)detailsMarshmallow);
    InMemoryFileSystems.recordExistingFile(pkgMarshmallow.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Preview image
    String previewPath = "system-images;android-ZZZ;google_apis;x86";
    FakePackage.FakeLocalPackage pkgPreview = new FakePackage.FakeLocalPackage(previewPath, sdkRoot.resolve("previewSysImg"));
    DetailsTypes.SysImgDetailsType detailsPreview =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPreview.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsPreview.getAbis().add("x86");
    detailsPreview.setApiLevel(99);
    detailsPreview.setCodename("Z"); // Setting a code name is the key!
    pkgPreview.setTypeDetails((TypeDetails)detailsPreview);
    InMemoryFileSystems.recordExistingFile(pkgPreview.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image with an unknown API level
    // (This is not supposed to happen. But it does sometimes.)
    String zuluPath = "system-images;android-Z;google_apis;x86";
    FakePackage.FakeLocalPackage pkgZulu = new FakePackage.FakeLocalPackage(zuluPath, sdkRoot.resolve("zSysImg"));
    DetailsTypes.SysImgDetailsType detailsZulu =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsZulu.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsZulu.getAbis().add("x86");
    detailsZulu.setApiLevel(99);
    pkgZulu.setTypeDetails((TypeDetails)detailsZulu);
    InMemoryFileSystems.recordExistingFile(pkgZulu.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // Image that contains SDK extensions and is not the base SDK
    String extensionsPath = "system-images;android-32-3;google_apis;x86";
    FakePackage.FakeLocalPackage pkgExtensions = new FakePackage.FakeLocalPackage(extensionsPath, sdkRoot.resolve("extensionSysImg"));
    DetailsTypes.SysImgDetailsType detailsExtensions =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsExtensions.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    detailsExtensions.getAbis().add("x86");
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

    ISystemImage QImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, qPath, progress);
    ISystemImage marshmallowImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, marshmallowPath, progress);
    ISystemImage NPreviewImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, previewPath, progress);
    ISystemImage ZuluImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, zuluPath, progress);
    ISystemImage extensionsImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, extensionsPath, progress);

    mySnapshotSystemImage = ZuluImage; // Re-use Zulu for the snapshot test

    myManager = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    myFoldable = myManager.getDevice("7.6in Foldable", "Generic");
    myDesktop = myManager.getDevice("desktop_small", "Google");
    myAutomotive = myManager.getDevice("automotive_1024p_landscape", "Google");

    var ini = Paths.get("ini");
    var folder = Paths.get("folder");

    myQAvdInfo = new AvdInfo(ini, folder, QImage, myPropertiesMap, null);
    myMarshmallowAvdInfo = new AvdInfo(ini, folder, marshmallowImage, myPropertiesMap, null);
    myPreviewAvdInfo = new AvdInfo(ini, folder, NPreviewImage, myPropertiesMap, null);
    myZuluAvdInfo = new AvdInfo(ini, folder, ZuluImage, myPropertiesMap, null);
    myExtensionsAvdInfo = new AvdInfo(ini, folder, extensionsImage, myPropertiesMap, null);
    myNoSystemImageAvdInfo = new AvdInfo(ini, folder, null, myPropertiesMap, null, AvdInfo.AvdStatus.ERROR_IMAGE_MISSING);

    BatchInvoker.setOverrideStrategy(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY);
  }

  @After
  public void tearDown() {
    BatchInvoker.clearOverrideStrategy();
    IconLoader.deactivate();
    IconLoader.INSTANCE.clearCacheInTests();
  }

  @Test
  public void onEntering() {
    // Arrange
    var model = new AvdOptionsModel(myQAvdInfo);
    model.device().setNullableValue(myManager.getDevice("pixel_tablet", "Google"));

    var step = new ConfigureAvdOptionsStep(myRule.getProject(), model, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), step);

    // Act
    step.onEntering();

    // Assert
    assertEquals(ScreenOrientation.LANDSCAPE, step.getOrientationToggle().getSelectedElement());
  }

  @Test
  public void preferredABi() throws IOException {
    StudioFlags.RISC_V.override(true);

    // Arrange
    Files.writeString(
      myQAvdInfo.getSystemImage().getLocation().resolve("build.prop"),
      """
        ro.system.product.cpu.abilist=x86_64,arm64-v8a
        ro.system.product.cpu.abilist32=
        ro.system.product.cpu.abilist64=x86_64,arm64-v8a
        """,
      StandardCharsets.UTF_8
    );

    var model = new AvdOptionsModel(myQAvdInfo);
    model.device().setNullableValue(myManager.getDevice("pixel_tablet", "Google"));

    var step = new ConfigureAvdOptionsStep(myRule.getProject(), model, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), step);
    Disposer.register(myRule.getTestRootDisposable(), new ModelWizard.Builder(step).build());

    assertThat(myQAvdInfo.getUserSettings()).isEmpty();

    // Act
    step.onEntering();

    // Assert
    var abi = "arm64-v8a";
    assertThat(step.getPreferredAbi().getModel().getSize()).isEqualTo(3);
    step.getPreferredAbi().setSelectedItem(abi); // 0 for null, 1 for x86_64, 2 for arm64-v8a
    assertThat(model.preferredAbi().get().isPresent()).isTrue();
    assertThat(model.preferredAbi().get().get()).isEqualTo(abi);
  }

  @Test
  public void gpuOtherMode() {
    assertEquals(SWIFT, ConfigureAvdOptionsStep.gpuOtherMode(23, true, true));

    assertEquals(OFF, ConfigureAvdOptionsStep.gpuOtherMode(22, false, true));
    assertEquals(OFF, ConfigureAvdOptionsStep.gpuOtherMode(22, true, true));
    assertEquals(OFF, ConfigureAvdOptionsStep.gpuOtherMode(22, true, false));
    assertEquals(OFF, ConfigureAvdOptionsStep.gpuOtherMode(23, true, false));

    assertEquals(OFF, ConfigureAvdOptionsStep.gpuOtherMode(23, false, false));
  }

  @Test
  public void automotiveDevice() {
    //Device without SdCard
    AvdOptionsModel optionsModelNoSdCard = new AvdOptionsModel(myQAvdInfo);
    var optionsStepNoSdCard = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModelNoSdCard, newSkinComboBox());
    optionsStepNoSdCard.addListeners();
    Disposer.register(myRule.getTestRootDisposable(), optionsStepNoSdCard);
    optionsModelNoSdCard.device().setNullableValue(myAutomotive);

    assertFalse(optionsModelNoSdCard.useBuiltInSdCard().get());
    assertFalse(optionsModelNoSdCard.useExternalSdCard().get());

    // Device with SdCard
    AvdOptionsModel optionsModelWithSdCard = new AvdOptionsModel(myQAvdInfo);
    var optionsStepWithSdCard = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModelWithSdCard, newSkinComboBox());
    optionsStepWithSdCard.addListeners();
    Disposer.register(myRule.getTestRootDisposable(), optionsStepWithSdCard);
    optionsModelWithSdCard.device().setNullableValue(myFoldable);

    assertTrue(optionsModelWithSdCard.useBuiltInSdCard().get());
    assertFalse(optionsModelWithSdCard.useExternalSdCard().get());
  }

  @Test
  public void foldedDevice() {
    AvdOptionsModel optionsModel = new AvdOptionsModel(myQAvdInfo);
    var optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    optionsStep.addListeners();
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);
    optionsModel.device().setNullableValue(myFoldable);

    JBLabel label = optionsStep.getDeviceFrameTitle();
    assertFalse(label.isEnabled());
    label = optionsStep.getSkinDefinitionLabel();
    assertTrue(label.isEnabled());
    JCheckBox box = optionsStep.getDeviceFrameCheckbox();
    assertFalse(box.isEnabled());
    assertFalse(box.isSelected());
    assertFalse(optionsStep.getSkinComboBox().isEnabled());
  }

  @Test
  public void desktopDevice() {
    AvdOptionsModel optionsModel = new AvdOptionsModel(myQAvdInfo);
    var optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    optionsStep.addListeners();
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);
    optionsModel.device().setNullableValue(myDesktop);

    JBLabel label = optionsStep.getDeviceFrameTitle();
    assertFalse(label.isEnabled());
    label = optionsStep.getSkinDefinitionLabel();
    assertTrue(label.isEnabled());
    JCheckBox box = optionsStep.getDeviceFrameCheckbox();
    assertFalse(box.isEnabled());
    assertFalse(box.isSelected());
    assertTrue(optionsStep.getSkinComboBox().isEnabled());
  }

  @Test
  public void customSkinDefinitionComboBoxDisablesWhenEnableDeviceFrameCheckboxIsDeselected() {
    // Arrange
    var step = new ConfigureAvdOptionsStep(myRule.getProject(), new AvdOptionsModel(myQAvdInfo), newSkinComboBox());
    var checkbox = step.getDeviceFrameCheckbox();
    var comboBox = step.getSkinComboBox();

    // Act
    var wizard = new ModelWizard.Builder(step).build();
    Disposer.register(myRule.getTestRootDisposable(), wizard);

    checkbox.setSelected(true);

    // Assert
    assertTrue(comboBox.isEnabled());

    // Act
    checkbox.setSelected(false);

    // Assert
    assertFalse(comboBox.isEnabled());
  }

  @Test
  public void updateSystemImageData() {
    AvdOptionsModel optionsModel = new AvdOptionsModel(myMarshmallowAvdInfo);

    var optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);

    optionsStep.updateSystemImageData();
    Icon icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    String iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for non-preview API: " + iconUrl, iconUrl.contains("Marshmallow_32.png"));

    optionsModel = new AvdOptionsModel(myPreviewAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for Preview API: " + iconUrl, iconUrl.contains("Default_32.png"));

    optionsModel = new AvdOptionsModel(myZuluAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    assertEquals("Android API 99 x86", optionsStep.getSystemImageDetailsText());
    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    iconUrl = icon.toString();
    assertTrue("Wrong icon fetched for unknown API: " + iconUrl, iconUrl.contains("Default_32.png"));

    optionsModel = new AvdOptionsModel(myExtensionsAvdInfo);

    optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);
    optionsStep.updateSystemImageData();
    assertEquals("Android 12L x86 (Extension Level 3)", optionsStep.getSystemImageDetailsText());
  }


  @Test
  public void nullSystemImage() {
    AvdOptionsModel optionsModel = new AvdOptionsModel(myNoSystemImageAvdInfo);

    var optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    optionsModel.device().setNullableValue(myFoldable);
    Disposer.register(myRule.getTestRootDisposable(), new ModelWizard.Builder(optionsStep).build());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);

    optionsStep.updateSystemImageData();
    Icon icon = optionsStep.getSystemImageIcon();
    assertNull(icon);

    assertThat(optionsStep.canGoForward().get()).isFalse();
    assertThat(optionsStep.getAdvancedOptionsButton().isEnabled()).isFalse();
    optionsModel.systemImage().setValue(new SystemImageDescription(mySnapshotSystemImage));
    optionsStep.updateSystemImageData();

    icon = optionsStep.getSystemImageIcon();
    assertNotNull(icon);
    assertThat(optionsStep.getAdvancedOptionsButton().isEnabled()).isTrue();
    assertThat(optionsStep.canGoForward().get()).isTrue();
  }

  @Test
  public void populateSnapshotList() throws Exception {
    Path snapAvdDir = InMemoryFileSystems.createInMemoryFileSystemAndFolder("proto_avd");
    AvdInfo snapshotAvdInfo =
      new AvdInfo(Paths.get("ini"), snapAvdDir, mySnapshotSystemImage, myPropertiesMap, null);
    AvdOptionsModel optionsModel = new AvdOptionsModel(snapshotAvdInfo);

    var optionsStep = new ConfigureAvdOptionsStep(myRule.getProject(), optionsModel, newSkinComboBox());
    Disposer.register(myRule.getTestRootDisposable(), optionsStep);

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

    assertEquals(List.of("snapSelected", "snapOldest", "snapNewest"), optionsStep.getSnapshotNamesList("snapSelected"));
  }

  @NotNull
  private SkinComboBox newSkinComboBox() {
    @SuppressWarnings("unchecked")
    var callback = (FutureCallback<Collection<Skin>>)Mockito.mock(FutureCallback.class);

    return new SkinComboBox(myRule.getProject(), new SkinComboBoxModel(List::of, model -> callback));
  }
}
