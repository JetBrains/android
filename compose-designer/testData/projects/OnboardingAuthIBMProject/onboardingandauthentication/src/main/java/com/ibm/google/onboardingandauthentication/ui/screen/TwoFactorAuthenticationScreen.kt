/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.ibm.google.onboardingandauthentication.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

/**
 * Composable function that displays the two-factor authentication screen.
 *
 * @param modifier Modifier to be applied to the screen layout.
 * @param onVerifyClick Callback invoked when the "Verify" button is clicked.
 * @param onBackNavigationClick Callback invoked when the back navigation button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorAuthenticationScreen(
  modifier: Modifier = Modifier,
  onVerifyClick: () -> Unit = {},
  onBackNavigationClick: () -> Unit = {},
) {
  var enteredText by remember { mutableStateOf("") }
  val isError by remember { mutableStateOf(false) }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          IconButton(onClick = onBackNavigationClick) {
            Icon(
              Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(R.string.navigate_back),
            )
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        modifier =
          Modifier.padding(innerPadding).fillMaxWidth(maxWidth()).fillMaxHeight().padding(16.dp)
      ) {
        Text(
          text = stringResource(R.string.verify_your_login),
          modifier = Modifier.padding(top = 16.dp),
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = stringResource(R.string.we_have_sent),
          modifier = Modifier.padding(top = 28.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
        DefaultOutlinedTextField(
          modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
          label = stringResource(R.string.enter_verification_code),
          isError = isError,
          value = enteredText,
          trailingIcon = getClearOrErrorIcon(value = enteredText),
          onTrailingClick = { if (isError.not()) enteredText = "" },
          onValueChange = { enteredText = it.trim() },
        )
        RoundedFilledButton(
          modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
          label = stringResource(R.string.verify),
          onClick = {
            KeyboardControllerUtils.hide()
            enteredText = ""
            onVerifyClick()
          },
        )
        VerificationResendPrompt(modifier = Modifier.align(Alignment.CenterHorizontally))
      }
    }
  }
}

@Composable
@Preview(showBackground = true)
fun TwoFactorAuthenticationScreenPreview() {
  OnBoardingAndAuthenticationTheme { TwoFactorAuthenticationScreen() }
}
