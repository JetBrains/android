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
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.parentOfType

class ProguardR8PsiImplUtilTest : ProguardR8TestCase() {

  private fun createParameterList(vararg types: String): PsiParameterList {
    val psiTypes = types.map { elementFactory.createTypeFromText(it, null) }.toTypedArray()
    val names = types.indices.map { "param$it" }
    return elementFactory.createParameterList(names.toTypedArray(), psiTypes)
  }

  private fun getTypeUnderCaret() =
    myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!.type

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

    val qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))
  }

  fun testResolveInnerPsiClassFromQualifiedName() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        class Inner {
          class SecondInner {}
        }
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      " -keep class test.MyClass\$Inner\$Second${caret}Inner {}"
    )
    val qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()!!

    assertThat(qName.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass.Inner.SecondInner"))
  }

  fun testDontResolveInnerClassAfterDot() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {
        class Inner {
        }
      }
    """.trimIndent())

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      " -keep class test.MyClass.${caret}Inner {}"
    )
    val qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()!!

    assertThat(qName.resolveToPsiClass()).isNull()
  }

  fun testResolvePsiClassFromQualifiedNameInQuotes() {
    myFixture.addClass(
      //language=JAVA
      """
      package test;

      class MyClass {}
    """.trimIndent())

    //double quotes
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class "test.MyClas${caret}s" {}
      """.trimIndent())

    var qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))

    //single quotes
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class 'test.MyClas${caret}s' {}
      """.trimIndent())

    qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))

    // unterminated quotes
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class "test.MyClas${caret}s
      """.trimIndent())

    qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()

    assertThat(qName).isNotNull()
    assertThat(qName!!.resolveToPsiClass()).isEqualTo(myFixture.findClass("test.MyClass"))

    // unterminated quotes
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyClas${caret}s' {}
      """.trimIndent())

    qName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8QualifiedName>()

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

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Type>()
    assertThat(type).isNotNull()
    val arrayStringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String[]", null)
    assertThat(type!!.matchesPsiType(arrayStringType)).isTrue()
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isFalse()
    val listType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.util.List", null)
    assertThat(type.matchesPsiType(listType)).isFalse()

    myFixture.moveCaret("Li|st")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Type>()!!
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

    var primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8JavaPrimitive>()

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiTypes.intType())

    myFixture.moveCaret("by|te")

    primitiveType = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8JavaPrimitive>()

    assertThat(primitiveType).isNotNull()
    assertThat(primitiveType!!.psiPrimitive).isEqualTo(PsiTypes.byteType())
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

    var type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Type>()!!
    assertThat(type.matchesPsiType(PsiTypes.booleanType())).isTrue()

    myFixture.moveCaret("Str|ing")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Type>()!!
    val stringType = JavaPsiFacade.getElementFactory(project).createTypeFromText("java.lang.String", null)
    assertThat(type.matchesPsiType(stringType)).isTrue()

    myFixture.moveCaret("%|")
    type = myFixture.file.findElementAt(myFixture.caretOffset - 1)!!.parentOfType<ProguardR8Type>()!!
    // String is NOT primitive
    assertThat(type.matchesPsiType(stringType)).isFalse()
    assertThat(type.matchesPsiType(PsiTypes.longType())).isTrue()
    assertThat(type.matchesPsiType(PsiTypes.voidType())).isFalse()

    myFixture.moveCaret("*|**")
    type = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Type>()!!
    assertThat(type.matchesPsiType(stringType)).isTrue()
    assertThat(type.matchesPsiType(PsiTypes.longType())).isTrue()
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
    var parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    assertThat(parameters.isAcceptAnyParameters).isTrue()

    myFixture.moveCaret("myMethod(int, ..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()

    myFixture.moveCaret("myMethod(..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()

    myFixture.moveCaret("myMethod(int, ..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    assertThat(parameters.isAcceptAnyParameters).isFalse()
  }

  fun testMatchesParameterList() {

    val stringFQ = String::class.java.canonicalName
    val intFQ = PsiTypes.intType().name

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
    var parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    // (int) == (int)
    var psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int) != (int, int)
    psiParameters = createParameterList(intFQ, intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != (long)
    psiParameters = createParameterList(PsiTypes.longType().name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int) != (int[])
    psiParameters = createParameterList("${intFQ}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(i|nt, java.lang.String)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
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
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
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
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
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
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    // (%, String, %) == (int, String, boolean)
    psiParameters = createParameterList(
      intFQ, stringFQ, PsiTypes.booleanType().name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (%, String, %) != (int, String, void)
    psiParameters = createParameterList(
      intFQ, stringFQ, PsiTypes.voidType().name)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (%, String, %) != (int, String, String)
    psiParameters = createParameterList(
      intFQ, stringFQ, stringFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    // (...) == (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (...) == (int, String, long[])
    psiParameters = createParameterList(
      intFQ, stringFQ, "${PsiTypes.longType().name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (...) == ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()

    myFixture.moveCaret("myMethod(int, ..|.)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
    // (int, ...) == (int)
    psiParameters = createParameterList(intFQ)
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int, ...) == (int, String, long[])
    psiParameters = createParameterList(
      intFQ, stringFQ, "${PsiTypes.longType().name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isTrue()
    // (int, ...) != ()
    psiParameters = createParameterList()
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()
    // (int, ...) != (String, long[])
    psiParameters = createParameterList(stringFQ, "${PsiTypes.longType().name}[]")
    assertThat(parameters.matchesPsiParameterList(psiParameters)).isFalse()

    myFixture.moveCaret("myMethod(..|., int)")
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
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
    parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!
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
    val parameters = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8Parameters>()!!

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

    var header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassSpecificationHeader>()!!
    assertThat(header.resolvePsiClasses()).containsExactly(myClass)

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret * implements p1.p2.MyClass
      """.trimIndent()
    )
    header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassSpecificationHeader>()!!
    assertThat(header.resolveSuperPsiClasses()).containsExactly(myClass)

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret p1.p2.MyClass extends p1.p2.MySuperClass
      """.trimIndent()
    )
    header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassSpecificationHeader>()!!
    assertThat(header.resolvePsiClasses() + header.resolveSuperPsiClasses()).containsExactly(myClass, superClass)
  }

  fun testResolveToMultiplePsiClasses() {
    val class1 = myFixture.addClass(
      """
      package p1.p2

      class MyClass1 {}
    """.trimIndent()
    )

    val class2 = myFixture.addClass(
      """
      package p1.p2

      class MyClass2 {}
    """.trimIndent()
    )

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
          -keep class $caret p1.p2.MyClass1, p1.p2.MyClass2 
      """.trimIndent()
    )

    val header = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassSpecificationHeader>()!!
    assertThat(header.resolvePsiClasses()).containsExactly(class1, class2)
    assertThat(header.resolvePsiClasses().map { it.qualifiedName }).containsExactly("p1.p2.MyClass1", "p1.p2.MyClass2")
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
    fun getParameters() = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMember>()!!.parameters

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
    var classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMemberName>()!!
    assertThat(classMemberName.containsWildcards()).isFalse()

    myFixture.moveCaret("|*")
    classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMemberName>()!!
    assertThat(classMemberName.containsWildcards()).isTrue()

    myFixture.moveCaret("**w|ildcard")
    classMemberName = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<ProguardR8ClassMemberName>()!!
    assertThat(classMemberName.containsWildcards()).isTrue()
  }

  fun testIsNegated() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
    -keep class myClass {
      public int field1;
      static int field1;
      !public int field2;
      ! public int field3;
      ! final int field3;
    }
    """.trimIndent()
    )

    var accessModifier = myFixture.moveCaret("publ|ic int field1").parentOfType<ProguardR8Modifier>()!!
    assertThat(accessModifier.isNegated).isFalse()

    accessModifier = myFixture.moveCaret("stat|ic int field1").parentOfType<ProguardR8Modifier>()!!
    assertThat(accessModifier.isNegated).isFalse()

    accessModifier = myFixture.moveCaret("!publ|ic int field2").parentOfType<ProguardR8Modifier>()!!
    assertThat(accessModifier.isNegated).isTrue()

    accessModifier = myFixture.moveCaret("! publ|ic int field3").parentOfType<ProguardR8Modifier>()!!
    assertThat(accessModifier.isNegated).isTrue()

    accessModifier = myFixture.moveCaret("! fi|nal int field3").parentOfType<ProguardR8Modifier>()!!
    assertThat(accessModifier.isNegated).isTrue()
  }

  fun testMultiDimensionArray() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class MyClass {
          java.lang.Object[][] myFunction1();
          int[][][] myFunction2();
        }
      """.trimIndent()
    )

    var type = myFixture.moveCaret("Objec|t").parentOfType<ProguardR8Type>()!!
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("Object[][]", null))).isTrue()

    type = myFixture.moveCaret("in|t").parentOfType()!!
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("int[][][]", null))).isTrue()
  }

  fun testAnyNotPrimitiveType() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class MyClass {
          ** myFunction1();
          **[] myFunction2();
        }
      """.trimIndent()
    )

    var type = myFixture.moveCaret("*|* myFunction1();").parentOfType<ProguardR8Type>()!!
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("Object", null))).isTrue()
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("Object[]", null))).isFalse()

    type = myFixture.moveCaret("*|*[] myFunction2();").parentOfType()!!
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("Object", null))).isFalse()
    assertThat(type.matchesPsiType(elementFactory.createTypeFromText("Object[]", null))).isTrue()
  }

  fun testQualifiedNameContainsWildCards() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -keep class java.wildCard**.myClass
        -keep class java.lang.String
        -keep class *
      """.trimIndent()
    )

    var name = myFixture.moveCaret("java.wildCard**.myCl|ass").parentOfType<ProguardR8QualifiedName>()!!
    assertThat(name.containsWildcards()).isTrue()

    name = myFixture.moveCaret("java.lang.Strin|g").parentOfType()!!
    assertThat(name.containsWildcards()).isFalse()

    name = myFixture.moveCaret("keep class |*").parentOfType()!!
    assertThat(name.containsWildcards()).isTrue()
  }

  fun testIsQuotedForFiles() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include file
        -include "file"
        -include 'file'
        -include 'file2
      """.trimIndent()
    )

    var file = myFixture.moveCaret("-include fi|le").parentOfType<ProguardR8File>()!!
    assertThat(file.isQuoted).isFalse()

    file = myFixture.moveCaret("-include \"file|\"").parentOfType()!!
    assertThat(file.isQuoted).isTrue()

    file = myFixture.moveCaret("include 'fi|le'").parentOfType()!!
    assertThat(file.isQuoted).isTrue()

    file = myFixture.moveCaret("include 'fil|e2").parentOfType()!!
    assertThat(file.isQuoted).isTrue()
  }

  fun testFileCompletion() {
    myFixture.addFileToProject("myFile.pro", "")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include <caret>
      """.trimIndent()
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("myFile.pro")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include "<caret>
      """.trimIndent()
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("myFile.pro")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include '<caret>
      """.trimIndent()
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("myFile.pro")

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include "my<caret>File.pro"
      """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isNotNull()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include 'my<caret>File.pro'
      """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isNotNull()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -include "my<caret>File.pro
      """.trimIndent()
    )

    assertThat(myFixture.elementAtCaret).isNotNull()
  }
}
