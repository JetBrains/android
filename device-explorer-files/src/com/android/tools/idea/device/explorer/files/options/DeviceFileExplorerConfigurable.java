/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.options;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings;
import com.android.tools.idea.device.explorer.files.DeviceExplorerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceFileExplorerConfigurable implements SearchableConfigurable {
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myDownloadLocation;

  DeviceFileExplorerConfigurable() {
    setupUI();
    myDownloadLocation.addBrowseFolderListener(null, new FileChooserDescriptor(false, true, false, false, false, false)
      .withTitle(DeviceExplorerBundle.message("dialog.title.device.file.explorer.download.location")));
  }

  @NotNull
  @Override
  public String getId() {
    return "device.explorer";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return !DeviceExplorerSettings.getInstance().getDownloadLocation().equals(myDownloadLocation.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    // Validate the path for download location
    Path path = Paths.get(getDownloadLocation());
    if (Files.isDirectory(path)) {
      DeviceExplorerSettings.getInstance().setDownloadLocation(path.toString());
    }
    else {
      throw new ConfigurationException(DeviceExplorerBundle.message("dialog.message.path.must.be.existing.directory"),
                                       DeviceExplorerBundle.message("dialog.title.invalid.path"));
    }
  }

  @Override
  public void reset() {
    myDownloadLocation.setText(DeviceExplorerSettings.getInstance().getDownloadLocation());
  }

  @Override
  public void disposeUIResources() {
  }

  @NlsContexts.ConfigurableName
  @Override
  public String getDisplayName() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return DeviceExplorerBundle.message("configurable.name.device.file.explorer");
    }
    else {
      return DeviceExplorerBundle.message("configurable.name.android.device.file.explorer");
    }
  }

  @NotNull
  private String getDownloadLocation() {
    return myDownloadLocation.getText();
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myDownloadLocation = new TextFieldWithBrowseButton();
    myDownloadLocation.setText("");
    myContentPanel.add(myDownloadLocation, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    loadLabelText(label1, getMessageFromBundle("messages/AndroidBundle", "label.download.location"));
    myContentPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
  }

  private static Method cachedGetBundleMethod = null;

  private String getMessageFromBundle(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if (cachedGetBundleMethod == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        cachedGetBundleMethod = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)cachedGetBundleMethod.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  private void loadLabelText(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }
}