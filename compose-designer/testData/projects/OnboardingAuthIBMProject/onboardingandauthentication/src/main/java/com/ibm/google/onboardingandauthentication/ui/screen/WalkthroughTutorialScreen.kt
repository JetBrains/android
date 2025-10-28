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
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.data.WalkthroughStepModel
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.component.WalkthroughHorizontalImageCarousel
import com.ibm.google.onboardingandauthentication.ui.component.WalkthroughHorizontalImageCarouselPreviewProvider
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.Utils
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

/**
 * Composable function that displays a walkthrough tutorial screen with a headline, progress
 * indicator, description, image carousel, and a start button.
 *
 * @param walkthroughImages List of WalkthroughStepModel resources to be displayed in the carousel.
 * @param modifier Modifier to be applied to the screen layout.
 * @param onStartClick Lambda function invoked when the start button is clicked.
 * @param onBackNavigationClick Lambda function invoked when the back navigation icon is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkthroughTutorialScreen(
  walkthroughImages: List<WalkthroughStepModel>,
  modifier: Modifier = Modifier,
  onStartClick: () -> Unit = {},
  onBackNavigationClick: () -> Unit = {},
) {
  Scaffold(
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.surface,
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
  ) { innerPadding ->
    WalkthroughTutorialContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface)
          .padding(innerPadding)
          .fillMaxSize()
          .padding(16.dp),
      walkthroughImages = walkthroughImages,
      onStartClick = onStartClick,
    )
  }
}

/**
 * Composable function that displays the main content of the walkthrough tutorial, including a
 * headline, progress indicator, description, image carousel, and a start button.
 *
 * @param walkthroughImages List of WalkthroughStepModel resources to be displayed in the carousel.
 * @param modifier Modifier to be applied to the content layout.
 * @param onStartClick Lambda function invoked when the start button is clicked.
 */
@Composable
fun WalkthroughTutorialContent(
  walkthroughImages: List<WalkthroughStepModel>,
  modifier: Modifier = Modifier,
  onStartClick: () -> Unit = {},
) {
  val currentIndex by remember { mutableIntStateOf(0) }
  val preferredScreenWidth = LocalConfiguration.current.screenWidthDp.dp / 2

  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.fillMaxWidth(maxWidth()).fillMaxHeight().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        text = stringResource(R.string.find_your_inner_calm),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
      )
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        progress = { Utils.getProgress(currentIndex, walkthroughImages) },
      )
      Text(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 20.dp),
        text = stringResource(R.string.daily_reminders_to_take_a_moment_to_unwind),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
      )
      WalkthroughHorizontalImageCarousel(
        modifier =
          Modifier.padding(top = 4.dp).requiredHeightIn(min = 351.dp, max = 400.dp).fillMaxWidth(),
        images = walkthroughImages,
        preferredItemWidth = preferredScreenWidth,
      )
      RoundedFilledButton(
        modifier = Modifier.padding(top = 40.dp).fillMaxWidth(),
        label = stringResource(R.string.start_my_journey),
        onClick = onStartClick,
      )
    }
  }
}

@Composable
@Preview(showBackground = true)
fun WalkthroughTutorialScreenPreview(
  @PreviewParameter(WalkthroughHorizontalImageCarouselPreviewProvider::class)
  walkthroughImages: List<WalkthroughStepModel>
) {
  OnBoardingAndAuthenticationTheme {
    WalkthroughTutorialScreen(walkthroughImages = walkthroughImages)
  }
}
