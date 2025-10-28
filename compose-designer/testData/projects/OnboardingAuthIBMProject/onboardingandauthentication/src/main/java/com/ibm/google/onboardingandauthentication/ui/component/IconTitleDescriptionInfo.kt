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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function that displays an icon, a title, and an optional description in a horizontal
 * row.
 *
 * @param title The title text to display next to the icon.
 * @param description The description text to display below the title. If empty, it is not shown.
 * @param iconResId The [ImageVector] icon to display at the start of the row.
 * @param modifier Modifier applied to the root composable for layout or styling.
 */
@Composable
fun IconTitleDescriptionInfo(
  title: String,
  description: String,
  iconResId: ImageVector,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = iconResId,
      contentDescription = null, // Decorative Icon
      modifier = Modifier.size(24.dp),
      tint = MaterialTheme.colorScheme.primary,
    )
    Column(
      modifier = Modifier.padding(start = 16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      if (description.isNotEmpty()) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFF9FFE9)
@Composable
fun IconTitleDescriptionInfoPreview() {
  OnBoardingAndAuthenticationTheme {
    IconTitleDescriptionInfo(
      iconResId = Icons.Outlined.Person,
      title = stringResource(R.string.how_you_will_use_this),
      description = stringResource(R.string.to_record_videos_or_audio_within_the_app),
    )
  }
}
