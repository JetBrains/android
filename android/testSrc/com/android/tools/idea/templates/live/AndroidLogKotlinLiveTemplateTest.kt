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
package com.android.tools.idea.templates.live

import com.android.tools.idea.testing.caret

class AndroidLogKotlinLiveTemplateTest : LiveTemplateTestCase() {

  private val TEMPLATE_LOGD = "logd"

  fun testLogD_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGD)

  fun testLogD_inClass() = testNotInClass(TEMPLATE_LOGD)

  fun testLogD_inCompanion() = testNotInCompanion(TEMPLATE_LOGD)

  fun testLogD_inComment() = testNotInComment(TEMPLATE_LOGD)

  fun testLogD_inMethod() {
    testLog(TEMPLATE_LOGD, additionalData = arrayOf("foo"), expectedCompletion = "Log.d(TAG, \"myMethod: foo\")")
  }

  fun testLogD_inExpression() = testNotInExpression(TEMPLATE_LOGD)

  private val TEMPLATE_LOGI = "logi"

  fun testLogI_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGI)

  fun testLogI_inClass() = testNotInClass(TEMPLATE_LOGI)

  fun testLogI_inCompanion() = testNotInCompanion(TEMPLATE_LOGI)

  fun testLogI_inComment() = testNotInComment(TEMPLATE_LOGI)

  fun testLogI_inMethod() {
    testLog(TEMPLATE_LOGI, additionalData = arrayOf("foo"), expectedCompletion = "Log.i(TAG, \"myMethod: foo\")")
  }

  fun testLogI_inExpression() = testNotInExpression(TEMPLATE_LOGI)

  private val TEMPLATE_LOGW = "logw"

  fun testLogW_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGW)

  fun testLogW_inClass() = testNotInClass(TEMPLATE_LOGW)

  fun testLogW_inCompanion() = testNotInCompanion(TEMPLATE_LOGW)

  fun testLogW_inComment() = testNotInComment(TEMPLATE_LOGW)

  fun testLogW_inMethod() {
    testLog(TEMPLATE_LOGW, additionalData = arrayOf("foo", "e"), expectedCompletion = "Log.w(TAG, \"myMethod: foo\", e)")
  }

  fun testLogW_inExpression() = testNotInExpression(TEMPLATE_LOGW)

  private val TEMPLATE_LOGE = "loge"

  fun testLogE_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGE)

  fun testLogE_inClass() = testNotInClass(TEMPLATE_LOGE)

  fun testLogE_inCompanion() = testNotInCompanion(TEMPLATE_LOGE)

  fun testLogE_inComment() = testNotInComment(TEMPLATE_LOGE)

  fun testLogE_inMethod() {
    testLog(TEMPLATE_LOGE, additionalData = arrayOf("foo", "e"), expectedCompletion = "Log.e(TAG, \"myMethod: foo\", e)")
  }

  fun testLogE_inExpression() = testNotInExpression(TEMPLATE_LOGE)

  fun testLogE_nonDefaultExceptionName() {
    testLog(TEMPLATE_LOGE, additionalData = arrayOf("foo", "bar"), expectedCompletion = "Log.e(TAG, \"myMethod: foo\", bar)")
  }

  private val TEMPLATE_WTF = "wtf"

  fun testWtf_onTopLevel() = testNotOnTopLevel(TEMPLATE_WTF)

  fun testWtf_inClass() = testNotInClass(TEMPLATE_WTF)

  fun testWtf_inCompanion() = testNotInCompanion(TEMPLATE_WTF)

  fun testWtf_inComment() = testNotInComment(TEMPLATE_WTF)

  fun testWtf_inMethod() {
    testLog(TEMPLATE_WTF,
            additionalData = arrayOf("foo", "e"),
            methodHeader = "wtfMethod()",
            expectedCompletion = "Log.wtf(TAG, \"wtfMethod: foo\", e)"
    )
  }

  fun testWtf_inExpression() = testNotInExpression(TEMPLATE_WTF)

  private fun testLog(
    templateName: String,
    additionalData: Array<String> = emptyArray(),
    methodHeader: String = "myMethod()",
    expectedCompletion: String) {
    // Given:
    val psiFile = myFixture.addFileToProject(
      "src/com/example/MyClass.kt",
      """
      package com.example

      class MyClass {
          fun $methodHeader {
              $caret
          }
      }
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    // When:
    insertTemplate(templateName)
    additionalData.forEach { myFixture.type("$it\n") }
    // Then:
    myFixture.checkResult(
      """
      package com.example

      import android.util.Log

      class MyClass {
          fun $methodHeader {
              $expectedCompletion
          }
      }
      """.trimIndent()
    )
  }

  private val TEMPLATE_LOGM = "logm"

  fun testLogM_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGM)

  fun testLogM_inClass() = testNotInClass(TEMPLATE_LOGM)

  fun testLogM_inCompanion() = testNotInCompanion(TEMPLATE_LOGM)

  fun testLogM_inComment() = testNotInComment(TEMPLATE_LOGM)

  fun testLogM_inMethod() {
    testLog(TEMPLATE_LOGM, methodHeader = "myMethod()", expectedCompletion = "Log.d(TAG, \"myMethod() called\")")
  }

  fun testLogM_inMethod_withParameters() {
    testLog(
      TEMPLATE_LOGM,
      methodHeader = "myMethod(foo: String, bar: String)",
      expectedCompletion = "Log.d(TAG, \"myMethod() called with: foo = \$foo, bar = \$bar\")"
    )
  }

  fun testLogM_inExpression() = testNotInExpression(TEMPLATE_LOGM)

  private val TEMPLATE_LOGR = "logr"

  fun testLogR_onTopLevel() = testNotOnTopLevel(TEMPLATE_LOGR)

  fun testLogR_inClass() = testNotInClass(TEMPLATE_LOGR)

  fun testLogR_inCompanion() = testNotInCompanion(TEMPLATE_LOGR)

  fun testLogR_inComment() = testNotInComment(TEMPLATE_LOGR)

  fun testLogR_inMethod() {
    // Given:
    val psiFile = myFixture.addFileToProject(
      "src/com/example/MyLogRClass.kt",
      """
      package com.example

      class MyLogRClass {
        fun myFunction(): String {
          val result = "Return Value"
          $caret
          return result
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    // When:
    insertTemplate(TEMPLATE_LOGR)

    //Then:
    myFixture.checkResult(
      """
      package com.example

      import android.util.Log

      class MyLogRClass {
        fun myFunction(): String {
          val result = "Return Value"
            Log.d(TAG, "myFunction() returned: ${'$'}result")
          return result
        }
      }
      """.trimIndent()
    )
  }

  fun testLogR_returnTypeUnit() {
    // Given:
    val psiFile = myFixture.addFileToProject(
      "src/com/example/MyLogRClass.kt",
      """
      package com.example

      class MyLogRClass {
        fun myFunction(): String {
          val result = "Return Value"
          $caret
          return result
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    // When:
    insertTemplate(TEMPLATE_LOGR)

    //Then:
    myFixture.checkResult(
      """
      package com.example

      import android.util.Log

      class MyLogRClass {
        fun myFunction(): String {
          val result = "Return Value"
            Log.d(TAG, "myFunction() returned: ${'$'}result")
          return result
        }
      }
      """.trimIndent()
    )
  }

  fun testLogR_inExpression() = testNotInExpression(TEMPLATE_LOGR)

  private val TEMPLATE_LOGT = "logt"

  fun testLogT_onTopLevel() {
    // Given:
    addPreparedFileToProject(Location.TOP_LEVEL)
    // When:
    insertTemplate(TEMPLATE_LOGT)
    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.TOP_LEVEL,
      imports = "", content = "private const val TAG = \"MyClass\""
    )
    )
  }

  fun testLogT_inClass() {
    // Given:
    addPreparedFileToProject(Location.CLASS)
    // When:
    insertTemplate(TEMPLATE_LOGT)
    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.CLASS,
      imports = "", content = "private const val TAG = \"MyClass\""
    )
    )
  }

  fun testLogT_inCompanionObject() {
    // Given:
    addPreparedFileToProject(Location.OBJECT_DECLARATION)
    // When:
    insertTemplate(TEMPLATE_LOGT)
    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.OBJECT_DECLARATION,
      imports = "", content = "private const val TAG = \"MyClass\""
    )
    )
  }

  fun testLogT_longClassName() {
    // Given:
    val psiFile = myFixture.addFileToProject(
      "src/com/example/MyExtremelyLongClassName.kt",
       """
       package com.example

       import android.util.Log

       class MyExtremelyLongClassName {
           $caret
       }
       """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    // When:
    insertTemplate(TEMPLATE_LOGT)

    //Then:
    myFixture.checkResult(
       """
       package com.example

       import android.util.Log

       class MyExtremelyLongClassName {
           private const val TAG = "MyExtremelyLongClassNam"
       }
       """.trimIndent()
    )
  }

  fun testLogT_inMethod() = testNotInStatement(TEMPLATE_LOGT)

  fun testLogT_inExpression() = testNotInExpression(TEMPLATE_LOGT)
}