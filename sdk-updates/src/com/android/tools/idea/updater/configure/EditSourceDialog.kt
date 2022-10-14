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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.layout.selected
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.net.MalformedURLException
import java.net.URL
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField

/**
 * Dialog box allowing the user to edit or create an [RepositorySource]. Does some very basic validation.
 */
class EditSourceDialog(private val provider: RepositorySourceProvider, private val existingSource: RepositorySource?) : DialogWrapper(null) {

  private val existingAuth = existingSource?.let { AndroidAuthenticator.getAuthentication(existingSource.url) }
  private lateinit var urlField: JTextField
  private lateinit var nameField: JTextField
  private lateinit var useAuthentication: JBCheckBox
  private lateinit var loginField: JTextField
  private lateinit var passwordField: JPasswordField
  private lateinit var errorLabel: JLabel

  private var myUrlSet = false

  val uiName: String
      get() = nameField.text

  val url: String
      get() = urlField.text

  init {
    isModal = true

    init()
  }

  private fun validateUrl(url: String): Boolean {
    val error = getErrorMessage(url)
    if (error == null) {
      errorLabel.text = ""
      isOKActionEnabled = true
      contentPane.repaint()
      return true
    }
    else {
      errorLabel.text = error
      isOKActionEnabled = false
      contentPane.repaint()
      return false
    }
  }

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
      if (useAuthentication.isSelected) {
        return Credentials(loginField.text, passwordField.password)
      }
      return null
    }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        text("Please enter the Name and URL of the addon.xml for the update site")
      }
      row("Name:") {
        nameField = textField()
          .text(existingSource?.displayName ?: "Custom Update Site")
          .align(AlignX.FILL)
          .component
      }
      row("URL:") {
        urlField = textField()
          .text(existingSource?.url ?: "http://")
          .align(AlignX.FILL)
          .applyToComponent {
            addActionListener {
              myUrlSet = true
              validateUrl(urlField.text)
            }

            addFocusListener(object : FocusAdapter() {
              override fun focusLost(e: FocusEvent) {
                myUrlSet = true
                validateUrl(urlField.text)
              }
            })

            addKeyListener(object : KeyAdapter() {
              override fun keyTyped(e: KeyEvent) {
                if (myUrlSet) {
                  validateUrl(urlField.text + e.keyChar)
                }
              }
            })
          }.component
      }
      row {
        useAuthentication = checkBox("Use Authentication")
          .applyToComponent { isSelected = existingAuth != null }
          .component
      }
      panel {
        indent {
          row("Login:") {
            loginField = textField()
              .applyToComponent { text = existingAuth?.userName }
              .align(AlignX.FILL)
              .component
          }
          row("Password:") {
            passwordField = passwordField()
              .applyToComponent { text = existingAuth?.let { OneTimeString(existingAuth.password, clearable = true).toString() } }
              .align(AlignX.FILL)
              .component
          }
        }.enabledIf(useAuthentication.selected)
      }
      row {
        errorLabel = label("").component
      }
    }
  }

  override fun doOKAction() {
    myUrlSet = true
    if (validateUrl(urlField.text)) {
      super.doOKAction()
    }
  }
}
