/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.kotlin.android


import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.Assert

// Adapted from the Kotlin test framework (after taking over android-kotlin sources).
fun testCompletion(
  fileText: String,
  platform: TargetPlatform?,
  complete: (CompletionType, Int) -> Array<LookupElement>?,
  defaultCompletionType: CompletionType = CompletionType.BASIC,
  defaultInvocationCount: Int = 0,
  additionalValidDirectives: Collection<String> = emptyList()
) {
  val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType
  val invocationCount = ExpectedCompletionUtils.getInvocationCount(fileText) ?: defaultInvocationCount
  val items = complete(completionType, invocationCount) ?: emptyArray()

  ExpectedCompletionUtils.assertDirectivesValid(fileText, additionalValidDirectives)

  val expected = ExpectedCompletionUtils.itemsShouldExist(fileText, platform)
  val unexpected = ExpectedCompletionUtils.itemsShouldAbsent(fileText, platform)
  val nothingElse = ExpectedCompletionUtils.isNothingElseExpected(fileText)

  Assert.assertTrue(
    "Should be some assertions about completion",
    expected.isNotEmpty() || unexpected.isNotEmpty() || nothingElse
  )
  ExpectedCompletionUtils.assertContainsRenderedItems(expected, items, ExpectedCompletionUtils.isWithOrder(fileText), nothingElse)
  ExpectedCompletionUtils.assertNotContainsRenderedItems(unexpected, items)
}
