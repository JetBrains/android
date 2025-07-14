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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.inspections.AffectedClassesProjectService
import com.android.tools.idea.lang.proguardR8.inspections.ExpensiveKeepRuleInspection
import com.android.tools.idea.lang.proguardR8.inspections.ExpensiveKeepRuleInspection.Companion.CLASSES_AFFECTED_LIMIT
import com.android.tools.idea.lang.proguardR8.inspections.ExpensiveKeepRuleInspection.Companion.RULE_USES_NEGATION_DESCRIPTION
import com.android.tools.idea.lang.proguardR8.inspections.ExpensiveKeepRuleInspection.Companion.TOO_MANY_AFFECTED_CLASSES_DESCRIPTION
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.android.tools.idea.testing.highlightedAs
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

abstract class ExpensiveKeepRuleTestCase : JavaCodeInsightFixtureAdtTestCase() {

  lateinit var affectedClassesProjectService: AffectedClassesProjectService

  override fun setUp() {
    super.setUp()
    affectedClassesProjectService = mock()
    whenever(
      methodCall = affectedClassesProjectService.affectedClassesForQualifiedName(
        qualifiedName = any<ProguardR8QualifiedName>(),
        limit = any<Int>()
      )
    ).thenReturn(CLASSES_AFFECTED_LIMIT + 1) // Return 1 more than what we allow.
    // Only turn on expensive keep rule inspections
    myFixture.enableInspections(ExpensiveKeepRuleInspection::class.java)
    project.registerServiceInstance(
      serviceInterface = AffectedClassesProjectService::class.java,
      instance = affectedClassesProjectService
    )
  }
}

class ExpensiveKeepRuleInspectionTest : ExpensiveKeepRuleTestCase() {
  fun testOverlyBroadKeepRules() {
    val rules = listOf(
      "-keep class **",
      "-keep class **.*",
      "-keep class **.*.*.*", // Makes no sense to do something like this, but this is a valid rule
      "-keep class **.* { **; }",
      "-keep class com.** { <fields>; }",
      "-keep class com.** { <methods>; }",
      "-keep class com.** { **; }",
      "-keep class **.internal.** { **; }"
    )

    rules.forEach { rule ->
      val highlight = rule.highlightedAs(
        level = ERROR,
        message = TOO_MANY_AFFECTED_CLASSES_DESCRIPTION
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
        message = RULE_USES_NEGATION_DESCRIPTION
      )

      myFixture.configureByText(
        /* fileType = */ ProguardR8FileType.INSTANCE,
        /* text = */ highlight
      )

      myFixture.checkHighlighting()
    }
  }

  fun testOverlyBroadKeepRulesWithZeroAffectedClasses() {
    val rules = listOf(
      "-keep class **",
      "-keep class **.*",
      "-keep class **.*.*.*", // Makes no sense to do something like this, but this is a valid rule
      "-keep class **.* { **; }",
      "-keep class com.** { <fields>; }",
      "-keep class com.** { <methods>; }",
      "-keep class com.** { **; }",
      "-keep class **.internal.** { **; }"
    )

    // We reduce the number of affected classes to make sure we don't end up highlighting
    // these rules.
    whenever(
      methodCall = affectedClassesProjectService.affectedClassesForQualifiedName(
        qualifiedName = any<ProguardR8QualifiedName>(),
        limit = any<Int>()
      )
    ).thenReturn(0) // 100 is the limit on the number of affected classes

    rules.forEach { rule ->
      myFixture.configureByText(
        /* fileType = */ ProguardR8FileType.INSTANCE,
        /* text = */rule
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
      "-keep @proguard.annotation.Keep class **.*",
      "-keep class **.R$* { <fields>; }" // Intentionally allowing these kinds of rules for now.
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
