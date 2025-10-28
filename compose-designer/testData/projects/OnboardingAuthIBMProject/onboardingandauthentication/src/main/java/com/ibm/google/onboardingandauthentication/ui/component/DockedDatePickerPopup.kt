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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function to display a docked date picker popup.
 *
 * @param headlineSelectedDate The headline for the selected date.
 * @param onDateChanged Callback to handle the selected date change.
 * @param onDismissRequest Callback to handle the dismiss request.
 * @param modifier The modifier to be applied to the layout.
 * @param containerColor The color of the popup container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockedDatePickerPopup(
  headlineSelectedDate: String,
  onDateChanged: (Long?) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
  val datePickerState = rememberDatePickerState()
  Popup(onDismissRequest = onDismissRequest) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      modifier = modifier.background(color = containerColor, shape = RoundedCornerShape(24.dp)),
    ) {
      Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Top) {
        DatePicker(
          title = {
            Text(
              text = stringResource(R.string.select_date),
              style =
                MaterialTheme.typography.labelLarge.copy(
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
              modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 24.dp),
            )
          },
          headline = {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
              verticalArrangement = Arrangement.Top,
              horizontalAlignment = Alignment.Start,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = headlineSelectedDate,
                  style =
                    MaterialTheme.typography.headlineLarge.copy(
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                )
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = stringResource(R.string.edit_content_description),
                )
              }
            }
          },
          state = datePickerState,
          showModeToggle = false,
          colors = DatePickerDefaults.colors(containerColor = containerColor),
        )
        DatePickerPopupActionButtons(
          onCancelClick = onDismissRequest,
          onOkClick = {
            onDateChanged(datePickerState.selectedDateMillis)
            onDismissRequest()
          },
        )
      }
    }
  }
}

/**
 * Composable function to display a row of buttons for a date picker popup.
 *
 * @param onCancelClick Callback to handle the cancel action.
 * @param onOkClick Callback to handle the confirm action.
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun DatePickerPopupActionButtons(
  onCancelClick: () -> Unit,
  onOkClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val textStyle =
    MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary)

  Row(
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    TextButton(onClick = onCancelClick) {
      Text(text = stringResource(R.string.cancel), style = textStyle)
    }
    TextButton(onClick = onOkClick) { Text(text = stringResource(R.string.ok), style = textStyle) }
  }
}

@Preview(showBackground = true)
@Composable
fun DockedDatePickerPopupPreview() {
  OnBoardingAndAuthenticationTheme {
    DockedDatePickerPopup(
      headlineSelectedDate = "01/05/2025",
      onDateChanged = {},
      onDismissRequest = {},
    )
  }
}
