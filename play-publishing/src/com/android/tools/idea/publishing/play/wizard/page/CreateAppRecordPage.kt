/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play.wizard.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.publishing.play.client.PlayPublishingClient
import com.android.tools.idea.publishing.play.client.PlayPublishingException
import com.android.tools.idea.publishing.play.client.playStoreLanguageNames
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppType
import com.android.tools.idea.publishing.play.wizard.FormField
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardState
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

internal const val DEFAULT_RELEASE_NAME = "First Release"

internal val DEFAULT_RELEASE_NOTES =
  """
  <en-US>
  - Initial internal testing build.
  </en-US>
  """
    .trimIndent()

private const val APP_NAME_CHAR_LIMIT = 30

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
fun WizardPageScope.CreateAppRecordPage() {
  val state = getOrCreateState<PlayPublishingWizardState> { error("State not initialized") }
  val project = LocalProject.current ?: error("Project cannot be null")

  var isLoadingDevelopers by remember { mutableStateOf(true) }
  var errorMessage: String? by remember { mutableStateOf(null) }
  val accounts by
    produceState(initialValue = emptyList()) {
      try {
        value = PlayPublishingClient.getInstance().listDevelopers()
      } catch (e: PlayPublishingException) {
        errorMessage = "Failed to load developers: ${e.message}"
      } catch (e: Exception) {
        errorMessage = "Failed to load developers: ${e.message}"
      } finally {
        isLoadingDevelopers = false
      }
    }
  var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }
  val appNameState = rememberTextFieldState(state.appName ?: "")

  val languages = remember { playStoreLanguageNames.entries.sortedBy { it.value } }
  var selectedLanguageKey by remember { mutableStateOf(state.defaultLanguage ?: "en-US") }
  var isCreatingApp by remember { mutableStateOf(false) }

  LaunchedEffect(appNameState.text) { state.appName = appNameState.text.toString() }

  LaunchedEffect(selectedLanguageKey) { state.defaultLanguage = selectedLanguageKey }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      PlayPublishingWizardHeader(subtitle = "Create new app")

      Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Form fields
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          FormField(label = "Developer account:") {
            if (isLoadingDevelopers) {
              Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading accounts...")
              }
            } else if (accounts.isEmpty()) {
              Text(
                text = "No developer accounts found.",
                modifier = Modifier.padding(top = 8.dp),
                style = JewelTheme.defaultTextStyle.copy(color = Color.Gray),
              )
            } else {
              Dropdown(
                modifier = Modifier.fillMaxWidth(),
                menuContent = {
                  accounts.forEach { account ->
                    selectableItem(selected = (account == selectedAccount), onClick = { selectedAccount = account }) {
                      Row(verticalAlignment = Alignment.CenterVertically) { Text(account.businessName) }
                    }
                  }
                },
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(selectedAccount?.businessName ?: "Select account") }
              }
            }
          }

          // App Name
          FormField(label = "App name:") {
            Column {
              TextField(
                state = appNameState,
                modifier = Modifier.fillMaxWidth(),
                inputTransformation = InputTransformation.maxLength(APP_NAME_CHAR_LIMIT),
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "${(APP_NAME_CHAR_LIMIT - appNameState.text.length).coerceIn(0, APP_NAME_CHAR_LIMIT)} characters remaining",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp, color = Color.Gray),
              )
            }
          }

          // Default Language
          FormField(label = "Default language:") {
            Dropdown(
              modifier = Modifier.fillMaxWidth(),
              menuContent = {
                languages.forEach { entry ->
                  selectableItem(selected = (entry.key == selectedLanguageKey), onClick = { selectedLanguageKey = entry.key }) {
                    Text("${entry.value} - ${entry.key}")
                  }
                }
              },
            ) {
              Text("${playStoreLanguageNames[selectedLanguageKey]} - $selectedLanguageKey")
            }
          }
        }
      }
    }

    errorMessage?.let { InlineErrorBanner(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Text(it) } }
  }

  nextActionName = if (isCreatingApp) "Creating..." else "Next"
  nextAction =
    if (
      isCreatingApp ||
        selectedAccount == null ||
        errorMessage?.contains("The package name ${state.packageName} is not available on Play") == true
    )
      WizardAction.Disabled
    else
      WizardAction {
        val dev = selectedAccount ?: return@WizardAction
        state.developerId = dev.developerId
        val languageCode = selectedLanguageKey
        val appConfig =
          AppConfig(
            packageName = state.packageName ?: "",
            title = appNameState.text.toString(),
            defaultLanguageCode = languageCode,
            // We default to free apps. Users can change this selection in the console.
            appType = AppType.APP_TYPE_APP,
            paid = false,
          )
        isCreatingApp = true

        val success =
          runWithModalProgressBlocking(ModalTaskOwner.project(project), "Creating App...", TaskCancellation.nonCancellable()) {
            try {
              PlayPublishingClient.getInstance().createAppRecord(dev.developerId, appConfig)
              true
            } catch (e: PlayPublishingException) {
              val message = e.message ?: "Unknown error"
              errorMessage =
                if (message.contains("Package name ${state.packageName} is not available on Play", ignoreCase = true)) {
                  "The package name ${state.packageName} is not available on Play. Please change the package name, generate a new signed bundle or APK and try again."
                } else {
                  message
                }
              false
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              errorMessage = e.message ?: "Unknown error"
              false
            } finally {
              isCreatingApp = false
            }
          }
        if (success) {
          state.isAppCreated = true
          state.releaseName = DEFAULT_RELEASE_NAME
          state.releaseNotes = DEFAULT_RELEASE_NOTES
          pushPage { CreateReleasePage() }
        }
      }
}
