/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Objects;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.android.tools.idea.avdmanager.AvdManagerConnection.GOOGLE_APIS_TAG;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * Wizard step for selecting a {@link SystemImage} from the installed images in the SDK.
 */
public class ChooseSystemImageStep extends DynamicWizardStepWithDescription
  implements SystemImageList.SystemImageSelectionListener, SystemImageListModel.StatusIndicator {
  private SystemImageList myRecommendedImageList;
  private JPanel myPanel;
  private SystemImagePreview mySystemImagePreview;
  private JBTabbedPane myTabPane;
  private SystemImageList myX86ImageList;
  private SystemImageList myOtherImageList;
  private JBLabel myStatusLabel;
  private JButton myRefreshButton;
  private AsyncProcessIcon myAsyncIcon;
  private Device myCurrentDevice;
  private SystemImageListModel myModel;

  private enum SystemImageClassification {RECOMMENDED, X86, OTHER}

  public ChooseSystemImageStep(@Nullable Project project, @Nullable Disposable parentDisposable) {
    super(parentDisposable);
    myModel = new SystemImageListModel(project, this);
    setupImageLists();
    setBodyComponent(myPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), createStepBody());
    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myModel.refreshImages(true);
      }
    });
    myTabPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent changeEvent) {
        previewCurrentTab();
      }
    });
  }

  @NotNull
  private static SystemImageClassification getClassification(@NotNull SystemImageDescription image) {
    Abi abi = Abi.getEnum(image.getAbiType());
    boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;
    if (!isAvdIntel) {
      return SystemImageClassification.OTHER;
    }
    int apiLevel = image.getVersion().getApiLevel();
    if (apiLevel == 21) {
      // The emulator is not working very well on older system images.
      // Remove this when they are fully supported.
      return SystemImageClassification.X86;
    }
    if (TAGS_WITH_GOOGLE_API.contains(image.getTag())) {
      return SystemImageClassification.RECOMMENDED;
    }
    return SystemImageClassification.X86;
  }

  private void setupImageLists() {
    setupImageList(myRecommendedImageList);
    setupImageList(myX86ImageList);
    setupImageList(myOtherImageList);
    setImageListFilters();
  }

  private void setupImageList(@NotNull SystemImageList list) {
    list.setModel(myModel);
    list.addSelectionListener(this);
    list.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
  }

  private void setImageListFilters() {
    myRecommendedImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.RECOMMENDED));
    myX86ImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.X86));
    myOtherImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.OTHER));
  }

  public static boolean systemImageMatchesDevice(@Nullable SystemImageDescription image, @Nullable Device device) {
    if (device == null || image == null) {
      return false;
    }
    String deviceTagId = device.getTagId();
    IdDisplay imageTag = image.getTag();

    // Unknown/generic device?
    if (deviceTagId == null || deviceTagId.equals(SystemImage.DEFAULT_TAG.getId())) {
      // If so include all system images, except those we *know* not to match this type
      // of device. Rather than just checking "imageTag.getId().equals(SystemImage.DEFAULT_TAG.getId())"
      // here (which will filter out system images with a non-default tag, such as the Google API
      // system images (see issue #78947), we instead deliberately skip the other form factor images

      return imageTag.equals(SystemImage.DEFAULT_TAG) || !imageTag.equals(TV_TAG) && !imageTag.equals(WEAR_TAG);
    }

    return deviceTagId.equals(imageTag.getId());
  }

  @Override
  public boolean validate() {
    return myState.get(SYSTEM_IMAGE_KEY) != null;
  }

  @Override
  public boolean isStepVisible() {
    return !myState.getNotNull(IS_IN_EDIT_MODE_KEY, false) || !myState.containsKey(SYSTEM_IMAGE_KEY);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    Device newDevice = myState.get(DEVICE_DEFINITION_KEY);
    String newTag = newDevice == null ? null : newDevice.getTagId();
    String oldTag = myCurrentDevice == null ? null : myCurrentDevice.getTagId();
    // If we've changed device types, the previously selected device is invalid
    if (!Objects.equal(newTag, oldTag)) {
      myState.remove(SYSTEM_IMAGE_KEY);
    }
    myCurrentDevice = newDevice;
    SystemImageDescription selectedImage = myState.get(SYSTEM_IMAGE_KEY);
    // synchronously get the local images in case one should already be selected
    myModel.refreshLocalImagesSynchronously();
    myModel.refreshImages(false);
    setSelectedImage(selectedImage);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return CHOOSE_SYSTEM_IMAGE_STEP;
  }

  @Override
  public void onSystemImageSelected(@Nullable SystemImageDescription systemImage) {
    mySystemImagePreview.setImage(systemImage);

    if (systemImage != null && !systemImage.isRemote()) {
      myState.put(SYSTEM_IMAGE_KEY, systemImage);
    } else {
      myState.remove(SYSTEM_IMAGE_KEY);
    }
  }

  private void setSelectedImage(@Nullable SystemImageDescription image) {
    if (image != null) {
      SystemImageClassification classification = getClassification(image);
      switch (classification) {
        case RECOMMENDED:
          myRecommendedImageList.setSelectedImage(image);
          myTabPane.setSelectedIndex(0);
          break;
        case X86:
          myX86ImageList.setSelectedImage(image);
          myTabPane.setSelectedIndex(1);
          break;
        default:
          myOtherImageList.setSelectedImage(image);
          myTabPane.setSelectedIndex(2);
          break;
      }
    }
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "System Image";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Select a system image";
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
    myRecommendedImageList.restoreSelection(partlyDownloaded);
    myX86ImageList.restoreSelection(partlyDownloaded);
    myOtherImageList.restoreSelection(partlyDownloaded);
    previewCurrentTab();
  }

  private void previewCurrentTab() {
    switch (myTabPane.getSelectedIndex()) {
      case 0:
        myRecommendedImageList.makeListCurrent();
        mySystemImagePreview.showExplanationForRecommended(true);
        break;
      case 1:
        myX86ImageList.makeListCurrent();
        mySystemImagePreview.showExplanationForRecommended(false);
        break;
      default:
        myOtherImageList.makeListCurrent();
        mySystemImagePreview.showExplanationForRecommended(false);
        break;
    }
  }

  private void createUIComponents() {
    myAsyncIcon = new AsyncProcessIcon("refresh images");
    myRecommendedImageList = new SystemImageList();
    myX86ImageList = new SystemImageList();
    myOtherImageList = new SystemImageList();
    mySystemImagePreview = new SystemImagePreview(getDisposable());
  }

  private class ClassificationRowFilter extends RowFilter<ListTableModel<SystemImageDescription>, Integer> {
    private final SystemImageClassification myClassification;

    public ClassificationRowFilter(@NotNull SystemImageClassification classification) {
      myClassification = classification;
    }

    @Override
    public boolean include(Entry<? extends ListTableModel<SystemImageDescription>, ? extends Integer> entry) {
      SystemImageDescription image = myModel.getRowValue(entry.getIdentifier());
      return getClassification(image) == myClassification &&
             systemImageMatchesDevice(image, myCurrentDevice) &&
             versionSupported(image);
    }

    private boolean versionSupported(@NotNull SystemImageDescription image) {
      // https://code.google.com/p/android/issues/detail?id=187938
      return image.getVersion().getApiLevel() != 15;
    }
  }
}
