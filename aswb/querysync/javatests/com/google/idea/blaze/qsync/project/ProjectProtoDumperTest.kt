/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.qsync.project.testutil.compareFormattedStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

internal data class TestModel(
  val name: String,
  val children: List<ChildModel>,
  val smallList: List<SmallModel>,
  val optional: String? = null,
): FormattableModel

internal data class ChildModel(
  val stringSet: Set<String>,
  val map: Map<String, SmallModel>,
  val list: List<ChildModel>
): FormattableModel

internal data class SmallModel(val name: String, val b: Boolean): FormattableModel

internal sealed interface SealedInterface : FormattableModel
internal data class SealedInterfaceImplA(val name: String, val a: String) : SealedInterface
internal data class SealedInterfaceImplB(val name: String, val b: String) : SealedInterface

internal sealed class SealedClass(val typeName: String) : FormattableModel
internal data class SealedClassImpl(val name: String, val value: String) : SealedClass(name)

internal data class SealedListModel(
  val name: String,
  val interfaceItems: List<SealedInterface>,
  val classItems: List<SealedClass>,
) : FormattableModel

@RunWith(JUnit4::class)
class ProjectProtoDumperTest {
  @Test
  fun formatting() {
    compareFormattedStrings(
      TestModel(
        name = "root",
        children = listOf(
          ChildModel(
            stringSet = setOf("a", "b"),
            map = mapOf(
              "c" to SmallModel("d", true),
              "e" to SmallModel("f", false),
            ),
            list = listOf(
              ChildModel(
                stringSet = setOf("g", "h"),
                map = mapOf(),
                list = listOf()
              )
            )
          ),
          ChildModel(
            stringSet = setOf("i", "j"),
            map = mapOf()            ,
            list = listOf()
          ),
        ),
        smallList = listOf(
          SmallModel("aa", true),
          SmallModel("bb", false),
        ),
      )
        .format(),
    """
    root:
        children:
            -
                stringSet:
                    - a
                    - b
                map:
                    "c":
                        d:
                            b: true
                    "e":
                        f:
                list:
                    -
                        stringSet:
                            - g
                            - h
            -
                stringSet:
                    - i
                    - j
        smallList:
            - aa:
                b: true
            - bb:
""".trimIndent())
  }

  @Test
  fun sealedClasses() {
    compareFormattedStrings(
      SealedListModel(
        name = "sealed examples",
        interfaceItems = listOf(
          SealedInterfaceImplA("a1", "valA"),
          SealedInterfaceImplB("b1", "valB"),
        ),
        classItems = listOf(
          SealedClassImpl("c1", "valC"),
        )
      ).format(),
      """
      sealed examples:
          interfaceItems:
              - a1:
                  a: valA
              - b1:
                  b: valB
          classItems:
              - c1:
                  value: valC
      """.trimIndent()
    )
  }
}
