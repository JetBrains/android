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
package com.ibm.google.onboardingandauthentication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { OnBoardingAndAuthenticationTheme { MainScreenContent() } }
  }
}

/** A composable function that represents the main screen content. */
@Composable
fun MainScreenContent() {
  Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
    Text(text = "Hello Android!", modifier = Modifier.padding(innerPadding))
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenContentPreview() {
  OnBoardingAndAuthenticationTheme { MainScreenContent() }
}
