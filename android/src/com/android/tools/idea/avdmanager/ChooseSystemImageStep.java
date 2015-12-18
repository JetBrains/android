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

import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.DEVICE_DEFINITION_KEY;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.IS_IN_EDIT_MODE_KEY;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.SYSTEM_IMAGE_KEY;

/**
 * Wizard step for selecting a {@link SystemImage} from the installed images in the SDK.
 */
public class ChooseSystemImageStep extends DynamicWizardStepWithDescription
  implements SystemImageList.SystemImageSelectionListener {
  private SystemImageList mySystemImageList;
  private JPanel myPanel;
  private SystemImagePreview mySystemImagePreview;
  private Device myCurrentDevice;
  private Project myProject;

  public ChooseSystemImageStep(@Nullable Project project, @Nullable Disposable parentDisposable) {
    super(parentDisposable);
    setBodyComponent(myPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), createStepBody());

    mySystemImageList.addSelectionListener(this);
    // We want to filter out any system images which are incompatible with our device
    Predicate<SystemImageDescription> filter = new Predicate<SystemImageDescription>() {
      @Override
      public boolean apply(SystemImageDescription input) {
        return systemImageMatchesDevice(input, myCurrentDevice);
      }
    };
    mySystemImageList.setFilter(filter);
    mySystemImageList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
  }

  public static boolean systemImageMatchesDevice(SystemImageDescription image, Device device) {
    if (device == null || image == null) {
      return false;
    }
    String deviceTagId = device.getTagId();
    IdDisplay inputTag = image.getTag();
    if (inputTag == null) {
      return true;
    }

    // Unknown/generic device?
    if (deviceTagId == null || deviceTagId.equals(SystemImage.DEFAULT_TAG.getId())) {
      // If so include all system images, except those we *know* not to match this type
      // of device. Rather than just checking "inputTag.getId().equals(SystemImage.DEFAULT_TAG.getId())"
      // here (which will filter out system images with a non-default tag, such as the Google API
      // system images (see issue #78947), we instead deliberately skip the other form factor images

      return inputTag.getId().equals(SystemImage.DEFAULT_TAG.getId()) ||
             !inputTag.equals(AvdWizardConstants.TV_TAG) && !inputTag.equals(AvdWizardConstants.WEAR_TAG);
    }

    return deviceTagId.equals(inputTag.getId());
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
    mySystemImageList.refreshLocalImagesSynchronously();
    if (selectedImage != null) {
      mySystemImageList.setSelectedImage(selectedImage);
    } else {
      mySystemImageList.selectDefaultImage();
    }
    mySystemImageList.refreshImages(false);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return AvdWizardConstants.CHOOSE_SYSTEM_IMAGE_STEP;
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

  private void createUIComponents() {
    mySystemImageList = new SystemImageList(myProject);
    mySystemImagePreview = new SystemImagePreview(getDisposable());
  }
}
