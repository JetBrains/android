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

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTPS_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTP_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.ProxySettings.replaceCommasWithPipesAndClean;
import static com.android.tools.idea.gradle.util.ProxySettings.replacePipesWithCommasAndClean;
import static com.android.tools.idea.gradle.util.ParametersListUtil.COMMA_LINE_JOINER;
import static com.android.tools.idea.gradle.util.ParametersListUtil.COMMA_LINE_PARSER;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.util.ProxySettings;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.PortField;
import com.intellij.ui.RawCommandLineEditor;
import java.util.Properties;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProxySettingsDialog extends DialogWrapper {
  private static final String SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME = "show.do.not.copy.http.proxy.settings.to.gradle";
  private final boolean myShouldShowDialog;

  private JPanel myPane;

  private JTextField myHttpProxyLoginTextField;
  private JCheckBox myHttpProxyAuthCheckBox;
  private PortField myHttpProxyPortTextField;
  private JTextField myHttpProxyHostTextField;
  private RawCommandLineEditor myHttpProxyExceptions;

  private JTextField myHttpsProxyLoginTextField;
  private JCheckBox myHttpsProxyAuthCheckBox;
  private PortField myHttpsProxyPortTextField;
  private JTextField myHttpsProxyHostTextField;
  private RawCommandLineEditor myHttpsProxyExceptions;

  private JCheckBox myEnableHttpsProxyCheckBox;
  private JPanel myHttpsProxyPanel;
  private JTextPane myMessageTextLabel;

  public ProxySettingsDialog(@NotNull Project project, @NotNull ProxySettings httpProxySettings) {
    super(project);
    setTitle(AndroidBundle.message("android.proxy.settings.dialog.title"));
    setOKButtonText("Yes");
    setCancelButtonText("No");

    myShouldShowDialog = PropertiesComponent.getInstance(project).getBoolean(SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME, true);
    setDoNotAskOption(new PropertyBasedDoNotAskOption(project, SHOW_DO_NOT_ASK_TO_COPY_PROXY_SETTINGS_PROPERTY_NAME));
    init();

    enableHttpProxyAuth(false);
    enableHttpsProxyAuth(false);

    setUpAsHtmlLabel(myMessageTextLabel);
    myMessageTextLabel.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    String text = AndroidBundle.message("android.proxy.settings.dialog.message", ApplicationNamesInfo.getInstance().getProductName());
    myMessageTextLabel.setText(text);

    myHttpProxyHostTextField.setText(httpProxySettings.getHost());
    myHttpProxyPortTextField.setNumber(httpProxySettings.getPort());
    myHttpProxyAuthCheckBox.setSelected(httpProxySettings.getUser() != null);
    myHttpProxyExceptions.setText(httpProxySettings.getExceptions());

    if (httpProxySettings.getExceptions() != null) {
      myHttpProxyExceptions.setText(replacePipesWithCommasAndClean(httpProxySettings.getExceptions()));
    }
    if (httpProxySettings.getUser() != null) {
      myHttpProxyLoginTextField.setText(httpProxySettings.getUser());
      enableHttpProxyAuth(true);
    }

    myEnableHttpsProxyCheckBox.setSelected(true);
    myHttpsProxyHostTextField.setText(httpProxySettings.getHost());
    myHttpsProxyPortTextField.setNumber(httpProxySettings.getPort());
    myHttpsProxyAuthCheckBox.setSelected(httpProxySettings.getUser() != null);

    if (httpProxySettings.getExceptions() != null) {
      myHttpsProxyExceptions.setText(replacePipesWithCommasAndClean(httpProxySettings.getExceptions()));
    }
    if (httpProxySettings.getUser() != null) {
      myHttpsProxyLoginTextField.setText(httpProxySettings.getUser());
      enableHttpsProxyAuth(true);
    }

    myEnableHttpsProxyCheckBox.addActionListener(e -> {
      Object source = e.getSource();
      if (source == myEnableHttpsProxyCheckBox) {
        enableHttpsProxy(myEnableHttpsProxyCheckBox.isSelected());
      }
    });

    myHttpProxyAuthCheckBox.addActionListener(e -> {
      Object source = e.getSource();
      if (source == myHttpProxyAuthCheckBox) {
        enableHttpProxyAuth(myHttpProxyAuthCheckBox.isSelected());
      }
    });

    myHttpsProxyAuthCheckBox.addActionListener(e -> {
      Object source = e.getSource();
      if (source == myHttpsProxyAuthCheckBox) {
        enableHttpsProxyAuth(myHttpsProxyAuthCheckBox.isSelected());
      }
    });
  }

  @Override
  public void show() {
    if (myShouldShowDialog) {
      super.show();
    }
    else {
      doCancelAction();
    }
  }

  /**
   * Apply proxy settings to properties and return whether or not passwords are needed.
   * @param properties
   * @return {@code true} if authentication is needed but no passwords are defined.
   */
  public boolean applyProxySettings(@NotNull Properties properties) {
    ProxySettings httpProxySettings = createProxySettingsFromUI(HTTP_PROXY_TYPE, myHttpProxyHostTextField, myHttpProxyPortTextField,
                                                               myHttpProxyExceptions, myHttpProxyAuthCheckBox, myHttpProxyLoginTextField);
    boolean hasHttpPassword = properties.containsKey("systemProp.http.proxyPassword");
    boolean hasHttpsPassword = properties.containsKey("systemProp.https.proxyPassword");
    // Prevent clearing password if it is already defined
    httpProxySettings.setPassword(properties.getProperty("systemProp.http.proxyPassword"));
    httpProxySettings.applyProxySettings(properties);
    boolean needsPassword = isNotEmpty(httpProxySettings.getUser()) && !hasHttpPassword;

    if (myEnableHttpsProxyCheckBox.isSelected()) {
      ProxySettings httpsProxySettings = createProxySettingsFromUI(HTTPS_PROXY_TYPE, myHttpsProxyHostTextField, myHttpsProxyPortTextField,
                                                                   myHttpsProxyExceptions, myHttpsProxyAuthCheckBox,
                                                                   myHttpsProxyLoginTextField);
      // Prevent clearing password if it is already defined
      httpsProxySettings.setPassword(properties.getProperty("systemProp.https.proxyPassword"));
      httpsProxySettings.applyProxySettings(properties);
      needsPassword |= isNotEmpty(httpsProxySettings.getUser()) && !hasHttpsPassword;
    }
    return needsPassword;
  }

  private void createUIComponents() {
    myHttpProxyExceptions = new RawCommandLineEditor(COMMA_LINE_PARSER, COMMA_LINE_JOINER);
    myHttpsProxyExceptions = new RawCommandLineEditor(COMMA_LINE_PARSER, COMMA_LINE_JOINER);
  }

  @NotNull
  private static ProxySettings createProxySettingsFromUI(@NotNull String proxyType,
                                                         @NotNull JTextField proxyHostTextField,
                                                         @NotNull PortField proxyPortTextField,
                                                         @NotNull RawCommandLineEditor proxyExceptions,
                                                         @NotNull JCheckBox proxyAuthCheckBox,
                                                         @NotNull JTextField proxyLoginTextField) {
    ProxySettings proxySettings = new ProxySettings(proxyType);

    proxySettings.setHost(proxyHostTextField.getText());
    proxySettings.setPort(proxyPortTextField.getNumber());
    proxySettings.setExceptions(replaceCommasWithPipesAndClean(proxyExceptions.getText()));

    if (proxyAuthCheckBox.isSelected()) {
      proxySettings.setUser(proxyLoginTextField.getText());
      // See http://b/63914231
      proxySettings.setPassword("");
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
  }

  private void enableHttpsProxyAuth(boolean enabled) {
    myHttpsProxyLoginTextField.setEnabled(enabled);
  }

  @VisibleForTesting
  void setHttpProxyHost(@NotNull String value) {
    myHttpProxyHostTextField.setText(value);
  }

  @VisibleForTesting
  void setHttpPortNumber(int value) {
    myHttpProxyPortTextField.setValue(value);
  }

  @VisibleForTesting
  void setHttpProxyException(@NotNull String value) {
    myHttpProxyExceptions.setText(value);
  }

  @VisibleForTesting
  void setHttpProxyAuthenticationEnabled(boolean value) {
    myHttpProxyAuthCheckBox.setSelected(value);
  }

  @VisibleForTesting
  void setHttpProxyLogin(@NotNull String value) {
    myHttpProxyLoginTextField.setText(value);
  }

  @VisibleForTesting
  void setHttpsProxyEnabled(boolean value) {
    myEnableHttpsProxyCheckBox.setSelected(value);
  }

  @VisibleForTesting
  void setHttpsProxyHost(@NotNull String value) {
    myHttpsProxyHostTextField.setText(value);
  }

  @VisibleForTesting
  void setHttpsPortNumber(int value) {
    myHttpsProxyPortTextField.setValue(value);
  }

  @VisibleForTesting
  void setHttpsProxyException(@NotNull String value) {
    myHttpsProxyExceptions.setText(value);
  }

  @VisibleForTesting
  void setHttpsProxyAuthenticationEnabled(boolean value) {
    myHttpsProxyAuthCheckBox.setSelected(value);
  }

  @VisibleForTesting
  void setHttpsProxyLogin(@NotNull String value) {
    myHttpsProxyLoginTextField.setText(value);
  }
}
