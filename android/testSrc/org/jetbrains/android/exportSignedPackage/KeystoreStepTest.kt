/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.MockitoThreadLocalsCleaner
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.PasswordSafeSettings
import com.intellij.credentialStore.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.TestPasswordSafeImpl
import com.intellij.ide.wizard.CommitStepException
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_PASSWORD_KEY
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_STORE_PASSWORD_KEY
import org.jetbrains.android.exportSignedPackage.KeystoreStep.trySavePasswords
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.jetbrains.android.util.AndroidBundle
import org.mockito.Mockito.mock
import java.io.File
import java.util.Arrays
import java.util.concurrent.TimeUnit

class KeystoreStepTest : LightPlatformTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var facets: MutableList<AndroidFacet>
  private lateinit var myAndroidFacet1: AndroidFacet
  private lateinit var myAndroidFacet2: AndroidFacet

  private val mockitoCleaner = MockitoThreadLocalsCleaner()

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(project, testRootDisposable)
    facets = ArrayList()
    myAndroidFacet1 = AndroidFacet(module, AndroidFacet.NAME, AndroidFacetConfiguration())
    myAndroidFacet2 = AndroidFacet(module, AndroidFacet.NAME, AndroidFacetConfiguration())
    mockitoCleaner.setup()
  }

  override fun tearDown() {
    try {
      mockitoCleaner.cleanupAndTearDown()
    } finally {
      super.tearDown()
    }
  }

  fun testEnableEncryptedKeyExportFlagFalse() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(false, keystoreStep.exportKeysCheckBox.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testEnableEncryptedKeyExportFlagTrue() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(true, keystoreStep.exportKeysCheckBox.isVisible)
    assertEquals(true, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(true, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testEnableEncryptedKeyCheckboxButNotSelected_ExportKeyPathFieldsShouldBeHidden() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.EXPORT_PRIVATE_KEY = false
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(false, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testEnableEncryptedKeyCheckboxNotSelected_NextSucceeds() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = false
    settings.EXPORT_PRIVATE_KEY = false
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep._init()
    keystoreStep.commitForNext()
  }

  fun testEnableEncryptedKeyCheckboxSelectedWithoutExportPath_NextFails() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = false
    settings.EXPORT_PRIVATE_KEY = true
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.myExportKeyPathField.text = ""
    keystoreStep._init()
    assertThrows(CommitStepException::class.java,
                 AndroidBundle.message("android.apk.sign.gradle.missing.destination", wizard.targetType),
                 ThrowableRunnable<RuntimeException> { keystoreStep.commitForNext() })
  }

  fun testEnableEncryptedKeyCheckboxSelectedWithExportPath_NextSucceeds() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = false
    settings.EXPORT_PRIVATE_KEY = true
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    keystoreStep._init()
  }

  fun testModuelDropDownEnabledByDefault() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    assertEquals(true, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testModuleDropDownDisabledWhenOnlyOneFacet() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(myAndroidFacet1)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    assertEquals(false, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testUpdatesInvalidSelection() {
    // if the current selected facet is no longer in the list of facets, then it should be updated to the first one in the list
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(myAndroidFacet1)
    facets.add(myAndroidFacet2)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    assertEquals(myAndroidFacet1, keystoreStep.myModuleCombo.selectedItem)

    // remove the selected facet
    keystoreStep.myFacets.removeAt(0)

    keystoreStep._init()
    assertEquals(myAndroidFacet2, keystoreStep.myModuleCombo.selectedItem)
  }

  fun setupWizardHelper(): ExportSignedPackageWizard {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    return wizard
  }

  fun testRememberPasswords() {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    assertEquals(0, keystoreStep.keyPasswordField.password.size)

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Assert that the passwords are persisted and a new form instance fields populated as necessary.
    val keystoreStep2 = KeystoreStep(wizard, true, facets)
    assertEquals(testKeyStorePath, keystoreStep2.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep2.keyAliasField.text)
    waitForCondition(1, TimeUnit.SECONDS) {
      Arrays.equals(testKeyStorePassword.toCharArray(), keystoreStep2.keyStorePasswordField.password)
    }
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(testKeyPassword.toCharArray(), keystoreStep2.keyPasswordField.password) }
  }

  fun testRemembersPasswordForAllKeystoresAndAliases() {
    val testKeyStorePath1 = "/test/path/to/keystore1"
    val testKeyStorePath2 = "/test/path/to/keystore2"
    val testKeyAlias1 = "testkey1"
    val testKeyAlias2 = "testkey2"
    val testExportKeyPath = "test"

    // Setup in-memory PasswordSafe for tests
    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val settings = GenerateSignedApkSettings()
    settings.KEY_ALIAS = testKeyAlias1
    settings.KEY_STORE_PATH = testKeyStorePath1
    settings.REMEMBER_PASSWORDS = true
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    fun KeystoreStep.setFieldsAndCommit(keyStore: String, keyAlias: String, keyStorePassword: String, keyPassword: String) {
      keyStorePathField.text = keyStore
      keyAliasField.text = keyAlias
      keyStorePasswordField.text = keyStorePassword
      keyPasswordField.text = keyPassword
      commitForNext()
    }
    KeystoreStep(wizard, true, facets).setFieldsAndCommit(
      keyStore = testKeyStorePath1,
      keyAlias = testKeyAlias1,
      keyStorePassword = "keystore1",
      keyPassword = "keystore1_alias1"
    )

    KeystoreStep(wizard, true, facets).also {
      waitForCondition(1, TimeUnit.SECONDS) { it.keyStorePasswordField.password.isNotEmpty() }
    }.setFieldsAndCommit(
      keyStore = testKeyStorePath1,
      keyAlias = testKeyAlias2,
      keyStorePassword = "keystore1",
      keyPassword = "keystore1_alias2"
    )

    KeystoreStep(wizard, true, facets).also {
      waitForCondition(1, TimeUnit.SECONDS) { it.keyStorePasswordField.password.isNotEmpty() }
    }.setFieldsAndCommit(
      keyStore = testKeyStorePath2,
      keyAlias = testKeyAlias1,
      keyStorePassword = "keystore2",
      keyPassword = "keystore2_alias1"
    )

    fun KeystoreStep.checkFields(keyStore: String, keyAlias: String, keyStorePassword: String, keyPassword: String) {
      waitForCondition(1, TimeUnit.SECONDS) {
        keyStorePasswordField.password.isNotEmpty() && keyPasswordField.password.isNotEmpty()
      }
      assertEquals(keyStore, keyStorePathField.text)
      assertEquals(keyAlias, keyAliasField.text)
      assertEquals(keyStorePassword, String(keyStorePasswordField.password))
      assertEquals(keyPassword, String(keyPasswordField.password))
    }
    // Change settings back to first keystore and first alias
    settings.KEY_STORE_PATH = testKeyStorePath1
    settings.KEY_ALIAS = testKeyAlias1

    KeystoreStep(wizard, true, facets).checkFields(
      keyStore = testKeyStorePath1,
      keyAlias = testKeyAlias1,
      keyStorePassword = "keystore1",
      keyPassword = "keystore1_alias1"
    )

    // Change settings back to first keystore and second alias
    settings.KEY_STORE_PATH = testKeyStorePath1
    settings.KEY_ALIAS = testKeyAlias2

    KeystoreStep(wizard, true, facets).checkFields(
      keyStore = testKeyStorePath1,
      keyAlias = testKeyAlias2,
      keyStorePassword = "keystore1",
      keyPassword = "keystore1_alias2"
    )

    // Change settings back to second keystore
    settings.KEY_STORE_PATH = testKeyStorePath2
    settings.KEY_ALIAS = testKeyAlias1

    KeystoreStep(wizard, true, facets).checkFields(
      keyStore = testKeyStorePath2,
      keyAlias = testKeyAlias1,
      keyStorePassword = "keystore2",
      keyPassword = "keystore2_alias1"
    )
  }

  // See b/64995008 & b/70937387 - we want to ensure smooth transition so that the user didn't have to retype both passwords
  fun testRememberPasswordsUsingLegacyRequestor() {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"
    val testLegacyKeyPassword = "somestuff"
    val legacyRequestor = KeystoreStep::class.java
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(CredentialAttributes(legacyRequestor.name, keyPasswordKey, legacyRequestor), testLegacyKeyPassword)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    // Yes, it's weird but before the fix for b/64995008 this was exactly the observed behavior: the keystore password would
    // never be populated, whereas the key password would be saved as expected.
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    waitForCondition(1, TimeUnit.SECONDS) { keystoreStep.keyPasswordField.password.isNotEmpty() }
    assertEquals(testLegacyKeyPassword, String(keystoreStep.keyPasswordField.password))

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyRequestor.name, keyPasswordKey, legacyRequestor)))
  }

  // See b/192344567. We had to replace requestor with service name once again
  // (change to the new API and use separate service name per keystore/alias).
  fun testRememberPasswordsUsingLegacyRequestor2() {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"
    val testLegacyKeyStorePassword = "111111"
    val testLegacyKeyPassword = "somestuff"
    val legacyKeystoreRequestor = "${KeystoreStep::class.java.name}\$KeyStorePasswordRequestor"
    val legacyKeyRequestor = "${KeystoreStep::class.java.name}\$KeyPasswordRequestor"
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    val keyStorePasswordKey = KeystoreStep.makePasswordKey(KEY_STORE_PASSWORD_KEY, settings.KEY_STORE_PATH, null)
    passwordSafe.setPassword(CredentialAttributes(legacyKeystoreRequestor, keyStorePasswordKey), testLegacyKeyStorePassword)
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(CredentialAttributes(legacyKeyRequestor, keyPasswordKey), testLegacyKeyPassword)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    waitForCondition(1, TimeUnit.SECONDS) {
      Arrays.equals(testLegacyKeyStorePassword.toCharArray(), keystoreStep.keyStorePasswordField.password)
    }
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(testLegacyKeyPassword.toCharArray(), keystoreStep.keyPasswordField.password) }

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased.
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyKeyRequestor, keyPasswordKey)))
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyKeystoreRequestor, keyStorePasswordKey)))
  }

  fun testPasswordsReloadOnKeyStoreChange() {
    val testKeyStorePath1 = "/test/path/to/keystore1"
    val testKeyStorePassword1 = "keystorePassword1"
    val testKeyAlias1 = "testkey1"
    val testKeyPassword1 = "keyPassword1"
    val testKeyStorePath2 = "/test/path/to/keystore2"
    val testKeyStorePassword2 = "keystorePassword2"
    val testKeyAlias2 = "testkey2"
    val testKeyPassword2 = "keyPassword2"

    // Setup in-memory PasswordSafe for tests
    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val settings = GenerateSignedApkSettings()
    settings.KEY_ALIAS = testKeyAlias1
    settings.KEY_STORE_PATH = testKeyStorePath1
    settings.REMEMBER_PASSWORDS = true
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    trySavePasswords(testKeyStorePath1, testKeyStorePassword1.toCharArray(), testKeyAlias1, testKeyPassword1.toCharArray(), true)
    trySavePasswords(testKeyStorePath2, testKeyStorePassword2.toCharArray(), testKeyAlias2, testKeyPassword2.toCharArray(), true)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(testKeyStorePassword1, String(keystoreStep.keyStorePasswordField.password))
    assertEquals(testKeyPassword1, String(keystoreStep.keyPasswordField.password))

    // Change keystore.
    keystoreStep.keyStorePathField.apply {
      text = testKeyStorePath2
      postActionEvent()
    }

    assertEquals(testKeyStorePassword2, String(keystoreStep.keyStorePasswordField.password))
    assertEmpty(String(keystoreStep.keyPasswordField.password))

    // Change key alias.
    keystoreStep.keyAliasField.textField.apply {
      text = testKeyAlias2
      postActionEvent()
    }

    assertEquals(testKeyStorePassword2, String(keystoreStep.keyStorePasswordField.password))
    assertEquals(testKeyPassword2, String(keystoreStep.keyPasswordField.password))
  }

  fun testGetHelpId() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    Truth.assertThat(keystoreStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }
}
