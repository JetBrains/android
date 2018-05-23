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

import libcore.io.Streams
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest

private fun hash(content: String): ByteArray =
  MessageDigest.getInstance("SHA-1").digest(content.toByteArray(Charsets.UTF_8))

/**
 * Holder class for the templates information
 */
internal data class Template(private val myTemplateName: String, val myTemplate: String, val isGrid: Boolean) {
  private val hash: ByteArray by lazy { hash(myTemplate) }

  fun hasSameContent(content: String?): Boolean {
    return !content.isNullOrBlank() && content!!.length == myTemplate.length && hash.contentEquals(hash(content))
  }

  override fun toString(): String {
    return myTemplateName
  }

  companion object {
    @JvmField
    val NONE_TEMPLATE = Template("Default", "", false)

    /**
     * Returns a new template using the contents from the given stream
     */
    @JvmStatic
    @JvmOverloads
    fun fromStream(name: String, stream: InputStream, isGrid: Boolean = false): Template {
      val content = Streams.readFully(InputStreamReader(stream)).trim()
      return Template(name, content, isGrid)
    }
  }
}