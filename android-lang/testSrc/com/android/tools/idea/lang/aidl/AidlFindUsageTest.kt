/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl

import com.android.tools.idea.lang.aidl.psi.AidlDeclaration
import com.android.tools.idea.lang.aidl.psi.AidlNamedElement
import com.android.tools.idea.lang.aidl.psi.AidlQualifiedName
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class AidlFindUsageTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    @Suppress("UnnecessaryModifier")
    myFixture.addFileToProject(
      "src/Interface.java",
      //language=Java
      """
      // This is the skeleton of generated file of Interface AIDL file.
      public interface Interface {
        public static abstract class Stub implements Interface {
          public Stub() {
          }

          private static class Proxy implements Interface {
            @Override
            public void foo(int a) {
            }
          }
        }
        void foo(int a);
      }
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "src/Main.java",
      //language=Java
      """
      class Main {
        static void main(String []args) {
          Interface i = new Interface.Proxy();
          i.foo(1);
        }
      }
      """.trimIndent()
    )
  }

  fun testFindMethodUsage() {
    myFixture.configureByText(
      "file.aidl",
      """
        interface Interface {
        void fo<caret>o(int a);
      }
      """
    )
    checkUsages(
      """
      AIDL element AidlMethodDeclaration("foo") in file.aidl:2
      Corresponding AIDL generated Java code: method Interface#foo in Interface.java:12

      Usages:
      * reference `i.foo` in `i.foo(1)` in Main.java:3
          i.foo(1);
            ~~~
      """
    )
  }

  fun testFindInterfaceUsage() {
    myFixture.configureByText(
      "file.aidl",
      """
        interface Inter<caret>face {
        void foo(int a);
      }"""
    )
    checkUsages(
      """
      AIDL element AidlInterfaceDeclaration("Interface") in file.aidl:1
      Corresponding AIDL generated Java code: class Interface in Interface.java:0

      Usages:
      * reference `Interface` in `implements Interface` in Interface.java:2
        public static abstract class Stub implements Interface {
                                                     ~~~~~~~~~
      * reference `Interface` in `implements Interface` in Interface.java:6
          private static class Proxy implements Interface {
                                                ~~~~~~~~~
      * reference `Interface` in `Interface` in Main.java:2
          Interface i = new Interface.Proxy();
          ~~~~~~~~~
      * reference `Interface` in `Interface.Proxy` in Main.java:2
          Interface i = new Interface.Proxy();
                            ~~~~~~~~~
      """
    )
  }

  fun testParcelableUsage() {
    myFixture.addFileToProject(
      "src/Rect.java",
      //language=Java
      """
      class Rect extends Parcelable {}
      """.trimIndent()
    )
    myFixture.configureByText(
      "file.aidl",
      """
      parcelable Rec<caret>t;
      """.trimIndent()
    )
    checkUsages(
      """
      AIDL element AidlParcelableDeclaration("Rect") in file.aidl:0
      Corresponding AIDL generated Java code: class Rect in Rect.java:0
      """
    )
  }

  fun testEnumUsage() {
    @Suppress("UnnecessaryModifier")
    myFixture.addFileToProject(
      "src/INewParcelable.java",
      //language=Java
      """
      /*
       * This file is auto-generated.  DO NOT MODIFY.
       */
      package com.android.tools.test.myapplication;

      public @interface INewParcelable {
        public static final byte FOO = 1;
        public static final byte BAR = 2;
      }
      """.trimIndent()
    )
    myFixture.configureByText(
      "INewParcelable.aidl",
      //language=AIDL
      """
      package com.android.tools.test.myapplication;
      enum INew<caret>Parcelable {
         FOO = 1,
         BAR = 2
      }
      """
    )
    checkUsages(
      """
      AIDL element AidlEnumDeclaration("INewParcelable") in INewParcelable.aidl:2
      Corresponding AIDL generated Java code: class INewParcelable in INewParcelable.java:5
      """
    )
  }

  fun testEnumConstantUsage() {
    @Suppress("UnnecessaryModifier")
    myFixture.addFileToProject(
      "src/INewParcelable.java",
      //language=Java
      """
      /*
       * This file is auto-generated.  DO NOT MODIFY.
       */
      package com.android.tools.test.myapplication;

      public @interface INewParcelable {
        public static final byte FOO = 1;
        public static final byte BAR = 2;
      }
      """.trimIndent()
    )
    myFixture.configureByText(
      "INewParcelable.aidl",
      //language=AIDL
      """
      package com.android.tools.test.myapplication;
      enum INewParcelable {
         FOO = 1,
         B<caret>AR = 2
      }
      """
    )
    checkUsages(
      """
      AIDL element AidlEnumeratorDeclaration("BAR") in INewParcelable.aidl:4
      Corresponding AIDL generated Java code: field INewParcelable#BAR in INewParcelable.java:7
      """
    )
  }

  fun testUnions() {
    // Based on AIDL unit test case Union.aidl/Union.java

    // Generated file:
    // https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/tests/golden_output/aidl-test-interface-java-source/gen/android/aidl/tests/Union.java
    // (but with lots of boilerplate parcelable and toString/equals/hashcode methods removed)
    @Suppress("RedundantSuppression")
    myFixture.addFileToProject(
      "src/android/aidl/tests/Union.java",
      //language=Java
      """
      /*
       * This file is auto-generated.  DO NOT MODIFY.
       */
      package android.aidl.tests;
      public final class Union {
        // tags for union fields
        public final static int ns = 0;  // int[] ns;
        public final static int n = 1;  // int n;
        public final static int m = 2;  // int m;
        public final static int s = 3;  // String s;
        public final static int ibinder = 4;  // IBinder ibinder;
        public final static int ss = 5;  // List<String> ss;
        public final static int be = 6;  // android.aidl.tests.ByteEnum be;

        private int _tag;
        private Object _value;

        public Union() {
          int[] _value = {};
          this._tag = ns;
          this._value = _value;
        }

        private Union(int _tag, Object _value) {
          this._tag = _tag;
          this._value = _value;
        }

        public int getTag() {
          return _tag;
        }

        // int[] ns;

        public static Union ns(int[] _value) {
          return new Union(ns, _value);
        }

        public int[] getNs() {
          _assertTag(ns);
          return (int[]) _value;
        }

        public void setNs(int[] _value) {
          _set(ns, _value);
        }

        // int n;

        public static Union n(int _value) {
          return new Union(n, _value);
        }

        public int getN() {
          _assertTag(n);
          return (int) _value;
        }

        public void setN(int _value) {
          _set(n, _value);
        }

        // int m;

        public static Union m(int _value) {
          return new Union(m, _value);
        }

        public int getM() {
          _assertTag(m);
          return (int) _value;
        }

        public void setM(int _value) {
          _set(m, _value);
        }

        // String s;

        public static Union s(java.lang.String _value) {
          return new Union(s, _value);
        }

        public java.lang.String getS() {
          _assertTag(s);
          return (java.lang.String) _value;
        }

        public void setS(java.lang.String _value) {
          _set(s, _value);
        }

        // IBinder ibinder;

        public static Union ibinder(android.os.IBinder _value) {
          return new Union(ibinder, _value);
        }

        public android.os.IBinder getIbinder() {
          _assertTag(ibinder);
          return (android.os.IBinder) _value;
        }

        public void setIbinder(android.os.IBinder _value) {
          _set(ibinder, _value);
        }

        // List<String> ss;

        public static Union ss(java.util.List<java.lang.String> _value) {
          return new Union(ss, _value);
        }

        @SuppressWarnings("unchecked")
        public java.util.List<java.lang.String> getSs() {
          _assertTag(ss);
          return (java.util.List<java.lang.String>) _value;
        }

        public void setSs(java.util.List<java.lang.String> _value) {
          _set(ss, _value);
        }

        // android.aidl.tests.ByteEnum be;

        public static Union be(byte _value) {
          return new Union(be, _value);
        }

        public byte getBe() {
          _assertTag(be);
          return (byte) _value;
        }

        public void setBe(byte _value) {
          _set(be, _value);
        }

        public static final String S1 = "a string constant in union";

        private void _assertTag(int tag) {
        }

        private String _tagString(int _tag) {
          throw new IllegalStateException("unknown field: " + _tag);
        }

        private void _set(int _tag, Object _value) {
        }
      }
      """.trimIndent()
    )

    //language=AIDL
    val aidlFile = """
      package android.aidl.tests;
      import android.aidl.tests.ByteEnum;

      @JavaDerive(toString=true, equals=true)
      @RustDerive(Clone=true, PartialEq=true)
      union Union {
          int[] ns = {};
          int n;
          int m;
          @utf8InCpp String s;
          @nullable IBinder ibinder;
          @utf8InCpp List<String> ss;
          ByteEnum be;

          const @utf8InCpp String S1 = "a string constant in union";
      }
      """

    // Made up nonsensical usage:
    myFixture.addFileToProject(
      "src/UnionUsageExample.java",
      //language=JAVA
      """
      import android.aidl.tests.Union;

      public class UnionUsageExample {
          public void test(Union union) {
              switch (union.getTag()) {
                  case Union.ns:
                     int[] ns = union.getNs();
                     union.setNs(new int[0]);
                  case Union.ibinder:
                      System.out.println(union.getIbinder());
              }
          }
      }
      """.trimIndent()
    )

    myFixture.configureByText("Union.aidl", aidlFile.replace("ibinder;", "ib<caret>inder;"))
    checkUsages(
      """
      AIDL element AidlVariableDeclaration("ibinder") in Union.aidl:11
      Corresponding AIDL generated Java code: method Union#getIbinder in Union.java:98
      Corresponding AIDL generated Java code: method Union#setIbinder in Union.java:103
      Corresponding AIDL generated Java code: field Union#ibinder in Union.java:10

      Usages:
      * reference `union.getIbinder` in `union.getIbinder()` in UnionUsageExample.java:9
                      System.out.println(union.getIbinder());
                                               ~~~~~~~~~~
      * reference `ibinder` in `(ibinder, _value)` in Union.java:95
          return new Union(ibinder, _value);
                           ~~~~~~~
      * reference `ibinder` in `(ibinder)` in Union.java:99
          _assertTag(ibinder);
                     ~~~~~~~
      * reference `ibinder` in `(ibinder, _value)` in Union.java:104
          _set(ibinder, _value);
               ~~~~~~~
      * reference `Union.ibinder` in `Union.ibinder` in UnionUsageExample.java:8
                  case Union.ibinder:
                             ~~~~~~~
      """
    )

    myFixture.configureByText("Union.aidl", aidlFile.replace("ns = ", "n<caret>s = "))
    checkUsages(
      """
      AIDL element AidlVariableDeclaration("ns") in Union1.aidl:7
      Corresponding AIDL generated Java code: method Union#getNs in Union.java:38
      Corresponding AIDL generated Java code: method Union#setNs in Union.java:43
      Corresponding AIDL generated Java code: field Union#ns in Union.java:5

      Usages:
      * reference `union.getNs` in `union.getNs()` in UnionUsageExample.java:6
                     int[] ns = union.getNs();
                                      ~~~~~
      * reference `union.setNs` in `union.setNs(new int[0])` in UnionUsageExample.java:7
                     union.setNs(new int[0]);
                           ~~~~~
      * reference `ns` in `this._tag = ns` in Union.java:19
          this._tag = ns;
                      ~~
      * reference `ns` in `(ns, _value)` in Union.java:35
          return new Union(ns, _value);
                           ~~
      * reference `ns` in `(ns)` in Union.java:39
          _assertTag(ns);
                     ~~
      * reference `ns` in `(ns, _value)` in Union.java:44
          _set(ns, _value);
               ~~
      * reference `Union.ns` in `Union.ns` in UnionUsageExample.java:5
                  case Union.ns:
                             ~~
      """
    )

    myFixture.configureByText("Union.aidl", aidlFile.replace("union Union", "union Un<caret>ion"))
    checkUsages(
      """
      AIDL element AidlUnionDeclaration("Union") in Union2.aidl:6
      Corresponding AIDL generated Java code: class Union in Union.java:4

      Usages:
      * reference `Union` in `Union` in Union.java:34
        public static Union ns(int[] _value) {
                      ~~~~~
      * reference `Union` in `new Union(ns, _value)` in Union.java:35
          return new Union(ns, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:49
        public static Union n(int _value) {
                      ~~~~~
      * reference `Union` in `new Union(n, _value)` in Union.java:50
          return new Union(n, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:64
        public static Union m(int _value) {
                      ~~~~~
      * reference `Union` in `new Union(m, _value)` in Union.java:65
          return new Union(m, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:79
        public static Union s(java.lang.String _value) {
                      ~~~~~
      * reference `Union` in `new Union(s, _value)` in Union.java:80
          return new Union(s, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:94
        public static Union ibinder(android.os.IBinder _value) {
                      ~~~~~
      * reference `Union` in `new Union(ibinder, _value)` in Union.java:95
          return new Union(ibinder, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:109
        public static Union ss(java.util.List<java.lang.String> _value) {
                      ~~~~~
      * reference `Union` in `new Union(ss, _value)` in Union.java:110
          return new Union(ss, _value);
                     ~~~~~
      * reference `Union` in `Union` in Union.java:125
        public static Union be(byte _value) {
                      ~~~~~
      * reference `Union` in `new Union(be, _value)` in Union.java:126
          return new Union(be, _value);
                     ~~~~~
      * reference `android.aidl.tests.Union` in `import android.aidl.tests.Union;` in UnionUsageExample.java:0
      import android.aidl.tests.Union;
                                ~~~~~
      * reference `Union` in `Union` in UnionUsageExample.java:3
          public void test(Union union) {
                           ~~~~~
      * reference `Union` in `Union.ns` in UnionUsageExample.java:5
                  case Union.ns:
                       ~~~~~
      * reference `Union` in `Union.ibinder` in UnionUsageExample.java:8
                  case Union.ibinder:
                       ~~~~~
      """
    )
  }

  private fun checkUsages(expected: String) {
    val caretElement = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val nameElement = caretElement.getParentOfType<AidlNamedElement>(strict = false)
    nameElement!!
    val declarationElement = nameElement.getParentOfType<AidlDeclaration>(strict = true)
    declarationElement!!
    val generatedElements = declarationElement.generatedPsiElements
    assertThat(generatedElements).isNotEmpty()

    val sb = StringBuilder()
    val aidlElementClass = declarationElement.javaClass.simpleName.removeSuffix("Impl")
    sb.append("AIDL element $aidlElementClass(\"${declarationElement.name}\") in ${declarationElement.location()}\n")

    for (generated in generatedElements) {
      sb.append("Corresponding AIDL generated Java code: ")
      sb.append("${generated.describe()} in ${generated.location()}\n")
    }

    val usages = myFixture.findUsages(nameElement)
    if (usages.isNotEmpty()) {
      sb.append("\nUsages:\n")
      for (usage in usages) {
        sb.append("* ")
        val element = usage.element
        element!!
        sb.append("${element.describe()} in ${element.location()}")
        sb.append("\n")
        val segment = usage.segment!!
        val fileText = usage.file?.text!!
        val lineStart = fileText.lastIndexOf('\n', segment.startOffset) + 1
        val lineEnd = fileText.indexOf('\n', lineStart).let { if (it == -1) fileText.length else it }
        val lineText = fileText.substring(lineStart, lineEnd)
        sb.append(lineText).append("\n")
        val range = segment.startOffset until segment.endOffset
        for (i in lineStart..range.last) {
          sb.append(if (i in range) '~' else ' ')
        }
        sb.append("\n")

        if (usage is PsiReference) {
          val resolved = (usage as PsiReference).resolve()!!
          val found = generatedElements.find { it == resolved }
          assertNotNull(found)
        }
      }
    }

    assertEquals(expected.trimIndent().trim(), sb.toString().trim())
  }

  private fun PsiElement.location(): String {
    return this.containingFile.name + ":" + this.getLineNumber()
  }

  private fun PsiElement.describe(): String {
    return when (this) {
      is PsiClass -> "class ${name!!}"
      is PsiField -> "field ${(parent as PsiClass).name}#$name"
      is PsiMethod -> "method ${(parent as PsiClass).name}#$name"
      is PsiReference -> "reference `$text` in `${parent.text}`"
      is AidlDeclaration -> "AIDL declaration $name"
      is AidlQualifiedName -> "AIDL reference $qualifiedName"
      else -> error("Unexpected element $this")
    }
  }
}