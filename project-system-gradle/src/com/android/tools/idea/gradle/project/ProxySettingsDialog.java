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
import static com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge.HTTPS_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge.HTTP_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge.replaceCommasWithPipesAndClean;
import static com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge.replacePipesWithCommasAndClean;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.util.IdeGradleProxySettingsBridge;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PortField;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.Function;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.TitledBorder;
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

  public ProxySettingsDialog(@NotNull Project project, @NotNull IdeGradleProxySettingsBridge httpProxySettings, boolean ideProxyUsed) {
    super(project);
    setupUI();
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
    myMessageTextLabel.setText(generateDialogText(ideProxyUsed));

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
   * Apply proxy settings to properties and return whether passwords are needed.
   * @param properties where the settings will be applied to.
   * @return {@code true} if authentication is needed but no passwords are defined.
   */
  public boolean applyProxySettings(@NotNull Properties properties) {
    IdeGradleProxySettingsBridge
      httpProxySettings = createProxySettingsFromUI(HTTP_PROXY_TYPE, myHttpProxyHostTextField, myHttpProxyPortTextField,
                                                    myHttpProxyExceptions, myHttpProxyAuthCheckBox, myHttpProxyLoginTextField);
    boolean hasHttpPassword = properties.containsKey("systemProp.http.proxyPassword");
    boolean hasHttpsPassword = properties.containsKey("systemProp.https.proxyPassword");
    // Prevent clearing password if it is already defined
    httpProxySettings.setPassword(properties.getProperty("systemProp.http.proxyPassword"));
    httpProxySettings.applyProxySettings(properties);
    boolean needsPassword = isNotEmpty(httpProxySettings.getUser()) && !hasHttpPassword;

    if (myEnableHttpsProxyCheckBox.isSelected()) {
      IdeGradleProxySettingsBridge
        httpsProxySettings = createProxySettingsFromUI(HTTPS_PROXY_TYPE, myHttpsProxyHostTextField, myHttpsProxyPortTextField,
                                                       myHttpsProxyExceptions, myHttpsProxyAuthCheckBox,
                                                       myHttpsProxyLoginTextField);
      // Prevent clearing password if it is already defined
      httpsProxySettings.setPassword(properties.getProperty("systemProp.https.proxyPassword"));
      httpsProxySettings.applyProxySettings(properties);
      needsPassword |= isNotEmpty(httpsProxySettings.getUser()) && !hasHttpsPassword;
    }
    return needsPassword;
  }

  private static final Function<String, List<String>> COMMA_LINE_PARSER = text ->
    Arrays.stream(text.split(",")).map(String::trim).filter(Predicate.not(String::isEmpty)).toList();
  private static final Function<List<String>, String> COMMA_LINE_JOINER = list ->
    StringUtil.join(list, ", ");

  private void createUIComponents() {
    myHttpProxyExceptions = new RawCommandLineEditor(COMMA_LINE_PARSER, COMMA_LINE_JOINER);
    myHttpsProxyExceptions = new RawCommandLineEditor(COMMA_LINE_PARSER, COMMA_LINE_JOINER);
  }

  private void setupUI() {
    createUIComponents();
    myPane = new JPanel();
    myPane.setLayout(new GridLayoutManager(7, 1, new Insets(10, 10, 10, 10), -1, -1));
    myPane.setMaximumSize(new Dimension(-1, -1));
    myPane.setMinimumSize(new Dimension(735, 555));
    myPane.setOpaque(true);
    myPane.setPreferredSize(new Dimension(735, 555));
    myPane.setRequestFocusEnabled(false);
    myPane.setBorder(
      IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEmptyBorder(), "", TitledBorder.DEFAULT_JUSTIFICATION,
                                                               TitledBorder.DEFAULT_POSITION, null, null));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 6, 6), -1, -1));
    myPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEmptyBorder(), "HTTP Proxy",
                                                                              TitledBorder.DEFAULT_JUSTIFICATION,
                                                                              TitledBorder.DEFAULT_POSITION, null, null));
    final JLabel label1 = new JLabel();
    label1.setHorizontalAlignment(4);
    loadLabelText(label1, getMessageFromBundle("messages/UIBundle", "proxy.manual.host"));
    panel1.add(label1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    myHttpProxyAuthCheckBox = new JCheckBox();
    myHttpProxyAuthCheckBox.setMargin(new Insets(2, 1, 2, 2));
    myHttpProxyAuthCheckBox.setSelected(false);
    loadButtonText(myHttpProxyAuthCheckBox, getMessageFromBundle("messages/UIBundle", "proxy.manual.auth"));
    panel1.add(myHttpProxyAuthCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    final JLabel label2 = new JLabel();
    label2.setHorizontalAlignment(4);
    loadLabelText(label2, getMessageFromBundle("messages/UIBundle", "auth.login.label"));
    panel1.add(label2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
    final JLabel label3 = new JLabel();
    label3.setHorizontalAlignment(4);
    loadLabelText(label3, getMessageFromBundle("messages/UIBundle", "proxy.manual.exclude"));
    panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    myHttpProxyLoginTextField = new JTextField();
    myHttpProxyLoginTextField.setName("httpUser");
    myHttpProxyLoginTextField.setText("");
    panel1.add(myHttpProxyLoginTextField, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              new Dimension(150, -1), null, 0, false));
    myHttpProxyHostTextField = new JTextField();
    myHttpProxyHostTextField.setName("httpHost");
    panel1.add(myHttpProxyHostTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                             new Dimension(150, -1), null, 0, false));
    myHttpProxyExceptions.setDialogCaption("Proxy exceptions");
    myHttpProxyExceptions.setName("httpExceptions");
    panel1.add(myHttpProxyExceptions, new GridConstraints(2, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label4 = new JLabel();
    label4.setHorizontalAlignment(4);
    loadLabelText(label4, getMessageFromBundle("messages/UIBundle", "proxy.manual.port"));
    panel1.add(label4, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
    myHttpProxyPortTextField = new PortField();
    myHttpProxyPortTextField.setName("httpPort");
    panel1.add(myHttpProxyPortTextField, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
    final JLabel label5 = new JLabel();
    label5.setText("");
    panel1.add(label5,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label6 = new JLabel();
    label6.setHorizontalAlignment(4);
    loadLabelText(label6, getMessageFromBundle("messages/UIBundle", "auth.password.label"));
    panel1.add(label6, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 4, false));
    final JLabel label7 = new JLabel();
    label7.setText("<html><b>N/A</b></html>");
    panel1.add(label7,
               new GridConstraints(4, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHttpsProxyPanel = new JPanel();
    myHttpsProxyPanel.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 6, 6), -1, -1));
    myHttpsProxyPanel.setEnabled(true);
    myPane.add(myHttpsProxyPanel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
    myHttpsProxyPanel.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEmptyBorder(), "HTTPS Proxy",
                                                                                         TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                         TitledBorder.DEFAULT_POSITION, null, null));
    final JLabel label8 = new JLabel();
    label8.setHorizontalAlignment(4);
    loadLabelText(label8, getMessageFromBundle("messages/UIBundle", "proxy.manual.host"));
    myHttpsProxyPanel.add(label8, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      1, false));
    myHttpsProxyAuthCheckBox = new JCheckBox();
    myHttpsProxyAuthCheckBox.setMargin(new Insets(2, 1, 2, 2));
    myHttpsProxyAuthCheckBox.setSelected(false);
    loadButtonText(myHttpsProxyAuthCheckBox, getMessageFromBundle("messages/UIBundle", "proxy.manual.auth"));
    myHttpsProxyPanel.add(myHttpsProxyAuthCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    final JLabel label9 = new JLabel();
    label9.setHorizontalAlignment(4);
    loadLabelText(label9, getMessageFromBundle("messages/UIBundle", "proxy.manual.exclude"));
    myHttpsProxyPanel.add(label9, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      1, false));
    myHttpsProxyLoginTextField = new JTextField();
    myHttpsProxyLoginTextField.setName("httpsUser");
    myHttpsProxyLoginTextField.setText("");
    myHttpsProxyPanel.add(myHttpsProxyLoginTextField,
                          new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                              new Dimension(150, -1), null, 0, false));
    myHttpsProxyHostTextField = new JTextField();
    myHttpsProxyHostTextField.setName("httpsHost");
    myHttpsProxyPanel.add(myHttpsProxyHostTextField,
                          new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                              new Dimension(150, -1), null, 0, false));
    myHttpsProxyExceptions.setDialogCaption("Proxy exceptions");
    myHttpsProxyExceptions.setName("httpsExceptions");
    myHttpsProxyPanel.add(myHttpsProxyExceptions,
                          new GridConstraints(2, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label10 = new JLabel();
    label10.setHorizontalAlignment(4);
    loadLabelText(label10, getMessageFromBundle("messages/UIBundle", "proxy.manual.port"));
    myHttpsProxyPanel.add(label10, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       2, false));
    myHttpsProxyPortTextField = new PortField();
    myHttpsProxyPortTextField.setName("httpsPort");
    myHttpsProxyPanel.add(myHttpsProxyPortTextField,
                          new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
    final JLabel label11 = new JLabel();
    label11.setText("");
    myHttpsProxyPanel.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    final JLabel label12 = new JLabel();
    label12.setHorizontalAlignment(4);
    loadLabelText(label12, getMessageFromBundle("messages/UIBundle", "auth.password.label"));
    myHttpsProxyPanel.add(label12, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       4, false));
    final JLabel label13 = new JLabel();
    label13.setHorizontalAlignment(4);
    loadLabelText(label13, getMessageFromBundle("messages/UIBundle", "auth.login.label"));
    myHttpsProxyPanel.add(label13, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       2, false));
    final JLabel label14 = new JLabel();
    label14.setText("<html><b>N/A</b></html>");
    myHttpsProxyPanel.add(label14, new GridConstraints(4, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myMessageTextLabel = new JTextPane();
    myMessageTextLabel.setText("");
    myPane.add(myMessageTextLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    myEnableHttpsProxyCheckBox = new JCheckBox();
    myEnableHttpsProxyCheckBox.setText("Enable HTTPS Proxy");
    myPane.add(myEnableHttpsProxyCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label15 = new JLabel();
    label15.setText("");
    myPane.add(label15,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label16 = new JLabel();
    label16.setText("");
    myPane.add(label16,
               new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPane.add(spacer1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    label1.setLabelFor(myHttpProxyHostTextField);
    label2.setLabelFor(myHttpProxyLoginTextField);
    label8.setLabelFor(myHttpsProxyHostTextField);
    label13.setLabelFor(myHttpsProxyLoginTextField);
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

  private void loadButtonText(AbstractButton component, String text) {
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
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  @NotNull
  private static IdeGradleProxySettingsBridge createProxySettingsFromUI(@NotNull String proxyType,
                                                                        @NotNull JTextField proxyHostTextField,
                                                                        @NotNull PortField proxyPortTextField,
                                                                        @NotNull RawCommandLineEditor proxyExceptions,
                                                                        @NotNull JCheckBox proxyAuthCheckBox,
                                                                        @NotNull JTextField proxyLoginTextField) {
    IdeGradleProxySettingsBridge proxySettings = new IdeGradleProxySettingsBridge(proxyType);

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

  @VisibleForTesting
  static String generateDialogText(boolean ideProxyUsed) {
    String dialogTemplate = ideProxyUsed ? "android.proxy.settings.dialog.message" : "android.proxy.settings.dialog.no.ide.message";
    return AndroidBundle.message(dialogTemplate, ApplicationNamesInfo.getInstance().getProductName());
  }
}
