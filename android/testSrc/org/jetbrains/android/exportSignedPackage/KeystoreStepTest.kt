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

import com.android.tools.idea.testing.IdeComponents
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.testFramework.IdeaTestCase
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_PASSWORD_KEY
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

import org.junit.Assert.assertArrayEquals
import org.mockito.Mockito.*

class KeystoreStepTest : IdeaTestCase() {
  private lateinit var ideComponents: IdeComponents

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)
  }

  override fun tearDown() {
    try {
      ideComponents.restore()
    }
    finally {
      super.tearDown()
    }
  }

  fun testRememberPasswords() {
    val testKeyStorePath = "/test/path/to/keystore"
    val testKeyAlias = "testkey"
    val testKeyStorePassword = "123456"
    val testKeyPassword = "qwerty"

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafe = PasswordSafeMock()
    ideComponents.replaceService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(myProject)

    val keystoreStep = KeystoreStep(wizard, true)
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(testKeyAlias, keystoreStep.keyAliasField.text)
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    assertEquals(0, keystoreStep.keyPasswordField.password.size)

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = testKeyStorePassword
    keystoreStep.keyPasswordField.text = testKeyPassword
    keystoreStep.commitForNext()

    // Assert that the passwords are persisted and a new form instance fields populated as necessary.
    val keystoreStep2 = KeystoreStep(wizard, true)
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

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = testKeyAlias
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafe = PasswordSafeMock()
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(legacyRequestor, keyPasswordKey, testLegacyKeyPassword)
    ideComponents.replaceService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    `when`(wizard.project).thenReturn(myProject)

    val keystoreStep = KeystoreStep(wizard, true)
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
    assertEquals(null, passwordSafe.getPassword(legacyRequestor, keyPasswordKey))
  }

  class PasswordSafeMock : PasswordSafe() {
    private val storedPasswords = HashMap<Class<*>, HashMap<String, String?>>()

    override fun getAsync(attributes: CredentialAttributes): Promise<Credentials> = resolvedPromise()
    override fun isMemoryOnly(): Boolean = false
    override fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean = false
    override fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean) {}
    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {}
    override fun get(attributes: CredentialAttributes): Credentials? = null

    override fun setPassword(requestor: Class<*>, accountName: String, value: String?) {
      val account = storedPasswords.getOrDefault(requestor, HashMap())
      account[accountName] = value
      storedPasswords[requestor] = account
    }

    override fun getPassword(requestor: Class<*>, accountName: String): String? {
      return storedPasswords[requestor]?.get(accountName)
    }
  }
}
