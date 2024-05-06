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
package com.android.tools.idea.res

import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.getPathFromFqcn
import com.android.tools.idea.testing.JavacUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests extraction of resources' names and IDs from R classes. */
@RunWith(JUnit4::class)
class RClassIdAnalysisTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private val packageName = "com.myapp"

  @Test
  fun testNamesAndIds() {
    val rClass =
      """
      package $packageName;

      public final class R {
        public static final class string {
          public static final int app_name = 101;
          public static final int ok_text = 102;
          public static final int cancel_text = 103;
        }
        public static class drawable {
          public static final int ic_first = 201;
          public static final int ic_second = 202;
        }
        public static class layout {
          public static final int main_page = 301;
        }
      }
    """
        .trimIndent()
    val resources = getResources(rClass)

    ResourceType.values().forEach {
      when (it) {
        ResourceType.STRING ->
          assertThat(resources.getResources(it)!!)
            .containsExactlyEntriesIn(
              mapOf("app_name" to 101, "ok_text" to 102, "cancel_text" to 103)
            )
        ResourceType.DRAWABLE ->
          assertThat(resources.getResources(it)!!)
            .containsExactlyEntriesIn(mapOf("ic_first" to 201, "ic_second" to 202))
        ResourceType.LAYOUT ->
          assertThat(resources.getResources(ResourceType.LAYOUT)!!)
            .containsExactlyEntriesIn(mapOf("main_page" to 301))
        else -> assertThat(resources.getResources(it)).isNull()
      }
    }
  }

  @Test
  fun testStyleables() {
    val rClass =
      """
      package $packageName;

      public final class R {
        public static final class attr {
          public static final int a1 = 101;
        }
        public static final class styleable {
          public static final int x1 = 201;
          public static final int x2 = 202;
          public static final int[] x = {203, 204, 205};
        }
      }
    """
        .trimIndent()
    val resources = getResources(rClass)

    assertThat(resources.getResources(ResourceType.ATTR))
      .containsExactlyEntriesIn(mapOf("a1" to 101))
    assertThat(resources.getResources(ResourceType.STYLEABLE))
      .containsExactlyEntriesIn(mapOf("x1" to 201, "x2" to 202))
  }

  @Test
  fun testUnknownTypesAreIgnored() {
    val rClass =
      """
      package $packageName;

      public final class R {
        public static final class unknown {
          public static final int app_name = 101;
          public static final int ok_text = 102;
          public static final int cancel_text = 103;
        }
        public static class string {
          public static final int app_name = 101;
        }
      }
    """
        .trimIndent()
    val resources = getResources(rClass)

    assertThat(resources.getResources(ResourceType.STRING))
      .containsExactlyEntriesIn(mapOf("app_name" to 101))
  }

  @Test
  fun testNonIntConstantsAreIgnored() {
    val rClass =
      """
      package $packageName;

      public final class R {
        public static final class string {
          public static final long not_a_resource_id_1 = 101L;
          public static final String not_a_resource_id_2 = "foo";
          public static final int app_name = 100;
        }
      }
    """
        .trimIndent()
    val resources = getResources(rClass)

    assertThat(resources.getResources(ResourceType.STRING))
      .containsExactlyEntriesIn(mapOf("app_name" to 100))
  }

  @Test
  fun testDoubleNestedClassesAreIgnored() {
    val rClass =
      """
      package $packageName;

      public final class R {
        public static final class string {
          public static final int app_name = 100;

            public static final class layout {
              public static final int main_layout = 200;
            }
        }
      }
    """
        .trimIndent()
    val resources = getResources(rClass)

    assertThat(resources.getResources(ResourceType.STRING))
      .containsExactlyEntriesIn(mapOf("app_name" to 100))
    assertThat(resources.getResources(ResourceType.LAYOUT)).isNull()
  }

  private fun getResources(rClass: String): RClassResources {
    val rJava = tempFolder.newFile("R.java").also { it.writeText(rClass) }
    val output = tempFolder.newFolder()
    JavacUtil.getJavac().run(null, null, null, "-d", output.path, rJava.path)

    val loader = { fqcn: String -> output.resolve(getPathFromFqcn(fqcn)).readBytes() }
    return getRClassResources(packageName, loader)!!
  }
}
