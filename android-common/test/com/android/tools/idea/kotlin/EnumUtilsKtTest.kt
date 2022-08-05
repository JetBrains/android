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
package com.android.tools.idea.kotlin

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumUtilsKtTest {

  private enum class MyEnum {
    EntryA,
    EntryB
  }

  @Test
  fun testEnumValueOfOrNull() {
    assertEquals(MyEnum.EntryA, enumValueOfOrNull<MyEnum>("EntryA"))
    assertEquals(MyEnum.EntryB, enumValueOfOrNull<MyEnum>("EntryB"))
    assertNull(enumValueOfOrNull<MyEnum>("Entrya"))
    assertNull(enumValueOfOrNull<MyEnum>("entryA"))
    assertNull(enumValueOfOrNull<MyEnum>("entrya"))
  }

  @Test
  fun testEnumValueOfOrDefault() {
    assertEquals(MyEnum.EntryA, enumValueOfOrDefault<MyEnum>("EntryA", MyEnum.EntryB))
    assertEquals(MyEnum.EntryB, enumValueOfOrDefault<MyEnum>("EntryB", MyEnum.EntryA))

    assertEquals(MyEnum.EntryB, enumValueOfOrDefault<MyEnum>("Entrya", MyEnum.EntryB))
    assertEquals(MyEnum.EntryB, enumValueOfOrDefault<MyEnum>("entryA", MyEnum.EntryB))
    assertEquals(MyEnum.EntryB, enumValueOfOrDefault<MyEnum>("entrya", MyEnum.EntryB))

    assertEquals(MyEnum.EntryA, enumValueOfOrDefault<MyEnum>("Entryb", MyEnum.EntryA))
    assertEquals(MyEnum.EntryA, enumValueOfOrDefault<MyEnum>("entryB", MyEnum.EntryA))
    assertEquals(MyEnum.EntryA, enumValueOfOrDefault<MyEnum>("entryb", MyEnum.EntryA))
  }
}