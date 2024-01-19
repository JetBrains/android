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
package com.android.tools.idea.uibuilder.handlers.constraint

import junit.framework.TestCase

class ConstraintUtilitiesTest : TestCase() {

  fun testSpecialCharacters() {
    TestCase.assertNotSame("A > B", ConstraintUtilities.replaceSpecialChars("A &sgt; B"))
    TestCase.assertEquals("A > B", ConstraintUtilities.replaceSpecialChars("A &gt; B"))
    TestCase.assertEquals("B < A", ConstraintUtilities.replaceSpecialChars("B &lt; A"))
    TestCase.assertEquals("A & B", ConstraintUtilities.replaceSpecialChars("A &amp; B"))
    TestCase.assertEquals("A&B", ConstraintUtilities.replaceSpecialChars("A&amp;B"))
    TestCase.assertEquals(
      "\"Hello world\"",
      ConstraintUtilities.replaceSpecialChars("&quot;Hello world&quot;"),
    )
    TestCase.assertEquals("A' B", ConstraintUtilities.replaceSpecialChars("A&apos; B"))
    TestCase.assertEquals(
      "A&gt;&gt;B",
      ConstraintUtilities.replaceSpecialChars("A&amp;gt;&amp;gt;B"),
    )
    TestCase.assertEquals("Aamp;&amp;", ConstraintUtilities.replaceSpecialChars("Aamp;&amp;amp;"))
  }
}
