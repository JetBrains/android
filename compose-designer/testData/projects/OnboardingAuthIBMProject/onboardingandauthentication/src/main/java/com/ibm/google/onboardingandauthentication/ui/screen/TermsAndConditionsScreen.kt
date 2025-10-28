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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.modifier.verticalColumnScrollbar
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val HORIZONTAL_SPACE = "\t\t"
private const val BULLET = "\u2022"

/**
 * Composable function for the account verification screen.
 *
 * @param modifier Modifier to be applied to the screen layout.
 * @param onAcceptButtonClick Lambda function invoked when the accept button is clicked.
 * @param onBackNavigationClick Callback invoked when the back navigation button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
  modifier: Modifier = Modifier,
  onAcceptButtonClick: () -> Unit = {},
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
    TermsAndConditionsContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface)
          .padding(innerPadding)
          .fillMaxSize()
          .padding(horizontal = 16.dp),
      onAcceptButtonClick = onAcceptButtonClick,
    )
  }
}

/**
 * Composable function for the content of the Terms and Conditions screen.
 *
 * @param modifier Modifier to be applied to the content layout.
 * @param onAcceptButtonClick Lambda function invoked when the accept button is clicked.
 */
@Composable
fun TermsAndConditionsContent(modifier: Modifier = Modifier, onAcceptButtonClick: () -> Unit = {}) {
  var isChecked by remember { mutableStateOf(false) }
  val scrollState = rememberScrollState()
  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxWidth(maxWidth()).fillMaxHeight()) {
      HeadlineSection(
        modifier = Modifier.fillMaxWidth(),
        headline = stringResource(R.string.terms_and_conditions_heading),
        subHeadline = stringResource(R.string.last_updates),
      )
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 16.dp)
            .verticalScroll(scrollState)
            .verticalColumnScrollbar(scrollState)
            .weight(1f, false)
      ) {
        TermsAndConditionsText(modifier = Modifier.fillMaxWidth())
      }
      TermsAndConditionsCheckbox(checked = isChecked, onCheckedChange = { isChecked = it })
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.accept),
        onClick = {
          onAcceptButtonClick()
          isChecked = false
        },
      )
    }
  }
}

/**
 * Composable function for the content of the Terms and Conditions Text.
 *
 * @param modifier Modifier to be applied to the content layout.
 */
@Composable
fun TermsAndConditionsText(modifier: Modifier = Modifier) {
  val useOfServiceList = stringArrayResource(R.array.service_array)
  val updateList = stringArrayResource(R.array.updates_array)
  val paragraphStyle = ParagraphStyle(textIndent = TextIndent(restLine = 12.sp))
  val paragraphStyle1 = ParagraphStyle(textIndent = TextIndent(restLine = 12.sp))

  Text(
    modifier = modifier,
    text = stringResource(R.string.please_read),
    style = MaterialTheme.typography.bodyMedium,
  )
  Text(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    text = stringResource(R.string.agreement_to_tnc),
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
  )
  Text(
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 4.dp, top = 4.dp),
    text = stringResource(R.string.by_accessing),
    style = MaterialTheme.typography.bodyMedium,
  )
  Text(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    text = stringResource(R.string.use_of_service),
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.onSurface,
  )
  Text(
    modifier = Modifier.padding(start = 24.dp, end = 4.dp, top = 4.dp),
    text =
      buildAnnotatedString {
        useOfServiceList.forEach {
          withStyle(style = paragraphStyle) {
            append(BULLET)
            append(HORIZONTAL_SPACE)
            append(it)
          }
        }
      },
    style = MaterialTheme.typography.bodyMedium,
  )
  Text(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    text = stringResource(R.string.updates),
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    color = MaterialTheme.colorScheme.onSurface,
  )
  Text(
    modifier = Modifier.padding(start = 24.dp, end = 4.dp, top = 4.dp),
    text =
      buildAnnotatedString {
        updateList.forEach {
          withStyle(style = paragraphStyle1) {
            append(BULLET)
            append(HORIZONTAL_SPACE)
            append(it)
          }
        }
      },
    style = MaterialTheme.typography.bodyMedium,
  )
}

/**
 * Composable function for the verify email button.
 *
 * @param headline Composable content representing the headline section.
 * @param modifier Modifier to be applied to the button layout.
 * @param subHeadline Composable content representing the sub-headline section.
 */
@Composable
fun HeadlineSection(headline: String, modifier: Modifier = Modifier, subHeadline: String? = null) {
  Column(modifier = modifier) {
    Text(
      modifier = Modifier.fillMaxWidth(),
      text = headline,
      style = MaterialTheme.typography.headlineMedium,
    )
    Text(
      text = subHeadline ?: "",
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun TermsAndConditionsPreview() {
  OnBoardingAndAuthenticationTheme { TermsAndConditionsScreen() }
}
