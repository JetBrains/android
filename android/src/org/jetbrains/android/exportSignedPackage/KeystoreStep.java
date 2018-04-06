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

package org.jetbrains.android.exportSignedPackage;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.compiler.artifact.ApkSigningSettingsForm;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUiUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE;

/**
 * @author Eugene.Kudelevsky
 */
class KeystoreStep extends ExportSignedPackageWizardStep implements ApkSigningSettingsForm {
  public static final String MODULE_PROPERTY = "ExportedModule";
  @VisibleForTesting static final String KEY_STORE_PASSWORD_KEY = "KEY_STORE_PASSWORD";
  @VisibleForTesting static final String KEY_PASSWORD_KEY = "KEY_PASSWORD";
  private boolean myShowError;

  private static class KeyStorePasswordRequestor {
    // dummy: used as a requestor class id to access the key store password
  }

  private static class KeyPasswordRequestor {
    // dummy: used as a requestor class id to access the key password
  }

  private JPanel myContentPanel;
  private JPasswordField myKeyStorePasswordField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JTextField myKeyStorePathField;
  private JButton myCreateKeyStoreButton;
  private JButton myLoadKeyStoreButton;
  private JBCheckBox myRememberPasswordCheckBox;
  private JComboBox myModuleCombo;
  private JPanel myGradlePanel;
  private HyperlinkLabel myCloseAndUpdateLink;
  private JBLabel myKeyStorePathLabel;
  private JBLabel myKeyStorePasswordLabel;
  private JBLabel myKeyAliasLabel;
  private JBLabel myKeyPasswordLabel;

  private final ExportSignedPackageWizard myWizard;
  private final boolean myUseGradleForSigning;
  @NotNull
  private AndroidFacet mySelection;

  public KeystoreStep(@NotNull ExportSignedPackageWizard wizard,
                      boolean useGradleForSigning,
                      @NotNull List<AndroidFacet> facets) {
    assert !facets.isEmpty();
    myWizard = wizard;
    myUseGradleForSigning = useGradleForSigning;
    final Project project = wizard.getProject();

    final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);
    myKeyStorePathField.setText(settings.KEY_STORE_PATH);
    myKeyAliasField.setText(settings.KEY_ALIAS);
    myRememberPasswordCheckBox.setSelected(settings.REMEMBER_PASSWORDS);

    if (settings.REMEMBER_PASSWORDS) {
      final String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, settings.KEY_STORE_PATH, null);
      String password = retrievePassword(KeyStorePasswordRequestor.class, keyStorePasswordKey);
      if (password != null) {
        myKeyStorePasswordField.setText(password);
      }

      final String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS);
      password = retrievePassword(KeyPasswordRequestor.class, keyPasswordKey);
      if (password != null) {
        myKeyPasswordField.setText(password);
      }
    }

    mySelection = facets.get(0);
    String moduleName = PropertiesComponent.getInstance(wizard.getProject()).getValue(MODULE_PROPERTY);
    if (moduleName != null) {
      for (AndroidFacet facet : facets) {
        if (moduleName.equals(facet.getModule().getName())) {
          mySelection = facet;
          break;
        }
      }
    }

    myModuleCombo.setModel(new CollectionComboBoxModel(facets, mySelection));
    myModuleCombo.setRenderer(new ListCellRendererWrapper<AndroidFacet>() {
      @Override
      public void customize(JList list, AndroidFacet value, int index, boolean selected, boolean hasFocus) {
        if (value == null) return;
        final Module module = value.getModule();
        setText(module.getName());
        setIcon(ModuleType.get(module).getIcon());
      }
    });
    myModuleCombo.setEnabled(facets.size() > 1);
    myCloseAndUpdateLink.setHyperlinkText(AndroidBundle.message("android.export.package.bundle.gradle.update"));
    myCloseAndUpdateLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
        GradleVersion pluginVersion = GradleVersion.parse(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
        AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
        updater.updatePluginVersion(pluginVersion, gradleVersion);
        myWizard.close(CANCEL_EXIT_CODE);
      }
    });
    myGradlePanel.setVisible(false);
    myModuleCombo.addActionListener(e -> updateSelection((AndroidFacet)myModuleCombo.getSelectedItem()));
    AndroidUiUtil.initSigningSettingsForm(project, this);
  }

  @Override
  public void _init() {
    super._init();
    updateSelection(mySelection);
  }

  private void updateSelection(AndroidFacet selectedItem) {
    mySelection = selectedItem;
    showGradleError(!isGradleValid(myWizard.getTargetType()));
  }


  private boolean isGradleValid(@Nullable String targetType) {
    // all gradle versions are valid unless targetType is bundle
    if (targetType == null || !targetType.equals("bundle")) {
      return true;
    }

    GradleVersion version = AndroidModuleModel.get(mySelection).getModelVersion();
    return version.isAtLeastIncludingPreviews(3, 2, 0);
  }

  private void showGradleError(boolean showError) {
    myShowError = showError;
    // key store fields
    myKeyStorePasswordField.setVisible(!showError);
    myKeyPasswordField.setVisible(!showError);
    myKeyAliasField.setVisible(!showError);
    myKeyStorePathField.setVisible(!showError);
    myCreateKeyStoreButton.setVisible(!showError);
    myLoadKeyStoreButton.setVisible(!showError);
    myRememberPasswordCheckBox.setVisible(!showError);
    myKeyStorePasswordLabel.setVisible(!showError);
    myKeyPasswordLabel.setVisible(!showError);
    myKeyAliasLabel.setVisible(!showError);
    myKeyStorePathLabel.setVisible(!showError);

    // gradle error fields
    myGradlePanel.setVisible(showError);
  }

  private static String retrievePassword(@NotNull Class<?> primaryRequestor, @NotNull String key) {
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    String password = passwordSafe.getPassword(primaryRequestor, key);
    if (password == null) {
      // Try to retrieve password previously saved with an old requestor in order to make user experience more seamless
      // while transitioning to a version which contains the fix for b/64995008, rather than having them retype all the
      // passwords at once.
      password = passwordSafe.getPassword(KeystoreStep.class, key);
    }

    return password;
  }

  private static void updateSavedPassword(@NotNull Class<?> primaryRequestor, @NotNull String key, @Nullable String value) {
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    passwordSafe.setPassword(primaryRequestor, key, value);
    // Always erase the one stored with the old requestor (the one used before the fix for b/64995008).
    passwordSafe.setPassword(KeystoreStep.class, key, null);
  }

  @VisibleForTesting
  static String makePasswordKey(@NotNull String prefix, @NotNull String keyStorePath, @Nullable String keyAlias) {
    return prefix + "__" + keyStorePath + (keyAlias != null ? "__" + keyAlias : "");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myKeyStorePathField.getText().isEmpty()) {
      return myKeyStorePathField;
    }
    else if (myKeyStorePasswordField.getPassword().length == 0) {
      return myKeyStorePasswordField;
    }
    else if (myKeyAliasField.getText().isEmpty()) {
      return myKeyAliasField;
    }
    else if (myKeyPasswordField.getPassword().length == 0) {
      return myKeyPasswordField;
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.specify.keystore";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (!isGradleValid(myWizard.getTargetType())) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.bundle.gradle.error"));
    }

    final String keyStoreLocation = myKeyStorePathField.getText().trim();
    if (keyStoreLocation.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.keystore.location.error"));
    }

    final char[] keyStorePassword = myKeyStorePasswordField.getPassword();
    if (keyStorePassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.store.password.error"));
    }

    final String keyAlias = myKeyAliasField.getText().trim();
    if (keyAlias.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.alias.error"));
    }

    final char[] keyPassword = myKeyPasswordField.getPassword();
    if (keyPassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.password.error"));
    }

    if (myUseGradleForSigning) {
      myWizard.setGradleSigningInfo(new GradleSigningInfo(keyStoreLocation, keyStorePassword, keyAlias, keyPassword));
    } else {
      final KeyStore keyStore = loadKeyStore(new File(keyStoreLocation));
      if (keyStore == null) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.keystore.error.title"));
      }
      loadKeyAndSaveToWizard(keyStore, keyAlias, keyPassword);
    }

    final Project project = myWizard.getProject();
    final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);

    settings.KEY_STORE_PATH = keyStoreLocation;
    settings.KEY_ALIAS = keyAlias;

    final boolean rememberPasswords = myRememberPasswordCheckBox.isSelected();
    settings.REMEMBER_PASSWORDS = rememberPasswords;

    final String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, keyStoreLocation, null);
    final String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, keyStoreLocation, keyAlias);

    updateSavedPassword(KeyStorePasswordRequestor.class, keyStorePasswordKey, rememberPasswords ? new String(keyStorePassword) : null);
    updateSavedPassword(KeyPasswordRequestor.class, keyPasswordKey, rememberPasswords ? new String(keyPassword) : null);

    AndroidFacet selectedFacet = getSelectedFacet();
    assert selectedFacet != null;
    myWizard.setFacet(selectedFacet);
  }

  private KeyStore loadKeyStore(File keystoreFile) throws CommitStepException {
    final char[] password = myKeyStorePasswordField.getPassword();
    FileInputStream fis = null;
    AndroidUtils.checkPassword(password);
    if (!keystoreFile.isFile()) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.find.file.error", keystoreFile.getPath()));
    }
    final KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      //noinspection IOResourceOpenedButNotSafelyClosed
      fis = new FileInputStream(keystoreFile);
      keyStore.load(fis, password);
    }
    catch (Exception e) {
      throw new CommitStepException(e.getMessage());
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
      }
      Arrays.fill(password, '\0');
    }
    return keyStore;
  }

  private void loadKeyAndSaveToWizard(KeyStore keyStore, String alias, char[] keyPassword) throws CommitStepException {
    KeyStore.PrivateKeyEntry entry;
    try {
      assert keyStore != null;
      entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, new KeyStore.PasswordProtection(keyPassword));
    }
    catch (Exception e) {
      throw new CommitStepException("Error: " + e.getMessage());
    }
    if (entry == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    PrivateKey privateKey = entry.getPrivateKey();
    Certificate certificate = entry.getCertificate();
    if (privateKey == null || certificate == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    myWizard.setPrivateKey(privateKey);
    myWizard.setCertificate((X509Certificate)certificate);
  }

  private AndroidFacet getSelectedFacet() {
    return (AndroidFacet)myModuleCombo.getSelectedItem();
  }

  @Override
  public JButton getLoadKeyStoreButton() {
    return myLoadKeyStoreButton;
  }

  @Override
  public JTextField getKeyStorePathField() {
    return myKeyStorePathField;
  }

  @Override
  public JPanel getPanel() {
    return myContentPanel;
  }

  @Override
  public JButton getCreateKeyStoreButton() {
    return myCreateKeyStoreButton;
  }

  @Override
  public JPasswordField getKeyStorePasswordField() {
    return myKeyStorePasswordField;
  }

  @Override
  public TextFieldWithBrowseButton getKeyAliasField() {
    return myKeyAliasField;
  }

  @Override
  public JPasswordField getKeyPasswordField() {
    return myKeyPasswordField;
  }
}
