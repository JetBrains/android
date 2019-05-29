/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.spellchecker

import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import org.jetbrains.android.AndroidTestCase

class AndroidJsonSpellcheckingStrategyTest : AndroidTestCase() {

  fun testGoogleServicesSpelling() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "google-services.json",
      //language=JSON
      """
        {
        "project_info": {
          "project_number": "123456789",
          "firebase_url": "https://asdfg-12345.firebaseio.com",
          "project_id": "example-12345",
          "storage_bucket": "example-12345.asdjhk.com"
        },
        "client": [
          {
            "client_info": {
              "mobilesdk_app_id": "12646387:adshg:732739cd55665c",
              "android_client_info": {
                "package_name": "com.example.example"
              }
            },
            "oauth_client": [
              {
                "client_id": "sdfjhkoue.sdhgfks.com",
                "client_type": 3
              }
            ],
            "api_key": [
              {
                "current_key": "AJHSGAKjhassa7687DASNDjn"
              }
            ],
            "services": {
              "appinvite_service": {
                "other_platform_oauth_client": [
                  {
                    "client_id": "123456789-asdfgh123456asdf123.apps.asdf.com",
                    "client_type": 3
                  }
                ]
              }
            }
          }
        ],
        "configuration_version": "1"
      }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testGoogleServicesSpellingWithErrors() {
    myFixture.enableInspections(setOf(SpellCheckingInspection::class.java))
    val virtualFile = myFixture.addFileToProject(
      "google-services.json",
      //language=JSON
      """
        {
        "porject_info": {
          "project_numbur": "123456789",
          "firebase_url": "https://asdfg-12345.firebaseio.com",
          "project_id": "example-12345",
          "storage_bucket": "example-12345.asdjhk.com"
        }
      }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val highlightingInfoList = myFixture.doHighlighting(SpellCheckerSeveritiesProvider.TYPO)
    val expectedDescriptions = ImmutableList.of("Typo: In word 'porject'", "Typo: In word 'numbur'")
    assertThat(highlightingInfoList).hasSize(2)
    assertThat(highlightingInfoList.stream().allMatch {expectedDescriptions.contains(it.description)}).isTrue()
  }
}