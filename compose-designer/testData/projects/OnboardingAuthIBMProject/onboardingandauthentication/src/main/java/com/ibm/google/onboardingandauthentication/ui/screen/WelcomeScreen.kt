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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.PromptTextButton
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

/**
 * WelcomeScreen is the main composable for the app's welcome screen. It displays a logo, welcome
 * text, illustration, and buttons for user actions.
 *
 * @param onGetStartedClick Callback for the "Get Started" button click.
 * @param onLoginClick Callback for the "Login" button click.
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 */
@Composable
fun WelcomeScreen(
  modifier: Modifier = Modifier,
  onGetStartedClick: () -> Unit = {},
  onLoginClick: () -> Unit = {},
) {
  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { innerPadding
    ->
    WelcomeScreenContent(
      modifier = Modifier.padding(innerPadding).fillMaxSize(),
      onGetStartedClick = onGetStartedClick,
      onLoginClick = onLoginClick,
    )
  }
}

/**
 * WelcomeScreenContent is the main content of the welcome screen. It displays the logo, welcome
 * text, illustration, and buttons.
 *
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 * @param onGetStartedClick Callback for the "Get Started" button click.
 * @param onLoginClick Callback for the "Login" button click.
 */
@Composable
fun WelcomeScreenContent(
  modifier: Modifier = Modifier,
  onGetStartedClick: () -> Unit = {},
  onLoginClick: () -> Unit = {},
) {
  Box(modifier = modifier) {
    Column(
      modifier = modifier.verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(R.drawable.ic_bloom_logo),
        contentDescription = stringResource(R.string.green_logo_titled_bloom),
        contentScale = ContentScale.Inside,
      )
      Text(
        modifier = Modifier.padding(vertical = 52.dp),
        text = stringResource(R.string.grow_your_inner_peace),
        style =
          MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      )
      Image(
        painter = painterResource(R.drawable.ic_welcome_illustration),
        contentDescription = stringResource(R.string.welcome_illustration_content_description),
        contentScale = ContentScale.Crop,
        modifier = Modifier.padding(top = 24.dp).width(936.dp).height(510.dp),
      )
      RoundedFilledButton(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(maxWidth()),
        label = stringResource(R.string.get_started),
        onClick = onGetStartedClick,
      )
      PromptTextButton(
        message = stringResource(R.string.already_have_an_account),
        actionLabel = stringResource(R.string.login),
        onActionClick = onLoginClick,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
  OnBoardingAndAuthenticationTheme { WelcomeScreen() }
}
