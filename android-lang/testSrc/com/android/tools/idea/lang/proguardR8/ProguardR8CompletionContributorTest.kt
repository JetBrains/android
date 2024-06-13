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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement
import com.intellij.codeInsight.lookup.Lookup
import org.jetbrains.android.AndroidTestCase

class ProguardR8CompletionContributorTest : ProguardR8TestCase() {

  fun testFlagCompletion() {

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -k$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("keepattributes")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -$caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("allowaccessmodification")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class myClass {
          -k$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // No flag suggestions within JAVA specification
    assertThat(keys).isEmpty()
  }

  fun testClassTypeCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -koop $caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification header
    assertThat(keys.map { it.lookupString }.toList()).containsNoneOf("class", "interface", "enum")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after keep flags
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("class", "interface", "enum")


    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -if $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after if flag
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("class", "interface", "enum")
  }


  fun testFieldMethodWildcardsCompletion() {
    // Don't appear outside class specification body.
    myFixture.configureByText(ProguardR8FileType.INSTANCE, caret)

    var keys = myFixture.completeBasic()

    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("")

    // At start of new rule.
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("<fields>", "<init>", "<methods>", "<clinit>")

    // Suggest only with right prefix
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          <ini$caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).contains("<init>")
    assertThat(keys.map { it.lookupString }.toList()).doesNotContain("<fields>")

    // After modifier.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          private $caret
      """.trimIndent()
    )

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("<fields>", "<init>", "<methods>", "<clinit>")

    // Don't suggest after type.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          int $caret
      """.trimIndent()
    )

    keys = myFixture.completeBasic()

    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo(
      """
        -keep class * {
          int 
      """.trimIndent())
  }

  fun testFieldMethodModifiersCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        pu$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification body
    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("pu")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // suggests at the start of new rule
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("public", "private", "protected",
                                                                    "static", "synchronized", "native", "abstract", "strictfp",
                                                                    "volatile", "transient", "final")


    // suggests after !
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          !$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf("public", "private", "protected",
                                                                    "static", "synchronized", "native", "abstract", "strictfp",
                                                                    "volatile", "transient", "final")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          public $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // suggests after another modifier
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsAllOf(
      "private", "protected",
      "static", "synchronized", "native", "abstract", "strictfp",
      "volatile", "transient", "final"
    )

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          int $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // don't suggest after type
    assertThat(keys.map { it.lookupString }.toList()).containsNoneOf("public", "private", "protected",
                                                                     "static", "synchronized", "native", "abstract", "strictfp",
                                                                     "volatile", "transient", "final")
  }

  fun testPrimitiveTypesCompletion() {
    val primitives = setOf("boolean", "byte", "char", "short", "int", "long", "float", "double", "void")
    // isn't suggested outside class specification body
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        in$caret
      """.trimIndent()
    )

    var keys = myFixture.completeBasic().toList()

    assertThat(keys).containsNoneIn(primitives)
    assertThat(myFixture.editor.document.text).isEqualTo("in")

    // suggested at the start of new rule
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          $caret
        }
    """.trimIndent()
    )

    keys = myFixture.completeBasic().toList()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).containsAllIn(primitives)

    // suggested after modifier
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          public $caret
        }
    """.trimIndent()
    )

    keys = myFixture.completeBasic().toList()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).containsAllIn(primitives)

    // isn't suggested after type
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          int $caret
        }
    """.trimIndent()
    )

    keys = myFixture.completeBasic().toList()

    assertThat(keys.map { it.lookupString }).containsNoneIn(primitives)

    // suggested inside type list
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          int method($caret
        }
    """.trimIndent()
    )

    keys = myFixture.completeBasic().toList()

    assertThat(keys.map { it.lookupString }).containsAllIn(primitives)
    // isn't suggested inside type list after type
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          int method(int $caret
        }
    """.trimIndent()
    )

    keys = myFixture.completeBasic().toList()

    assertThat(keys.map { it.lookupString }).containsNoneIn(primitives)
  }

  fun testSuggestClassName() {
    myFixture.addClass(
      //language=JAVA
      """
      package p1.p2;

      class MyClass {}
    """.trimIndent()
    ).containingFile

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class $caret
      """.trimIndent()
    )

    var fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // suggest in header after enum/class/interface
    assertThat(fields).isNotEmpty()
    assertThat(fields.map { it.qualifiedName }).containsAllOf("p1.p2.MyClass", "java.lang.String")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class SomeClass extends $caret
      """.trimIndent()
    )

    fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // suggest in header after extends/implements
    assertThat(fields).isNotEmpty()
    assertThat(fields.map { it.qualifiedName }).containsAllOf("p1.p2.MyClass", "java.lang.String")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class SomeClass {
        $caret
      }
      """.trimIndent()
    )

    fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // suggest at the start of new java rule
    assertThat(fields).isNotEmpty()
    assertThat(fields.map { it.qualifiedName }).containsAllOf("p1.p2.MyClass", "java.lang.String")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class SomeClass {
        public $caret
      }
      """.trimIndent()
    )

    fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // suggest after modifier
    assertThat(fields).isNotEmpty()
    assertThat(fields.map { it.qualifiedName }).containsAllOf("p1.p2.MyClass", "java.lang.String")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class SomeClass {
        public int method($caret
      }
      """.trimIndent()
    )

    fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // suggest inside parameter list
    assertThat(fields).isNotEmpty()
    assertThat(fields.map { it.qualifiedName }).containsAllOf("p1.p2.MyClass", "java.lang.String")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class SomeClass {
        public int member$caret
      }
      """.trimIndent()
    )

    fields = myFixture.completeBasic().filterIsInstance<JavaPsiClassReferenceElement>()

    // don't suggest after class member
    assertThat(fields).isEmpty()
  }

  fun testPackageNameCompletion() {
    myFixture.addClass(
      //language=JAVA
      """
      package p1.myPackage1;

      public class MyClass {}
    """.trimIndent()
    ).qualifiedName

    myFixture.addClass(
      //language=JAVA
      """
      package p1.myPackage2;

      public class MyClass2 {}
    """.trimIndent()
    ).qualifiedName

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.myPackage1.MyClass {
          $caret
        """.trimIndent()
    )

    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsAllOf("p1", "MyClass2")
  }

  fun testFilterModifiersInSuggestion() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.myPackage1.MyClass {
          private $caret
        """.trimIndent()
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).doesNotContain("private")

    // Ignore negated modifiers as well.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class p1.myPackage1.MyClass {
          !static $caret
        """.trimIndent()
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).doesNotContain("static")
  }

  fun testCompletionForInnerClass() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass {
        class InnerClass {}
      }
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass${"$"}${caret} {
        }
    """.trimIndent()
    )

    val classes = myFixture.completeBasic()
    assertThat(classes).isNotEmpty()
    assertThat(classes.map { it.lookupString }).containsExactly("InnerClass")


    // Test that ProguardR8CompletionContributor doesn't duplicate static classes.
    // Static classes are provided by JavaClassReferenceCompletionContributor
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      public class MyClass2 {
        static class StaticInnerClass {}
      }
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE, """
        -keep class test.MyClass2${"$"}${caret} {
        }
    """.trimIndent()
    )

    assertThat(myFixture.completeBasic()).hasLength(1)
  }

  fun testKeepModifierOption() {
    // Suggest after FLAG + COMMA.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep,<caret>
      """.trimIndent()
    )

    myFixture.completeBasic()
    var modifiers = myFixture.lookupElementStrings

    assertThat(modifiers).containsAllOf("includedescriptorclasses",
                                        "includecode",
                                        "allowshrinking",
                                        "allowoptimization",
                                        "allowobfuscation")

    // Suggest after KEEP_OPTION_MODIFIER + COMMA.
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep, includecode, allowobfuscation, <caret>
      """.trimIndent()
    )

    myFixture.completeBasic()
    modifiers = myFixture.lookupElementStrings

    assertThat(modifiers).containsAllOf("includedescriptorclasses",
                                        "includecode",
                                        "allowshrinking",
                                        "allowoptimization",
                                        "allowobfuscation")
  }

  fun testInsertInit() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          <in<caret>
        }
      """.trimIndent()
    )
    val elements = myFixture.completeBasic()
    val element = elements.find { it.lookupString == "<init>" }!!
    myFixture.lookup.currentItem = element
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.checkResult(
      """
      -keep class * {
        <init>
      }
    """.trimIndent())
  }
}

class ProguardR8FlagsCodeCompletion : AndroidTestCase() {

  fun testFlagSuggestionRegardingShrinkerType() {
    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.R8

    val justProguardFlag = PROGUARD_FLAGS.minus(R8_FLAGS).first()
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -$caret
    """.trimIndent())

    myFixture.completeBasic()
    var flags = myFixture.lookupElementStrings

    assertThat(flags).doesNotContain(justProguardFlag)

    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.PROGUARD

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -$caret
    """.trimIndent())

    myFixture.completeBasic()
    flags = myFixture.lookupElementStrings

    assertThat(flags).contains(justProguardFlag)
  }
}