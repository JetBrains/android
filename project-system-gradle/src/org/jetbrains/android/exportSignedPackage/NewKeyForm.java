package org.jetbrains.android.exportSignedPackage;

import com.android.ide.common.signing.KeystoreHelper;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.lang.reflect.Method;
import java.util.ResourceBundle;
import javax.swing.border.TitledBorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class NewKeyForm {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.exportSignedPackage.NewKeyForm");

  private static final String PKCS12_WARNING_PREFIX = "Warning:\nThe JKS keystore uses a proprietary format. It is recommended to" +
                                                      " migrate to PKCS12 which is an industry standard format using \"keytool" +
                                                      " -importkeystore -srckeystore";
  private static final String PKCS12_WARNING_SUFFIX = "-deststoretype pkcs12\".\n";

  private JPanel myContentPanel;
  private JTextField myAliasField;
  private JPasswordField myKeyPasswordField;
  private JPasswordField myConfirmKeyPasswordField;
  private JSpinner myValiditySpinner;
  private JTextField myFirstAndLastNameField;
  private JTextField myOrganizationUnitField;
  private JTextField myCityField;
  private JTextField myStateOrProvinceField;
  private JTextField myCountryCodeField;
  private JPanel myCertificatePanel;
  private JTextField myOrganizationField;

  private KeyStore myKeyStore;
  private PrivateKey myPrivateKey;
  private X509Certificate myCertificate;

  public NewKeyForm() {
    setupUI();
    myValiditySpinner.setModel(new SpinnerNumberModel(25, 1, 1000, 1));
    ((JSpinner.DefaultEditor)myValiditySpinner.getEditor()).getTextField().setColumns(2);
  }

  private int getValidity() {
    SpinnerNumberModel model = (SpinnerNumberModel)myValiditySpinner.getModel();
    return model.getNumber().intValue();
  }

  public void init() {
    myAliasField.setText(generateAlias());

    myKeyPasswordField.setName("myKeyPasswordField");
    myConfirmKeyPasswordField.setName("myConfirmKeyPasswordField");
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  private boolean findNonEmptyCertificateField() {
    for (Component component : myCertificatePanel.getComponents()) {
      if (component instanceof JTextField) {
        if (!((JTextField)component).getText().trim().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  public void createKey() throws CommitStepException {
    if (getKeyAlias().isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.alias.error"));
    }
    AndroidUtils.checkNewPassword(myKeyPasswordField, myConfirmKeyPasswordField);
    if (!findNonEmptyCertificateField()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.certificate.field.error"));
    }
    doCreateKey();
  }

  @NotNull
  private String generateAlias() {
    List<String> aliasList = getExistingKeyAliasList();
    String prefix = "key";
    if (aliasList == null) {
      return prefix + '0';
    }
    Set<String> aliasSet = new HashSet<String>();
    for (String alias : aliasList) {
      aliasSet.add(StringUtil.toLowerCase(alias));
    }
    for (int i = 0; ; i++) {
      String alias = prefix + i;
      if (!aliasSet.contains(alias)) {
        return alias;
      }
    }
  }

  @Nullable
  protected abstract List<String> getExistingKeyAliasList();

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(5, 4, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    loadLabelText(label1,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.new.key.alias.label"));
    myContentPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myAliasField = new JTextField();
    myContentPanel.add(myAliasField, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
    final JLabel label2 = new JLabel();
    loadLabelText(label2, getMessageFromBundle("messages/AndroidBundle", "android.key.password.label"));
    myContentPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    myKeyPasswordField = new JPasswordField();
    myContentPanel.add(myKeyPasswordField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               new Dimension(150, -1), null, 0, false));
    final JLabel label3 = new JLabel();
    loadLabelText(label3,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.key.validity.label"));
    myContentPanel.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    myValiditySpinner = new JSpinner();
    myContentPanel.add(myValiditySpinner, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                              new Dimension(60, -1), null, null, 0, false));
    myCertificatePanel = new JPanel();
    myCertificatePanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    myContentPanel.add(myCertificatePanel, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
    myCertificatePanel.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Certificate",
                                                                                          TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                          TitledBorder.DEFAULT_POSITION, null, null));
    final JLabel label4 = new JLabel();
    loadLabelText(label4, getMessageFromBundle("messages/AndroidBundle",
                                                                     "android.export.package.key.certificate.name.label"));
    myCertificatePanel.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myFirstAndLastNameField = new JTextField();
    myCertificatePanel.add(myFirstAndLastNameField,
                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                               new Dimension(150, -1), null, 0, false));
    final JLabel label5 = new JLabel();
    loadLabelText(label5,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.organization.unit.label"));
    myCertificatePanel.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myOrganizationUnitField = new JTextField();
    myCertificatePanel.add(myOrganizationUnitField,
                           new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                               new Dimension(150, -1), null, 0, false));
    final JLabel label6 = new JLabel();
    loadLabelText(label6, getMessageFromBundle("messages/AndroidBundle", "android.export.package.city.label"));
    myCertificatePanel.add(label6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myCityField = new JTextField();
    myCertificatePanel.add(myCityField, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            new Dimension(150, -1), null, 0, false));
    final JLabel label7 = new JLabel();
    loadLabelText(label7, getMessageFromBundle("messages/AndroidBundle", "android.export.package.state.label"));
    myCertificatePanel.add(label7, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myStateOrProvinceField = new JTextField();
    myCertificatePanel.add(myStateOrProvinceField,
                           new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                               new Dimension(150, -1), null, 0, false));
    final JLabel label8 = new JLabel();
    loadLabelText(label8,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.country.code.label"));
    myCertificatePanel.add(label8, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myCountryCodeField = new JTextField();
    myCertificatePanel.add(myCountryCodeField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                   GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                   null, new Dimension(150, -1), null, 0, false));
    final JLabel label9 = new JLabel();
    loadLabelText(label9,
                             getMessageFromBundle("messages/AndroidBundle", "android.export.package.organization.label"));
    myCertificatePanel.add(label9, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                       0, false));
    myOrganizationField = new JTextField();
    myCertificatePanel.add(myOrganizationField,
                           new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                               new Dimension(150, -1), null, 0, false));
    final JLabel label10 = new JLabel();
    loadLabelText(label10, getMessageFromBundle("messages/AndroidBundle", "android.confirm.password.label"));
    myContentPanel.add(label10, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
    myConfirmKeyPasswordField = new JPasswordField();
    myContentPanel.add(myConfirmKeyPasswordField,
                       new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                           new Dimension(150, -1), null, 0, false));
    label1.setLabelFor(myAliasField);
    label2.setLabelFor(myKeyPasswordField);
    label3.setLabelFor(myValiditySpinner);
    label4.setLabelFor(myFirstAndLastNameField);
    label5.setLabelFor(myOrganizationUnitField);
    label6.setLabelFor(myCityField);
    label7.setLabelFor(myStateOrProvinceField);
    label8.setLabelFor(myCountryCodeField);
    label9.setLabelFor(myOrganizationField);
    label10.setLabelFor(myConfirmKeyPasswordField);
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

  private static void buildDName(StringBuilder builder, String prefix, JTextField textField) {
    if (textField != null) {
      String value = textField.getText().trim();
      if (!value.isEmpty()) {
        if (builder.length() > 0) {
          builder.append(",");
        }
        builder.append(prefix);
        builder.append('=');
        builder.append(value);
      }
    }
  }

  private String getDName() {
    StringBuilder builder = new StringBuilder();
    buildDName(builder, "CN", myFirstAndLastNameField);
    buildDName(builder, "OU", myOrganizationUnitField);
    buildDName(builder, "O", myOrganizationField);
    buildDName(builder, "L", myCityField);
    buildDName(builder, "ST", myStateOrProvinceField);
    buildDName(builder, "C", myCountryCodeField);
    return builder.toString();
  }


  private void doCreateKey() throws CommitStepException {
    String keystoreLocation = getKeyStoreLocation();
    String keystorePassword = new String(getKeyStorePassword());
    String keyPassword = new String(getKeyPassword());
    String keyAlias = getKeyAlias();
    String dname = getDName();
    assert dname != null;

    boolean createdStore = false;
    final StringBuilder errorBuilder = new StringBuilder();
    final StringBuilder outBuilder = new StringBuilder();
    try {
      createdStore = KeystoreHelper
        .createNewStore(null, new File(keystoreLocation), keystorePassword, keyPassword, keyAlias, dname, getValidity(), 2048);
    }
    catch (Exception e) {
      LOG.info(e);
      errorBuilder.append(e.getMessage()).append('\n');
    }

    // Do not output the warning about migrating to PKCS12
    int warningStartIndex = errorBuilder.indexOf(PKCS12_WARNING_PREFIX);
    int warningEndIndex = errorBuilder.indexOf(PKCS12_WARNING_SUFFIX);
    if (warningStartIndex >= 0 && warningEndIndex > warningStartIndex) {
      errorBuilder.delete(warningStartIndex, warningEndIndex + PKCS12_WARNING_SUFFIX.length());
    }

    normalizeBuilder(errorBuilder);
    normalizeBuilder(outBuilder);

    if (createdStore) {
      if (errorBuilder.length() > 0) {
        String prefix = AndroidBundle.message("android.create.new.key.error.prefix");
        Messages.showErrorDialog(myContentPanel, prefix + '\n' + errorBuilder.toString());
      }
    }
    else {
      if (errorBuilder.length() > 0) {
        throw new CommitStepException(errorBuilder.toString());
      }
      if (outBuilder.length() > 0) {
        throw new CommitStepException(outBuilder.toString());
      }
      throw new CommitStepException(AndroidBundle.message("android.cannot.create.new.key.error"));
    }
    loadKeystoreAndKey(keystoreLocation, keystorePassword, keyAlias, keyPassword);
  }

  @NotNull
  public char[] getKeyPassword() {
    return myKeyPasswordField.getPassword();
  }

  @NotNull
  public String getKeyAlias() {
    return myAliasField.getText().trim();
  }

  @NotNull
  protected abstract Project getProject();

  @NotNull
  protected abstract char[] getKeyStorePassword();

  @NotNull
  protected abstract String getKeyStoreLocation();

  private void loadKeystoreAndKey(String keystoreLocation, String keystorePassword, String keyAlias, String keyPassword)
    throws CommitStepException {
    FileInputStream fis = null;
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      fis = new FileInputStream(new File(keystoreLocation));
      keyStore.load(fis, keystorePassword.toCharArray());
      myKeyStore = keyStore;
      KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
        keyAlias, new KeyStore.PasswordProtection(keyPassword.toCharArray()));
      if (entry == null) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", keyAlias));
      }
      PrivateKey privateKey = entry.getPrivateKey();
      Certificate certificate = entry.getCertificate();
      if (privateKey == null || certificate == null) {
        throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", keyAlias));
      }
      myPrivateKey = privateKey;
      myCertificate = (X509Certificate)certificate;
    }
    catch (Exception e) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.error.0.message", e.getMessage()));
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  private static void normalizeBuilder(StringBuilder builder) {
    if (builder.length() > 0) {
      builder.deleteCharAt(builder.length() - 1);
    }
  }

  @Nullable
  public KeyStore getKeyStore() {
    return myKeyStore;
  }

  @Nullable
  public PrivateKey getPrivateKey() {
    return myPrivateKey;
  }

  @Nullable
  public X509Certificate getCertificate() {
    return myCertificate;
  }
}
