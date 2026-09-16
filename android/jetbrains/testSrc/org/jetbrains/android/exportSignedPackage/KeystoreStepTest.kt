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

import com.android.testutils.MockitoThreadLocalsCleaner
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.recordExistingFile
import com.android.testutils.file.someRoot
import com.android.testutils.waitForCondition
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.PasswordSafeSettings
import com.intellij.credentialStore.ProviderType
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.passwordSafe.impl.TestPasswordSafeImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.outputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_PASSWORD_KEY
import org.jetbrains.android.exportSignedPackage.KeystoreStep.KEY_STORE_PASSWORD_KEY
import org.jetbrains.android.exportSignedPackage.KeystoreStep.trySavePasswords
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.math.BigInteger
import java.nio.file.FileSystem
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Arrays
import java.util.Date
import java.util.concurrent.TimeUnit

private const val KEY_ALIAS = "testkey"
private const val KEY_STORE_PASSWORD = "123456"
private const val KEY_PASSWORD = "qwerty"

internal class KeystoreStepTest : HeavyPlatformTestCase() {
  private lateinit var ideComponents: IdeComponents
  private lateinit var facets: MutableList<AndroidFacet>
  private lateinit var myAndroidFacet1: AndroidFacet
  private lateinit var myAndroidFacet2: AndroidFacet

  private val keystore: KeyStore = createKeyStore().apply { addKey() }
  private lateinit var testKeyStorePath: String
  private val fileSystem: FileSystem =
    createInMemoryFileSystem().apply {
      val keystorePath = someRoot.resolve("test/path/to/keystore").also { testKeyStorePath = it.toString() }.recordExistingFile()
      keystore.store(keystorePath.outputStream(), KEY_STORE_PASSWORD.toCharArray())
    }

  private val mockitoCleaner = MockitoThreadLocalsCleaner()

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(project, testRootDisposable)
    facets = ArrayList()
    myAndroidFacet1 = AndroidFacet(module, AndroidFacet.NAME, AndroidFacetConfiguration())
    myAndroidFacet2 = AndroidFacet(module, AndroidFacet.NAME, AndroidFacetConfiguration())
    mockitoCleaner.setup()
  }

  private fun createKeyStore(keyStorePassword: String = KEY_STORE_PASSWORD): KeyStore =
    KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, keyStorePassword.toCharArray()) }

  private fun KeyStore.addKey(alias: String = KEY_ALIAS, password: String = KEY_PASSWORD) {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()

    val issuer = X500Name("CN=Test")
    val serial = BigInteger.valueOf(1)
    val notBefore = Date()
    val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000)

    val certBuilder = JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, issuer, keyPair.public)
    val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.private)
    val certHolder = certBuilder.build(signer)
    val cert = JcaX509CertificateConverter().getCertificate(certHolder)

    setKeyEntry(alias, keyPair.private, password.toCharArray(), arrayOf(cert))
  }

  override fun tearDown() {
    try {
      mockitoCleaner.cleanupAndTearDown()
    }
    finally {
      super.tearDown()
    }
  }

  fun testNextSucceeds() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)

    val settings = GenerateSignedApkSettings.getInstance(wizard.project)
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = KEY_ALIAS
    settings.REMEMBER_PASSWORDS = false
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep.myFileSystem =
      createInMemoryFileSystem().apply {
        val keystorePath = someRoot.resolve(testKeyStorePath).recordExistingFile()
        keystore.store(keystorePath.outputStream(), KEY_STORE_PASSWORD.toCharArray())
      }
    keystoreStep.keyStorePasswordField.text = KEY_STORE_PASSWORD
    keystoreStep.keyPasswordField.text = KEY_PASSWORD
    keystoreStep.keyStorePathField.text = testKeyStorePath
    keystoreStep._init()
    keystoreStep.commitForNext()
  }

  fun testModuleDropDownEnabledByDefault() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    val keystoreStep = KeystoreStep(wizard, facets)
    assertEquals(true, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testModuleDropDownDisabledWhenOnlyOneFacet() {
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(myAndroidFacet1)
    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep._init()
    assertEquals(false, keystoreStep.myModuleCombo.isEnabled)
  }

  fun testModuleDropDownOrder() {
    val tmpDir = Files.createTempDirectory("androidTest")
    Disposer.register(testRootDisposable) {
      tmpDir.deleteRecursively()
    }
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    val unsortedModulesOrder = listOf("appB", "app1", "appD", "xappC", "appA")

    val moduleManager = ModuleManager.getInstance(project)
    WriteAction.run<Throwable> {
      for (moduleName in unsortedModulesOrder) {
        val module = moduleManager.newNonPersistentModule(moduleName, "JAVA_MODULE")
        Disposer.register(testRootDisposable, module)
        facets.add(FakeAndroidFacet(module))
      }
    }

    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep._init()

    val expectedModulesOrder = listOf("app1", "appA", "appB", "appD", "xappC")
    val currentModulesOrder = (0 until keystoreStep.myModuleCombo.itemCount).map { keystoreStep.myModuleCombo.getItemAt(it).module.name }
    assertEquals(expectedModulesOrder, currentModulesOrder)
  }

  fun testUpdatesInvalidSelection() {
    // if the current selected facet is no longer in the list of facets, then it should be updated to the first one in the list
    val wizard = setupWizardHelper()
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)
    facets.add(myAndroidFacet1)
    facets.add(myAndroidFacet2)
    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep._init()
    assertEquals(myAndroidFacet1, keystoreStep.myModuleCombo.selectedItem)

    // remove the selected facet
    keystoreStep.myFacets.removeAt(0)

    keystoreStep._init()
    assertEquals(myAndroidFacet2, keystoreStep.myModuleCombo.selectedItem)
  }

  fun testRememberPasswords() {
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = KEY_ALIAS
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep.myFileSystem = fileSystem

    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(KEY_ALIAS, keystoreStep.keyAliasField.text)
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    assertEquals(0, keystoreStep.keyPasswordField.password.size)

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = KEY_STORE_PASSWORD
    keystoreStep.keyPasswordField.text = KEY_PASSWORD
    keystoreStep.commitForNext()

    // Assert that the passwords are persisted and a new form instance fields populated as necessary.
    val keystoreStep2 = KeystoreStep(wizard, facets)
    assertEquals(testKeyStorePath, keystoreStep2.keyStorePathField.text)
    assertEquals(KEY_ALIAS, keystoreStep2.keyAliasField.text)
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(KEY_STORE_PASSWORD.toCharArray(), keystoreStep2.keyStorePasswordField.password) }
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(KEY_PASSWORD.toCharArray(), keystoreStep2.keyPasswordField.password) }
  }

  fun testRemembersPasswordForAllKeystoresAndAliases() {
    val testKeyAlias1 = "testkey1"
    val testKeyAlias2 = "testkey2"
    val testKeyStorePath1 = fileSystem.someRoot.resolve("test/path/to/keystore1")
    val testKeyStorePath2 = fileSystem.someRoot.resolve("test/path/to/keystore2")
    val testKeyStoreLocation1 = testKeyStorePath1.toString()
    val testKeyStoreLocation2 = testKeyStorePath2.toString()

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
    settings.KEY_STORE_PATH = testKeyStoreLocation1
    settings.REMEMBER_PASSWORDS = true
    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val keyStore1 =
      createKeyStore("keystore1").apply {
        addKey(testKeyAlias1, "keystore1_alias1")
        addKey(testKeyAlias2, "keystore1_alias2")
      }
    val keyStore2 =
      createKeyStore("keystore2").apply {
        addKey(testKeyAlias1, "keystore2_alias1")
        addKey(testKeyAlias2, "keystore2_alias2")
      }

    fileSystem.someRoot.resolve(testKeyStoreLocation1).recordExistingFile().also {
      keyStore1.store(it.outputStream(), "keystore1".toCharArray())
    }
    fileSystem.someRoot.resolve(testKeyStoreLocation2).recordExistingFile().also {
      keyStore2.store(it.outputStream(), "keystore2".toCharArray())
    }

    fun KeystoreStep.setFieldsAndCommit(keyStore: String, keyAlias: String, keyStorePassword: String, keyPassword: String) {
      keyStorePathField.text = keyStore
      keyAliasField.text = keyAlias
      keyStorePasswordField.text = keyStorePassword
      keyPasswordField.text = keyPassword
      commitForNext()
    }
    KeystoreStep(wizard, facets)
      .apply { myFileSystem = fileSystem }
      .setFieldsAndCommit(
        keyStore = testKeyStoreLocation1,
        keyAlias = testKeyAlias1,
        keyStorePassword = "keystore1",
        keyPassword = "keystore1_alias1",
      )

    KeystoreStep(wizard, facets)
      .apply { myFileSystem = fileSystem }
      .also { waitForCondition(1, TimeUnit.SECONDS) { it.keyStorePasswordField.password.isNotEmpty() } }
      .setFieldsAndCommit(
        keyStore = testKeyStoreLocation1,
        keyAlias = testKeyAlias2,
        keyStorePassword = "keystore1",
        keyPassword = "keystore1_alias2",
      )

    KeystoreStep(wizard, facets)
      .apply { myFileSystem = fileSystem }
      .also { waitForCondition(1, TimeUnit.SECONDS) { it.keyStorePasswordField.password.isNotEmpty() } }
      .setFieldsAndCommit(
        keyStore = testKeyStoreLocation2,
        keyAlias = testKeyAlias1,
        keyStorePassword = "keystore2",
        keyPassword = "keystore2_alias1",
      )

    fun KeystoreStep.checkFields(keyStore: String, keyAlias: String, keyStorePassword: String, keyPassword: String) {
      waitForCondition(1, TimeUnit.SECONDS) { keyStorePasswordField.password.isNotEmpty() && keyPasswordField.password.isNotEmpty() }
      assertEquals(keyStore, keyStorePathField.text)
      assertEquals(keyAlias, keyAliasField.text)
      assertEquals(keyStorePassword, String(keyStorePasswordField.password))
      assertEquals(keyPassword, String(keyPasswordField.password))
    }
    // Change settings back to first keystore and first alias
    settings.KEY_STORE_PATH = testKeyStoreLocation1
    settings.KEY_ALIAS = testKeyAlias1

    KeystoreStep(wizard, facets)
      .checkFields(
        keyStore = testKeyStoreLocation1,
        keyAlias = testKeyAlias1,
        keyStorePassword = "keystore1",
        keyPassword = "keystore1_alias1",
      )

    // Change settings back to first keystore and second alias
    settings.KEY_STORE_PATH = testKeyStoreLocation1
    settings.KEY_ALIAS = testKeyAlias2

    KeystoreStep(wizard, facets)
      .checkFields(
        keyStore = testKeyStoreLocation1,
        keyAlias = testKeyAlias2,
        keyStorePassword = "keystore1",
        keyPassword = "keystore1_alias2",
      )

    // Change settings back to second keystore
    settings.KEY_STORE_PATH = testKeyStoreLocation2
    settings.KEY_ALIAS = testKeyAlias1

    KeystoreStep(wizard, facets)
      .apply { myFileSystem = fileSystem }
      .checkFields(
        keyStore = testKeyStoreLocation2,
        keyAlias = testKeyAlias1,
        keyStorePassword = "keystore2",
        keyPassword = "keystore2_alias1",
      )
  }

  // See b/64995008 & b/70937387 - we want to ensure smooth transition so that the user didn't have to retype both passwords
  fun testRememberPasswordsUsingLegacyRequestor() {
    val testLegacyKeyPassword = "somestuff"
    val legacyRequestor = KeystoreStep::class.java

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = KEY_ALIAS
    settings.REMEMBER_PASSWORDS = true

    ideComponents.replaceProjectService(GenerateSignedApkSettings::class.java, settings)

    val passwordSafeSettings = PasswordSafeSettings()
    passwordSafeSettings.providerType = ProviderType.MEMORY_ONLY
    val passwordSafe = TestPasswordSafeImpl(passwordSafeSettings)
    val keyPasswordKey = KeystoreStep.makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS)
    passwordSafe.setPassword(CredentialAttributes(legacyRequestor.name, keyPasswordKey), testLegacyKeyPassword)
    ideComponents.replaceApplicationService(PasswordSafe::class.java, passwordSafe)

    val wizard = mock(ExportSignedPackageWizard::class.java)
    whenever(wizard.project).thenReturn(project)
    whenever(wizard.targetType).thenReturn(ExportSignedPackageWizard.APK)

    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep.myFileSystem = fileSystem
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(KEY_ALIAS, keystoreStep.keyAliasField.text)
    // Yes, it's weird but before the fix for b/64995008 this was exactly the observed behavior: the keystore password would
    // never be populated, whereas the key password would be saved as expected.
    assertEquals(0, keystoreStep.keyStorePasswordField.password.size)
    waitForCondition(1, TimeUnit.SECONDS) { keystoreStep.keyPasswordField.password.isNotEmpty() }
    assertEquals(testLegacyKeyPassword, String(keystoreStep.keyPasswordField.password))

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = KEY_STORE_PASSWORD
    keystoreStep.keyPasswordField.text = KEY_PASSWORD
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyRequestor.name, keyPasswordKey)))
  }

  // See b/192344567. We had to replace requestor with service name once again
  // (change to the new API and use separate service name per keystore/alias).
  fun testRememberPasswordsUsingLegacyRequestor2() {
    val testLegacyKeyStorePassword = "111111"
    val testLegacyKeyPassword = "somestuff"
    val legacyKeystoreRequestor = "${KeystoreStep::class.java.name}\$KeyStorePasswordRequestor"
    val legacyKeyRequestor = "${KeystoreStep::class.java.name}\$KeyPasswordRequestor"
    val testExportKeyPath = "test"
    File(testExportKeyPath).mkdir()

    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = KEY_ALIAS
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

    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep.myFileSystem = fileSystem
    assertEquals(testKeyStorePath, keystoreStep.keyStorePathField.text)
    assertEquals(KEY_ALIAS, keystoreStep.keyAliasField.text)
    waitForCondition(1, TimeUnit.SECONDS) {
      Arrays.equals(testLegacyKeyStorePassword.toCharArray(), keystoreStep.keyStorePasswordField.password)
    }
    waitForCondition(1, TimeUnit.SECONDS) { Arrays.equals(testLegacyKeyPassword.toCharArray(), keystoreStep.keyPasswordField.password) }

    // Set passwords and commit.
    keystoreStep.keyStorePasswordField.text = KEY_STORE_PASSWORD
    keystoreStep.keyPasswordField.text = KEY_PASSWORD
    keystoreStep.commitForNext()

    // Now check that the old-style password is erased.
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyKeyRequestor, keyPasswordKey)))
    assertEquals(null, passwordSafe.getPassword(CredentialAttributes(legacyKeystoreRequestor, keyStorePasswordKey)))
  }

  fun testPasswordsReloadOnKeyStoreChange() {
    val testKeyStorePath1 = fileSystem.someRoot.resolve("test/path/to/keystore1").toString()
    val testKeyStorePassword1 = "keystorePassword1"
    val testKeyAlias1 = "testkey1"
    val testKeyPassword1 = "keyPassword1"
    val testKeyStorePath2 = fileSystem.someRoot.resolve("test/path/to/keystore2").toString()
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

    val keystoreStep = KeystoreStep(wizard, facets)
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
    val keystoreStep = KeystoreStep(wizard, facets)
    keystoreStep._init()
    Truth.assertThat(keystoreStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }

  private class FakeAndroidFacet(module: Module) : AndroidFacet(module, NAME, AndroidFacetConfiguration())

  private fun setupWizardHelper(): ExportSignedPackageWizard {
    val settings = GenerateSignedApkSettings()
    settings.KEY_STORE_PATH = testKeyStorePath
    settings.KEY_ALIAS = KEY_ALIAS
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
}
