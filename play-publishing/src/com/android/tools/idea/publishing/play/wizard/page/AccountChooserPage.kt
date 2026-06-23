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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.publishing.play.PlayPublishingUsageTracker
import com.android.tools.idea.publishing.play.wizard.FormField
import com.android.tools.idea.publishing.play.wizard.PlayPublishingWizardHeader
// AND-142 JetBrains patch (Google Play Publishing): com.google.gct.login2 is tools/vendor/google/login, which this repository does not carry.
// import com.google.gct.login2.GoogleLoginService
// import com.google.gct.login2.PreferredUser
// import com.google.gct.login2.fstLoginFeature
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import icons.StudioIllustrationsCompose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.banner.BannerLinkActionScope
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.editorTabStyle
import org.jetbrains.jewel.ui.theme.linkStyle

private const val PLAY_PUBLISHING_SERVICE_ID = "play/publishing"
private const val PLAY_PUBLISHING_SERVICE_NAME = "Google Play Publishing"

private data class AccountChooserPageState(val userNotifiedTracked: Boolean = false, val isWarningBannerDismissed: Boolean = false)

@Composable
fun WizardPageScope.AccountChooserPage() {
  val project = LocalProject.current
  val deprecationData = remember {
    DevServicesDeprecationDataProvider.getInstance().getCurrentDeprecationData(PLAY_PUBLISHING_SERVICE_ID, PLAY_PUBLISHING_SERVICE_NAME)
  }

  var pageState by getOrCreateState { mutableStateOf(AccountChooserPageState()) }

  if (!deprecationData.isSupported() && !pageState.userNotifiedTracked) {
    LaunchedEffect(deprecationData) {
      PlayPublishingUsageTracker.trackDeprecation(deprecationData.status, userNotified = true)
      // Update the page state so that this is only tracked once per dialog if the user goes to the next page and comes back.
      pageState = pageState.copy(userNotifiedTracked = true)
    }
  }

  // AND-142 JetBrains patch (Google Play Publishing): the signed in accounts come from com.google.gct.login2.GoogleLoginService in
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

    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Column(modifier = Modifier.fillMaxHeight().padding(24.dp, 8.dp).weight(0.75f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        // AND-142 JetBrains patch (Google Play Publishing): the account dropdown lists the users of
        // com.google.gct.login2.GoogleLoginService in tools/vendor/google/login, which this repository does not
        // carry, so the list is always empty here and the dropdown is never shown.
        // if (loggedInUsers.isNotEmpty() && !deprecationData.isUnsupported()) {
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

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
      Column(modifier = Modifier.padding(24.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!deprecationData.isSupported()) {
          val linkActions: BannerLinkActionScope.() -> Unit = {
            if (deprecationData.showUpdateAction) {
              action("Update Android Studio") {
                PlayPublishingUsageTracker.trackDeprecation(deprecationData.status, updateClicked = true)
                if (project != null) {
                  UpdateChecker.updateAndShowResult(project)
                } else {
                  thisLogger().error("Cannot run update check: project is null")
                }
              }
            }
            if (deprecationData.moreInfoUrl.isNotEmpty()) {
              action("More info") {
                PlayPublishingUsageTracker.trackDeprecation(deprecationData.status, moreInfoClicked = true)
                BrowserUtil.browse(deprecationData.moreInfoUrl)
              }
            }
          }
          if (deprecationData.isDeprecated() && !pageState.isWarningBannerDismissed) {
            InlineWarningBanner(
              text = deprecationData.description,
              linkActions = linkActions,
              iconActions = {
                iconAction(
                  icon = AllIconsKeys.General.Close,
                  contentDescription = "Dismiss",
                  onClick = {
                    pageState = pageState.copy(isWarningBannerDismissed = true)
                    PlayPublishingUsageTracker.trackDeprecation(deprecationData.status, dismissed = true)
                  },
                )
              },
              modifier = Modifier.fillMaxWidth(),
            )
          } else if (deprecationData.isUnsupported()) {
            InlineErrorBanner(text = deprecationData.description, linkActions = linkActions, modifier = Modifier.fillMaxWidth())
          }
        }

        // AND-142 JetBrains patch (Google Play Publishing): choosing the banner text from the login state needs
        // com.google.gct.login2.GoogleLoginService and fstLoginFeature from tools/vendor/google/login, which this
        // repository does not carry. Without them there are no logged in users, so the second branch below is the
        // one that always applies, and its text is shown whenever the service is still supported.
        // val infoBannerText: String? =
        //   when {
        //     deprecationData.isUnsupported() -> null
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
        //   @OptIn(ExperimentalJewelApi::class) InlineInformationBanner(text = it, modifier = Modifier.fillMaxWidth())
        // }

        if (!deprecationData.isUnsupported()) {
          // Info Banner
          @OptIn(ExperimentalJewelApi::class)
          InlineInformationBanner(
            text = "Signing in to Android Studio is required. You will be redirected to the web to sign in as the next step.",
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }

  nextActionName = "Next"
  // AND-142 JetBrains patch (Google Play Publishing): the sign-in step needs com.google.gct.login2.fstLoginFeature, PreferredUser and
  // GoogleLoginService from tools/vendor/google/login, which this repository does not carry. Upstream's new
  // WizardAction.Disabled branch is a no-op here: with nothing assigned, nextButton already holds
  // WizardAction.Disabled, which is its default in WizardButton.
  // nextAction =
  //   if (deprecationData.isUnsupported()) {
  //     WizardAction.Disabled
  //   } else {
  //     WizardAction {
  //       if (isSignInWithNewAccount) {
  //         fstLoginFeature.logInBlocking(preferredUser = PreferredUser.None, parentComponent = component)
  //       } else {
  //         selectedUserEmail?.let { GoogleLoginService.instance.setActiveUser(it) }
  //         if (!fstLoginFeature.isLoggedIn()) {
  //           fstLoginFeature.logInBlocking(parentComponent = component)
  //         }
  //       }
  //       if (fstLoginFeature.isLoggedIn()) {
  //         pushPage { ChooseBundlePage() }
  //       }
  //     }
  //   }
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
