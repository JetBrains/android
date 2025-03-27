/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.res.ids

import org.junit.Assert.*
import org.junit.Test

class ResourceIdBinaryParserTest {
  @Test
  fun `test parse successfully`() {
    val testResourceClass =
      ResourceClass(
        name = "FakeRClass1",
        declaredClasses =
          listOf(
            ResourceClass(
              name = "styleable",
              declaredFields =
                listOf(
                  ResourceClass.Field.Int(name = "Styleable1_attr1", isStatic = true, value = 0),
                  ResourceClass.Field.Int(name = "Styleable2_attr1", isStatic = true, value = 0),
                  ResourceClass.Field.Int(name = "Styleable2_attr2", isStatic = true, value = 1),
                  ResourceClass.Field.IntArray(
                    name = "Styleable1",
                    isStatic = true,
                    value = listOf(ResourceClass.Field.Int(name = "[0]", isStatic = false, value = 0x7f030001)),
                  ),
                  ResourceClass.Field.IntArray(
                    name = "Styleable2",
                    isStatic = true,
                    value =
                      listOf(
                        ResourceClass.Field.Int(name = "[0]", isStatic = false, value = 0x7f030003),
                        ResourceClass.Field.Int(name = "[1]", isStatic = false, value = 0x7f030004),
                      ),
                  ),
                ),
            ),
            ResourceClass(
              name = "style",
              declaredFields =
                listOf(
                  ResourceClass.Field.Int(name = "Style1", isStatic = true, value = 0x7f010001),
                  ResourceClass.Field.Int(name = "Style2", isStatic = true, value = 0x7f010002),
                  ResourceClass.Field.Int(name = "Style3", isStatic = true, value = 0x7f010003),
                ),
            ),
            ResourceClass(
              name = "string",
              declaredFields =
                listOf(
                  ResourceClass.Field.Int(name = "string1", isStatic = true, value = 0x7f000001),
                  ResourceClass.Field.Int(name = "string2", isStatic = true, value = 0x7f000002),
                ),
            ),
            ResourceClass(
              name = "layout",
              declaredFields =
                listOf(
                  ResourceClass.Field.Int(name = "layout1", isStatic = true, value = 0x7f020001),
                  ResourceClass.Field.Int(name = "layout2", isStatic = true, value = 0x7f020002),
                ),
            ),
          ),
      )
    print(testResourceClass)
    assertEquals(testResourceClass, resourceIdClassBinaryParser(FakeRClass1::class.java))
  }
}
