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

class DeviceSpecParserTest: ParsingTestCase("no_data_path_needed", "", DeviceSpecParserDefinition()) {
  override fun getTestDataPath() = TestUtils.resolveWorkspacePath("tools/adt/idea/compose-designer/testData").toString()

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
          PsiElement(DEVICE_ID_T)('my_device_spec')
      """.trimIndent(),
      toParseTreeText("id:my_device_spec")
    )

    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecParamImpl(PARAM)
              DeviceSpecNameParamImpl(NAME_PARAM)
                PsiElement(name)('name')
                PsiElement(=)('=')
                PsiElement(DEVICE_ID_T)('my_device_spec')
            PsiElement(,)(',')
            DeviceSpecParamImpl(PARAM)
              DeviceSpecWidthParamImpl(WIDTH_PARAM)
                PsiElement(width)('width')
                PsiElement(=)('=')
                DeviceSpecSizeTImpl(SIZE_T)
                  PsiElement(INT_T)('200')
                  DeviceSpecUnitImpl(UNIT)
                    PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecParamImpl(PARAM)
              DeviceSpecHeightParamImpl(HEIGHT_PARAM)
                PsiElement(height)('height')
                PsiElement(=)('=')
                DeviceSpecSizeTImpl(SIZE_T)
                  PsiElement(INT_T)('200')
                  DeviceSpecUnitImpl(UNIT)
                    PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecParamImpl(PARAM)
              DeviceSpecOrientationParamImpl(ORIENTATION_PARAM)
                PsiElement(orientation)('orientation')
                PsiElement(=)('=')
                DeviceSpecOrientationTImpl(ORIENTATION_T)
                  PsiElement(portrait)('portrait')
      """.trimIndent(),
      toParseTreeText("spec: name=my_device_spec,width=200dp,height=200dp,orientation=portrait")
    )
  }

  fun testMultilineSpec() {
    assertEquals(
      """
        FILE
          PsiElement(spec)('spec')
          PsiElement(:)(':')
          DeviceSpecSpecImpl(SPEC)
            DeviceSpecParamImpl(PARAM)
              DeviceSpecWidthParamImpl(WIDTH_PARAM)
                PsiElement(width)('width')
                PsiElement(=)('=')
                DeviceSpecSizeTImpl(SIZE_T)
                  PsiElement(INT_T)('30')
                  DeviceSpecUnitImpl(UNIT)
                    PsiElement(dp)('dp')
            PsiElement(,)(',')
            DeviceSpecParamImpl(PARAM)
              DeviceSpecHeightParamImpl(HEIGHT_PARAM)
                PsiElement(height)('height')
                PsiElement(=)('=')
                DeviceSpecSizeTImpl(SIZE_T)
                  PsiElement(INT_T)('30')
                  DeviceSpecUnitImpl(UNIT)
                    PsiElement(px)('px')
            PsiElement(,)(',')
            DeviceSpecParamImpl(PARAM)
              DeviceSpecNameParamImpl(NAME_PARAM)
                PsiElement(name)('name')
                PsiElement(=)('=')
                PsiElement(DEVICE_ID_T)('my_device')
      """.trimIndent(),
      toParseTreeText("""
        spec:
          width=30dp,
          height=30px,
          name=my_device
      """.trimIndent())
    )
  }
}