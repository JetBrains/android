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
package com.android.tools.idea.compose.preview.util.device

import com.android.testutils.TestUtils
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParserDefinition
import com.intellij.testFramework.ParsingTestCase

class DeviceSpecParserTest :
  ParsingTestCase("no_data_path_needed", "", DeviceSpecParserDefinition()) {
  override fun getTestDataPath() =
    TestUtils.resolveWorkspacePath("tools/adt/idea/compose-designer/testData").toString()

  private fun toParseTreeText(input: String): String {
    val psiFile = createPsiFile("in-memory", input)
    return toParseTreeText(psiFile, true, false).trim()
  }

  fun testBasicSpec() {
    assertEquals(
      """
        FILE
          PsiElement(id)('id')
          PsiElement(:)(':')
          PsiElement(STRING_T)('my_device_spec')
      """
        .trimIndent(),
      toParseTreeText("id:my_device_spec")
    )

    assertEquals(
      """
        FILE
          PsiElement(name)('name')
          PsiElement(:)(':')
          PsiElement(STRING_T)('A Device Name')
      """
        .trimIndent(),
      toParseTreeText("name:A Device Name")
    )

    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecIdParamImpl(ID_PARAM)
              PsiElement(id)('id')
              PsiElement(=)('=')
              PsiElement(STRING_T)('an_id')
            PsiElement(,)(',')
            DeviceSpecNameParamImpl(NAME_PARAM)
              PsiElement(name)('name')
              PsiElement(=)('=')
              PsiElement(STRING_T)('my_device_spec')
            PsiElement(,)(',')
            DeviceSpecWidthParamImpl(WIDTH_PARAM)
              PsiElement(width)('width')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('200')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecHeightParamImpl(HEIGHT_PARAM)
              PsiElement(height)('height')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('200')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecOrientationParamImpl(ORIENTATION_PARAM)
              PsiElement(orientation)('orientation')
              PsiElement(=)('=')
              DeviceSpecOrientationTImpl(ORIENTATION_T)
                PsiElement(portrait)('portrait')
      """
        .trimIndent(),
      toParseTreeText(
        "spec: id=an_id,name=my_device_spec,width=200dp,height=200dp,orientation=portrait"
      )
    )
  }

  fun testMultilineSpec() {
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecWidthParamImpl(WIDTH_PARAM)
              PsiElement(width)('width')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('30')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecHeightParamImpl(HEIGHT_PARAM)
              PsiElement(height)('height')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('30')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(px)('px')
            PsiElement(,)(',')
            DeviceSpecNameParamImpl(NAME_PARAM)
              PsiElement(name)('name')
              PsiElement(=)('=')
              PsiElement(STRING_T)('my_device')
      """
        .trimIndent(),
      toParseTreeText(
        """
        spec:
          width=30dp,
          height=30px,
          name=my_device
      """
          .trimIndent()
      )
    )
  }

  /** Cases where a number is part of a string are parsed correctly depending on the context. */
  fun testSpecWithNumberInIdOrName() {
    // ID, Name or parent with dimension value is considered a String token
    assertEquals(
      """
        FILE
          PsiElement(id)('id')
          PsiElement(:)(':')
          PsiElement(STRING_T)('1080.0px')
      """
        .trimIndent(),
      toParseTreeText("id:1080.0px")
    )
    assertEquals(
      """
        FILE
          PsiElement(name)('name')
          PsiElement(:)(':')
          PsiElement(STRING_T)('1080.0px')
      """
        .trimIndent(),
      toParseTreeText("name:1080.0px")
    )
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecParentParamImpl(PARENT_PARAM)
              PsiElement(parent)('parent')
              PsiElement(=)('=')
              PsiElement(STRING_T)('1080.0px')
      """
        .trimIndent(),
      toParseTreeText("spec:parent=1080.0px")
    )

    // Same value on width parameter is a SIZE_T element with Numeric + px tokens
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecWidthParamImpl(WIDTH_PARAM)
              PsiElement(width)('width')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('1080.0')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(px)('px')
      """
        .trimIndent(),
      toParseTreeText("spec:width=1080.0px")
    )
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecWidthParamImpl(WIDTH_PARAM)
              PsiElement(width)('width')
              PsiElement(=)('=')
              DeviceSpecSizeTImpl(SIZE_T)
                PsiElement(NUMERIC_T)('1080')
                DeviceSpecUnitImpl(UNIT)
                  PsiElement(px)('px')
      """
        .trimIndent(),
      toParseTreeText("spec:width= 1080px ")
    )

    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecParentParamImpl(PARENT_PARAM)
              PsiElement(parent)('parent')
              PsiElement(=)('=')
              PsiElement(STRING_T)('1024.0px by 1800.0px Custom')
            PsiElement(,)(',')
            DeviceSpecOrientationParamImpl(ORIENTATION_PARAM)
              PsiElement(orientation)('orientation')
              PsiElement(=)('=')
              DeviceSpecOrientationTImpl(ORIENTATION_T)
                PsiElement(landscape)('landscape')
      """
        .trimIndent(),
      toParseTreeText("spec:parent=1024.0px by 1800.0px Custom,orientation=landscape")
    )

    // Error on unsupported characters
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          PsiElement(width)('width')
          PsiElement(=)('=')
          PsiErrorElement:NUMERIC_T expected, got 'e'
            PsiElement(BAD_CHARACTER)('e')
          PsiElement(NUMERIC_T)('1080')
          PsiElement(px)('px')
          PsiElement(BAD_CHARACTER)('e')
      """
        .trimIndent(),
      toParseTreeText("spec:width=e 1080px e")
    )
  }
}
