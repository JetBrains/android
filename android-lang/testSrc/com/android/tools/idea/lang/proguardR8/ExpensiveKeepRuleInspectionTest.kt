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
package com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.inspections.ExpensiveKeepRuleInspection
import com.android.tools.idea.testing.highlightedAs
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

abstract class ExpensiveKeepRuleTestCase : JavaCodeInsightFixtureAdtTestCase() {
  override fun setUp() {
    super.setUp()
    // Only turn on expensive keep rule inspections
    myFixture.enableInspections(ExpensiveKeepRuleInspection::class.java)
  }
}

class ExpensiveKeepRuleInspectionTest : ExpensiveKeepRuleTestCase() {
  fun testOverlyBroadKeepRules() {
    val rules = listOf(
      "-keep class **.*",
      "-keep class **.* { **; }",
      "-keep class com.** { <fields>; }",
      "-keep class com.** { <methods>; }",
      "-keep class com.** { **; }",
    )

    rules.forEach { rule ->
      val highlight = rule.highlightedAs(
        level = ERROR,
        message = "Scope rules using annotations, specific classes, or using specific field/method selectors"
      )

      myFixture.configureByText(
        /* fileType = */ ProguardR8FileType.INSTANCE,
        /* text = */ highlight
      )

      myFixture.checkHighlighting()
    }
  }

  fun testRulesWithNegation() {
    val rules = listOf(
      "-keep class !com.**.*"
    )

    rules.forEach { rule ->
      val highlight = rule.highlightedAs(
        level = ERROR,
        message = "Rules that use negation typically end up keeping a lot more classes than intended, prefer one that does not use (!)"
      )

      myFixture.configureByText(
        /* fileType = */ ProguardR8FileType.INSTANCE,
        /* text = */ highlight
      )

      myFixture.checkHighlighting()
    }
  }

  fun testGoodRules() {
    val rules = listOf(
      "-keepclassmembers **.*",
      "-keep class **.* { <init>(...); }",
      "-keep class something.internal.*",
      "-keep class * extends android.app.Activity { <init>(...); }",
      "-keep @proguard.annotation.Keep class **.*"
    )

    rules.forEach { rule ->
      myFixture.configureByText(
        /* fileType = */ ProguardR8FileType.INSTANCE,
        /* text = */rule
      )
      myFixture.checkHighlighting()
    }
  }
}
