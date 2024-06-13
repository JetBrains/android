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

class AndroidKotlinLiveTemplateTest : LiveTemplateTestCase() {

  private val TEMPLATE_INTENT_VIEW = "IntentView"

  fun testIntentView_inTopLevel() = testNotOnTopLevel(TEMPLATE_INTENT_VIEW)

  fun testIntentView_inClass() = testNotInClass(TEMPLATE_INTENT_VIEW)

  fun testIntentView_inCompanion() = testNotInCompanion(TEMPLATE_INTENT_VIEW)

  fun testIntentView_inComment() = testNotInComment(TEMPLATE_INTENT_VIEW)

  fun testIntentView_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)

    // When:
    insertTemplate(TEMPLATE_INTENT_VIEW)
    myFixture.type("myUrl\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(Location.STATEMENT,
                                              """import android.content.Intent
      |import android.net.Uri""",
                                              """val intent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(myUrl))

        startActivity(intent)"""
    )
    )
  }

  fun testIntentView_inExpression() = testNotInExpression(TEMPLATE_INTENT_VIEW)

  private val TEMPLATE_KEY = "key"

  fun testKey_inTopLevel() {
    // Given:
    addPreparedFileToProject(Location.TOP_LEVEL)

    // When:
    insertTemplate(TEMPLATE_KEY)
    myFixture.type("SOME\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(Location.TOP_LEVEL, "", "private const val KEY_SOME = \"SOME\""))
  }

  fun testKey_inClass() = testNotInClass(TEMPLATE_KEY)

  fun testKey_inCompanion() {
    // Given:
    addPreparedFileToProject(Location.OBJECT_DECLARATION)

    // When:
    insertTemplate(TEMPLATE_KEY)
    myFixture.type("SOME\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(Location.OBJECT_DECLARATION, "", "private const val KEY_SOME = \"SOME\""))

  }

  fun testKey_inComment() = testNotInComment(TEMPLATE_KEY)

  fun testKey_inStatement() = testNotInStatement(TEMPLATE_KEY)

  fun testKey_inExpression() = testNotInExpression(TEMPLATE_KEY)

  private val TEMPLATE_NEW_INSTANCE = "newInstance"

  fun testNewInstance_inTopLevel() {
    // Given:
    addPreparedFileToProject(Location.TOP_LEVEL)

    // When:
    insertTemplate(TEMPLATE_NEW_INSTANCE)
    myFixture.type("foo: String\n")
    myFixture.type("MyFragment\n")
    myFixture.type("args.putExtra(foo)")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(Location.TOP_LEVEL, "",
                                              """fun newInstance(foo: String): MyFragment {
                                              |    val args = Bundle()
                                              |    args.putExtra(foo)
                                              |    val fragment = MyFragment()
                                              |    fragment.arguments = args
                                              |    return fragment
                                              |}"""
    )
    )
  }

  fun testNewInstance_inClass() {
    // Given:
    addPreparedFileToProject(Location.CLASS)

    // When:
    insertTemplate(TEMPLATE_NEW_INSTANCE)
    myFixture.type("foo: String\n")
    myFixture.type("MyFragment\n")
    myFixture.type("args.putExtra(foo)")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(Location.CLASS,
                                              imports = "",
                                              content = """fun newInstance(foo: String): MyFragment {
                                              |        val args = Bundle()
                                              |        args.putExtra(foo)
                                              |        val fragment = MyFragment()
                                              |        fragment.arguments = args
                                              |        return fragment
                                              |    }"""
    )
    )
  }

  fun testNewInstance_inCompanion() {
    // Given:
    addPreparedFileToProject(Location.OBJECT_DECLARATION)

    // When:
    insertTemplate(TEMPLATE_NEW_INSTANCE)
    myFixture.type("foo: String\n")
    myFixture.type("android.app.Fragment\n")
    myFixture.type("args.putExtra(foo)")

    // Then:
    myFixture.checkResult(
      insertIntoPsiFileAt(
        Location.OBJECT_DECLARATION,
        "import android.app.Fragment",
        """fun newInstance(foo: String): Fragment {
          val args = Bundle()
          args.putExtra(foo)
          val fragment = Fragment()
          fragment.arguments = args
          return fragment
      }"""
      ))
  }

  fun testNewInstance_inComment() = testNotInComment(TEMPLATE_NEW_INSTANCE)

  fun testNewInstance_inStatement() = testNotInStatement(TEMPLATE_NEW_INSTANCE)

  fun testNewInstance_inExpression() = testNotInExpression(TEMPLATE_NEW_INSTANCE)

  private val TEMPLATE_RGS = "rgS"

  fun testRgS_inTopLevel() = testNotOnTopLevel(TEMPLATE_RGS)

  fun testRgS_inClass() = testNotInClass(TEMPLATE_RGS)

  fun testRgS_inCompanion() = testNotInCompanion(TEMPLATE_RGS)

  fun testRgS_inComment() = testNotInComment(TEMPLATE_RGS)

  fun testRgS_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)

    // When:
    insertTemplate(TEMPLATE_RGS)
    myFixture.type("resources\n")
    myFixture.type("foo\n")
    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.STATEMENT,
      imports = "",
      content = "resources.getString(R.string.foo)"
    )
    )
  }

  fun testRgS_inExpression() {
    // Given:
    addPreparedFileToProject(Location.EXPRESSION)

    // When:
    insertTemplate(TEMPLATE_RGS)
    myFixture.type("resources\n")
    myFixture.type("foo\n")
    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.EXPRESSION,
      imports = "",
      content = "resources.getString(R.string.foo)"
    )
    )
  }

  private val TEMPLATE_ROUIT = "rouiT"

  fun testRouiT_inTopLevel() = testNotOnTopLevel(TEMPLATE_ROUIT)

  fun testRouiT_inClass() = testNotInClass(TEMPLATE_ROUIT)

  fun testRouiT_inCompanion() = testNotInCompanion(TEMPLATE_ROUIT)

  fun testRouiT_inComment() = testNotInComment(TEMPLATE_ROUIT)

  fun testRouiT_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)

    // When:
    insertTemplate(TEMPLATE_ROUIT)
    myFixture.type("val foo = \"bar\"\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.STATEMENT,
      imports = "",
      content = """activity.runOnUiThread(Runnable() {
                  |            override fun run() {
                  |                val foo = "bar"
                  |            }
                  |        })""".trimMargin()
    )
    )
  }

  private val TEMPLATE_SBC = "sbc"

  private fun testSbc(location: Location, content: String = "") {
    // Given:
    addPreparedFileToProject(location)

    // When:
    insertTemplate(TEMPLATE_SBC)
    myFixture.type("foo")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      location,
      imports = "",
      content = if (content.isEmpty()) {
        """|///////////////////////////////////////////////////////////////////////////
           |// foo
           |///////////////////////////////////////////////////////////////////////////""".trimMargin()
      } else {
        content
      }
    )
    )
  }

  fun testSbc_inTopLevel() = testSbc(Location.TOP_LEVEL)

  fun testSbc_inClass() = testSbc(Location.CLASS, content =
  """    |///////////////////////////////////////////////////////////////////////////
     |    // foo
     |    ///////////////////////////////////////////////////////////////////////////""".trimMargin()
  )

  fun testSbc_inCompanion() = testSbc(Location.OBJECT_DECLARATION, content =
  """      |///////////////////////////////////////////////////////////////////////////
     |      // foo
     |      ///////////////////////////////////////////////////////////////////////////""".trimMargin()
  )

  fun testSbc_inComment() = testNotInComment(TEMPLATE_SBC)

  fun testSbc_inStatement() = testNotInStatement(TEMPLATE_SBC)

  fun testSbc_inExpression() = testNotInStatement(TEMPLATE_SBC)

  private val TEMPLATE_STARTER = "starter"

  private fun testStarter(location: Location, imports: String = "", content: String = "") {
    // Given:
    addPreparedFileToProject(location)

    // When:
    insertTemplate(TEMPLATE_STARTER)
    myFixture.type("MyClass\n")
    myFixture.type("\"foo\"\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      location,
      imports = if (imports.isEmpty()) "import android.content.Context" else imports,
      content = if (content.isEmpty()) {
        """|      @JvmStatic
           |      fun start(context: Context) {
           |          val starter = Intent(context, MyClass::class.java)
           |            .putExtra("foo")
           |          context.startActivity(starter)
           |      }
           |""".trimMargin()
      }
      else {
        content
      }
    )
    )
  }

  fun testStarter_inTopLevel() = testStarter(Location.TOP_LEVEL, content =
      """|@JvmStatic
         |fun start(context: Context) {
         |    val starter = Intent(context, MyClass::class.java)
         |        .putExtra("foo")
         |    context.startActivity(starter)
         |}""".trimMargin()
  )

  fun testStarter_inCompanion() = testStarter(Location.OBJECT_DECLARATION, content =
      """   |@JvmStatic
      |      fun start(context: Context) {
      |          val starter = Intent(context, MyClass::class.java)
      |              .putExtra("foo")
      |          context.startActivity(starter)
      |      }""".trimMargin()
  )

  fun testStarter_inClass() = testNotInClass(TEMPLATE_STARTER)

  fun testStarter_inComment() = testNotInComment(TEMPLATE_STARTER)

  fun testStarter_inStatement() = testNotInStatement(TEMPLATE_STARTER)

  fun testStarter_inExpression() = testNotInExpression(TEMPLATE_STARTER)

  private val TEMPLATE_TOAST = "toast"

  fun testToast_onTopLevel() = testNotOnTopLevel(TEMPLATE_TOAST)

  fun testToast_inClass() = testNotInClass(TEMPLATE_TOAST)

  fun testToast_inCompanion() = testNotInCompanion(TEMPLATE_TOAST)

  fun testToast_inComment() = testNotInComment(TEMPLATE_TOAST)

  fun testToast_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)

    // When:
    insertTemplate(TEMPLATE_TOAST)
    myFixture.type("this\n")
    myFixture.type("foo\n")

    // Then:
    myFixture.checkResult(insertIntoPsiFileAt(
      Location.STATEMENT,
      imports = "import android.widget.Toast",
      content = "Toast.makeText(this, \"foo\", Toast.LENGTH_SHORT).show()"
      )
    )
  }

  fun testToast_inExpression() = testNotInExpression(TEMPLATE_TOAST)

  private val TEMPLATE_GONE = "viewGone"

  fun testGone_topLevel() = testNotOnTopLevel(TEMPLATE_GONE)

  fun testGone_inClass() = testNotInClass(TEMPLATE_GONE)

  fun testGone_inCompanion() = testNotInCompanion(TEMPLATE_GONE)

  fun testGone_inComment() = testNotInComment(TEMPLATE_GONE)

  fun testGone_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)


    // When:
    insertTemplate(TEMPLATE_GONE)
    myFixture.type("myView\n")

    // Then:
    myFixture.checkResult(
      insertIntoPsiFileAt(
        Location.STATEMENT,
        "import android.view.View",
        "myView.visibility = View.GONE"
      )
    )
  }

  fun testGone_inExpression() = testNotInExpression(TEMPLATE_GONE)

  private val TEMPLATE_VISIBLE = "viewVisible"

  fun testVisible_topLevel() = testNotOnTopLevel(TEMPLATE_VISIBLE)

  fun testVisible_inClass() = testNotInClass(TEMPLATE_VISIBLE)

  fun testVisible_inCompanion() = testNotInCompanion(TEMPLATE_VISIBLE)

  fun testVisible_inComment() = testNotInComment(TEMPLATE_VISIBLE)

  fun testVisible_inStatement() {
    // Given:
    addPreparedFileToProject(Location.STATEMENT)

    // When:
    insertTemplate(TEMPLATE_VISIBLE)
    myFixture.type("myView\n")

    // Then:
    myFixture.checkResult(
      insertIntoPsiFileAt(
        Location.STATEMENT,
        "import android.view.View",
        "myView.visibility = View.VISIBLE"
      )
    )
  }

  fun testVisible_inExpression() = testNotInExpression(TEMPLATE_VISIBLE)
}
