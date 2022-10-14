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
package com.android.tools.idea.updater.configure

import com.android.repository.api.RepositorySource
import com.android.repository.api.RepositorySourceProvider
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidAuthenticator
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.OneTimeString
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.net.MalformedURLException
import java.net.URL
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

/**
 * Dialog box allowing the user to edit or create an [RepositorySource]. Does some very basic validation.
 */
class EditSourceDialog(private val provider: RepositorySourceProvider, private val existingSource: RepositorySource?) : DialogWrapper(null) {

  private val existingAuth = existingSource?.let { AndroidAuthenticator.getAuthentication(existingSource.url) }
  private val urlField = JTextField(existingSource?.url ?: "http://")
  private val nameField = JTextField(existingSource?.displayName ?: "Custom Update Site")
  private lateinit var useAuthentication: Cell<JBCheckBox>
  private val loginField = JTextField(existingAuth?.userName)
  private val passwordField = JPasswordField(existingAuth?.let { OneTimeString(existingAuth.password, clearable = true).toString() })

  val uiName: String
      get() = nameField.text

  val url: String
      get() = urlField.text

  init {
    isModal = true

    urlField.addActionListener {
      initValidation()
    }

    urlField.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        initValidation()
      }
    })

    init()
  }

  override fun doValidate() = getErrorMessage(urlField.text)?.let { ValidationInfo(it) }

  private fun getErrorMessage(urlString: String): String? {
    try {
      URL(urlString)
    }
    catch (e: MalformedURLException) {
      return "URL is invalid"
    }

    if (existingSource == null) {
      // Reject URLs that are already in the source list.
      // URLs are generally case-insensitive (except for file:// where it all depends
      // on the current OS so we'll ignore this case.)
      // If we're editing a source, skip this.
      for (s in provider.getSources(null, StudioLoggerProgressIndicator(javaClass), false)) {
        if (urlString.equals(s.url, ignoreCase = true)) {
          return "An update site with this URL already exists"
        }
      }
    }
    return null
  }

  val credentials: Credentials?
    get() {
      if (useAuthentication.component.isSelected) {
        return Credentials(loginField.text, passwordField.password)
      }
      return null
    }

  override fun createCenterPanel(): JComponent {
    return panel {
      row { comment("Please enter the Name and URL of the addon.xml for the update site") }
      row("Name:") { cell(nameField).align(AlignX.FILL) }
      row("URL:") { cell(urlField).align(AlignX.FILL) }
      row { useAuthentication = checkBox("Use Authentication").also { it.component.isSelected = existingAuth != null } }
      row("Login:") { cell(loginField).align(AlignX.FILL) }.enabledIf(useAuthentication.selected)
      row("Password:") { cell(passwordField).align(AlignX.FILL) }.enabledIf(useAuthentication.selected)
    }
  }
}
