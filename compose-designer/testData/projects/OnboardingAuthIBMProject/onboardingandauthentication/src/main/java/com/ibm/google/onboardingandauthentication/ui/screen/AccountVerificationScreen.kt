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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.ReCaptchaCheckbox
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIconDescription
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val MAX_VERIFICATION_CODE = 6
private const val START_TIMER = "00"
private const val MAX_TIMER = 30

/**
 * Composable function that displays the account verification screen.
 *
 * @param modifier Modifier to apply to the screen layout.
 * @param verifyEmail Callback invoked to handle the email verification action.
 * @param onBackNavigationClick Callback invoked when the back navigation button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountVerificationScreen(
  modifier: Modifier = Modifier,
  verifyEmail: () -> Unit = {},
  onBackNavigationClick: () -> Unit = {},
) {
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
    AccountVerificationContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface)
          .padding(innerPadding)
          .fillMaxSize()
          .padding(horizontal = 16.dp),
      verifyEmail = verifyEmail,
    )
  }
}

/**
 * Composable function that displays the content of the account verification screen.
 *
 * @param modifier Modifier to apply to the content layout.
 * @param verifyEmail Callback invoked to handle the email verification action.
 */
@Composable
fun AccountVerificationContent(modifier: Modifier = Modifier, verifyEmail: () -> Unit = {}) {
  var verificationCode by remember { mutableStateOf("") }
  var verificationCodeError by remember { mutableStateOf(false) }
  var isChecked by remember { mutableStateOf(false) }
  val resetFields = {
    verificationCode = ""
    verificationCodeError = false
    isChecked = false
  }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxWidth(maxWidth()).fillMaxHeight(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        text = stringResource(R.string.verify_your_email),
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        text = stringResource(R.string.verification_message),
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
      DefaultOutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 16.dp),
        label = stringResource(R.string.enter_verification_code),
        value = verificationCode,
        isError = verificationCodeError,
        supportingText =
          if (verificationCodeError) stringResource(R.string.enter_valid_verification_code) else "",
        trailingIcon = getClearOrErrorIcon(verificationCode, verificationCodeError),
        trailingIconContentDescription =
          stringResource(getClearOrErrorIconDescription(verificationCodeError)),
        onTrailingClick = { if (verificationCodeError.not()) verificationCode = "" },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = {
          verificationCode = it
          verificationCodeError = it.length > MAX_VERIFICATION_CODE && it.isNotEmpty()
        },
      )
      ReCaptchaCheckbox(
        modifier = Modifier.align(Alignment.Start).wrapContentSize().padding(bottom = 16.dp),
        label = stringResource(R.string.i_am_not_robot),
        checked = isChecked,
        onCheckedChange = { isChecked = it },
      )
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        label = stringResource(R.string.verify_email),
        onClick = {
          KeyboardControllerUtils.hide()
          resetFields()
          verifyEmail()
        },
      )
      VerificationResendPrompt()
    }
  }
}

/**
 * Composable function that displays the verification resend prompt.
 *
 * @param modifier Modifier to apply to the prompt layout.
 */
@Composable
fun VerificationResendPrompt(modifier: Modifier = Modifier) {
  val spanStyle =
    SpanStyle(
      fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
      fontSize = MaterialTheme.typography.bodySmall.fontSize,
      fontStyle = MaterialTheme.typography.bodySmall.fontStyle,
    )

  Text(
    buildAnnotatedString {
      withStyle(style = spanStyle.copy(color = MaterialTheme.colorScheme.onSurface)) {
        append("${stringResource(R.string.did_not_get_code)}\t ")
      }
      withStyle(style = spanStyle.copy(color = MaterialTheme.colorScheme.primary)) {
        append(stringResource(R.string.resend_in, START_TIMER, MAX_TIMER))
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFF9FFE9)
@Composable
fun VerificationResendPromptPreview() {
  OnBoardingAndAuthenticationTheme { VerificationResendPrompt() }
}

@Preview(showBackground = true)
@Composable
fun AccountVerificationScreenPreview() {
  OnBoardingAndAuthenticationTheme { AccountVerificationScreen() }
}
