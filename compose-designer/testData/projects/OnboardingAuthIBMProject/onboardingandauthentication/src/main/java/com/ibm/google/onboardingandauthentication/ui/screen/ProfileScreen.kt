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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.component.TextSwitchRow
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIconDescription
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val DEFAULT_SELECTED_CHIP = "Stress Relief"

/**
 * Composable function representing the Profile screen.
 *
 * This screen uses a Scaffold layout that provides a TopAppBar and a content area hosting the main
 * profile content.
 *
 * @param modifier Optional [Modifier] to apply external styling.
 * @param onSaveButtonClick Callback invoked when the Save button is clicked.
 * @param onBackNavigationClick Callback invoked when the back navigation button is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  modifier: Modifier = Modifier,
  onSaveButtonClick: () -> Unit = {},
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
    ProfileContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(innerPadding),
      onSaveButtonClick = onSaveButtonClick,
    )
  }
}

/**
 * Composable that displays the entire profile screen content.
 *
 * It includes:
 * - A profile header with welcome text and logo.
 * - A user input form for name and short bio.
 * - A personalization section with mindfulness chips and a daily reminder toggle.
 * - A save button to submit the form.
 *
 * @param modifier Modifier to apply for styling and layout adjustments.
 * @param onSaveButtonClick Callback invoked when the Save button is pressed.
 */
@Composable
fun ProfileContent(modifier: Modifier = Modifier, onSaveButtonClick: () -> Unit = {}) {
  var isChecked by remember { mutableStateOf(true) }
  var name by remember { mutableStateOf("") }
  var shortBio by remember { mutableStateOf("") }
  val selectedMindfulnessChips = remember { mutableStateListOf(DEFAULT_SELECTED_CHIP) }
  val scrollState = rememberScrollState()
  val resetFields = {
    name = ""
    shortBio = ""
    isChecked = false
    selectedMindfulnessChips.clear()
  }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxWidth(maxWidth()).fillMaxHeight().verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ProfileHeaderSection(modifier = Modifier.padding(top = 16.dp))
      ProfileInfoForm(
        modifier = Modifier.padding(top = 40.dp),
        name = name,
        shortBio = shortBio,
        onNameChange = { name = it },
        onBioChange = { shortBio = it },
      )
      PersonalizeSection(
        modifier = Modifier.padding(bottom = 24.dp),
        isChecked = isChecked,
        onCheckedChange = { isChecked = it },
        selectedMindfulnessChips = selectedMindfulnessChips,
        onSelectedMindfulnessChipChange = { isSelected, label ->
          if (isSelected) selectedMindfulnessChips.remove(label)
          else selectedMindfulnessChips.add(label)
        },
      )
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
        label = stringResource(R.string.save_changes),
        onClick = {
          KeyboardControllerUtils.hide()
          resetFields()
          onSaveButtonClick()
        },
      )
    }
  }
}

/**
 * Composable function displaying the header section of the Profile screen.
 *
 * This section includes:
 * - A welcome message.
 * - An app logo.
 * - A message asking the user to personalize their experience.
 *
 * @param modifier Optional [Modifier] for external styling and layout adjustments.
 */
@Composable
fun ProfileHeaderSection(modifier: Modifier = Modifier) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = stringResource(R.string.welcome_to_bloom),
      style = MaterialTheme.typography.headlineMedium,
    )
    Image(
      modifier = Modifier.padding(top = 26.dp),
      painter = painterResource(R.drawable.ic_bloom_leaves),
      contentDescription = stringResource(R.string.app_logo),
      contentScale = ContentScale.Crop,
    )
    Text(
      modifier = Modifier.padding(top = 4.dp),
      text = stringResource(R.string.lets_personalize_your_experience),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/**
 * Composable function displaying the Personalization section within the Profile screen.
 *
 * This section includes:
 * - A title text asking what brings the user to mindfulness.
 * - A list of selectable chips representing different mindfulness options.
 * - A toggle switch to enable or disable daily reminders.
 *
 * @param onCheckedChange Callback invoked when the daily reminders switch is toggled.
 * @param onSelectedMindfulnessChipChange Callback invoked when a chip is selected or deselected.
 * @param modifier Optional [Modifier] to apply for styling and layout adjustments.
 * @param isChecked Boolean indicating the current state of the daily reminders switch.
 * @param selectedMindfulnessChips List of currently selected mindfulness chip labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizeSection(
  onCheckedChange: (Boolean) -> Unit,
  onSelectedMindfulnessChipChange: (Boolean, String) -> Unit,
  modifier: Modifier = Modifier,
  isChecked: Boolean = false,
  selectedMindfulnessChips: List<String> = emptyList(),
) {
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = stringResource(R.string.what_brings_you_to_mindfulness),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).align(Alignment.Start),
    )
    MindfulnessChipsSelector(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      chipItems = stringArrayResource(R.array.chip_items),
      selectedChips = selectedMindfulnessChips,
      onSelectedChipChange = onSelectedMindfulnessChipChange,
    )
    TextSwitchRow(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
      label = stringResource(R.string.daily_remainders),
      isChecked = isChecked,
      onCheckedChange = onCheckedChange,
    )
  }
}

/**
 * Composable function that displays a row of selectable chips for mindfulness options.
 *
 * @param chipItems Array of chip labels to display.
 * @param onSelectedChipChange Callback invoked when a chip is selected or deselected. Receives a
 *   boolean indicating if the chip is currently selected and the chip label.
 * @param modifier Optional [Modifier] for styling and layout adjustments.
 * @param selectedChips List of currently selected chip labels.
 */
@ExperimentalLayoutApi
@Composable
fun MindfulnessChipsSelector(
  chipItems: Array<String>,
  onSelectedChipChange: (Boolean, String) -> Unit,
  modifier: Modifier = Modifier,
  selectedChips: List<String> = emptyList(),
) {
  FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    chipItems.forEach { label ->
      val isSelected = selectedChips.contains(label)

      InputChip(
        selected = selectedChips.contains(label),
        onClick = { onSelectedChipChange(isSelected, label) },
        label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
          Icon(
            imageVector =
              if (selectedChips.contains(label)) Icons.Default.Done else Icons.Default.Add,
            contentDescription = null, // Decorative Item
            modifier = Modifier.size(SwitchDefaults.IconSize),
          )
        },
        shape = MaterialTheme.shapes.small,
        colors =
          InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            disabledLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.surface,
          ),
      )
    }
  }
}

/**
 * Composable function to display a form for user profile information.
 *
 * @param name The current name entered by the user.
 * @param shortBio The current short bio entered by the user.
 * @param onNameChange Callback invoked when the user changes the name.
 * @param onBioChange Callback invoked when the user changes the short bio.
 * @param modifier Optional [Modifier] to apply for styling and layout adjustments.
 */
@Composable
fun ProfileInfoForm(
  name: String,
  shortBio: String,
  onNameChange: (String) -> Unit,
  onBioChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.your_name),
      value = name,
      trailingIcon = getClearOrErrorIcon(value = name),
      trailingIconContentDescription = stringResource(getClearOrErrorIconDescription(false)),
      onTrailingClick = { onNameChange("") },
      onValueChange = { onNameChange(it) },
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.enter_a_short_bio),
      value = shortBio,
      trailingIcon = getClearOrErrorIcon(value = shortBio),
      trailingIconContentDescription = stringResource(getClearOrErrorIconDescription(false)),
      onTrailingClick = { onBioChange("") },
      onValueChange = { onBioChange(it) },
    )
  }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
  OnBoardingAndAuthenticationTheme { ProfileScreen() }
}
