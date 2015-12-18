package org.jetbrains.android.newProject;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ArrayUtil;
import com.android.tools.idea.run.TargetSelectionMode;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApplicationModifiedSettingsStep extends AndroidModifiedSettingsStep {
  private final ComboBox myTargetDeviceCombo;

  private static final String TARGET_DO_NOT_CREATE_RUN_CONF = AndroidBundle.message("deployment.target.settings.wizard.configure.later");
  private static final String TARGET_SHOW_CHOOSER_DIALOG = AndroidBundle.message("deployment.target.settings.wizard.show.dialog");
  private static final String TARGET_USB_DEVICE = AndroidBundle.message("deployment.target.settings.wizard.usb.device");
  private static final String TARGET_EMULATOR = AndroidBundle.message("deployment.target.settings.wizard.emulator");

  @NonNls private static final String TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY = "ANDROID_TARGET_SELECTION_MODE_FOR_NEW_MODULE";

  AndroidApplicationModifiedSettingsStep(@NotNull AndroidModuleBuilder builder, @NotNull SettingsStep settingsStep) {
    super(builder, settingsStep);
    final String applicationName = builder.getApplicationName();

    if (applicationName != null && applicationName.length() > 0) {
      settingsStep.getModuleNameField().setText(applicationName);
    }
    final String[] items = {TARGET_DO_NOT_CREATE_RUN_CONF, TARGET_SHOW_CHOOSER_DIALOG,
      TARGET_USB_DEVICE, TARGET_EMULATOR};
    myTargetDeviceCombo = new ComboBox(items);
    settingsStep.addSettingsField("\u001BTarget device: ", myTargetDeviceCombo);
    final String prevTargetMode = PropertiesComponent.getInstance().getValue(TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY);
    myTargetDeviceCombo.setSelectedItem(prevTargetMode != null && ArrayUtil.contains(prevTargetMode, items)
                                        ? prevTargetMode
                                        : TARGET_SHOW_CHOOSER_DIALOG);
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    TargetSelectionMode targetSelectionMode = null;
    final Object selectedItem = myTargetDeviceCombo.getSelectedItem();

    if (TARGET_EMULATOR.equals(selectedItem)) {
      targetSelectionMode = TargetSelectionMode.EMULATOR;
    }
    else if (TARGET_SHOW_CHOOSER_DIALOG.equals(selectedItem)) {
      targetSelectionMode = TargetSelectionMode.SHOW_DIALOG;
    }
    else if (TARGET_USB_DEVICE.equals(selectedItem)) {
      targetSelectionMode = TargetSelectionMode.USB_DEVICE;
    }
    myBuilder.setTargetSelectionMode(targetSelectionMode);

    if (selectedItem != null) {
      PropertiesComponent.getInstance().setValue(TARGET_SELECTION_MODE_FOR_NEW_MODULE_PROPERTY, selectedItem.toString());
    }
  }
}
