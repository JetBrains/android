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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.publishing.play.wizard.FormField
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
// TODO: android-merge; com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
// import com.google.gct.login2.PreferredUser
// import com.google.gct.login2.fstLoginFeature
import com.intellij.ide.BrowserUtil
import icons.StudioIllustrationsCompose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.editorTabStyle
import org.jetbrains.jewel.ui.theme.linkStyle

@Composable
fun WizardPageScope.AccountChooserPage() {
  // TODO: android-merge; the signed in accounts come from com.google.gct.login2.GoogleLoginService in
  // tools/vendor/google/login, which this repository does not carry, so there is no account list to choose from.
  // val loggedInUsers by GoogleLoginService.instance.allUsersFlow.collectAsState()
  // val activeUser by GoogleLoginService.instance.activeUserFlow.collectAsState()
  //
  // var selectedUserEmail by remember(activeUser) { mutableStateOf(activeUser?.email) }
  // var isSignInWithNewAccount by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    PlayPublishingWizardHeader()

    // Illustration
    Box(
      modifier = Modifier.fillMaxWidth().height(150.dp).background(JewelTheme.editorTabStyle.colors.background),
      contentAlignment = Alignment.Center,
    ) {
      Illustration()
    }

    Row(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(24.dp).weight(0.75f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Publish your application directly to Google Play Store from Android Studio.")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("In the following steps, you will be guided to:", fontWeight = FontWeight.Medium)
          BulletItem("Sign in and link your Google Play account to Android Studio, if necessary")
          BulletItem("Upload your Android App Bundle (.aab)")
          BulletItem("Configure your release")
        }

        val linkStyle =
          TextLinkStyles(
            SpanStyle(color = JewelTheme.linkStyle.colors.content),
            hoveredStyle = SpanStyle(color = JewelTheme.linkStyle.colors.content, textDecoration = TextDecoration.Underline),
          )

        val linkId = "external_link_icon"
        val inlineContent =
          mapOf(
            linkId to
              InlineTextContent(Placeholder(16.sp, 16.sp, PlaceholderVerticalAlign.Center)) {
                Icon(AllIconsKeys.Ide.External_link_arrow, null)
              }
          )

        Text(
          buildAnnotatedString {
            append(
              "You must have a Google Play developer account to publish apps. If you don't have one yet, you can start the registration at "
            )
            withLink(LinkAnnotation.Clickable("signup", linkStyle) { BrowserUtil.browse("https://play.google.com/console/signup") }) {
              append("Google Play Console")
              appendInlineContent(linkId, " ")
            }
            append(" which might take several days.\n\nOnce complete, please return here to continue with publishing.")
          },
          inlineContent = inlineContent,
        )

        // TODO: android-merge; the account dropdown lists the users of
        // com.google.gct.login2.GoogleLoginService in tools/vendor/google/login, which this repository does not
        // carry, so the list is always empty here and the dropdown is never shown.
        // if (loggedInUsers.isNotEmpty()) {
        //   FormField(label = "Google account:") {
        //     Dropdown(
        //       menuContent = {
        //         loggedInUsers.keys.forEach { email ->
        //           selectableItem(
        //             selected = (!isSignInWithNewAccount && email == selectedUserEmail),
        //             onClick = {
        //               selectedUserEmail = email
        //               isSignInWithNewAccount = false
        //             },
        //           ) {
        //             Text(email)
        //           }
        //         }
        //         separator()
        //         selectableItem(selected = isSignInWithNewAccount, onClick = { isSignInWithNewAccount = true }) {
        //           Text("Sign in with a new account")
        //         }
        //       }
        //     ) {
        //       val dropdownText = if (isSignInWithNewAccount) "Sign in with a new account" else (selectedUserEmail ?: "Select account")
        //       Text(dropdownText)
        //     }
        //   }
        // }
      }

      Spacer(modifier = Modifier.fillMaxWidth().weight(0.25f))
    }

    Spacer(modifier = Modifier.weight(1f))

    // TODO: android-merge; choosing the banner text from the login state needs
    // com.google.gct.login2.GoogleLoginService and fstLoginFeature from tools/vendor/google/login, which this
    // repository does not carry. Without them there are no logged in users, so the first branch below is the
    // one that always applies and its text is shown unconditionally.
    // val infoBannerText: String? =
    //   when {
    //     loggedInUsers.isEmpty() || isSignInWithNewAccount -> {
    //       "Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step."
    //     }
    //     selectedUserEmail?.let { !fstLoginFeature.isLoggedIn(it) } ?: false -> {
    //       "Using this wizard requires new authorization for Android Studio. You will be redirected to the web to sign in as the next step."
    //     }
    //     else -> null
    //   }
    //
    // infoBannerText?.let {
    //   // Info Banner
    //   @OptIn(ExperimentalJewelApi::class)
    //   InlineInformationBanner(text = it, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp))
    // }

    // Info Banner
    @OptIn(ExperimentalJewelApi::class)
    InlineInformationBanner(
      text = "Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step.",
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
  }

  nextActionName = "Next"
  // TODO: android-merge; the sign-in step needs com.google.gct.login2.fstLoginFeature, PreferredUser and
  // GoogleLoginService from tools/vendor/google/login, which this repository does not carry.
  // nextAction = WizardAction {
  //   if (isSignInWithNewAccount) {
  //     fstLoginFeature.logInBlocking(preferredUser = PreferredUser.None, parentComponent = component)
  //   } else {
  //     selectedUserEmail?.let { GoogleLoginService.instance.setActiveUser(it) }
  //     if (!fstLoginFeature.isLoggedIn()) {
  //       fstLoginFeature.logInBlocking(parentComponent = component)
  //     }
  //   }
  //   if (fstLoginFeature.isLoggedIn()) {
  //     pushPage { ChooseBundlePage() }
  //   }
  // }
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
    IllustrationIcon(key = StudioIllustrationsCompose.Common.PackageAab)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.PackageAab)
    IllustrationIcon(key = StudioIllustrationsCompose.Common.Launch)
  }
}

@Composable
private fun IllustrationIcon(key: IconKey) {
  Icon(key, contentDescription = null, modifier = Modifier.size(100.dp))
}
