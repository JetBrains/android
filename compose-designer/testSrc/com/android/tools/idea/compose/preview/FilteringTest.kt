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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.preview.StaticPreviewProvider
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Test

class FilteringTest {
  @Test
  fun testGroupFiltering() = runBlocking {
    val groupPreviewProvider =
      GroupNameFilteredPreviewProvider(
        StaticPreviewProvider(
          listOf(
            SingleComposePreviewElementInstance.forTesting(
              "com.sample.preview.TestClass.PreviewMethod1",
              "PreviewMethod1",
              "GroupA"
            ),
            SingleComposePreviewElementInstance.forTesting(
              "com.sample.preview.TestClass.PreviewMethod2",
              "PreviewMethod2",
              "GroupA"
            ),
            SingleComposePreviewElementInstance.forTesting(
              "com.sample.preview.TestClass.PreviewMethod3",
              "PreviewMethod3",
              "GroupB"
            ),
            SingleComposePreviewElementInstance.forTesting(
              "com.sample.preview.TestClass.PreviewMethod4",
              "PreviewMethod4"
            )
          )
        )
      )

    // No filtering at all
    assertEquals(4, groupPreviewProvider.previewElements().count())
    assertThat(groupPreviewProvider.allAvailableGroups(), `is`(setOf("GroupA", "GroupB")))

    // An invalid group should return all the items
    groupPreviewProvider.groupName = "InvalidGroup"
    assertThat(
      groupPreviewProvider.previewElements().map { it.displaySettings.name }.toList(),
      `is`(listOf("PreviewMethod1", "PreviewMethod2", "PreviewMethod3", "PreviewMethod4"))
    )

    groupPreviewProvider.groupName = "GroupA"
    assertThat(
      groupPreviewProvider.previewElements().map { it.displaySettings.name }.toList(),
      `is`(listOf("PreviewMethod1", "PreviewMethod2"))
    )

    groupPreviewProvider.groupName = "GroupB"
    assertEquals(
      "PreviewMethod3",
      groupPreviewProvider.previewElements().map { it.displaySettings.name }.single()
    )

    groupPreviewProvider.groupName = null
  }

  @Test
  fun testSingleElementFiltering() = runBlocking {
    val staticPreviewProvider =
      StaticPreviewProvider<ComposePreviewElementInstance>(
        listOf(
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod1",
            "PreviewMethod1",
            "GroupA"
          ),
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod2",
            "PreviewMethod2",
            "GroupA"
          ),
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod3",
            "PreviewMethod3",
            "GroupB"
          ),
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod4",
            "PreviewMethod4"
          )
        )
      )
    val singleElementProvider =
      SinglePreviewElementInstanceFilteredPreviewProvider(staticPreviewProvider)

    // No filtering at all
    assertEquals(4, singleElementProvider.previewElements().count())

    // An invalid group should return all the items
    singleElementProvider.instance =
      SingleComposePreviewElementInstance.forTesting("com.notvalid.NotValid", "blank", "GroupX")
    assertEquals(4, singleElementProvider.previewElements().count())

    singleElementProvider.instance =
      staticPreviewProvider.previewElements().first {
        it.composableMethodFqn == "com.sample.preview.TestClass.PreviewMethod3"
      } as SingleComposePreviewElementInstance
    assertEquals(
      "PreviewMethod3",
      singleElementProvider.previewElements().single().displaySettings.name
    )
    singleElementProvider.instance =
      staticPreviewProvider.previewElements().first() as SingleComposePreviewElementInstance
    assertEquals(
      "PreviewMethod1",
      singleElementProvider.previewElements().single().displaySettings.name
    )
  }

  @Test
  fun `multiple @Preview for the same MethodFqn`() = runBlocking {
    val staticPreviewProvider =
      StaticPreviewProvider<ComposePreviewElementInstance>(
        listOf(
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod1",
            "Name1"
          ),
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod1",
            "Name2"
          ),
          SingleComposePreviewElementInstance.forTesting(
            "com.sample.preview.TestClass.PreviewMethod2",
            "Name1"
          )
        )
      )
    val singleElementProvider =
      SinglePreviewElementInstanceFilteredPreviewProvider(staticPreviewProvider)

    singleElementProvider.instance =
      staticPreviewProvider.previewElements().first {
        it.composableMethodFqn == "com.sample.preview.TestClass.PreviewMethod1" &&
          it.displaySettings.name == "Name1"
      } as SingleComposePreviewElementInstance

    assertEquals(1, singleElementProvider.previewElements().count())
    assertEquals(
      "com.sample.preview.TestClass.PreviewMethod1",
      singleElementProvider.previewElements().single().composableMethodFqn
    )
    assertEquals("Name1", singleElementProvider.previewElements().single().displaySettings.name)
  }

  private class TestComposePreviewElementTemplateInstance(
    private val basePreviewElement: ComposePreviewElement,
    index: Int
  ) : ComposePreviewElementInstance(), ComposePreviewElement by basePreviewElement {
    override val instanceId: String = "${basePreviewElement.composableMethodFqn}#$index"
  }

  private class TestComposePreviewElementTemplate(
    private val basePreviewElement: ComposePreviewElement,
    private val instanceCount: Int
  ) : ComposePreviewElementTemplate, ComposePreviewElement by basePreviewElement {
    override fun instances(): Sequence<ComposePreviewElementInstance> =
      generateSequence(instanceCount, { if (it > 1) it - 1 else null }).map {
        TestComposePreviewElementTemplateInstance(basePreviewElement, it)
      }
  }

  @Test
  fun testParametrizedElementFiltering() = runBlocking {
    val template =
      TestComposePreviewElementTemplate(
        SingleComposePreviewElementInstance.forTesting(
          "com.sample.preview.TestClass.PreviewMethod",
          "PreviewMethod"
        ),
        10
      )

    val instances = template.instances().toList()
    val singleElementProvider =
      SinglePreviewElementInstanceFilteredPreviewProvider(StaticPreviewProvider(instances))

    // No filtering at all
    assertEquals(10, singleElementProvider.previewElements().count())

    singleElementProvider.instance = instances[5]
    assertEquals(1, singleElementProvider.previewElements().count())
  }
}
