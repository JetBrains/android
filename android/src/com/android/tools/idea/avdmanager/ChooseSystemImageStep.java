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
import com.android.tools.idea.wizard.DynamicWizardStepWithHeaderAndDescription;
import com.android.tools.idea.wizard.WizardConstants;
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
public class ChooseSystemImageStep extends DynamicWizardStepWithHeaderAndDescription
  implements SystemImageList.SystemImageSelectionListener {
  private SystemImageList mySystemImageList;
  private JPanel myPanel;
  private SystemImagePreview mySystemImagePreview;
  private Device myCurrentDevice;
  private Project myProject;

  public ChooseSystemImageStep(@Nullable Project project, @Nullable Disposable parentDisposable) {
    super("System Image", "Select a system image", null, parentDisposable);
    mySystemImageList.addSelectionListener(this);
    // We want to filter out any system images which are incompatible with our device
    Predicate<AvdWizardConstants.SystemImageDescription> filter = new Predicate<AvdWizardConstants.SystemImageDescription>() {
      @Override
      public boolean apply(AvdWizardConstants.SystemImageDescription input) {
        if (myCurrentDevice == null) {
          return true;
        }
        String deviceTagId = myCurrentDevice.getTagId();
        if (deviceTagId == null || deviceTagId.equals(SystemImage.DEFAULT_TAG.getId())) {
          return input.getTag() == null || input.getTag().getId().equals(SystemImage.DEFAULT_TAG.getId());
        }
        return deviceTagId.equals(input.getTag().getId());
      }
    };
    mySystemImageList.setFilter(filter);
    mySystemImageList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    setBodyComponent(myPanel);
  }

  @Override
  public boolean validate() {
    return myState.get(SYSTEM_IMAGE_KEY) != null;
  }

  @Override
  public boolean isStepVisible() {
    Boolean isInEditMode = myState.get(IS_IN_EDIT_MODE_KEY);
    if (isInEditMode != null && isInEditMode) {
      return myState.get(SYSTEM_IMAGE_KEY) == null;
    } else {
      return true;
    }
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    myCurrentDevice = myState.get(DEVICE_DEFINITION_KEY);
    AvdWizardConstants.SystemImageDescription selectedImage = myState.get(SYSTEM_IMAGE_KEY);
    mySystemImageList.refreshImages(false);
    mySystemImageList.setSelectedImage(selectedImage);
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
  public void onSystemImageSelected(@Nullable AvdWizardConstants.SystemImageDescription systemImage) {
    mySystemImagePreview.setImage(systemImage);
    myState.put(SYSTEM_IMAGE_KEY, systemImage);
  }

  @Nullable
  @Override
  protected JBColor getTitleBackgroundColor() {
    return WizardConstants.ANDROID_NPW_HEADER_COLOR;
  }

  @Nullable
  @Override
  protected JBColor getTitleTextColor() {
    return WizardConstants.ANDROID_NPW_HEADER_TEXT_COLOR;
  }

  private void createUIComponents() {
    mySystemImageList = new SystemImageList(myProject);
  }
}
