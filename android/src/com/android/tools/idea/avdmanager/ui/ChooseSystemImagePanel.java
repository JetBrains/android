/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.sdklib.AndroidVersion.MAX_32_BIT_API;
import static com.android.sdklib.AndroidVersion.MIN_4K_TV_API;
import static com.android.sdklib.AndroidVersion.MIN_EMULATOR_FOLDABLE_DEVICE_API;
import static com.android.sdklib.AndroidVersion.MIN_FOLDABLE_DEVICE_API;
import static com.android.sdklib.AndroidVersion.MIN_FREEFORM_DEVICE_API;
import static com.android.sdklib.AndroidVersion.MIN_PIXEL_4A_DEVICE_API;
import static com.android.sdklib.AndroidVersion.MIN_RECOMMENDED_API;
import static com.android.sdklib.AndroidVersion.MIN_RECOMMENDED_WEAR_API;
import static com.android.sdklib.AndroidVersion.MIN_RECTANGULAR_WEAR_API;
import static com.android.sdklib.AndroidVersion.MIN_RESIZABLE_DEVICE_API;
import static com.android.sdklib.AndroidVersion.VersionCodes.TIRAMISU;

import com.android.repository.Revision;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.avdmanager.ui.SystemImagePreview.ImageRecommendation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.ProductDetails;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Consumer;
import com.intellij.util.system.CpuArch;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ListTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.RowFilter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * UI panel that presents the user with a list of {@link SystemImageDescription}s to choose from.
 *
 * <p>You should register a listener via {@link #addSystemImageListener(Consumer)} to be notified of
 * when the user updates their choice.
 */
public class ChooseSystemImagePanel extends JPanel
  implements SystemImageList.SystemImageSelectionListener, SystemImageListModel.StatusIndicator, Disposable {

  private static final boolean IS_ARM64_HOST_OS = CpuArch.isArm64() ||
                                                  CommonMetricsData.getOsArchitecture() == ProductDetails.CpuArchitecture.X86_ON_ARM;

  private final List<Consumer<SystemImageDescription>> mySystemImageListeners = Lists.newArrayListWithExpectedSize(1);

  private JPanel myPanel;
  private SystemImageList myRecommendedImageList;
  private SystemImageList myPerformantImageList;
  private SystemImageList myOtherImageList;
  private SystemImagePreview mySystemImagePreview;
  private JBTabbedPane myTabPane;
  private JBLabel myStatusLabel;
  private JButton myRefreshButton;
  private AsyncProcessIcon myAsyncIcon;
  private final SystemImageListModel myListModel;

  @Nullable private Device myDevice;
  @Nullable private SystemImageDescription mySystemImage;

  public void setDevice(@Nullable Device device) {
    myDevice = device;
    myListModel.refreshImages(false);
  }

  public ChooseSystemImagePanel(@Nullable Project project,
                                @Nullable Device initialDevice,
                                @Nullable SystemImageDescription initialSystemImage) {
    super(new BorderLayout());
    FormScalingUtil.scaleComponentTree(this.getClass(), myPanel);

    mySystemImage = initialSystemImage;
    myDevice = initialDevice;

    myListModel = new SystemImageListModel(project, this);
    setupImageLists();
    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myRefreshButton.addActionListener(event -> myListModel.refreshImages(true));

    // The center panel contains performant system images that are not recommended, therefore depending on the host system architecture, we
    // either provide x86 or ARM based images.
    //noinspection DialogTitleCapitalization
    myTabPane.setTitleAt(1, IS_ARM64_HOST_OS
                            ? AndroidBundle.message("avd.manager.arm.images")
                            : AndroidBundle.message("avd.manager.x86.images"));

    myTabPane.addChangeListener(event -> previewCurrentTab());

    myRecommendedImageList.addSelectionListener(this);
    myRecommendedImageList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));

    myListModel.refreshLocalImagesSynchronously();
    myListModel.refreshImages(false);
    setSelectedImage(mySystemImage);

    add(myPanel);
  }

  @NotNull
  @VisibleForTesting
  static SystemImageClassification getClassificationForDevice(@NotNull SystemImageDescription image, @Nullable Device theDevice,
                                                              boolean isArm64HostOs) {

    Abi abi = Abi.getEnum(image.getPrimaryAbiType());
    assert abi != null;

    SystemImageClassification classification = getClassificationFromParts(abi,
                                                                          image.getVersion(),
                                                                          image.getTags(),
                                                                          isArm64HostOs);

    if (theDevice != null && !image.getTags().contains(SystemImageTags.WEAR_TAG)) {
      // For non-Wear devices, adjust the recommendation based on Play Store
      if (theDevice.hasPlayStore()) {
        // The device supports Google Play Store. Recommend only system images that also support Play Store.
        if (classification == SystemImageClassification.RECOMMENDED && !image.getSystemImage().hasPlayStore()) {
          classification = (abi == Abi.X86 || abi == Abi.X86_64) ? SystemImageClassification.PERFORMANT : SystemImageClassification.OTHER;
        }
      }
      else {
        // The device does not support Google Play Store. Hide Play Store system images.
        if (image.getSystemImage().hasPlayStore()) {
          classification = SystemImageClassification.FORBIDDEN;
        }
      }
    }
    return classification;
  }

  @NotNull
  private SystemImageClassification getClassification(@NotNull SystemImageDescription image) {
    return getClassificationForDevice(image, myDevice, IS_ARM64_HOST_OS);
  }

  @NotNull
  @VisibleForTesting
  static SystemImageClassification getClassificationFromParts(@NotNull Abi abi,
                                                              @NotNull AndroidVersion androidVersion,
                                                              @NotNull IdDisplay tag,
                                                              boolean isArm64HostOs) {
    return getClassificationFromParts(abi, androidVersion, ImmutableList.of(tag), isArm64HostOs);
  }

  @NotNull
  @VisibleForTesting
  static SystemImageClassification getClassificationFromParts(@NotNull Abi abi,
                                                              @NotNull AndroidVersion androidVersion,
                                                              @NotNull List<IdDisplay> tags,
                                                              boolean isArm64HostOs) {
    int apiLevel = androidVersion.getApiLevel();
    boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;
    boolean isAvdArm = abi == Abi.ARM64_V8A;

    if (!isAvdArm && !isAvdIntel) {
      // None of these system images run natively on supported Android Studio platforms.
      return SystemImageClassification.OTHER;
    }

    if (!androidVersion.isBaseExtension() && androidVersion.getExtensionLevel() != null) {
      // System images that contain extension levels but are not the base SDK should not be placed in RECOMMENDED tab, it should be either
      // PERFORMANT or OTHER.
      return isArm64HostOs == isAvdIntel ? SystemImageClassification.OTHER : SystemImageClassification.PERFORMANT;
    }

    if (isAvdIntel == isArm64HostOs) {
      return SystemImageClassification.OTHER;
    }

    if (SystemImageTags.isWearImage(tags)) {
      // For Wear, recommend based on API level (all Wear have Google APIs)
      return apiLevel >= MIN_RECOMMENDED_WEAR_API ? SystemImageClassification.RECOMMENDED : SystemImageClassification.PERFORMANT;
    }
    if (apiLevel < MIN_RECOMMENDED_API) {
      return SystemImageClassification.PERFORMANT;
    }

    if (!SystemImageTags.hasGoogleApi(tags)) {
      return SystemImageClassification.PERFORMANT;
    }

    // Android TV does not ship x86_64 images at any API level, so we recommend
    // almost all images on the same OS.
    if (SystemImageTags.isTvImage(tags)) {
      if (apiLevel == TIRAMISU) { // Tiramisu is an unsupported Android TV version.
        return SystemImageClassification.PERFORMANT;
      }
      else {
        return SystemImageClassification.RECOMMENDED;
      }
    }

    // Recommend ARM images on ARM hosts.
    if (isArm64HostOs) {
      return SystemImageClassification.RECOMMENDED;
    }

    // Only recommend 32-bit x86 images when API level < 31, or 64-bit x86 images when API level >= 31.
    if (apiLevel <= MAX_32_BIT_API && abi == Abi.X86) {
      return SystemImageClassification.RECOMMENDED;
    }
    else if (apiLevel > MAX_32_BIT_API && abi == Abi.X86_64) {
      return SystemImageClassification.RECOMMENDED;
    }

    return SystemImageClassification.PERFORMANT;
  }

  public static boolean systemImageMatchesDevice(@Nullable SystemImageDescription image, @Nullable Device device) {
    if (device == null || image == null) {
      return false;
    }

    String deviceTagId = device.getTagId();
    String deviceId = device.getId();
    List<IdDisplay> imageTags = image.getTags();

    // Foldable device requires Q preview or API29 and above.
    if (device.getDefaultHardware().getScreen().isFoldable()) {
      if (image.getVersion().getFeatureLevel() < MIN_FOLDABLE_DEVICE_API ||
          image.getVersion().getFeatureLevel() < MIN_EMULATOR_FOLDABLE_DEVICE_API) {
        return false;
      }
    }

    // Freeform display device requires R preview DP2 or API30 and above.
    if (deviceId.equals("13.5in Freeform")) {
      if (image.getVersion().getFeatureLevel() < MIN_FREEFORM_DEVICE_API) {
        return false;
      }
      if ("R".equals(image.getVersion().getCodename())) {
        if (image.getRevision() == null || image.getRevision().compareTo(new Revision(2, 0, 0)) <= 0) {
          return false;
        }
      }
    }

    // pixel 4a requires API30 and above
    if (deviceId.equals(("pixel_4a"))) {
      if (image.getVersion().getFeatureLevel() < MIN_PIXEL_4A_DEVICE_API) {
        return false;
      }
    }

    // Resizable requires API31 and above
    if (deviceId.equals(("resizable"))) {
      if (image.getVersion().getFeatureLevel() < MIN_RESIZABLE_DEVICE_API) {
        return false;
      }
    }

    // TODO: http://b/326294450 - Try doing this in device and system image declarations
    if (!Device.isTablet(device)) {
      if (imageTags.contains(SystemImageTags.TABLET_TAG)) {
        return false;
      }
    }

    // Unknown/generic device?
    if (deviceTagId == null || deviceTagId.equals(SystemImageTags.DEFAULT_TAG.getId())) {
      // If so include all system images, except those we *know* not to match this type
      // of device. Rather than just checking "imageTag.getId().equals(SystemImage.DEFAULT_TAG.getId())"
      // here (which will filter out system images with a non-default tag, such as the Google API
      // system images (see issue #78947), we instead deliberately skip the other form factor images
      return !SystemImageTags.isTvImage(imageTags)
             && !SystemImageTags.isWearImage(imageTags)
             && !SystemImageTags.isAutomotiveImage(imageTags)
             && !imageTags.contains(SystemImageTags.DESKTOP_TAG)
             && !imageTags.contains(SystemImageTags.CHROMEOS_TAG);
    }

    // 4K TV requires at least S (API 31)
    if (deviceId.equals("tv_4k")) {
      if (image.getVersion().getFeatureLevel() < MIN_4K_TV_API) {
        return false;
      }
    }

    // Android TV / Google TV and vice versa
    if (deviceTagId.equals(SystemImageTags.ANDROID_TV_TAG.getId()) || deviceTagId.equals(SystemImageTags.GOOGLE_TV_TAG.getId())) {
      return image.isTvImage();
    }

    // Non-square rectangular Wear OS requires at least P (API 28)
    if (image.isWearImage() && !device.isScreenRound()) {
      Dimension screenSize = device.getScreenSize(ScreenOrientation.PORTRAIT);
      if (screenSize != null && screenSize.getWidth() != screenSize.getHeight()) {
        if (image.getVersion().getFeatureLevel() < MIN_RECTANGULAR_WEAR_API) {
          return false;
        }
      }
    }

    return imageTags.stream().anyMatch(it -> it.getId().equals(deviceTagId));
  }

  private void setupImageLists() {
    setupImageList(myRecommendedImageList);
    setupImageList(myPerformantImageList);
    setupImageList(myOtherImageList);
    setImageListFilters();
  }

  private void setupImageList(@NotNull SystemImageList list) {
    list.setModel(myListModel);
    list.addSelectionListener(this);
    list.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
  }

  private void setImageListFilters() {
    myRecommendedImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.RECOMMENDED));
    myPerformantImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.PERFORMANT));
    myOtherImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.OTHER));
  }

  @Override
  public void onSystemImageSelected(@Nullable SystemImageDescription systemImage) {
    mySystemImagePreview.setImage(systemImage);
    if (systemImage != null && !systemImage.isRemote()) {
      mySystemImage = systemImage;
    }
    else {
      mySystemImage = null;
    }

    for (Consumer<SystemImageDescription> listener : mySystemImageListeners) {
      listener.consume(mySystemImage);
    }
  }

  private void setSelectedImage(@Nullable SystemImageDescription systemImage) {
    if (systemImage != null) {
      SystemImageClassification classification = getClassification(systemImage);
      switch (classification) {
        case RECOMMENDED:
          myRecommendedImageList.setSelectedImage(systemImage);
          myTabPane.setSelectedIndex(0);
          break;
        case PERFORMANT:
          myPerformantImageList.setSelectedImage(systemImage);
          myTabPane.setSelectedIndex(1);
          break;
        default:
          myOtherImageList.setSelectedImage(systemImage);
          myTabPane.setSelectedIndex(2);
          break;
      }
    }
  }

  @Override
  public void onRefreshStart(@NotNull String message) {
    myStatusLabel.setText(message);
    myRefreshButton.setEnabled(false);
    myAsyncIcon.setVisible(true);
  }

  @Override
  public void onRefreshDone(@NotNull String message, boolean partlyDownloaded) {
    myStatusLabel.setText(message);
    myRefreshButton.setEnabled(true);
    myAsyncIcon.setVisible(false);
    myRecommendedImageList.restoreSelection(partlyDownloaded, mySystemImage);
    myPerformantImageList.restoreSelection(partlyDownloaded, mySystemImage);
    myOtherImageList.restoreSelection(partlyDownloaded, mySystemImage);
    previewCurrentTab();
  }

  private void previewCurrentTab() {
    switch (myTabPane.getSelectedIndex()) {
      case 0: // "Recommended"
        myRecommendedImageList.makeListCurrent();
        if (myDevice != null && SystemImageTags.WEAR_TAG.getId().equals(myDevice.getTagId())) {
          mySystemImagePreview.showExplanationForRecommended(ImageRecommendation.RECOMMENDATION_WEAR);
        }
        else if (myDevice != null && myDevice.hasPlayStore()) {
          mySystemImagePreview.showExplanationForRecommended(ImageRecommendation.RECOMMENDATION_GOOGLE_PLAY);
        }
        else {
          mySystemImagePreview.showExplanationForRecommended(ImageRecommendation.RECOMMENDATION_X86);
        }
        break;
      case 1: // Performant images, title either "x86 Images" or "ARM Images"
        myPerformantImageList.makeListCurrent();
        mySystemImagePreview.showExplanationForRecommended(ImageRecommendation.RECOMMENDATION_NONE);
        break;
      default: // "Other images"
        myOtherImageList.makeListCurrent();
        mySystemImagePreview.showExplanationForRecommended(ImageRecommendation.RECOMMENDATION_NONE);
        break;
    }
  }

  private void createUIComponents() {
    myAsyncIcon = new AsyncProcessIcon("refresh images");
    myRecommendedImageList = new SystemImageList();
    myPerformantImageList = new SystemImageList();
    myOtherImageList = new SystemImageList();
    mySystemImagePreview = new SystemImagePreview(this);
  }

  public void addSystemImageListener(@NotNull Consumer<SystemImageDescription> onSystemImageSelected) {
    mySystemImageListeners.add(onSystemImageSelected);
    onSystemImageSelected.consume(mySystemImage);
  }

  @Override
  public void dispose() {
    mySystemImageListeners.clear();
  }

  @Nullable
  public SystemImageDescription getSystemImage() {
    return mySystemImage;
  }

  @VisibleForTesting
  enum SystemImageClassification {
    RECOMMENDED,
    PERFORMANT,
    OTHER,
    FORBIDDEN
  }

  private class ClassificationRowFilter extends RowFilter<ListTableModel<SystemImageDescription>, Integer> {
    private final SystemImageClassification myClassification;

    public ClassificationRowFilter(@NotNull SystemImageClassification classification) {
      myClassification = classification;
    }

    @Override
    public boolean include(Entry<? extends ListTableModel<SystemImageDescription>, ? extends Integer> entry) {
      SystemImageDescription image = myListModel.getRowValue(entry.getIdentifier());
      return getClassification(image) == myClassification &&
             systemImageMatchesDevice(image, myDevice) &&
             versionSupported(image);
    }

    private boolean versionSupported(@NotNull SystemImageDescription image) {
      return image.getVersion().getApiLevel() > 2;
    }
  }
}
