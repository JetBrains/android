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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.IconTitleDescriptionInfo
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

/**
 * Composable that displays the permission request screen.
 *
 * This composable shows UI elements related to requesting permissions from the user.
 *
 * @param modifier Modifier to apply to the composable.
 * @param onAllowClick Callback invoked when the "Allow permissions" button is clicked.
 * @param onNotNowClick Callback invoked when the "Not now" text button is clicked.
 */
@Composable
fun PermissionScreen(
  modifier: Modifier = Modifier,
  onAllowClick: () -> Unit = {},
  onNotNowClick: () -> Unit = {},
) {
  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { innerPadding
    ->
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        modifier =
          Modifier.padding(innerPadding)
            .fillMaxWidth(maxWidth())
            .fillMaxHeight()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          modifier = Modifier.padding(top = 52.dp),
          painter = painterResource(R.drawable.ic_bloom_leaves),
          contentDescription = stringResource(R.string.app_logo),
          contentScale = ContentScale.Crop,
        )
        Text(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
          text = stringResource(R.string.allow_bloom_to_access_your_camera),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        PermissionUsageDetails()
        RoundedFilledButton(
          modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
          label = stringResource(R.string.allow_permissions),
          onClick = onAllowClick,
        )
        TextButton(onClick = onNotNowClick) {
          Text(
            text = stringResource(R.string.not_now),
            style =
              MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
          )
        }
      }
    }
  }
}

/**
 * Composable that displays the description information for the permission request screen.
 *
 * @param modifier Modifier to apply to the composable.
 */
@Composable
fun PermissionUsageDetails(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    IconTitleDescriptionInfo(
      iconResId = Icons.Outlined.Person,
      title = stringResource(R.string.how_you_will_use_this),
      description = stringResource(R.string.to_record_videos_or_audio_within_the_app),
    )
    IconTitleDescriptionInfo(
      iconResId = Icons.Outlined.Groups,
      title = stringResource(R.string.privacy),
      description =
        stringResource(R.string.we_do_not_record_without_your_direction_action_within_a_feature),
    )
    IconTitleDescriptionInfo(
      iconResId = Icons.Outlined.Settings,
      title = stringResource(R.string.your_control),
      description =
        stringResource(R.string.you_can_manage_these_permissions_from_your_device_settings),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PermissionScreenPreview() {
  OnBoardingAndAuthenticationTheme { PermissionScreen() }
}
