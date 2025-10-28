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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.PromptTextButton
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val ASTERISK = '*'
private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
private val PASSWORD_REGEX =
  Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}\$")

/**
 * A composable function that provides a social media login screen, including:
 * - Email and password input fields
 * - Sign-in button
 * - Forgot password action
 * - Social signin options
 *
 * @param modifier Modifier to be applied to the root composable.
 * @param onBackNavigationClick Callback invoked when the back navigation icon is clicked.
 * @param onSignInClick Callback invoked when the "Sign In" button is clicked.
 * @param onGoogleSignInClick Callback invoked when the "Sign in with Google" button is clicked.
 * @param onAppleSignInClick Callback invoked when the "Sign in with Apple" button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaLoginScreen(
  modifier: Modifier = Modifier,
  onBackNavigationClick: () -> Unit = {},
  onSignInClick: () -> Unit = {},
  onGoogleSignInClick: () -> Unit = {},
  onAppleSignInClick: () -> Unit = {},
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
    SocialLoginContent(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .imePadding()
          .verticalScroll(rememberScrollState()),
      onSignInClick = onSignInClick,
      onGoogleSignInClick = onGoogleSignInClick,
      onAppleSignInClick = onAppleSignInClick,
    )
  }
}

/**
 * A composable function that displays the full social login UI, including:
 * - Email and password input fields
 * - Sign-in button
 * - Forgot password action
 * - Social sign-in options (Google and Apple)
 *
 * @param modifier Modifier applied to the root layout of the content.
 * @param onSignInClick Callback invoked when the "Sign In" button is clicked.
 * @param onGoogleSignInClick Callback invoked when the "Sign in with Google" button is clicked.
 * @param onAppleSignInClick Callback invoked when the "Sign in with Apple" button is clicked.
 */
@Composable
fun SocialLoginContent(
  modifier: Modifier = Modifier,
  onSignInClick: () -> Unit = {},
  onGoogleSignInClick: () -> Unit = {},
  onAppleSignInClick: () -> Unit = {},
) {
  var emailValue by remember { mutableStateOf("") }
  var passwordValue by remember { mutableStateOf("") }
  var isEmailError by remember { mutableStateOf(false) }
  var isPasswordError by remember { mutableStateOf(false) }
  val onEmailChange: (String) -> Unit = { input ->
    emailValue = input
    isEmailError = EMAIL_REGEX.matches(input).not() && input.trim().isNotEmpty()
  }
  val onPasswordChange: (String) -> Unit = { input ->
    passwordValue = input
    isPasswordError = PASSWORD_REGEX.matches(input).not() && input.trim().isNotEmpty()
  }
  val clearLoginFields = {
    onEmailChange("")
    onPasswordChange("")
  }
  val passwordFocusRequester = remember { FocusRequester() }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxWidth(maxWidth()).fillMaxHeight().padding(16.dp)) {
      Text(
        modifier = Modifier.padding(top = 16.dp),
        text = stringResource(R.string.welcome_back_to_bloom),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineMedium,
      )
      Text(
        modifier = Modifier.padding(top = 28.dp),
        text = stringResource(R.string.ready_to_find_your_calm_again),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
      )
      DefaultOutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp).focusable(true),
        label = stringResource(R.string.email),
        isError = isEmailError,
        value = emailValue,
        supportingText = if (isEmailError) stringResource(R.string.email_error) else "",
        trailingIcon = getClearOrErrorIcon(emailValue, isEmailError),
        onTrailingClick = { if (!isEmailError) onEmailChange("") },
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
        onValueChange = { onEmailChange(it.trim()) },
      )
      DefaultOutlinedTextField(
        modifier = Modifier.fillMaxWidth().focusable(true).focusRequester(passwordFocusRequester),
        label = stringResource(R.string.password),
        isError = isPasswordError,
        value = passwordValue,
        visualTransformation =
          if (passwordValue.trim().isNotEmpty()) PasswordVisualTransformation(mask = ASTERISK)
          else VisualTransformation.None,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { KeyboardControllerUtils.hide() }),
        supportingText = if (isPasswordError) stringResource(R.string.password_error) else "",
        trailingIcon = getClearOrErrorIcon(passwordValue, isPasswordError),
        onTrailingClick = { if (isPasswordError.not()) onPasswordChange("") },
        onValueChange = { onPasswordChange(it.trim()) },
      )
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        label = stringResource(R.string.sign_in),
        onClick = {
          KeyboardControllerUtils.hide()
          clearLoginFields()
          onSignInClick()
        },
      )
      PromptTextButton(
        modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
        message = stringResource(R.string.forgot_password),
        actionLabel = stringResource(R.string.reset_password),
        onActionClick = {},
      )
      HorizontalDivider(
        modifier = Modifier.padding(top = 24.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.primary,
      )
      SocialSignInOutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        text = stringResource(R.string.sign_in_with_google),
        iconResId = R.drawable.ic_google,
        onClick = {
          KeyboardControllerUtils.hide()
          clearLoginFields()
          onGoogleSignInClick()
        },
      )
      SocialSignInOutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        text = stringResource(R.string.sign_in_with_apple),
        iconResId = R.drawable.ic_apple,
        onClick = {
          KeyboardControllerUtils.hide()
          clearLoginFields()
          onAppleSignInClick()
        },
      )
    }
  }
}

/**
 * A composable function for creating a social sign-in button with an icon and text.
 *
 * @param text The text to display on the button. Defaults to an empty string.
 * @param iconResId The resource ID of the icon to display on the button. Defaults to a Google icon.
 * @param onClick The callback to be invoked when the button is clicked.
 * @param modifier The [Modifier] to be applied to the button. Defaults to an empty [Modifier].
 */
@Composable
fun SocialSignInOutlinedButton(
  text: String,
  iconResId: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedButton(
    onClick = onClick,
    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
    modifier = modifier,
  ) {
    Image(
      painter = painterResource(id = iconResId),
      contentDescription = null, // Decorative Image
      modifier = Modifier.size(20.dp),
    )
    Text(modifier = Modifier.padding(start = 10.dp), text = text)
  }
}

@Preview(showBackground = true)
@Composable
fun SocialMediaLoginPreview() {
  OnBoardingAndAuthenticationTheme { SocialMediaLoginScreen() }
}
