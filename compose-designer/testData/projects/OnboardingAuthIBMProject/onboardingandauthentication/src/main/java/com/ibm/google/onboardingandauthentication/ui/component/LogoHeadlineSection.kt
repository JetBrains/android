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
package com.ibm.google.onboardingandauthentication.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Displays a section with a logo image, a headline, and an optional subHeadline.
 *
 * @param logo The [Painter] used to draw the logo image.
 * @param logoContentDescription The content description for the logo image, used for accessibility.
 * @param headline The main headline text displayed below the logo.
 * @param modifier The [Modifier] to be applied to the section.
 * @param subHeadline Optional subHeadline text displayed below the headline. If null, no
 *   subHeadline is shown.
 */
@Composable
fun LogoHeadlineSection(
  logo: Painter,
  logoContentDescription: String,
  headline: String,
  modifier: Modifier = Modifier,
  subHeadline: String? = null,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Image(
      painter = logo,
      contentDescription = logoContentDescription,
      contentScale = ContentScale.Crop,
    )
    Text(
      text = headline,
      style =
        MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 24.dp),
    )
    subHeadline?.let {
      Text(
        text = subHeadline,
        style =
          MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun LogoHeadlineSectionPreview() {
  OnBoardingAndAuthenticationTheme {
    LogoHeadlineSection(
      modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surface),
      logo = painterResource(R.drawable.ic_bloom_logo),
      headline = stringResource(R.string.grow_your_inner_peace),
      subHeadline = stringResource(R.string.grow_your_inner_peace),
      logoContentDescription = stringResource(R.string.green_logo_titled_bloom),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun LogoHeadlineSectionWithoutSubHeadlinePreview() {
  OnBoardingAndAuthenticationTheme {
    LogoHeadlineSection(
      modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surface),
      logo = painterResource(R.drawable.ic_bloom_logo),
      logoContentDescription = stringResource(R.string.green_logo_titled_bloom),
      headline = stringResource(R.string.grow_your_inner_peace),
    )
  }
}
