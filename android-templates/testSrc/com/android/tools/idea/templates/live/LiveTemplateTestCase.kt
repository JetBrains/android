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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.templates.SDK_VERSION_FOR_TEMPLATE_TESTS
import com.android.tools.idea.testing.caret
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import org.jetbrains.android.AndroidTestCase

/**
 * Base setup for live template tests.
 */
abstract class LiveTemplateTestCase : AndroidTestCase(AndroidVersion(SDK_VERSION_FOR_TEMPLATE_TESTS)) {

  /**
   * Insertion location of a live template
   */
  protected enum class Location {
    TOP_LEVEL, CLASS, COMMENT, STATEMENT, EXPRESSION, OBJECT_DECLARATION
  }

  override fun setUp() {
    super.setUp()
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
  }

  override fun tearDown() {
    try {
      myFixture.editor?.let(TemplateManagerImpl::getTemplateState)?.gotoEnd()
    }
    finally {
      if (myFixture != null) {
        super.tearDown()
      }
    }
  }

  fun insertTemplate(templateName: String) {
    myFixture.type(templateName)
    myFixture.completeBasic()
    myFixture.lookup.currentItem =
      myFixture.lookupElements!!
        .filterIsInstance<LiveTemplateLookupElement>()
        .first { it.lookupString == templateName }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
  }

  protected fun testNotOnTopLevel(template: String) =
    testTemplateNotProvided(Location.TOP_LEVEL, template)

  protected fun testNotInClass(template: String) =
    testTemplateNotProvided(Location.CLASS, template)

  protected fun testNotInComment(template: String) =
    testTemplateNotProvided(Location.COMMENT, template)

  protected fun testNotInStatement(template: String) =
    testTemplateNotProvided(Location.STATEMENT, template)

  protected fun testNotInExpression(template: String) =
    testTemplateNotProvided(Location.EXPRESSION, template)

  protected fun testNotInCompanion(template: String) =
    testTemplateNotProvided(Location.OBJECT_DECLARATION, template)

  private fun testTemplateNotProvided(location: Location, template: String) {
    // Given:
    addPreparedFileToProject(location)
    // When:
    myFixture.type(template)
    myFixture.completeBasic()
    // Then:
    assertFalse(
      "Unexpected template '$template' present in completion.",
      myFixture.lookupElements!!
        .filterIsInstance<LiveTemplateLookupElement>()
        .any { it.lookupString == template }
    )
  }

  protected fun addPreparedFileToProject(caretLocation: Location) {
    val content = when (caretLocation) {
      Location.TOP_LEVEL -> {
        """
        package com.example

        $caret
        class MyClass {
        }
        """
      }
      Location.CLASS -> {
        """
        package com.example

        class MyClass {
            $caret
        }
        """
      }

      Location.COMMENT -> {
        """
        package com.example

        /*
        $caret
        */
        class MyClass {
        }
        """
      }
      Location.STATEMENT -> {
        """
        package com.example

        class MyClass {
            fun myMethod() {
              $caret
            }
        }
        """
      }
      Location.EXPRESSION -> {
        """
        package com.example

        class MyClass {
            fun myMethod() {
              val foo = $caret
            }
        }
        """
      }
      Location.OBJECT_DECLARATION -> {
        """
        package com.example

        class MyClass {
          companion object() {
          $caret
          }
        }
        """
      }
    }.trimIndent()
    val psiFile = myFixture.addFileToProject("src/com/example/MyClass.kt", content)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
  }

  protected fun insertIntoPsiFileAt(
    location: Location,
    imports: String = "",
    content: String
  ): String {

    fun import() = if (!imports.isEmpty()) "\n" + imports + "\n" else ""

    return when (location) {
      Location.TOP_LEVEL -> {
        """|package com.example
           |${import()}
           |$content
           |class MyClass {
           |}
        """
      }
      Location.CLASS -> {
        """|package com.example
           |${import()}
           |class MyClass {
           |    $content
           |}
        """
      }
      Location.COMMENT -> {
        """|package com.example
           |${import()}
           |/*
           |$content
           |*/
           |class MyClass {
           |}
        """
      }
      Location.STATEMENT -> {
        """|package com.example
           |${import()}
           |class MyClass {
           |    fun myMethod() {
           |        $content
           |    }
           |}
        """
      }
      Location.EXPRESSION -> {
        """|package com.example
           |${import()}
           |class MyClass {
           |    fun myMethod() {
           |      val foo = $content
           |    }
           |}
        """
      }
      Location.OBJECT_DECLARATION -> {
        """|package com.example
           |${import()}
           |class MyClass {
           |  companion object() {
           |      $content
           |  }
           |}
        """
      }
    }.trimMargin()
  }
}