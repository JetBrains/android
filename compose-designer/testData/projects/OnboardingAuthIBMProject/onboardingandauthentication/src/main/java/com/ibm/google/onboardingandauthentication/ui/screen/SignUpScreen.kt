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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.component.DefaultOutlinedTextField
import com.ibm.google.onboardingandauthentication.ui.component.DockedDatePickerPopup
import com.ibm.google.onboardingandauthentication.ui.component.LogoHeadlineSection
import com.ibm.google.onboardingandauthentication.ui.component.PromptTextButton
import com.ibm.google.onboardingandauthentication.ui.component.RoundedFilledButton
import com.ibm.google.onboardingandauthentication.ui.component.SignUpSignInSegmentedButton
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils
import com.ibm.google.onboardingandauthentication.utils.KeyboardControllerUtils.initialize
import com.ibm.google.onboardingandauthentication.utils.Utils.convertMillisToDate
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIcon
import com.ibm.google.onboardingandauthentication.utils.Utils.getClearOrErrorIconDescription
import com.ibm.google.onboardingandauthentication.utils.Utils.maxWidth

private const val ASTERISK = '*'
private const val MAX_PASSWORD_LENGTH = 8
private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
private const val HEADLINE_DATE_FORMAT = "EEE, MMM d"
private const val SELECTED_DATE_FORMAT = "MM/dd/yyyy"
private const val MM_DD_YYYY = "MM/DD/YYYY"

/**
 * Composable function displaying the sign-up screen.
 *
 * @param modifier Optional [Modifier] to apply for styling and layout adjustments. Defaults to
 *   [Modifier].
 * @param onSignUpClick Callback invoked when the Sign Up action is triggered. Defaults to an empty
 *   lambda.
 * @param onLoginClick Callback invoked when the Login action is triggered. Defaults to an empty
 *   lambda.
 */
@Composable
fun SignUpScreen(
  modifier: Modifier = Modifier,
  onSignUpClick: () -> Unit = {},
  onLoginClick: () -> Unit = {},
) {
  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) { innerPadding
    ->
    SignUpContent(
      modifier =
        Modifier.background(MaterialTheme.colorScheme.surface).padding(innerPadding).fillMaxSize(),
      onSignUpClick = onSignUpClick,
      onLoginClick = onLoginClick,
    )
  }
}

/**
 * SignUpContent is the main content of the signup screen.
 *
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 * @param onSignUpClick Callback invoked when the "Sign Up" button is clicked.
 * @param onLoginClick Callback invoked when the "Login" text is clicked.
 */
@Composable
fun SignUpContent(
  modifier: Modifier = Modifier,
  onSignUpClick: () -> Unit = {},
  onLoginClick: () -> Unit = {},
) {
  val selectDate = stringResource(R.string.select_date)
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("") }
  var address by remember { mutableStateOf("") }
  var dateOfBirth by remember { mutableStateOf("") }
  var emailError by remember { mutableStateOf(false) }
  var passwordError by remember { mutableStateOf(false) }
  var confirmPasswordError by remember { mutableStateOf(false) }
  var selectedIndex by remember { mutableIntStateOf(1) }
  var checked by remember { mutableStateOf(false) }
  var headlineDate by remember { mutableStateOf(selectDate) }
  val resetFields = {
    email = ""
    password = ""
    confirmPassword = ""
    phoneNumber = ""
    address = ""
    dateOfBirth = ""
    checked = false
    selectedIndex = 1
    headlineDate = selectDate
  }
  KeyboardControllerUtils.initialize(LocalSoftwareKeyboardController.current)

  Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.fillMaxWidth(maxWidth())
          .padding(horizontal = 16.dp)
          .wrapContentHeight()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LogoHeadlineSection(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        logo = painterResource(R.drawable.ic_bloom_logo),
        logoContentDescription = stringResource(R.string.green_logo_titled_bloom),
        headline = stringResource(R.string.welcome_to_bloom),
        subHeadline = stringResource(R.string.ready_to_find),
      )
      SignUpSignInSegmentedButton(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        options = stringArrayResource(R.array.segmented_button_labels),
        selectedIndex = selectedIndex,
        onSelectionChange = { selectedIndex = it },
      )
      AccountInformationForm(
        email = email,
        password = password,
        confirmPassword = confirmPassword,
        isEmailError = emailError,
        isPasswordError = passwordError,
        isConfirmPasswordError = confirmPasswordError,
        onEmailChange = { input ->
          email = input
          emailError = EMAIL_REGEX.matches(input).not() && input.isNotEmpty()
        },
        onPasswordChange = { input ->
          password = input
          passwordError = input.length > MAX_PASSWORD_LENGTH && input.isNotEmpty()
        },
        onConfirmPasswordChange = { input ->
          confirmPassword = input
          confirmPasswordError = input.length > MAX_PASSWORD_LENGTH && input.isNotEmpty()
        },
      )
      ContactDetailsForm(
        phoneNumber = phoneNumber,
        address = address,
        onPhoneNumberChange = { input -> phoneNumber = input },
        onAddressChange = { input -> address = input },
      )
      PersonalDetailsForm(
        headlineDate = headlineDate,
        dateOfBirth = dateOfBirth,
        onDateChange = { date ->
          date?.let {
            dateOfBirth = convertMillisToDate(dateFormat = SELECTED_DATE_FORMAT, millis = it)
            headlineDate = convertMillisToDate(dateFormat = HEADLINE_DATE_FORMAT, millis = it)
          }
        },
        modifier = Modifier.padding(top = 16.dp),
      )
      TermsAndConditionsCheckbox(
        modifier = Modifier.padding(top = 16.dp).align(Alignment.Start),
        checked = checked,
        onCheckedChange = { checked = it },
      )
      RoundedFilledButton(
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.sign_up),
        onClick = {
          KeyboardControllerUtils.hide()
          resetFields()
          onSignUpClick()
        },
      )
      PromptTextButton(
        message = stringResource(R.string.already_have_an_account),
        actionLabel = stringResource(R.string.login),
        onActionClick = onLoginClick,
      )
    }
  }
}

/**
 * AccountInformationForm is a composable that displays a form with fields for email, password, and
 * confirm password.
 *
 * @param email The current value of the email field.
 * @param password The current value of the password field.
 * @param confirmPassword The current value of the confirm password field.
 * @param onEmailChange Callback invoked when the email field value changes.
 * @param onPasswordChange Callback invoked when the password field value changes.
 * @param onConfirmPasswordChange Callback invoked when the confirm password field value changes.
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 * @param isEmailError Flag indicating whether the email field has an error.
 * @param isPasswordError Flag indicating whether the password field has an error.
 * @param isConfirmPasswordError Flag indicating whether the confirm password field has an error.
 */
@Composable
fun AccountInformationForm(
  email: String,
  password: String,
  confirmPassword: String,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onConfirmPasswordChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  isEmailError: Boolean = false,
  isPasswordError: Boolean = false,
  isConfirmPasswordError: Boolean = false,
) {
  Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      modifier = Modifier.fillMaxWidth(),
      text = stringResource(R.string.account_information),
      color = MaterialTheme.colorScheme.primary,
      style =
        MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusable(true),
      label = stringResource(R.string.email),
      value = email,
      isError = isEmailError,
      supportingText =
        if (isEmailError) stringResource(R.string.please_enter_valid_email) else null,
      trailingIcon = getClearOrErrorIcon(email, isEmailError),
      trailingIconContentDescription = stringResource(getClearOrErrorIconDescription(isEmailError)),
      onTrailingClick = { if (isEmailError.not()) onEmailChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      onValueChange = { onEmailChange(it.trim()) },
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().focusable(true),
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
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      onValueChange = { onPasswordChange(it.trim()) },
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().focusable(true),
      label = stringResource(R.string.confirm_password),
      value = confirmPassword,
      isError = isConfirmPasswordError,
      visualTransformation =
        if (confirmPassword.trim().isNotEmpty()) PasswordVisualTransformation(mask = ASTERISK)
        else VisualTransformation.None,
      supportingText =
        if (isConfirmPasswordError) stringResource(R.string.the_password_you_entered) else "",
      trailingIcon = getClearOrErrorIcon(confirmPassword, isConfirmPasswordError),
      trailingIconContentDescription =
        stringResource(getClearOrErrorIconDescription(isConfirmPasswordError)),
      onTrailingClick = { if (isConfirmPasswordError.not()) onConfirmPasswordChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      onValueChange = { onConfirmPasswordChange(it.trim()) },
    )
  }
}

/**
 * ContactDetailsForm is a composable that displays a form with phone number and address fields.
 *
 * @param phoneNumber The current value of the phone number field.
 * @param address The current value of the address field.
 * @param onPhoneNumberChange Callback invoked when the phone number field value changes.
 * @param onAddressChange Callback invoked when the address field value changes.
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 */
@Composable
fun ContactDetailsForm(
  phoneNumber: String,
  address: String,
  onPhoneNumberChange: (String) -> Unit,
  onAddressChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      modifier = Modifier.padding(top = 12.dp),
      text = stringResource(R.string.contact_details),
      color = MaterialTheme.colorScheme.primary,
      style =
        MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusable(true),
      label = stringResource(R.string.phone_number),
      value = phoneNumber,
      trailingIcon = getClearOrErrorIcon(value = phoneNumber),
      trailingIconContentDescription = stringResource(getClearOrErrorIconDescription()),
      onTrailingClick = { onPhoneNumberChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      onValueChange = { onPhoneNumberChange(it.trim()) },
    )
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().focusable(true),
      label = stringResource(R.string.address),
      value = address,
      trailingIcon = if (address.trim().isNotEmpty()) Icons.Outlined.Cancel else null,
      onTrailingClick = { onAddressChange("") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      onValueChange = { onAddressChange(it) },
    )
  }
}

/**
 * PersonalDetailsForm is a composable that displays a personal details form with a date of birth
 * field.
 *
 * @param dateOfBirth The current value of the date of birth field.
 * @param headlineDate The headline for the selected date.
 * @param onDateChange Callback invoked when the date of birth field value changes.
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 */
@Composable
fun PersonalDetailsForm(
  dateOfBirth: String,
  headlineDate: String,
  onDateChange: (Long?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = stringResource(R.string.personal_details),
      color = MaterialTheme.colorScheme.primary,
      style =
        MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
    DockedDatePicker(
      modifier = Modifier.padding(top = 8.dp),
      selectedDate = dateOfBirth,
      headlineSelectedDate = headlineDate,
      onDateChanged = onDateChange,
    )
  }
}

/**
 * TermsAndConditionsCheckbox is a composable that displays a checkbox for agreeing to terms and
 * conditions.
 *
 * @param checked Boolean indicating whether the checkbox is currently checked.
 * @param onCheckedChange Callback invoked when the checkbox state changes.
 * @param modifier Modifier for customizing the layout's appearance or behavior.
 */
@Composable
fun TermsAndConditionsCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.primary),
    )
    val text = buildAnnotatedString {
      withStyle(
        style =
          MaterialTheme.typography.bodySmall
            .copy(color = MaterialTheme.colorScheme.onSurface)
            .toSpanStyle()
      ) {
        append(stringResource(R.string.i_agree_to_the))
      }
      withStyle(
        style =
          MaterialTheme.typography.bodySmall
            .copy(color = MaterialTheme.colorScheme.primary)
            .toSpanStyle()
      ) {
        append("")
        append(stringResource(R.string.terms_and_conditions))
      }
    }
    Text(modifier = Modifier.fillMaxWidth(), text = text)
  }
}

/**
 * Composable function to display a docked date picker.
 *
 * @param selectedDate The selected date as a string.
 * @param headlineSelectedDate The headline for the selected date.
 * @param modifier The modifier to be applied to the layout.
 * @param onDateChanged Callback to handle the selected date change.
 */
@Composable
fun DockedDatePicker(
  selectedDate: String,
  headlineSelectedDate: String,
  onDateChanged: (Long?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDatePopup by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.dob),
      value = selectedDate,
      supportingText = MM_DD_YYYY,
      supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      readOnly = true,
      trailingIconContentDescription = stringResource(R.string.select_date),
      trailingIcon = Icons.Filled.Today,
      onTrailingClick = { showDatePopup = showDatePopup.not() },
      onValueChange = { /* No-op, read-only field */ },
    )
    if (showDatePopup) {
      DockedDatePickerPopup(
        modifier = Modifier.fillMaxWidth(maxWidth()).padding(16.dp),
        headlineSelectedDate = headlineSelectedDate,
        onDateChanged = onDateChanged,
        onDismissRequest = { showDatePopup = false },
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
  OnBoardingAndAuthenticationTheme { SignUpScreen() }
}
