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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.util.ProxySettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.PortField;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

import static com.android.tools.idea.gradle.util.ProxySettings.HTTPS_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTP_PROXY_TYPE;

public class ProxySettingsDialog extends DialogWrapper {
  private static final String SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME = "show.do.not.copy.http.proxy.settings.to.gradle";

  private Project myProject;
  private JPanel myPane;

  private JTextField myHttpProxyLoginTextField;
  private JPasswordField myHttpProxyPasswordTextField;
  private JCheckBox myHttpProxyAuthCheckBox;
  private PortField myHttpProxyPortTextField;
  private JTextField myHttpProxyHostTextField;
  private RawCommandLineEditor myHttpProxyExceptions;

  private JTextField myHttpsProxyLoginTextField;
  private JPasswordField myHttpsProxyPasswordTextField;
  private JCheckBox myHttpsProxyAuthCheckBox;
  private PortField myHttpsProxyPortTextField;
  private JTextField myHttpsProxyHostTextField;
  private RawCommandLineEditor myHttpsProxyExceptions;

  private JCheckBox myEnableHTTPSProxyCheckBox;
  private JPanel myHttpsProxyPanel;
  private JPanel myHttpProxyPanel;
  private JBLabel myMessageTextLabel;

  public ProxySettingsDialog(@NotNull Project project, @NotNull ProxySettings httpProxySettings) {
    super(false);
    myProject = project;
    setTitle("Proxy Settings");
    setDoNotAskOption(new PropertyBasedDoNotAskOption(project, SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME));
    init();

    enableHttpsProxy(false);
    enableHttpProxyAuth(false);
    enableHttpsProxyAuth(false);

    myMessageTextLabel.setText("<html>Android Studio is configured to use a HTTP proxy." +
                               "Gradle may need these HTTP proxy settings to access the Internet (e.g. for downloading dependencies.)<p><p>" +
                               "Would you like to copy the IDE's proxy configuration to project's gradle.properties file?<p><p>" +
                               "For more details, please refer to the " +
                               "<a href=https://developer.android.com/tools/studio/studio-config.html#proxy>developers site</a>.</html>");

    myHttpProxyHostTextField.setText(httpProxySettings.getHost());
    myHttpProxyPortTextField.setNumber(httpProxySettings.getPort());
    myHttpProxyAuthCheckBox.setSelected(httpProxySettings.getUser() != null);
    myHttpProxyExceptions.setText(httpProxySettings.getExceptions());
    if (httpProxySettings.getExceptions() != null) {
      myHttpProxyExceptions.setText(httpProxySettings.getExceptions());
    }
    if (httpProxySettings.getUser() != null) {
      myHttpProxyLoginTextField.setText(httpProxySettings.getUser());
      enableHttpProxyAuth(true);
    }
    if (httpProxySettings.getPassword() != null) {
      myHttpProxyPasswordTextField.setText(httpProxySettings.getPassword());
    }

    myHttpsProxyHostTextField.setText(httpProxySettings.getHost());
    myHttpsProxyPortTextField.setNumber(httpProxySettings.getPort());
    myHttpsProxyAuthCheckBox.setSelected(httpProxySettings.getUser() != null);
    if (httpProxySettings.getExceptions() != null) {
      myHttpsProxyExceptions.setText(httpProxySettings.getExceptions());
      enableHttpsProxyAuth(true);
    }
    if (httpProxySettings.getUser() != null) {
      myHttpsProxyLoginTextField.setText(httpProxySettings.getUser());
    }
    if (httpProxySettings.getPassword() != null) {
      myHttpsProxyPasswordTextField.setText(httpProxySettings.getPassword());
    }

    myEnableHTTPSProxyCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enableHttpsProxy(((JCheckBox) e.getSource()).isSelected());
      }
    });

    myHttpProxyAuthCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enableHttpProxyAuth(((JCheckBox) e.getSource()).isSelected());
      }
    });

    myHttpsProxyAuthCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enableHttpsProxyAuth(((JCheckBox) e.getSource()).isSelected());
      }
    });
  }

  @Override
  public void show() {
    if (PropertiesComponent.getInstance(myProject).getBoolean(SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME, true)) {
      super.show();
    }
    // By default the exit code is CANCEL_EXIT_CODE
  }

  public void applyProxySettings(@NotNull Properties properties) {
    ProxySettings httpProxySetting =
      createProxySettingsFromUI(HTTP_PROXY_TYPE, myHttpProxyHostTextField, myHttpProxyPortTextField, myHttpProxyExceptions,
                                myHttpProxyAuthCheckBox, myHttpProxyLoginTextField, myHttpProxyPasswordTextField);

    httpProxySetting.applyProxySettings(properties);

    if (myEnableHTTPSProxyCheckBox.isSelected()) {
      ProxySettings httpsProxySettings =
        createProxySettingsFromUI(HTTPS_PROXY_TYPE, myHttpsProxyHostTextField, myHttpsProxyPortTextField, myHttpsProxyExceptions,
                                  myHttpsProxyAuthCheckBox, myHttpsProxyLoginTextField, myHttpsProxyPasswordTextField);
      httpsProxySettings.applyProxySettings(properties);
    }
  }

  @NotNull
  private static ProxySettings createProxySettingsFromUI(@NotNull String proxyType,
                                                         @NotNull JTextField proxyHostTextField,
                                                         @NotNull PortField proxyPortTextField,
                                                         @NotNull RawCommandLineEditor proxyExceptions,
                                                         @NotNull JCheckBox proxyAuthCheckBox,
                                                         @NotNull JTextField proxyLoginTextField,
                                                         @NotNull JPasswordField proxyPasswordTextField) {
    ProxySettings proxySettings = new ProxySettings(proxyType);

    proxySettings.setHost(proxyHostTextField.getText());
    proxySettings.setPort(proxyPortTextField.getNumber());
    proxySettings.setExceptions(proxyExceptions.getText());

    if (proxyAuthCheckBox.isSelected()) {
      proxySettings.setUser(proxyLoginTextField.getText());
      proxySettings.setPassword(new String(proxyPasswordTextField.getPassword()));
    }

    return proxySettings;
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPane;
  }

  private void enableHttpsProxy(boolean enabled) {
    myHttpsProxyPanel.setEnabled(enabled);
    myHttpsProxyHostTextField.setEnabled(enabled);
    myHttpsProxyPortTextField.setEnabled(enabled);
    myHttpsProxyExceptions.setEnabled(enabled);

    myHttpsProxyAuthCheckBox.setEnabled(enabled);
    enableHttpsProxyAuth(enabled && myHttpsProxyAuthCheckBox.isSelected());
  }

  private void enableHttpProxyAuth(boolean enabled) {
    myHttpProxyLoginTextField.setEnabled(enabled);
    myHttpProxyPasswordTextField.setEnabled(enabled);
  }

  private void enableHttpsProxyAuth(boolean enabled) {
    myHttpsProxyLoginTextField.setEnabled(enabled);
    myHttpsProxyPasswordTextField.setEnabled(enabled);
  }

}
