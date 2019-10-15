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
package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8TestCase
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.parentOfType

class ProguardR8PsiImplUtilTest : ProguardR8TestCase() {

  private fun createParameterList(vararg types: String): PsiParameterList {
    val psiTypes = types.map { elementFactory.createTypeFromText(it, null) }.toTypedArray()
    val names = types.indices.map { "param$it" }
    return elementFactory.createParameterList(names.toTypedArray(), psiTypes)
  }

  private fun getTypeUnderCaret() =
    myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!.type

  fun testResolvePsiClassFromQualifiedName() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClas${caret}s {}
      """.trimIndent())

    val qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8QualifiedName::class)

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))
  }

  fun testMatchesArrayType() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        String[] field;
        List<String> field2;
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        java.lang.S${caret}tring[] field;
        java.util.List field2;
      }
      """.trimIndent())

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)
    assertThat(type).isNotNull()
    val arrayStringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String[]", null)
    assertThat(type!!.matchesPsiType(arrayStringType)).isTrue()
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isFalse()
    val listType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.util.List", null)
    assertThat(type.matchesPsiType(listType)).isFalse()

    myFixture.moveCaret("Li|st")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(listType)).isTrue()
  }

  fun testGetPsiPrimitive() {

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        in${caret}t myPrimitive;
        byte myPrimitive2;
      }
      """.trimIndent())

    var primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8JavaPrimitive::class)

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiPrimitiveType.INT)

    myFixture.moveCaret("by|te")

    primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8JavaPrimitive::class)

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiPrimitiveType.BYTE)
  }

  fun testMatchesPsiType() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        boo${caret}lean myPrimitive;
        java.lang.String myString;
        % myAnyPrimitive;
        *** myAnyType;
      }
      """.trimIndent())

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(PsiPrimitiveType.BOOLEAN)).isTrue()

    myFixture.moveCaret("Str|ing")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isTrue()

    myFixture.moveCaret("%|")
    type = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.parentOfType(ProguardR8Type::class)!!
    // String is NOT primitive
    assertThat(type.matchesPsiType(stringType)).isFalse()
    assertThat(type.matchesPsiType(PsiPrimitiveType.LONG)).isTrue()
    assertThat(type.matchesPsiType(PsiPrimitiveType.VOID)).isFalse()

    myFixture.moveCaret("*|**")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Type::class)!!
    assertThat(type.matchesPsiType(stringType)).isTrue()
    assertThat(type.matchesPsiType(PsiPrimitiveType.LONG)).isTrue()
  }

  fun testAcceptAnyParameters() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        void myMethod(...);
        void myMethod(int, ...);
        void myMethod(..., int);
        void myMethod(int, ..., int);
        }
        """
    )

    myFixture.moveCaret("myMethod(..|.)")
    var parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    assertThat(parameters.isAcceptAnyParameters).isTrue()

    myFixture.moveCaret("myMethod(int, ..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()

    myFixture.moveCaret("myMethod(..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()

    myFixture.moveCaret("myMethod(int, ..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()
  }

  fun testMatchesParameterList() {

    val stringFQ = String::class.java.canonicalName
    val intFQ = PsiPrimitiveType.INT.name

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        void myMethod(int);
        void myMethod(int, java.lang.String);
        void myMethod(int[], java.lang.String[]);
        void myMethod(%, java.lang.String, %);
        void myMethod(***, java.lang.String);
        void myMethod(...);
        void myMethod(int, ...);
        void myMethod(..., int);
        void myMethod(wildcard**);
      }
      """.trimIndent())

    myFixture.moveCaret("myMethod(i|nt)")
    var parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (int) == (int)
    var psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int) != (int, int)
    psiParameters = createParameterList(intFQ, intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != (long)
    psiParameters = createParameterList(PsiPrimitiveType.LONG.name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != (int[])
    psiParameters = createParameterList("${intFQ}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(i|nt, java.lang.String)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (int, String) == (int, String)
    psiParameters = createParameterList(intFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int, String) != (int, int)
    psiParameters = createParameterList(intFQ, intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int, String) != (int, String, String)
    psiParameters = createParameterList(
      intFQ,
      stringFQ,
      stringFQ
    )
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int, String) != (int[], String[])
    psiParameters = createParameterList("${intFQ}[]", "${stringFQ}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(i|nt[], java.lang.String[])")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (int[], String[]) == (int[], String[])
    psiParameters = createParameterList("${intFQ}[]", "${stringFQ}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int[], String[]) != (int, String)
    psiParameters = createParameterList(intFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int[], String[]) != (int[], String)
    psiParameters = createParameterList("${intFQ}[]", stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(**|*, java.lang.String)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (***, String) == (String, String)
    psiParameters = createParameterList(stringFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (***, String) == (int, String)
    psiParameters = createParameterList(intFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (***, String) == (int[], String)
    psiParameters = createParameterList("${intFQ}[]", stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (***, String) != (int, int)
    psiParameters = createParameterList(intFQ, intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(%, java.lan|g.String, %)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (%, String, %) == (int, String, boolean)
    psiParameters = createParameterList(
      intFQ, stringFQ, PsiPrimitiveType.BOOLEAN.name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (%, String, %) != (int, String, void)
    psiParameters = createParameterList(
      intFQ, stringFQ, PsiPrimitiveType.VOID.name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (%, String, %) != (int, String, String)
    psiParameters = createParameterList(
      intFQ, stringFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (...) == (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (...) == (int, String, long[])
    psiParameters = createParameterList(
      intFQ, stringFQ, "${PsiPrimitiveType.LONG.name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (...) == ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()

    myFixture.moveCaret("myMethod(int, ..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (int, ...) == (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int, ...) == (int, String, long[])
    psiParameters = createParameterList(
      intFQ, stringFQ, "${PsiPrimitiveType.LONG.name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int, ...) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int, ...) != (String, long[])
    psiParameters = createParameterList(stringFQ, "${PsiPrimitiveType.LONG.name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (..., int) != (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (..., int) != (String, int)
    psiParameters = createParameterList(stringFQ, intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (..., int) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(wildca|rd**)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!
    // (wildcard**) != (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (wildcard**) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
  }

  fun testMatchesParameterListKotlin() {
    myFixture.addFileToProject(
      "MyType.kt",
      """
        package p1.p2

        class MyType {}
      """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class * {
          void myMethod(p1.p2.MyType)
        }
      """.trimIndent()
    )

    myFixture.moveCaret("myMethod(p1.p2.MyTyp|e)")
    val parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8Parameters::class)!!

    val psiParameters = createParameterList("p1.p2.MyType")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
  }

  fun testResolvePsiClasses() {
    val superClass = myFixture.addClass("""
      package p1.p2

      class MySuperClass {}
    """.trimIndent())

    val myClass = myFixture.addClass("""
      package p1.p2

      class MyClass extends MySuperClass {}
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret p1.p2.MyClass
      """.trimIndent())

    var header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassSpecificationHeader::class)!!
    assertThat(header.resolvePsiClasses()).containsExactly(myClass)

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret * implements p1.p2.MyClass
      """.trimIndent()
    )
    header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassSpecificationHeader::class)!!
    assertThat(header.resolvePsiClasses()).containsExactly(myClass)

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret p1.p2.MyClass extends p1.p2.MySuperClass
      """.trimIndent()
    )
    header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassSpecificationHeader::class)!!
    assertThat(header.resolvePsiClasses()).containsExactly(myClass, superClass)
  }

  fun testGetTypeForMethod() {

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class test.MyClass {
          int myMethod();
          % myMethod();
          *** myMethod();
          java.lang.String myMethod();
        }
      """.trimIndent()
    )

    myFixture.moveCaret("int myM|ethod()")
    assertThat(getTypeUnderCaret()?.javaPrimitive).isNotNull()
    assertThat(getTypeUnderCaret()?.javaPrimitive!!.node.findChildByType(ProguardR8PsiTypes.INT)).isNotNull()

    myFixture.moveCaret("% myM|ethod()")
    assertThat(getTypeUnderCaret()?.anyPrimitiveType).isNotNull()

    myFixture.moveCaret("*** myM|ethod()")
    assertThat(getTypeUnderCaret()?.anyType).isNotNull()

    myFixture.moveCaret("java.lang.String myM|ethod()")
    assertThat(getTypeUnderCaret()?.qualifiedName).isNotNull()
    assertThat(getTypeUnderCaret()?.qualifiedName!!.text).isEqualTo("java.lang.String")
    assertThat(getTypeUnderCaret()?.matchesPsiType(elementFactory.createTypeFromText("java.lang.String", null))).isTrue()
  }

  fun testGetTypeForField() {

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class test.MyClass {
          int myField;
          % myField;
          *** myField;
          java.lang.String myField;
        }
      """.trimIndent()
    )

    myFixture.moveCaret("int myF|ield")
    assertThat(getTypeUnderCaret()?.javaPrimitive).isNotNull()
    assertThat(getTypeUnderCaret()?.javaPrimitive!!.node.findChildByType(ProguardR8PsiTypes.INT)).isNotNull()

    myFixture.moveCaret("% myF|ield")
    assertThat(getTypeUnderCaret()?.anyPrimitiveType).isNotNull()

    myFixture.moveCaret("*** myF|ield")
    assertThat(getTypeUnderCaret()?.anyType).isNotNull()

    myFixture.moveCaret("java.lang.String myF|ield")
    assertThat(getTypeUnderCaret()?.qualifiedName).isNotNull()
    assertThat(getTypeUnderCaret()?.qualifiedName!!.text).isEqualTo("java.lang.String")
    assertThat(getTypeUnderCaret()?.matchesPsiType(elementFactory.createTypeFromText("java.lang.String", null))).isTrue()
  }

  fun testGetParameters() {
    fun getParameters() = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMember::class)!!.parameters

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClass {
        void myMethod1(int);
        void myMethod2 (int);
        void myMethod3
            (int);
      }
      """.trimIndent())

    myFixture.moveCaret("myM|ethod1")
    assertThat(getParameters()).isNotNull()

    myFixture.moveCaret("myM|ethod2")
    assertThat(getParameters()).isNotNull()

    myFixture.moveCaret("myM|ethod3")
    assertThat(getParameters()).isNotNull()
  }

  fun testContainsWildcards() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
    -keep class myClass {
      int NoWildcards;
      int *;
      int **wildcard;
    }
    """.trimIndent()
    )

    myFixture.moveCaret("No|Wildcard")
    var classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMemberName::class)!!
    assertThat(classMemberName.containsWildcards()).isFalse()

    myFixture.moveCaret("|*")
    classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMemberName::class)!!
    assertThat(classMemberName.containsWildcards()).isTrue()

    myFixture.moveCaret("**w|ildcard")
    classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8ClassMemberName::class)!!
    assertThat(classMemberName.containsWildcards()).isTrue()
  }

  fun testisNegated() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
    -keep class myClass {
      public int field1;
      !public int field2;
      ! public int field3;
    }
    """.trimIndent()
    )

    myFixture.moveCaret("publ|ic int field1")
    var accessModifier = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8AccessModifier::class)!!
    assertThat(accessModifier.isNegated()).isFalse()

    myFixture.moveCaret("!publ|ic int field2")
    accessModifier = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8AccessModifier::class)!!
    assertThat(accessModifier.isNegated()).isTrue()

    myFixture.moveCaret("! publ|ic int field3")
    accessModifier = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType(ProguardR8AccessModifier::class)!!
    assertThat(accessModifier.isNegated()).isTrue()
  }
}
