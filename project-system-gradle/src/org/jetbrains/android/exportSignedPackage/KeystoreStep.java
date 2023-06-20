// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.exportSignedPackage;

import static com.android.tools.idea.io.IdeFileUtils.getDesktopDirectoryVirtualFile;
import static icons.StudioIcons.Common.WARNING_INLINE;

import com.android.annotations.concurrency.Slow;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.instantapp.InstantApps;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ModalityUiUtil;
import java.awt.Cursor;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.jetbrains.android.compiler.artifact.ApkSigningSettingsForm;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class KeystoreStep extends ExportSignedPackageWizardStep implements ApkSigningSettingsForm {
  public static final String MODULE_PROPERTY = "ExportedModule";
  @VisibleForTesting static final String KEY_STORE_PASSWORD_KEY = "KEY_STORE_PASSWORD";
  @VisibleForTesting static final String KEY_PASSWORD_KEY = "KEY_PASSWORD";

  private JPanel myContentPanel;
  private JButton myCreateKeyStoreButton;
  private JBCheckBox myExportKeysCheckBox;
  private HyperlinkLabel myGoogleAppSigningLabel;
  private JPasswordField myKeyStorePasswordField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JTextField myKeyStorePathField;
  private JButton myLoadKeyStoreButton;
  private JBCheckBox myRememberPasswordCheckBox;
  @VisibleForTesting
  JComboBox<AndroidFacet> myModuleCombo;
  private JPanel myGradlePanel;
  private JBLabel myGradleWarning;
  private JBLabel myKeyStorePathLabel;
  private JBLabel myKeyStorePasswordLabel;
  private JBLabel myKeyAliasLabel;
  private JBLabel myKeyPasswordLabel;
  private JPanel myExportKeyPanel;
  @VisibleForTesting
  JBLabel myExportKeyPathLabel;
  @VisibleForTesting
  TextFieldWithBrowseButton myExportKeyPathField;

  private final ExportSignedPackageWizard myWizard;
  private final boolean myUseGradleForSigning;
  private boolean myIsBundle;
  @VisibleForTesting
  AndroidFacet mySelection;
  @VisibleForTesting final List<AndroidFacet> myFacets;

  public KeystoreStep(@NotNull ExportSignedPackageWizard wizard,
                      boolean useGradleForSigning,
                      @NotNull List<AndroidFacet> facets) {
    myWizard = wizard;
    myFacets = facets;
    myUseGradleForSigning = useGradleForSigning;
    Project project = wizard.getProject();

    GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);
    myKeyStorePathField.setText(settings.KEY_STORE_PATH);
    myKeyAliasField.setText(settings.KEY_ALIAS);
    myRememberPasswordCheckBox.setSelected(settings.REMEMBER_PASSWORDS);

    if (settings.REMEMBER_PASSWORDS) {
      tryLoadSavedPasswords();
    }

    myModuleCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value == null) return;
      Module module = value.getModule();
      label.setText(module.getName());
      label.setIcon(ModuleType.get(module).getIcon());
    }));
    myGradleWarning.setIcon(WARNING_INLINE);
    myGradlePanel.setVisible(false);
    myModuleCombo.addActionListener(e -> updateSelection((AndroidFacet)myModuleCombo.getSelectedItem()));

    myExportKeysCheckBox.addActionListener(e -> {
      myExportKeyPathLabel.setVisible(myExportKeysCheckBox.isSelected());
      myExportKeyPathField.setVisible(myExportKeysCheckBox.isSelected());
    });
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myExportKeyPathField.addBrowseFolderListener("Select Encrypted Key Destination Folder", null, myWizard.getProject(), descriptor);
    VirtualFile desktopDir = getDesktopDirectoryVirtualFile();
    if (desktopDir != null) {
      myExportKeyPathField.setText(desktopDir.getPath());
    }

    ExportSignedPackageUtil.initSigningSettingsForm(project, this);
  }

  @Override
  public void _init() {
    super._init();
    myIsBundle = myWizard.getTargetType().equals(ExportSignedPackageWizard.BUNDLE);
    updateModuleDropdown();

    if (myIsBundle) {
      GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(myWizard.getProject());
      myExportKeysCheckBox.setSelected(settings.EXPORT_PRIVATE_KEY);
      myGoogleAppSigningLabel.setHyperlinkText("Google Play App Signing");
      myGoogleAppSigningLabel.setHyperlinkTarget("https://support.google.com/googleplay/android-developer/answer/7384423");
      myGoogleAppSigningLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myExportKeysCheckBox.setVisible(true);
      myGoogleAppSigningLabel.setVisible(true);
      myExportKeyPathLabel.setVisible(myExportKeysCheckBox.isVisible() && myExportKeysCheckBox.isSelected());
      myExportKeyPathField.setVisible(myExportKeysCheckBox.isVisible() && myExportKeysCheckBox.isSelected());
    }
    else {
      myExportKeysCheckBox.setVisible(false);
      myGoogleAppSigningLabel.setVisible(false);
      myExportKeyPathLabel.setVisible(false);
      myExportKeyPathField.setVisible(false);
    }
    // Treat TextField actions as selections and try to refresh.
    myKeyStorePathField.addActionListener((action) -> keyStoreSelected());
    myKeyAliasField.getTextField().addActionListener((action) -> keyAliasSelected());
  }

  private void updateModuleDropdown() {
    List<AndroidFacet> facets = myIsBundle ? filteredFacets(myFacets) : myFacets;
    mySelection = null;
    myModuleCombo.setEnabled(facets.size() > 1);
    if (!facets.isEmpty()) {
      // If the selected module is not available, just pick the first item in the list
      String savedModuleName = PropertiesComponent.getInstance(myWizard.getProject()).getValue(getModuleProperty(myIsBundle));
      Optional<AndroidFacet> optionalFacet = facets.stream()
        .filter(facet -> facet.getModule().getName().equals(savedModuleName))
        .findFirst();
      mySelection = optionalFacet.orElse(facets.get(0));
      myModuleCombo.setModel(new CollectionComboBoxModel<>(facets, mySelection));
      updateSelection(mySelection);
    }
  }

  // Instant Apps cannot be built as bundles
  private List<AndroidFacet> filteredFacets(List<AndroidFacet> facets) {
    return facets.stream().filter(f -> !InstantApps.isInstantAppApplicationModule(f)).collect(Collectors.toList());
  }

  private void updateSelection(@Nullable AndroidFacet selectedItem) {
    PropertiesComponent.getInstance(myWizard.getProject())
      .setValue(getModuleProperty(myIsBundle), selectedItem == null ? "" : selectedItem.getModule().getName());
    mySelection = selectedItem;
    showGradleError(!isGradleValid(myWizard.getTargetType()));
  }

  @NotNull
  static String getModuleProperty(boolean isBundle) {
    return isBundle ? "Bundle" + MODULE_PROPERTY : "Apk" + MODULE_PROPERTY;
  }

  private boolean isGradleValid(@NotNull String targetType) {
    // all gradle versions are valid unless targetType is bundle
    if (!targetType.equals(ExportSignedPackageWizard.BUNDLE)) {
      return true;
    }

    if (mySelection == null) return true;
    return GradleProjectSystemUtil.supportsBundleTask(mySelection.getModule());
  }

  private void showGradleError(boolean showError) {
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
    myExportKeyPanel.setVisible(!showError);
    myExportKeyPathLabel.setVisible(!showError);
    myExportKeyPathField.setVisible(!showError);

    // gradle error fields
    myGradlePanel.setVisible(showError);
  }

  @Override
  public void keyStoreSelected() {
    myKeyStorePasswordField.setText(null);
    myKeyPasswordField.setText(null);
    tryLoadSavedPasswords();
  }

  @Override
  public void keyStoreCreated() {
    // Nothing to do.
  }

  @Override
  public void keyAliasSelected() {
    myKeyPasswordField.setText(null);
    tryLoadSavedPasswords();
  }

  @Override
  public void keyAliasCreated() {
    // Nothing to do.
  }

  private void tryLoadSavedPasswords() {
    String keyStorePath = myKeyStorePathField.getText();
    String keyAlias = myKeyAliasField.getText();
    executeInBackground(() -> {
      String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, keyStorePath, null);
      String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, keyStorePath, keyAlias);
      try {
        PasswordSafe passwordSafe = PasswordSafe.getInstance();

        retrievePassword(passwordSafe, Arrays.asList(
          credentialAttributesForKey(keyStorePasswordKey),
          createKeystoreDeprecatedAttributesPre_2021_1_1_3(keyStorePasswordKey),
          createDeprecatedAttributesPre_3_2(keyStorePasswordKey)
        )).map(Credentials::getPassword).ifPresent(password -> ModalityUiUtil.invokeLaterIfNeeded(
          // Need to be any modality as it is not guaranteed that dialog is already opened, but we need to run anyway.
          ModalityState.any(),
          () -> {
            if (myKeyStorePasswordField.getPassword().length == 0) {
              myKeyStorePasswordField.setText(password.toString());
            }
          }));

        retrievePassword(passwordSafe, Arrays.asList(
          credentialAttributesForKey(keyPasswordKey),
          createKeyDeprecatedAttributesPre_2021_1_1_3(keyPasswordKey),
          createDeprecatedAttributesPre_3_2(keyPasswordKey)
        )).map(Credentials::getPassword).ifPresent(password -> ModalityUiUtil.invokeLaterIfNeeded(
          // Need to be any modality as it is not guaranteed that dialog is already opened, but we need to run anyway.
          ModalityState.any(),
          () -> {
            if (myKeyPasswordField.getPassword().length == 0) {
              myKeyPasswordField.setText(password.toString());
            }
          }));
      }
      catch (Throwable t) {
        Logger.getInstance(KeystoreStep.class).error("Unable to use password safe", t);
      }
    });
  }

  /**
   * Execute task in background unless it is a unit test. Otherwise testing passwords loading becomes very tricky.
   */
  private void executeInBackground(Runnable runnable) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  }

  /**
   * Try to load password using all provided attributes in provided order and return first one found.
   * This is needed to be able to load passwords saved previously in a deprecated way
   * (changes for b/192344567, b/64995008).
   */
  @Slow
  private static @NotNull Optional<Credentials> retrievePassword(
    @NotNull PasswordSafe passwordSafe, @NotNull List<CredentialAttributes> credentialAttributesToTry) {
    return credentialAttributesToTry.stream()
      .map(attributes -> Optional.ofNullable(passwordSafe.get(attributes)))
      .filter(Optional::isPresent)
      .findFirst()
      .orElse(Optional.empty());
  }

  @VisibleForTesting
  static void trySavePasswords(@NotNull String keyStoreLocation,
                               char[] keyStorePassword,
                               @NotNull String keyAlias,
                               char[] keyPassword,
                               boolean rememberPasswords) {
    String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, keyStoreLocation, null);
    String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, keyStoreLocation, keyAlias);

    // Following the logic in retrieve method, PasswordSafe usage is unlikely but might fail.
    // Let's guard ourselfs here, otherwise it will completely block the users as they would not be able to pass
    // this step on the signing wizard.
    try {
      PasswordSafe passwordSafe = PasswordSafe.getInstance();
      if (rememberPasswords) {
        passwordSafe.set(credentialAttributesForKey(keyStorePasswordKey), new Credentials(keyStorePasswordKey, keyStorePassword));
        passwordSafe.set(credentialAttributesForKey(keyPasswordKey), new Credentials(keyPasswordKey, keyPassword));
      }
      else {
        // Delete stored passwords if remember passwords checkbox is unchecked.
        passwordSafe.set(credentialAttributesForKey(keyStorePasswordKey), null);
        passwordSafe.set(credentialAttributesForKey(keyPasswordKey), null);
      }
      // Always erase credentials stored with the old deprecated way (used before the fixes for b/64995008 and for b/192344567).
      passwordSafe.set(createDeprecatedAttributesPre_3_2(keyStorePasswordKey), null);
      passwordSafe.set(createDeprecatedAttributesPre_3_2(keyPasswordKey), null);
      passwordSafe.set(createKeystoreDeprecatedAttributesPre_2021_1_1_3(keyStorePasswordKey), null);
      passwordSafe.set(createKeyDeprecatedAttributesPre_2021_1_1_3(keyPasswordKey), null);
    }
    catch (Throwable t) {
      Logger.getInstance(KeystoreStep.class).error("Unable to use password safe", t);
    }
  }

  /**
   * This is the new recommended way to create CredentialAttributes.
   * Usage of accessor class for creating CredentialAttributes is deprecated.
   * We need to include key to the service name to be able to save passwords for several keystores/key aliases.
   * PasswordSafe does not attempt to find the correct credentials by username internally,
   * thus only one username/password pair can be saved per service name.
   * That's why we need to include password determining key into service name instead of passing it as user name.
   */
  private static @NotNull CredentialAttributes credentialAttributesForKey(@NotNull String key) {
    String serviceName = CredentialAttributesKt.generateServiceName("APK Signing Keystore Step", key);
    return new CredentialAttributes(serviceName);
  }

  /**
   * Deprecated way to create attributes that was used before Studio 3.2.
   * Left for migrating passwords from Studio before that version.
   */
  private static @NotNull CredentialAttributes createDeprecatedAttributesPre_3_2(@NotNull String key) {
    return new CredentialAttributes(KeystoreStep.class.getName(), key);
  }

  /**
   * Deprecated way to create attributes that was used from Studio ~3.2 to Bumblebee Canary 3 (2021.1.1.3).
   * Left for migrating passwords from Studio before that version.
   */
  private static @NotNull CredentialAttributes createKeystoreDeprecatedAttributesPre_2021_1_1_3(@NotNull String key) {
    return new CredentialAttributes("org.jetbrains.android.exportSignedPackage.KeystoreStep$KeyStorePasswordRequestor", key);
  }

  /**
   * Deprecated way to create attributes that was used from Studio ~3.2 to Bumblebee Canary 3 (2021.1.1.3).
   * Left for migrating passwords from Studio before that version.
   */
  private static @NotNull CredentialAttributes createKeyDeprecatedAttributesPre_2021_1_1_3(@NotNull String key) {
    return new CredentialAttributes("org.jetbrains.android.exportSignedPackage.KeystoreStep$KeyPasswordRequestor", key);
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
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing#generate-key";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (!isGradleValid(myWizard.getTargetType())) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.bundle.gradle.error"));
    }

    String keyStoreLocation = myKeyStorePathField.getText().trim();
    if (keyStoreLocation.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.keystore.location.error"));
    }

    char[] keyStorePassword = myKeyStorePasswordField.getPassword();
    if (keyStorePassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.store.password.error"));
    }

    String keyAlias = myKeyAliasField.getText().trim();
    if (keyAlias.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.alias.error"));
    }

    char[] keyPassword = myKeyPasswordField.getPassword();
    if (keyPassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.password.error"));
    }

    if (myUseGradleForSigning) {
      myWizard.setGradleSigningInfo(new GradleSigningInfo(keyStoreLocation, keyStorePassword, keyAlias, keyPassword));
    }
    else {
      KeyStore keyStore = loadKeyStore(new File(keyStoreLocation));
      if (keyStore == null) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.keystore.error.title"));
      }
      loadKeyAndSaveToWizard(keyStore, keyAlias, keyPassword);
    }

    Project project = myWizard.getProject();
    GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);

    settings.KEY_STORE_PATH = keyStoreLocation;
    settings.KEY_ALIAS = keyAlias;

    boolean rememberPasswords = myRememberPasswordCheckBox.isSelected();
    settings.REMEMBER_PASSWORDS = rememberPasswords;

    if (myWizard.getTargetType().equals(ExportSignedPackageWizard.BUNDLE)) {
      boolean exportPrivateKey = myExportKeysCheckBox.isSelected();
      settings.EXPORT_PRIVATE_KEY = exportPrivateKey;
      myWizard.setExportPrivateKey(exportPrivateKey);
      if (exportPrivateKey) {
        String keyFolder = myExportKeyPathField.getText().trim();
        if (keyFolder.isEmpty()) {
          throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.missing.destination", myWizard.getTargetType()));
        }

        File f = new File(keyFolder);
        if (!f.isDirectory() || !f.canWrite()) {
          throw new CommitStepException(AndroidBundle.message("android.apk.sign.gradle.invalid.destination"));
        }
        myWizard.setExportKeyPath(keyFolder);
      }
    }

    trySavePasswords(keyStoreLocation, keyStorePassword, keyAlias, keyPassword, rememberPasswords);

    myWizard.setFacet(getSelectedFacet());
  }

  private KeyStore loadKeyStore(File keystoreFile) throws CommitStepException {
    char[] password = myKeyStorePasswordField.getPassword();
    FileInputStream fis = null;
    AndroidUtils.checkPassword(password);
    if (!keystoreFile.isFile()) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.find.file.error", keystoreFile.getPath()));
    }
    KeyStore keyStore;
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
      throw new CommitStepException(AndroidBundle.message("android.extract.package.error.0.message", e.getLocalizedMessage()));
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

  @VisibleForTesting
  JBCheckBox getExportKeysCheckBox() {
    return myExportKeysCheckBox;
  }
}
