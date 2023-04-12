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
package com.android.tools.idea.dagger.index

import com.android.tools.idea.dagger.concepts.classToPsiType
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findParentElement
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiField
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class IndexKeyUtilTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private lateinit var myFixture: CodeInsightTestFixture

  @Before
  fun setup() {
    myFixture = projectRule.fixture
  }

  @Test
  fun getIndexKeys_standardTypeKotlin() {
    myFixture.configureByText(
      "/src/com/example/Foo.kt",
      // language=kotlin
      """
        package com.example
        class F<caret>oo
        """
        .trimIndent()
    )

    val psiType = (myFixture.elementAtCaret as KtClass).toPsiType()!!
    assertThat(getIndexKeys(psiType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.Foo", "Foo", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_standardTypeJava() {
    myFixture.configureByText(
      "/src/com/example/Foo.java",
      // language=kotlin
      """
        package com.example;
        class F<caret>oo {}
        """
        .trimIndent()
    )

    val psiType = myFixture.elementAtCaret.classToPsiType()
    assertThat(getIndexKeys(psiType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.Foo", "Foo", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeWithoutPackage() {
    myFixture.configureByText(
      "/src/Foo.kt",
      // language=kotlin
      """
        class F<caret>oo
        """
        .trimIndent()
    )

    val psiType = (myFixture.elementAtCaret as KtClass).toPsiType()!!
    assertThat(getIndexKeys(psiType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Foo", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeWithAliasByAlias() {
    // Files need to be added to the project (not just configured as in other test cases) to ensure
    // the references between files work.
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example
          class Foo
          """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("class F|oo")
    val basePsiType = (myFixture.elementAtCaret as KtClass).toPsiType()!!

    assertThat(getIndexKeys(basePsiType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.Foo", "Foo", "")
      .inOrder()

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/FooAlias1.kt",
          // language=kotlin
          """
          package com.example
          typealias FooAlias1 = com.example.Foo

          val fooAlias1: FooAlias1 = FooAlias()
          """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias1")
    val alias1PsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias1PsiType).isEqualTo(basePsiType)
    assertThat(getIndexKeys(alias1PsiType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.Foo", "Foo", "FooAlias1", "")
      .inOrder()

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/FooAlias2.kt",
          // language=kotlin
          """
          package com.example
          typealias FooAlias2 = com.example.Foo

          val fooAlias2: FooAlias2 = FooAlias()
          """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias2")
    val alias2PsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias2PsiType).isEqualTo(basePsiType)

    val indexKeysWithAlias2 =
      getIndexKeys(alias2PsiType, myFixture.project, myFixture.project.projectScope())
    assertThat(indexKeysWithAlias2)
      .containsExactly("com.example.Foo", "Foo", "FooAlias1", "FooAlias2", "")

    assertThat(indexKeysWithAlias2[0]).isEqualTo("com.example.Foo")
    assertThat(indexKeysWithAlias2[1]).isEqualTo("Foo")
    assertThat(indexKeysWithAlias2.subList(2, 4)).containsExactly("FooAlias1", "FooAlias2")
    assertThat(indexKeysWithAlias2[4]).isEqualTo("")

    // Same short name as above, but different package
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/other/FooAlias2.kt",
          // language=kotlin
          """
      package com.other
      typealias FooAlias2 = com.example.Foo

      val fooAlias2: FooAlias2 = FooAlias()
      """
            .trimIndent()
        )
        .virtualFile
    )

    myFixture.moveCaret("val fooAl|ias2")
    val alias2OtherPsiType = (myFixture.elementAtCaret as KtProperty).psiType!!

    assertThat(alias2OtherPsiType).isEqualTo(basePsiType)

    val indexKeysWithAlias2Other =
      getIndexKeys(alias2OtherPsiType, myFixture.project, myFixture.project.projectScope())
    assertThat(indexKeysWithAlias2Other)
      .containsExactly("com.example.Foo", "Foo", "FooAlias1", "FooAlias2", "")

    assertThat(indexKeysWithAlias2Other[0]).isEqualTo("com.example.Foo")
    assertThat(indexKeysWithAlias2Other[1]).isEqualTo("Foo")
    assertThat(indexKeysWithAlias2Other.subList(2, 4)).containsExactly("FooAlias1", "FooAlias2")
    assertThat(indexKeysWithAlias2Other[4]).isEqualTo("")
  }

  @Test
  fun getIndexKeys_primitivesKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example
          val fooZ: Boolean = false
          val fooB: Byte = 0
          val fooC: Char = 'a'
          val fooD: Double = 0.0
          val fooF: Float = 0f
          val fooI: Int = 0
          val fooJ: Long = 0L
          val fooS: Short = 0
          """
            .trimIndent()
        )
        .virtualFile
    )

    val booleanType = myFixture.findParentElement<KtProperty>("val foo|Z").psiType!!
    val byteType = myFixture.findParentElement<KtProperty>("val foo|B").psiType!!
    val charType = myFixture.findParentElement<KtProperty>("val foo|C").psiType!!
    val doubleType = myFixture.findParentElement<KtProperty>("val foo|D").psiType!!
    val floatType = myFixture.findParentElement<KtProperty>("val foo|F").psiType!!
    val intType = myFixture.findParentElement<KtProperty>("val foo|I").psiType!!
    val longType = myFixture.findParentElement<KtProperty>("val foo|J").psiType!!
    val shortType = myFixture.findParentElement<KtProperty>("val foo|S").psiType!!

    assertThat(getIndexKeys(booleanType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Boolean", "")
      .inOrder()
    assertThat(getIndexKeys(byteType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Byte", "")
      .inOrder()
    assertThat(getIndexKeys(charType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Char", "Character", "")
      .inOrder()
    assertThat(getIndexKeys(doubleType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Double", "")
      .inOrder()
    assertThat(getIndexKeys(floatType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Float", "")
      .inOrder()
    assertThat(getIndexKeys(intType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Int", "Integer", "")
      .inOrder()
    assertThat(getIndexKeys(longType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Long", "")
      .inOrder()
    assertThat(getIndexKeys(shortType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Short", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_primitivesUnboxedJava() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class Foo {
            private boolean unboxedZ = false;
            private byte unboxedB = 0;
            private char unboxedC = 'a';
            private double unboxedD = 0.0;
            private float unboxedF = 0.0f;
            private int unboxedI = 0;
            private long unboxedJ = 0l;
            private short unboxedS = 0;
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val unboxedBooleanType = myFixture.findParentElement<PsiField>("unboxed|Z").type
    val unboxedByteType = myFixture.findParentElement<PsiField>("unboxed|B").type
    val unboxedCharType = myFixture.findParentElement<PsiField>("unboxed|C").type
    val unboxedDoubleType = myFixture.findParentElement<PsiField>("unboxed|D").type
    val unboxedFloatType = myFixture.findParentElement<PsiField>("unboxed|F").type
    val unboxedIntType = myFixture.findParentElement<PsiField>("unboxed|I").type
    val unboxedLongType = myFixture.findParentElement<PsiField>("unboxed|J").type
    val unboxedShortType = myFixture.findParentElement<PsiField>("unboxed|S").type

    assertThat(
        getIndexKeys(unboxedBooleanType, myFixture.project, myFixture.project.projectScope())
      )
      .containsExactly("Boolean", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedByteType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Byte", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedCharType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Char", "Character", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedDoubleType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Double", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedFloatType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Float", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedIntType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Int", "Integer", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedLongType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Long", "")
      .inOrder()
    assertThat(getIndexKeys(unboxedShortType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Short", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_primitivesBoxedJava() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class Foo {
            private Boolean boxedZ = false;
            private Byte boxedB = 0;
            private Character boxedC = 'a';
            private Double boxedD = 0.0;
            private Float boxedF = 0.0f;
            private Integer boxedI = 0;
            private Long boxedJ = 0l;
            private Short boxedS = 0;
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val boxedBooleanType = myFixture.findParentElement<PsiField>(" boxed|Z").type
    val boxedByteType = myFixture.findParentElement<PsiField>(" boxed|B").type
    val boxedCharType = myFixture.findParentElement<PsiField>(" boxed|C").type
    val boxedDoubleType = myFixture.findParentElement<PsiField>(" boxed|D").type
    val boxedFloatType = myFixture.findParentElement<PsiField>(" boxed|F").type
    val boxedIntType = myFixture.findParentElement<PsiField>(" boxed|I").type
    val boxedLongType = myFixture.findParentElement<PsiField>(" boxed|J").type
    val boxedShortType = myFixture.findParentElement<PsiField>(" boxed|S").type

    assertThat(getIndexKeys(boxedBooleanType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Boolean", "")
      .inOrder()
    assertThat(getIndexKeys(boxedByteType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Byte", "")
      .inOrder()
    assertThat(getIndexKeys(boxedCharType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Char", "Character", "")
      .inOrder()
    assertThat(getIndexKeys(boxedDoubleType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Double", "")
      .inOrder()
    assertThat(getIndexKeys(boxedFloatType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Float", "")
      .inOrder()
    assertThat(getIndexKeys(boxedIntType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Int", "Integer", "")
      .inOrder()
    assertThat(getIndexKeys(boxedLongType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Long", "")
      .inOrder()
    assertThat(getIndexKeys(boxedShortType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Short", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_strings() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class Foo {
            private String javaString = "";
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val javaStringType = myFixture.findParentElement<PsiField>("java|String").type

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example
          val kotlinString: String = ""
          """
            .trimIndent()
        )
        .virtualFile
    )

    val kotlinStringType = myFixture.findParentElement<KtProperty>("kotlin|String").psiType!!

    assertThat(getIndexKeys(javaStringType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("java.lang.String", "String", "")
      .inOrder()
    assertThat(getIndexKeys(kotlinStringType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("java.lang.String", "String", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_genericsKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class SomeGenericType<T>

          val type1: SomeGenericType<kotlin.String> = SomeGenericType()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val psiType1 = myFixture.findParentElement<KtProperty>("type|1").psiType!!
    assertThat(getIndexKeys(psiType1, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.SomeGenericType", "SomeGenericType", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_genericsJava() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class SomeGenericType<T> {
            public static SomeGenericType<String> value = new SomeGenericType<String>();
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val psiType1 = myFixture.findParentElement<PsiField>("val|ue = new").type
    assertThat(getIndexKeys(psiType1, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("com.example.SomeGenericType", "SomeGenericType", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_genericsWithTypeAlias() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class SomeGenericType<T>

          typealias MyTypeAlias1 = SomeGenericType<List<Int>>
          typealias MyTypeAlias2<T> = SomeGenericType<List<T>>
          typealias MyTypeAlias3<T> = SomeGenericType<T>
          typealias MyIntAlias = Int
          typealias MyListAlias<T> = List<T>

          val type1: SomeGenericType<List<Int>> = SomeGenericType()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val psiType1 = myFixture.findParentElement<KtProperty>("type|1").psiType!!
    val indexKeys = getIndexKeys(psiType1, myFixture.project, myFixture.project.projectScope())

    assertThat(indexKeys[0]).isEqualTo("com.example.SomeGenericType")
    assertThat(indexKeys[1]).isEqualTo("SomeGenericType")
    assertThat(indexKeys.subList(2, 5))
      .containsExactly("MyTypeAlias1", "MyTypeAlias2", "MyTypeAlias3")
    assertThat(indexKeys[5]).isEqualTo("")
  }

  @Test
  fun getIndexKeys_arraysKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class Foo

          val fooArray: Array<Foo> = arrayOf()
          val stringArray: Array<String> = arrayOf()

          val booleanArray: BooleanArray = booleanArrayOf()
          val byteArray: ByteArray = byteArrayOf()
          val charArray: CharArray = charArrayOf()
          val doubleArray: DoubleArray = doubleArrayOf()
          val floatArray: FloatArray = floatArrayOf()
          val intArray: IntArray = intArrayOf()
          val longArray: LongArray = longArrayOf()
          val shortArray: ShortArray = shortArrayOf()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooArrayType = myFixture.findParentElement<KtProperty>("foo|Array").psiType!!
    assertThat(getIndexKeys(fooArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Foo[]", "")
      .inOrder()
    val stringArrayType = myFixture.findParentElement<KtProperty>("string|Array").psiType!!
    assertThat(getIndexKeys(stringArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "String[]", "")
      .inOrder()
    val booleanArrayType = myFixture.findParentElement<KtProperty>("boolean|Array").psiType!!
    assertThat(getIndexKeys(booleanArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("BooleanArray", "boolean[]", "")
      .inOrder()
    val byteArrayType = myFixture.findParentElement<KtProperty>("byte|Array").psiType!!
    assertThat(getIndexKeys(byteArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("ByteArray", "byte[]", "")
      .inOrder()
    val charArrayType = myFixture.findParentElement<KtProperty>("char|Array").psiType!!
    assertThat(getIndexKeys(charArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("CharArray", "char[]", "")
      .inOrder()
    val doubleArrayType = myFixture.findParentElement<KtProperty>("double|Array").psiType!!
    assertThat(getIndexKeys(doubleArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("DoubleArray", "double[]", "")
      .inOrder()
    val floatArrayType = myFixture.findParentElement<KtProperty>("float|Array").psiType!!
    assertThat(getIndexKeys(floatArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("FloatArray", "float[]", "")
      .inOrder()
    val intArrayType = myFixture.findParentElement<KtProperty>("int|Array").psiType!!
    assertThat(getIndexKeys(intArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("IntArray", "int[]", "")
      .inOrder()
    val longArrayType = myFixture.findParentElement<KtProperty>("long|Array").psiType!!
    assertThat(getIndexKeys(longArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("LongArray", "long[]", "")
      .inOrder()
    val shortArrayType = myFixture.findParentElement<KtProperty>("short|Array").psiType!!
    assertThat(getIndexKeys(shortArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("ShortArray", "short[]", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_arraysJava() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class Foo {
            public Foo[] fooArray = {};
            public String[] stringArray = {};

            public boolean[] booleanArray = {};
            public byte[] byteArray = {};
            public char[] charArray = {};
            public double[] doubleArray = {};
            public float[] floatArray = {};
            public int[] intArray = {};
            public long[] longArray = {};
            public short[] shortArray = {};

            public Integer[] boxedArray = {};
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooArrayType = myFixture.findParentElement<PsiField>("foo|Array").type!!
    assertThat(getIndexKeys(fooArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Foo[]", "")
      .inOrder()
    val stringArrayType = myFixture.findParentElement<PsiField>("string|Array").type
    assertThat(getIndexKeys(stringArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "String[]", "")
      .inOrder()
    val booleanArrayType = myFixture.findParentElement<PsiField>("boolean|Array").type
    assertThat(getIndexKeys(booleanArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("BooleanArray", "boolean[]", "")
      .inOrder()
    val byteArrayType = myFixture.findParentElement<PsiField>("byte|Array").type
    assertThat(getIndexKeys(byteArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("ByteArray", "byte[]", "")
      .inOrder()
    val charArrayType = myFixture.findParentElement<PsiField>("char|Array").type
    assertThat(getIndexKeys(charArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("CharArray", "char[]", "")
      .inOrder()
    val doubleArrayType = myFixture.findParentElement<PsiField>("double|Array").type
    assertThat(getIndexKeys(doubleArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("DoubleArray", "double[]", "")
      .inOrder()
    val floatArrayType = myFixture.findParentElement<PsiField>("float|Array").type
    assertThat(getIndexKeys(floatArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("FloatArray", "float[]", "")
      .inOrder()
    val intArrayType = myFixture.findParentElement<PsiField>("int|Array").type
    assertThat(getIndexKeys(intArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("IntArray", "int[]", "")
      .inOrder()
    val longArrayType = myFixture.findParentElement<PsiField>("long|Array").type
    assertThat(getIndexKeys(longArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("LongArray", "long[]", "")
      .inOrder()
    val shortArrayType = myFixture.findParentElement<PsiField>("short|Array").type
    assertThat(getIndexKeys(shortArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("ShortArray", "short[]", "")
      .inOrder()
    val boxedArrayType = myFixture.findParentElement<PsiField>("boxed|Array").type
    assertThat(getIndexKeys(boxedArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Integer[]", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_arraysWithGenericsKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class SomeGenericType<T>
          class Foo

          val fooArray: Array<SomeGenericType<Foo>> = arrayOf()
          val stringArray: Array<SomeGenericType<String>> = arrayOf()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooArrayType = myFixture.findParentElement<KtProperty>("foo|Array").psiType!!
    assertThat(getIndexKeys(fooArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "SomeGenericType[]", "")
      .inOrder()
    val stringArrayType = myFixture.findParentElement<KtProperty>("string|Array").psiType!!
    assertThat(getIndexKeys(stringArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "SomeGenericType[]", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_arraysWithGenericsJava() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.java",
          // language=java
          """
          package com.example;

          class SomeGenericType<T> {}

          class Foo {
            public SomeGenericType<Foo>[] fooArray = {};
            public SomeGenericType<String>[] stringArray = {};
          }
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooArrayType = myFixture.findParentElement<PsiField>("foo|Array").type
    assertThat(getIndexKeys(fooArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "SomeGenericType[]", "")
      .inOrder()
    val stringArrayType = myFixture.findParentElement<PsiField>("string|Array").type
    assertThat(getIndexKeys(stringArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "SomeGenericType[]", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeAliasesForPrimitivesKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          typealias MyInt = Int
          typealias MyChar = Char
          typealias MyInteger = java.lang.Integer

          val intProperty: Int = 0
          val charProperty: Char = 'a'
          """
            .trimIndent()
        )
        .virtualFile
    )

    val intType = myFixture.findParentElement<KtProperty>("int|Property").psiType!!
    assertThat(getIndexKeys(intType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Int", "Integer", "MyInt", "MyInteger", "")
      .inOrder()
    val charType = myFixture.findParentElement<KtProperty>("char|Property").psiType!!
    assertThat(getIndexKeys(charType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Char", "Character", "MyChar", "")
      .inOrder()
  }

  @Test
  fun getIndexKeys_typeAliasesForArraysKotlin() {
    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class Foo
          typealias MyIntArray = IntArray
          typealias MyIntegerArray = Array<Int>
          typealias MyFooArray = Array<Foo>

          val intArrayProperty: MyIntArray = arrayOf()
          val integerArrayProperty: MyIntegerArray = arrayOf()
          val charArrayProperty: MyFooArray = arrayOf()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val intArrayType = myFixture.findParentElement<KtProperty>("intArray|Property").psiType!!
    assertThat(getIndexKeys(intArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("IntArray", "int[]", "MyIntArray", "")
    val integerArrayType =
      myFixture.findParentElement<KtProperty>("integerArray|Property").psiType!!
    assertThat(getIndexKeys(integerArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Integer[]", "MyIntegerArray", "MyFooArray", "")
    val charArrayType = myFixture.findParentElement<KtProperty>("charArray|Property").psiType!!
    assertThat(getIndexKeys(charArrayType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Foo[]", "MyFooArray", "MyIntegerArray", "")
  }

  @Test
  fun getIndexKeys_multipleTypeAliasesWithDifferentGenericArgsKotlin() {
    myFixture.addFileToProject(
      "/src/com/other/Foo.kt",
      // language=kotlin
      """
        package com.other

        class Foo<T>
        """
        .trimIndent()
    )

    myFixture.openFileInEditor(
      myFixture
        .addFileToProject(
          "/src/com/example/Foo.kt",
          // language=kotlin
          """
          package com.example

          class Foo<T>
          class Bar

          typealias MyFooInt = Foo<Int>
          typealias MyFooBar = Foo<Bar>
          typealias OtherFooInt = com.other.Foo<Int>
          typealias OtherFooBar = com.other.Foo<Bar>

          typealias MyArrayInt = IntArray
          typealias MyArrayBar = Array<Bar>
          typealias MyArrayFooBar = Array<Foo<Bar>>

          val fooIntProperty: MyFooInt = MyFooInt()
          val fooBarProperty: MyFooBar = MyFooBar()
          val otherFooIntProperty: OtherFooInt = OtherFooInt()
          val otherFooBarProperty: OtherFooBar = OtherFooBar()
          val arrayIntProperty: MyArrayInt = intArrayOf()
          val arrayBarProperty: MyArrayBar = arrayOf()
          val arrayFooBarProperty: MyArrayFooBar = arrayOf()
          """
            .trimIndent()
        )
        .virtualFile
    )

    val fooIntType = myFixture.findParentElement<KtProperty>("fooInt|Property").psiType!!
    val fooIntIndexKeys =
      getIndexKeys(fooIntType, myFixture.project, myFixture.project.projectScope())
    assertThat(fooIntIndexKeys)
      .containsExactly(
        "com.example.Foo",
        "Foo",
        "MyFooInt",
        "MyFooBar",
        "OtherFooInt",
        "OtherFooBar",
        ""
      )
    val fooBarType = myFixture.findParentElement<KtProperty>("fooBar|Property").psiType!!
    assertThat(getIndexKeys(fooBarType, myFixture.project, myFixture.project.projectScope()))
      .containsExactlyElementsIn(fooIntIndexKeys)

    val otherFooIntType = myFixture.findParentElement<KtProperty>("otherFooInt|Property").psiType!!
    val otherFooIntIndexKeys =
      getIndexKeys(otherFooIntType, myFixture.project, myFixture.project.projectScope())
    assertThat(otherFooIntIndexKeys)
      .containsExactly(
        "com.other.Foo",
        "Foo",
        "MyFooInt",
        "MyFooBar",
        "OtherFooInt",
        "OtherFooBar",
        ""
      )
    val otherFooBarType = myFixture.findParentElement<KtProperty>("otherFooBar|Property").psiType!!
    assertThat(getIndexKeys(otherFooBarType, myFixture.project, myFixture.project.projectScope()))
      .containsExactlyElementsIn(otherFooIntIndexKeys)

    val arrayIntType = myFixture.findParentElement<KtProperty>("arrayInt|Property").psiType!!
    assertThat(getIndexKeys(arrayIntType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("IntArray", "int[]", "MyArrayInt", "")
    val arrayBarType = myFixture.findParentElement<KtProperty>("arrayBar|Property").psiType!!
    assertThat(getIndexKeys(arrayBarType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Bar[]", "MyArrayBar", "MyArrayFooBar", "")
    val arrayFooBarType = myFixture.findParentElement<KtProperty>("arrayFooBar|Property").psiType!!
    assertThat(getIndexKeys(arrayFooBarType, myFixture.project, myFixture.project.projectScope()))
      .containsExactly("Array", "Foo[]", "MyArrayBar", "MyArrayFooBar", "")
  }
}
