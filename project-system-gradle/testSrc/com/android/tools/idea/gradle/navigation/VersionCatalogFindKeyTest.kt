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
package com.android.tools.idea.gradle.navigation

import com.android.tools.idea.gradle.util.findCatalogKey
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.junit.Rule
import org.junit.Test
import org.toml.lang.psi.TomlFile

class VersionCatalogFindKeyTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val fixture get() = projectRule.fixture


  @Test
  fun testFindInLibraries() {
    testFindKeyInCatalog("""
      plugins.groovy-core = "plugin"
      [libraries]
      groovy-core = "lib"
    """.trimIndent(), "groovy.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("groovy-core = \"lib\"")
    }

    testFindKeyInCatalog("""
      libraries = { groovy-core = "lib" }
    """.trimIndent(), "groovy.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("groovy-core = \"lib\"")
    }

    testFindKeyInCatalog("""
      libraries.groovy-core = "lib"
    """.trimIndent(), "groovy.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("libraries.groovy-core = \"lib\"")
    }
  }

  @Test
  fun testFindInBundles() {
    testFindKeyInCatalog("""
      [bundles]
      bundle = ["lib"]
    """.trimIndent(), "bundles.bundle") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("bundle = [\"lib\"]")
    }

    testFindKeyInCatalog("""
      bundles = { bundle-core = ["lib"] }
    """.trimIndent(), "bundles.bundle.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("bundle-core = [\"lib\"]")
    }

    testFindKeyInCatalog("""
      bundles.bundle-core = "lib"
    """.trimIndent(), "bundles.bundle.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("bundles.bundle-core = \"lib\"")
    }
  }

  @Test
  fun testFindInPlugins() {
    testFindKeyInCatalog("""
      [plugins]
      plugin = "plugin"
    """.trimIndent(), "plugins.plugin") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("plugin = \"plugin\"")
    }

    testFindKeyInCatalog("""
      plugins = { plugin_core = ["plugin"] }
    """.trimIndent(), "plugins.plugin.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("plugin_core = [\"plugin\"]")
    }

    testFindKeyInCatalog("""
      plugins.plugin_core = "plugin"
    """.trimIndent(), "plugins.plugin.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("plugins.plugin_core = \"plugin\"")
    }
  }

  @Test
  fun testFindComplexPath() {
    testFindKeyInCatalog("""
      [libraries]
      alias-core-ext = "aaa"
    """.trimIndent(), "alias_core-ext") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("alias-core-ext = \"aaa\"")
    }

    testFindKeyInCatalog("""
      [libraries]
      alias-core-ext = "aaa"
    """.trimIndent(), "alias.core.ext") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("alias-core-ext = \"aaa\"")
    }

    testFindKeyInCatalog("""
      [libraries]
      alias-core-ext = "aaa"
      alias-core = "aaa"
    """.trimIndent(), "alias.core") {
      assertThat(it).isNotNull()
      assertThat(it!!.text).isEqualTo("alias-core = \"aaa\"")
    }
  }

  private fun testFindKeyInCatalog(versionCatalogText: String,
                                   path:String,
                                   checker: (PsiElement?) -> Unit) {
    fixture.run {
      val psiFile = addFileToProject("gradle/libs.versions.toml", versionCatalogText)

      runReadAction {
        val element = findCatalogKey(psiFile as TomlFile, path)

        checker(element)
      }
    }
  }
}