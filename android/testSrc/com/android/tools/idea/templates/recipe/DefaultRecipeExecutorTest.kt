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
package com.android.tools.idea.templates.recipe

import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase

// TODO(qumeric): add tests for more methods

class DefaultRecipeExecutorTest: TestCase() {
  fun testSquishEmptyLines() {
    val doubleEmptyLine = """
      aaa
      
      
      aaa
    """.trimIndent()
    val doubleEmptyLineResult = """
      aaa
      
      aaa
    """.trimIndent()
    assertThat(doubleEmptyLine.squishEmptyLines()).isEqualTo(doubleEmptyLineResult)
    // has three spaces on all middle lines
    val tripleEmptyLine = """
      bbb
         
         
         
      bbb
    """.trimIndent()
    val tripleEmptyLineResult = """
      bbb
      
      bbb
    """.trimIndent()
    assertThat(tripleEmptyLine.squishEmptyLines()).isEqualTo(tripleEmptyLineResult)
    val blanks = "\n\t \n"
    assertThat(blanks.squishEmptyLines()).isEqualTo("")
    val noEmptyLines =
      """
        abc
          def
        ghi  
      """.trimIndent()
    assertThat(noEmptyLines.squishEmptyLines()).isEqualTo(noEmptyLines)
    val emptyString = ""
    assertThat(emptyString.squishEmptyLines()).isEqualTo(emptyString)
  }
}