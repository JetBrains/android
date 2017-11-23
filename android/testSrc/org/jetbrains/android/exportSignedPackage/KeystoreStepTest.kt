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
import com.intellij.testFramework.IdeaTestCase

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
}
