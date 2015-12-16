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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Optional;
import com.intellij.icons.AllIcons;
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

/**
 * Wizard step for selecting a {@link SystemImage} from the installed images in the SDK.
 *
 * The whole purpose of this step is to select a single image from a list of possible images, and
 * instead of relying on a model, it takes an a constructor argument to store the selected result.
 */
public class ChooseSystemImageStep extends ModelWizardStep.WithoutModel
  implements SystemImageList.SystemImageSelectionListener, SystemImageListModel.StatusIndicator {
  private JPanel myPanel;
  private SystemImageList myRecommendedImageList;
  private SystemImageList myX86ImageList;
  private SystemImageList myOtherImageList;
  private SystemImagePreview mySystemImagePreview;
  private JBTabbedPane myTabPane;
  private JBLabel myStatusLabel;
  private JButton myRefreshButton;
  private AsyncProcessIcon myAsyncIcon;
  private SystemImageListModel myListModel;
  private StudioWizardStepPanel myStudioWizardStepPanel;
  private ValidatorPanel myValidatorPanel;

  private OptionalProperty<SystemImageDescription> mySystemImage = new OptionalValueProperty<SystemImageDescription>();
  private OptionalProperty<Device> myDevice = new OptionalValueProperty<Device>();

  public ChooseSystemImageStep(@Nullable Project project,
                               @NotNull OptionalProperty<Device> device,
                               @NotNull OptionalProperty<SystemImageDescription> systemImage) {
    super("System Image");
    myStudioWizardStepPanel = new StudioWizardStepPanel(myPanel, "Select a system image");
    myValidatorPanel = new ValidatorPanel(this, myStudioWizardStepPanel);
    mySystemImage = systemImage;
    myDevice = device;

    myListModel = new SystemImageListModel(project, this);
    setupImageLists();
    myRefreshButton.setIcon(AllIcons.Actions.Refresh);
    myRefreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myListModel.refreshImages(true);
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
      // The emulator does not yet work very well on older system images.
      // Remove this when they are fully supported.
      return SystemImageClassification.X86;
    }
    IdDisplay tag = image.getTag();
    if (AvdManagerConnection.GOOGLE_APIS_TAG.equals(tag) || AvdWizardUtils.WEAR_TAG.equals(tag) || AvdWizardUtils.TV_TAG.equals(tag)) {
      return SystemImageClassification.RECOMMENDED;
    }
    return SystemImageClassification.X86;
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
      return imageTag.equals(SystemImage.DEFAULT_TAG) ||
             !imageTag.equals(AvdWizardUtils.TV_TAG) && !imageTag.equals(AvdWizardUtils.WEAR_TAG);
    }
    return deviceTagId.equals(imageTag.getId());
  }

  private void setupImageLists() {
    setupImageList(myRecommendedImageList);
    setupImageList(myX86ImageList);
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
    myX86ImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.X86));
    myOtherImageList.setRowFilter(new ClassificationRowFilter(SystemImageClassification.OTHER));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);

    myRecommendedImageList.addSelectionListener(this);
    myRecommendedImageList.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    myValidatorPanel.registerValidator(mySystemImage, new Validator<Optional<SystemImageDescription>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<SystemImageDescription> value) {
        return (value.isPresent())
               ? Result.OK
               : new Validator.Result(Severity.ERROR, "A system image must be selected to continue.");
      }
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return mySystemImage.isPresent();
  }

  @Override
  protected void onEntering() {
    assert myDevice.get().isPresent();
    // synchronously get the local images in case one should already be selected
    myListModel.refreshLocalImagesSynchronously();
    myListModel.refreshImages(false);
    setSelectedImage(mySystemImage);
  }

  @Override
  public void onSystemImageSelected(@Nullable SystemImageDescription systemImage) {
    mySystemImagePreview.setImage(systemImage);
    if (systemImage != null && !systemImage.isRemote()) {
      mySystemImage.setValue(systemImage);
    }
    else {
      mySystemImage.clear();
    }
  }

  private void setSelectedImage(OptionalProperty<SystemImageDescription> imageProperty) {
    if (imageProperty.get().isPresent()) {
      SystemImageDescription image = imageProperty.getValue();
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
    myX86ImageList.restoreSelection(partlyDownloaded, mySystemImage);
    myOtherImageList.restoreSelection(partlyDownloaded, mySystemImage);
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
    mySystemImagePreview = new SystemImagePreview(this);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  private enum SystemImageClassification {
    RECOMMENDED,
    X86,
    OTHER
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
             systemImageMatchesDevice(image, myDevice.getValueOrNull()) &&
             versionSupported(image);
    }

    private boolean versionSupported(@NotNull SystemImageDescription image) {
      // https://code.google.com/p/android/issues/detail?id=187938
      return image.getVersion().getApiLevel() != 15;
    }
  }
}
