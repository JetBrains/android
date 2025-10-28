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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.LogoHeadlineSection
import com.ibm.google.onboardingandauthentication.ui.component.PromptTextButton
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.component.SignUpSignInSegmentedButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIconDescription
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val ASTERISK = '*'
private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
private const val MAX_PASSWORD_LENGTH = 8

/**
 * Composable function that displays the app's login screen.
 *
 * @param modifier Modifier to apply to the layout.
 * @param onSignInClick Callback invoked when the "Sign In" button is clicked.
 * @param onResetPasswordClick Callback invoked when the "Reset Password" text is clicked.
 */
@Composable
fun LoginScreen(
  modifier: Modifier = Modifier,
  onSignInClick: () -> Unit = {},
  onResetPasswordClick: () -> Unit = {},
) {
  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { innerPadding
    ->
    LoginContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface).padding(innerPadding).fillMaxSize(),
      onSignInClick = onSignInClick,
      onResetPasswordClick = onResetPasswordClick,
    )
  }
}

/**
 * Composable function that displays the main content of the login screen.
 *
 * @param modifier Modifier to apply to the layout.
 * @param onSignInClick Callback invoked when the "Sign In" button is clicked.
 * @param onResetPasswordClick Callback invoked when the "Reset Password" text is clicked.
 */
@Composable
fun LoginContent(
  modifier: Modifier = Modifier,
  onSignInClick: () -> Unit = {},
  onResetPasswordClick: () -> Unit = {},
) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var emailError by remember { mutableStateOf(false) }
  var passwordError by remember { mutableStateOf(false) }
  var selectedIndex by remember { mutableIntStateOf(0) }
  val resetFields = {
    email = ""
    password = ""
    selectedIndex = 0
  }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.fillMaxWidth(maxWidth()).fillMaxHeight().verticalScroll(rememberScrollState())
    ) {
      LogoHeadlineSection(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        logo = painterResource(R.drawable.ic_bloom_logo),
        logoContentDescription = stringResource(R.string.green_logo_titled_bloom),
        headline = stringResource(R.string.welcome_to_bloom),
        subHeadline = stringResource(R.string.ready_to_calm_again),
      )
      SignUpSignInSegmentedButton(
        modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
        options = stringArrayResource(R.array.segmented_button_labels),
        selectedIndex = selectedIndex,
        onSelectionChange = { selectedIndex = it },
      )
      LoginForm(
        modifier = Modifier.padding(bottom = 16.dp),
        email = email,
        password = password,
        isEmailError = emailError,
        isPasswordError = passwordError,
        onEmailChange = { input ->
          email = input
          emailError = EMAIL_REGEX.matches(input).not() && input.isNotEmpty()
        },
        onPasswordChange = { input ->
          password = input
          passwordError = input.length > MAX_PASSWORD_LENGTH && input.isNotEmpty()
        },
      )
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        label = stringResource(R.string.sign_in),
        onClick = {
          KeyboardControllerUtils.hide()
          resetFields()
          onSignInClick()
        },
      )
      PromptTextButton(
        modifier = Modifier.fillMaxWidth(),
        message = stringResource(R.string.forgot_password),
        actionLabel = stringResource(R.string.reset_password),
        onActionClick = onResetPasswordClick,
      )
    }
  }
}

/**
 * Composable that displays a login form with email and password fields.
 *
 * @param email The current email value.
 * @param password The current password value.
 * @param onEmailChange Callback invoked when the email value changes.
 * @param onPasswordChange Callback invoked when the password value changes.
 * @param modifier Modifier to apply to the layout.
 * @param isEmailError Whether the email field has an error.
 * @param isPasswordError Whether the password field has an error.
 */
@Composable
fun LoginForm(
  email: String,
  password: String,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  isEmailError: Boolean = false,
  isPasswordError: Boolean = false,
) {
  Column(
    modifier = modifier.padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.email),
      value = email,
      isError = isEmailError,
      supportingText = if (isEmailError) stringResource(R.string.please_enter_valid_email) else "",
      trailingIcon = getClearOrErrorIcon(email, isEmailError),
      trailingIconContentDescription = stringResource(getClearOrErrorIconDescription(isEmailError)),
      onTrailingClick = { if (isEmailError.not()) onEmailChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      onValueChange = { onEmailChange(it.trim()) },
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.password),
      value = password,
      isError = isPasswordError,
      visualTransformation =
        if (password.trim().isNotEmpty()) PasswordVisualTransformation(mask = ASTERISK)
        else VisualTransformation.None,
      supportingText =
        if (isPasswordError) stringResource(R.string.the_password_you_entered) else "",
      trailingIcon = getClearOrErrorIcon(password, isPasswordError),
      trailingIconContentDescription =
        stringResource(getClearOrErrorIconDescription(isPasswordError)),
      onTrailingClick = { if (isPasswordError.not()) onPasswordChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      onValueChange = { onPasswordChange(it.trim()) },
    )
  }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
  OnBoardingAndAuthenticationTheme { LoginScreen() }
}
