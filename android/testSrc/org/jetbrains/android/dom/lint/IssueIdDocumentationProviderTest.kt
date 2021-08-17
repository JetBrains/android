/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.dom.lint

import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.client.api.Vendor
import com.intellij.psi.PsiManager
import org.jetbrains.android.AndroidTestCase

class IssueIdDocumentationProviderTest : AndroidTestCase() {
  fun testDescribeIssue() {
    val psiManager = PsiManager.getInstance(project);
    val provider = IssueIdDocumentationProvider()
    val vendor = Vendor(
      vendorName = "Android Open Source Project",
      identifier = "IssueIdDocumentationProviderTest",
      feedbackUrl = "https://issuetracker.google.com/issues/new?component=192708",
      contact = "https://groups.google.com/g/lint-dev"
    )
    val issue = HardcodedValuesDetector.ISSUE
    try {
      issue.vendor = vendor
      val doc = provider.getDocumentationElementForLookupItem(psiManager, issue)
      assertEquals(
        "" +
        "Hardcoding text attributes directly in layout files is bad for several reasons:<br/>\n" +
        "<br/>\n" +
        "* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>\n" +
        "<br/>\n" +
        "* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>\n" +
        "<br/>\n" +
        "There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/>\n" +
        "Vendor: Android Open Source Project<br/>\n" +
        "Identifier: IssueIdDocumentationProviderTest<br/>\n" +
        "Contact: <a href=\"https://groups.google.com/g/lint-dev\">https://groups.google.com/g/lint-dev</a><br/>\n" +
        "Feedback: <a href=\"https://issuetracker.google.com/issues/new?component=192708\">https://issuetracker.google.com/issues/new?component=192708</a><br/>\n",
        doc.toString())
    } finally {
      issue.vendor = null // back to how it was: will fall back to IssueRegistry.AOSP_VENDOR
    }
  }
}