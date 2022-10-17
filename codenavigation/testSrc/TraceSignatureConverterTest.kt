/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.codenavigation.TraceSignatureConverter
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeVisitor
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TraceSignatureConverterTest {
  // Testing with PSI types is challenging since they are not trivial to create. To work around
  // this we mock the PsiClassType which allows us to test our logic. The hardest part of mocking
  // them comes from the "accept visitor" functionality which is used to resolve generics. This is
  // why we needed to mock "accept", "resolve", and "rawTypes".
  companion object {
    fun createPsiTypeFor(className: String): PsiType {
      val type = mock(PsiClassType::class.java)
      `when`(type.canonicalText).thenReturn(className)
      `when`(type.accept(any<PsiTypeVisitor<*>>())).thenCallRealMethod()
      `when`(type.resolve()).thenReturn(null)
      `when`(type.rawType()).thenReturn(type) // We are our own raw type.

      return type
    }

    fun createPsiTypeForGeneric(className: String, rawName: String): PsiType {
      val rawType = mock(PsiClassType::class.java)
      `when`(rawType.canonicalText).thenReturn(rawName)
      `when`(rawType.accept(any<PsiTypeVisitor<*>>())).thenCallRealMethod()
      `when`(rawType.resolve()).thenReturn(null)
      `when`(rawType.rawType()).thenReturn(rawType) // We are our own raw type.

      val genericType = mock(PsiClassType::class.java)
      `when`(genericType.canonicalText).thenReturn(className)
      `when`(genericType.accept(any<PsiTypeVisitor<*>>())).thenCallRealMethod()
      `when`(genericType.resolve()).thenReturn(null)
      `when`(genericType.rawType()).thenReturn(rawType)

      return genericType
    }
  }

  @Test
  fun convertsPrimitiveToString() {
    val str = TraceSignatureConverter.convertToString(PsiType.BYTE)
    assertThat(str).isEqualTo("B")
  }

  @Test
  fun convertsArrayToString() {
    val type = createPsiTypeFor("java.lang.String")
    val array = PsiArrayType(PsiArrayType(PsiArrayType(type)))

    val str = TraceSignatureConverter.convertToString(array)
    assertThat(str).isEqualTo("[[[Ljava/lang/String;")
  }

  @Test
  fun convertClassToString() {
    val type = createPsiTypeFor("java.util.ArrayList")

    val str = TraceSignatureConverter.convertToString(type)
    assertThat(str).isEqualTo("Ljava/util/ArrayList;")
  }

  @Test
  fun convertGenericClassToString() {
    val type = createPsiTypeForGeneric("java.util.ArrayList<T>", "java.util.ArrayList")

    val str = TraceSignatureConverter.convertToString(type)
    assertThat(str).isEqualTo("Ljava/util/ArrayList;")
  }

  @Test
  fun convertsVoidToString() {
    val str = TraceSignatureConverter.convertToString(PsiType.VOID)
    assertThat(str).isEqualTo("V")
  }

  @Test
  fun convertsEmptyMethodToString() {
    val str = TraceSignatureConverter.getTraceSignature(PsiType.INT, emptyArray())
    assertThat(str).isEqualTo("()I")
  }

  @Test
  fun convertsMethodToString() {
    val str = TraceSignatureConverter.getTraceSignature(PsiType.INT, arrayOf(
      createPsiTypeForGeneric("java.util.List<String>", "java.util.List"),
      createPsiTypeForGeneric("java.util.ArrayList<T>", "java.util.ArrayList"),
      PsiType.BOOLEAN,
      PsiArrayType(PsiArrayType(createPsiTypeFor("java.lang.Integer")))
    ))

    assertThat(str).isEqualTo("(Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I")
  }

  @Test
  fun convertsMethodWithNoReturnValueToString() {
    val str = TraceSignatureConverter.getTraceSignature(null, arrayOf(PsiType.BOOLEAN))
    assertThat(str).isEqualTo("(Z)")
  }
}