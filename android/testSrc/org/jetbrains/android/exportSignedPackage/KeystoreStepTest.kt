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

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.PasswordSafeSettings
import com.intellij.credentialStore.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.BasePasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.replaceService
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_PASSWORD_KEY
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.junit.Assert.assertArrayEquals
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class KeystoreStepTest : LightIdeaTestCase() {
  private lateinit var facets: MutableList<AndroidFacet>

  override fun setUp() {
    super.setUp()
    facets = ArrayList()
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
    project.replaceService(GenerateSignedApkSettings::class.java, settings, testRootDisposable)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()

    assertEquals(false, keystoreStep.myExportKeyPathLabel.isVisible)
    assertEquals(false, keystoreStep.myExportKeyPathField.isVisible)
  }

  fun testModuelDropDownEnabledByDefault() {
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    assertEquals(true, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testMooduleDropDownDisabledWhenOnlyOneFacet() {
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(mock(AndroidFacet::class.java))
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    assertEquals(false, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testUpdatesInvalidSelection() {
    // if the current selected facet is no longer in the list of facets, then it should be updated to the first one in the list
    val wizard = setupWizardHelper()
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    val mockFacet = mock(AndroidFacet::class.java)
    val mockFacet2 = mock(AndroidFacet::class.java)
    facets.add(mockFacet)
    facets.add(mockFacet2)
    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep._init()
    assertEquals(mockFacet, keystoreStep.myModuleCombo.selectedItem)

    // remove the selected facet
    keystoreStep.myFacets.removeAt(0)

    keystoreStep._init()
    assertEquals(mockFacet2, keystoreStep.myModuleCombo.selectedItem)
  }

  fun setupWizardHelper(): ExportSignedPackageWizard
  {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    project.replaceService(GenerateSignedApkSettings::class.java, settings, testRootDisposable)

    val passwordSafe = PasswordSafeMock()
    ApplicationManager.getApplication().replaceService(PasswordSafe::class.java, passwordSafe, testRootDisposable)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(getProject())
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

    project.replaceService(GenerateSignedApkSettings::class.java, settings, testRootDisposable)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = BasePasswordSafe(passwordSafeSettings)
    ApplicationManager.getApplication().replaceService(PasswordSafe::class.java, passwordSafe, testRootDisposable)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(getProject())
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
    assertArrayEquals(testKeyStorePassword.toCharArray(), keystoreStep2.keyStorePasswordField.password)
    assertArrayEquals(testKeyPassword.toCharArray(), keystoreStep2.keyPasswordField.password)
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

    project.replaceService(GenerateSignedApkSettings::class.java, settings, testRootDisposable)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = BasePasswordSafe(passwordSafeSettings)
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(CredentialAttributes(legacyRequestor, keyPasswordKey), testLegacyKeyPassword)
    ApplicationManager.getApplication().replaceService(PasswordSafe::class.java, passwordSafe, testRootDisposable)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(getProject())
    `when`(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, true, facets)
    keystoreStep.myExportKeyPathField.text = testExportKeyPath
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    // Yes, it's weird but before the fix for b/64995008 this was exactly the observed behavior: the keystore password would
    // never be populated, whereas the key password would be saved as expected.
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    assertArrayEquals(testLegacyKeyPassword.toCharArray(), keystoreStep.keyPasswordField.password)

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased
    assertEquals(null, passwordSafe.getPassword(getProject(), legacyRequestor, keyPasswordKey))
  }

  class PasswordSafeMock : PasswordSafe() {
    private val storedPasswords = HashMap<Class<*>, HashMap<String, String?>>()

    override fun getAsync(attributes: CredentialAttributes): Promise<Credentials?> = resolvedPromise()
    override var isRememberPasswordByDefault: Boolean = false
    override val isMemoryOnly: Boolean = false
    override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean = false
    override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) {}
    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {}
    override fun get(attributes: CredentialAttributes): Credentials? = null

    override fun storePassword(project: Project?, requestor: Class<*>, accountName: String, value: String?) {
      val account = storedPasswords.getOrDefault(requestor, HashMap())
      account[accountName] = value
      storedPasswords[requestor] = account
    }

    override fun getPassword(project: Project?, requestor: Class<*>, accountName: String): String? {
      return storedPasswords[requestor]?.get(accountName)
    }
  }
}
