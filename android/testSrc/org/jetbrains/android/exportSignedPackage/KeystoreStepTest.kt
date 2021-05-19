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

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.mockito.MockitoThreadLocalsCleaner
import com.android.tools.idea.testing.IdeComponents
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.PasswordSafeSettings
import com.intellij.credentialStore.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.ide.wizard.CommitStepException
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_PASSWORD_KEY
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.jetbrains.android.util.AndroidBundle
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.*
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
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(false, keystoreStep.exportKeysCheckBox.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testEnableEncryptedKeyExportFlagTrue() {
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(true, keystoreStep.exportKeysCheckBox.isVisible)
    assertEquals(true, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(true, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testEnableEncryptedKeyCheckboxButNotSelected_ExportKeyPathFieldsShouldBeHidden() {
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)

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
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
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
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
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
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
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
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    assertEquals(true, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testModuleDropDownDisabledWhenOnlyOneFacet() {
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(myAndroidFacet1)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    assertEquals(false, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testUpdatesInvalidSelection() {
    // if the current selected facet is no longer in the list of facets, then it should be updated to the first one in the list
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
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
    val passwordSafe = BasePasswordSafe(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(project)
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
    val passwordSafe = BasePasswordSafe(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(project)
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

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
    val passwordSafe = BasePasswordSafe(passwordSafeSettings)
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(CredentialAttributes(legacyRequestor, keyPasswordKey), testLegacyKeyPassword)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(project)
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    // Yes, it's weird but before the fix for b/64995008 this was exactly the observed behavior: the keystore password would
    // never be populated, whereas the key password would be saved as expected.
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(testLegacyKeyPassword.toCharArray(), keystoreStep.keyPasswordField.password) }

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyRequestor, keyPasswordKey)))
  }
}
