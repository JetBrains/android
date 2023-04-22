/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.log

import com.google.common.collect.ImmutableList
import org.junit.Assert
import org.junit.Test

class LogAnonymizerTest {
  @Test
  fun testIsGoogleClass() {
    val nonGoogleClasses: List<String> = ImmutableList.of(
      "com.google",
      "com.googletest.Class",
      "android2",
      "android2.test.Class",
      "android2.test.Class\$Inner"
    )
    val googleClasses: List<String> = ImmutableList.of(
      "com.google.Class",
      "android.test",
      "android.google.test.Class",
      "android.google.test.Class\$Inner"
    )
    for (c in nonGoogleClasses) {
      Assert.assertFalse(isPublicClass(c))
      Assert.assertFalse(isPublicClass(c.replace(".", "/")))
    }
    for (c in googleClasses) {
      Assert.assertTrue(isPublicClass(c))
      Assert.assertTrue(isPublicClass(c.replace(".", "/")))
    }
  }

  @Test
  fun testAnonymizeClassName() {
    Assert.assertEquals("com.google.Class", anonymizeClassName("com.google.Class"))
    val hashedClassName: String = anonymizeClassName("com.myapp.Class")
    Assert.assertNotEquals("com.myapp.Class", hashedClassName)
    Assert.assertEquals(hashedClassName, anonymizeClassName("com.myapp.Class"))
    Assert.assertEquals(hashedClassName, anonymizeClassName("com.myapp.Class"))
  }
}