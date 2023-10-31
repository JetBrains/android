/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SigningConfigSelectorDialogTest {
  @get:Rule
  val edtRule = EdtRule()

  private val debugSigning = FakeSigningConfigModel("debug")
  private val customSigning = FakeSigningConfigModel("custom")
  private lateinit var dialog: SigningConfigSelectorDialog

  @Before
  fun setUp() {
    dialog = SigningConfigSelectorDialog(listOf(debugSigning, customSigning))
  }

  @After
  fun tearDown() {
    // Close the dialog for proper disposal
    dialog.close(DialogWrapper.OK_EXIT_CODE)
  }

  @Test
  fun returnsSelectedConfig() {
    dialog.signingConfigComboBox.selectedIndex = 1
    assertThat(dialog.selectedConfig().name()).isEqualTo("custom")
  }
}

private class FakeSigningConfigModel(val name: String) : SigningConfigModel {
  override fun name(): String {
    return name
  }

  override fun getPsiElement() = throw NotImplementedError()
  override fun delete() = throw NotImplementedError()
  override fun getHolder() = throw NotImplementedError()
  override fun getRawElement() = throw NotImplementedError()
  override fun getRawPropertyHolder() = throw NotImplementedError()
  override fun getFullyQualifiedName() = throw NotImplementedError()
  override fun getInScopeProperties() = throw NotImplementedError()
  override fun getDeclaredProperties() = throw NotImplementedError()
  override fun rename(newName: String, renameReferences: Boolean) = throw NotImplementedError()
  override fun storeFile(): ResolvedPropertyModel = throw NotImplementedError()
  override fun storePassword(): PasswordPropertyModel = throw NotImplementedError()
  override fun storeType(): ResolvedPropertyModel = throw NotImplementedError()
  override fun keyAlias(): ResolvedPropertyModel = throw NotImplementedError()
  override fun keyPassword(): PasswordPropertyModel = throw NotImplementedError()
  override fun getDslElement(): GradleDslElement = throw NotImplementedError()
}