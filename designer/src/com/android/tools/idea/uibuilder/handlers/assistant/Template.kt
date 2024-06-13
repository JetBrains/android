/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.util.dependsOn
import com.android.tools.idea.util.dependsOnAndroidx
import com.android.tools.idea.util.dependsOnOldSupportLib
import com.intellij.openapi.module.Module
import com.intellij.util.io.DigestUtil
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter

private fun hash(content: String): ByteArray =
  DigestUtil.sha1().digest(content.toByteArray(Charsets.UTF_8))

internal enum class TemplateTag {
  /** This template only supports the old version of the androidx */
  ANDROIDX {
    override fun availableFor(module: Module) = module.dependsOnAndroidx()
  },
  /** This template only supports the old version of the support library (not androidx) */
  SUPPORT_LIBRARY {
    override fun availableFor(module: Module) =
      module.dependsOnOldSupportLib() && !module.dependsOnAndroidx()
  },
  /** This template has a constraint layout */
  CONSTRAINT_LAYOUT {
    override fun availableFor(module: Module): Boolean {
      return module.dependsOn(GoogleMavenArtifactId.CONSTRAINT_LAYOUT) ||
        module.dependsOn(GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT)
    }
  },
  /** This template uses GridLayoutManager (which is already included in recyclerview) */
  GRID;

  /**
   * Returns true if a template tagged with this [TemplateTag] is available for the [module] based
   * on its dependencies.
   */
  open fun availableFor(module: Module) = true
}

/** Holder class for the templates information */
internal data class Template(
  private val myTemplateName: String,
  val myTemplate: String,
  private val tags: Set<TemplateTag>,
) {
  private val hash: ByteArray by lazy { hash(myTemplate) }

  /**
   * Returns true if this [Template] is available to [module], based on the template's tags and the
   * module's dependencies.
   */
  fun availableFor(module: Module): Boolean = tags.all { it.availableFor(module) }

  fun hasSameContent(content: String?): Boolean {
    return !content.isNullOrBlank() &&
      content.length == myTemplate.length &&
      hash.contentEquals(hash(content))
  }

  fun hasTag(tag: TemplateTag) = tags.contains(tag)

  fun hasTags(): Boolean = tags.isNotEmpty()

  override fun toString(): String {
    return myTemplateName
  }

  companion object {
    @JvmField val NONE_TEMPLATE = Template("Default", "", setOf())

    /** Returns a new template using the contents from the given stream */
    @JvmStatic
    @JvmOverloads
    fun fromStream(name: String, stream: InputStream, tags: Set<TemplateTag> = setOf()): Template {
      val reader = InputStreamReader(stream)
      reader.use {
        val writer = StringWriter()
        val buffer = CharArray(1024)
        var count = it.read(buffer)
        while (count != -1) {
          writer.write(buffer, 0, count)
          count = it.read(buffer)
        }
        val content = writer.toString().trim()
        return Template(name, content, tags)
      }
    }
  }
}
