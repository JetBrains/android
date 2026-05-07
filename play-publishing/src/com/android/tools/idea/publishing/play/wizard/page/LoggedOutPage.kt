/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play.wizard.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.fstLoginFeature
import icons.StudioIllustrationsCompose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.editorTabStyle
import org.jetbrains.jewel.ui.theme.inlineBannerStyle

@Composable
fun WizardPageScope.LoggedOutPage() {
  nextActionName = "Next"
  // TODO: android-merge; the sign-in step needs com.google.gct.login2.fstLoginFeature from
  // tools/vendor/google/login, which this repository does not carry.
  // nextAction = WizardAction {
  //   if (!fstLoginFeature.isLoggedIn()) {
  //     fstLoginFeature.logInBlocking(parentComponent = component)
  //   }
  //   if (fstLoginFeature.isLoggedIn()) {
  //     close()
  //   }
  // }

  Column(modifier = Modifier.fillMaxSize()) {
    // Header
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(key = StudioIllustrationsCompose.Common.PlayConsoleIcon, contentDescription = null, modifier = Modifier.size(24.dp))
      Text("Upload to Play", style = JewelTheme.defaultTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    }

    // Illustration
    Box(
      modifier = Modifier.fillMaxWidth().height(180.dp).background(JewelTheme.editorTabStyle.colors.background),
      contentAlignment = Alignment.Center,
    ) {
      Illustration()
    }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text("Publish your application directly to the Google Play Store from Android Studio.")

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("In the following steps, you will be guided to:", fontWeight = FontWeight.Medium)
        BulletItem("Sign in and link your Play Console account, if necessary")
        BulletItem("Upload your Android App Bundle (.aab) or APK")
        BulletItem("Configure your release")
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Info Banner
    @OptIn(ExperimentalJewelApi::class)
    InlineInformationBanner(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      style = JewelTheme.inlineBannerStyle.information,
    ) {
      Text("Using this wizard requires signing into Android Studio. You will be redirected to the web to sign in at the next step.")
    }
  }
}

@Composable
private fun BulletItem(text: String) {
  Row(modifier = Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("•")
    Text(text)
  }
}

@Composable
private fun Illustration() {
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.PackageAab)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.PackageApk)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.PackageAab)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
  }
}

@Composable
private fun IllustrationIcon(key: IconKey) {
  Icon(key, contentDescription = null, modifier = Modifier.size(100.dp))
}
