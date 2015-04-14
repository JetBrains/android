/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.run.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.android.tools.idea.run.CloudConfiguration.Kind.MATRIX;

/**
 * @author Eugene.Kudelevsky
 */
public class ExtendedDeviceChooserDialog extends DialogWrapper {
  private final Project myProject;
  private final DeviceChooser myDeviceChooser;

  private JPanel myPanel;
  private JRadioButton myChooserRunningDeviceRadioButton;
  private JPanel myDeviceChooserWrapper;
  private JCheckBox myReuseSelectionCheckbox;
  private JRadioButton myRunTestsInGoogleCloudRadioButton;
  private JLabel myCloudConfigurationLabel;
  private JLabel myCloudProjectLabel;
  private CloudConfigurationComboBox myCloudConfigurationCombo;
  private CloudProjectIdLabel myCloudProjectIdLabel;
  private ActionButton myCloudProjectIdUpdateButton;
  private JButton myLaunchEmulatorButton;
  private final CloudConfigurationProvider myCloudConfigurationProvider;


  @NonNls private static final String SELECTED_SERIALS_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_SERIALS";


  public ExtendedDeviceChooserDialog(@NotNull final AndroidFacet facet,
                                     @NotNull IAndroidTarget projectTarget,
                                     boolean multipleSelection,
                                     boolean showReuseDevicesCheckbox,
                                     boolean selectReuseDevicesCheckbox,
                                     boolean showCloudTarget,
                                     @NotNull final String emulatorOptions) {
    super(facet.getModule().getProject(), true, IdeModalityType.PROJECT);

    myCloudConfigurationProvider = CloudConfigurationProvider.getCloudConfigurationProvider();

    setTitle(AndroidBundle.message("choose.device.dialog.title"));

    myProject = facet.getModule().getProject();
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    // TODO: Cloud testing is disabled by default. Before it becomes enabled by default and always shows up in the chooser dialog,
    // we need to make sure that:
    // a) showing it here makes sense in that a significant number of users actually select that option, and
    // b) it is obvious that by choosing this you will incur a cost on your Google account.
    boolean isGoogleCloudRadioButtonShown = CloudConfigurationProvider.isEnabled() && showCloudTarget;

    if (isGoogleCloudRadioButtonShown) {
      myCloudConfigurationCombo.getComboBox().addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateOkButton();
        }
      });

      myCloudConfigurationLabel.setLabelFor(myCloudConfigurationCombo);
    }

    final String[] selectedSerials;
    final String serialsStr = properties.getValue(SELECTED_SERIALS_PROPERTY);
    if (serialsStr != null) {
      selectedSerials = serialsStr.split(" ");
    }
    else {
      selectedSerials = null;
    }

    getOKAction().setEnabled(false);

    myDeviceChooser = new DeviceChooser(multipleSelection, getOKAction(), facet, projectTarget, null);
    Disposer.register(myDisposable, myDeviceChooser);
    myDeviceChooser.addListener(new DeviceChooserListener() {
      @Override
      public void selectedDevicesChanged() {
        updateEnabled();
      }
    });

    myDeviceChooserWrapper.add(myDeviceChooser.getPanel());

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    };
    myChooserRunningDeviceRadioButton.addActionListener(listener);
    myRunTestsInGoogleCloudRadioButton.addActionListener(listener);

    init();

    myDeviceChooser.init(selectedSerials);

    // Always select the first radio button (if present) since it handles both choosing and launching new devices.
    myChooserRunningDeviceRadioButton.setSelected(true);

    myLaunchEmulatorButton.setIcon(AllIcons.General.Add);
    myLaunchEmulatorButton.addActionListener(new LaunchDeviceActionListener(facet, emulatorOptions));

    myReuseSelectionCheckbox.setVisible(showReuseDevicesCheckbox);
    myReuseSelectionCheckbox.setSelected(selectReuseDevicesCheckbox);

    // Set facet after all other initializations.
    if (isGoogleCloudRadioButtonShown) {
      myCloudProjectIdLabel.setFacet(facet);
      myCloudConfigurationCombo.setFacet(facet);
    }

    updateDialogComponentsVisibility(isGoogleCloudRadioButtonShown);
    updateEnabled();
  }

  private class LaunchDeviceActionListener implements ActionListener {
    private static final String EMULATOR = "Local Emulator";
    private static final String CLOUD_DEVICE = "Cloud Device";

    private final AndroidFacet myFacet;
    private final String myEmulatorOptions;

    public LaunchDeviceActionListener(AndroidFacet facet, String emulatorOptions) {
      myFacet = facet;
      myEmulatorOptions = emulatorOptions;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (!CloudConfigurationProvider.isEnabled()) {
        promptToLaunchEmulator();
        return;
      }

      ListPopup popup = JBPopupFactory.getInstance()
        .createListPopup(new BaseListPopupStep<String>("Launch a Device", new String[]{EMULATOR, CLOUD_DEVICE}) {
          @Override
          public PopupStep onChosen(String selectedValue, boolean finalChoice) {
            if (selectedValue.equals(EMULATOR)) {
              doFinalStep(new Runnable() {
                @Override
                public void run() {
                  promptToLaunchEmulator();
                }
              });
            }
            else if (selectedValue.equals(CLOUD_DEVICE)) {
              doFinalStep(new Runnable() {
                @Override
                public void run() {
                  promptToLaunchCloudDevice();
                }
              });
            }
            return FINAL_CHOICE;
          }
        });

      popup.showUnderneathOf(myLaunchEmulatorButton);
    }

    private void promptToLaunchEmulator() {
      LaunchEmulatorDialog dialog = new LaunchEmulatorDialog(myFacet);
      dialog.show();
      if (dialog.isOK()) {
        final String avdName = dialog.getSelectedAvd();
        if (avdName != null) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              myFacet.launchEmulator(avdName, myEmulatorOptions);
            }
          });
        }
      }
    }

    private void promptToLaunchCloudDevice() {
      final LaunchCloudDeviceDialog dialog = new LaunchCloudDeviceDialog(myFacet);
      dialog.show();
      if (dialog.isOK()) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            myCloudConfigurationProvider
              .launchCloudDevice(dialog.getSelectedMatrixConfigurationId(), dialog.getChosenCloudProjectId(), myFacet);
          }
        });
      }
    }
  }

  private void updateDialogComponentsVisibility(boolean isGoogleCloudRadioButtonShown) {
    // It is visible only if there is an alternative option, which is to run tests in the cloud.
    myChooserRunningDeviceRadioButton.setVisible(isGoogleCloudRadioButtonShown);

    myRunTestsInGoogleCloudRadioButton.setVisible(isGoogleCloudRadioButtonShown);
    myCloudConfigurationCombo.setVisible(isGoogleCloudRadioButtonShown);
    myCloudConfigurationLabel.setVisible(isGoogleCloudRadioButtonShown);
    myCloudProjectLabel.setVisible(isGoogleCloudRadioButtonShown);
    myCloudProjectIdLabel.setVisible(isGoogleCloudRadioButtonShown);
    myCloudProjectIdUpdateButton.setVisible(isGoogleCloudRadioButtonShown);
  }

  private void updateOkButton() {
    if (myRunTestsInGoogleCloudRadioButton.isSelected()) {
      getOKAction().setEnabled(isValidGoogleCloudSelection());
    } else {
      getOKAction().setEnabled(getSelectedDevices().length > 0);
    }
  }

  private boolean isValidGoogleCloudSelection() {
    CloudConfiguration selection = (CloudConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
    return selection != null && selection.getDeviceConfigurationCount() > 0 && myCloudProjectIdLabel.isProjectSpecified();
  }

  private void updateEnabled() {
    myCloudConfigurationCombo.setEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
    myCloudConfigurationLabel.setEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
    myCloudProjectLabel.setEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
    myCloudProjectIdLabel.setEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
    myCloudProjectIdUpdateButton.setEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());

    myDeviceChooser.setEnabled(myChooserRunningDeviceRadioButton.isSelected());
    myLaunchEmulatorButton.setEnabled(myChooserRunningDeviceRadioButton.isSelected() || !myChooserRunningDeviceRadioButton.isVisible());
    updateOkButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceChooser.getPreferredFocusComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.android.chooseDevice";
  }

  @Override
  protected void doOKAction() {
    myDeviceChooser.finish();

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    properties.setValue(SELECTED_SERIALS_PROPERTY, AndroidRunningState.toString(myDeviceChooser.getSelectedDevices()));

    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidExtendedDeviceChooserDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }

  public boolean isCloudTestOptionSelected() {
    return myRunTestsInGoogleCloudRadioButton.isSelected();
  }

  public int getSelectedMatrixConfigurationId() {
    CloudConfiguration selection = (CloudConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
    if (selection == null) {
      return -1;
    }
    return selection.getId();
  }

  public String getChosenCloudProjectId() {
    return myCloudProjectIdLabel.getText();
  }

  public boolean useSameDevicesAgain() {
    return myReuseSelectionCheckbox.isSelected();
  }

  private void createUIComponents() {
    myCloudProjectIdLabel = new CloudProjectIdLabel(MATRIX);

    AnAction action = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myCloudConfigurationProvider == null) {
          return;
        }

        String selectedProjectId =
          myCloudConfigurationProvider.openCloudProjectConfigurationDialog(myProject, myCloudProjectIdLabel.getText());

        if (selectedProjectId != null) {
          myCloudProjectIdLabel.updateCloudProjectId(selectedProjectId);
          updateOkButton();
        }
      }

      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(AllIcons.General.Settings);
      }
    };

    myCloudProjectIdUpdateButton =
      new ActionButton(action, new PresentationFactory().getPresentation(action), "MyPlace", new Dimension(25, 25));

    myCloudConfigurationCombo = new CloudConfigurationComboBox(MATRIX);
    Disposer.register(myDisposable, myCloudConfigurationCombo);
  }
}
