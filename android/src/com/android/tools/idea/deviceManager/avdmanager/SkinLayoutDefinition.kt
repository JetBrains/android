/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.repository.io.FileOp
import com.google.common.base.Charsets
import com.google.common.base.Splitter
import java.io.File
import java.io.IOException
import java.util.TreeMap
import java.util.regex.Pattern

/**
 * Allows access to Device Skin Layout files. The layout file syntax is of the form:
 * <pre>
 * key {
 * subkey {
 * arbitrary-subkey {
 * keypair-key   keypair-value
 * }
 * }
 * }
</pre> *
 */
class SkinLayoutDefinition private constructor(private val properties: Map<String, String>, val children: Map<String, SkinLayoutDefinition>) {
  /**
   * Returns child nodes of this SkinLayoutDefinition.
   */

  /**
   * Returns the property associated with the given query string or null if no such property exists.
   * Example: Given
   * <pre>
   * foo {
   * bar {
   * abc 123
   * }
   * baz {
   * hello world
   * }
   * }
  </pre> *
   * The query string "foo.bar.abc" would return the string "123" and the query string "foo.baz.hello" would return "world."
   * The query string "foo.bar.def" would return null because the key referenced does not exist.
   * The query string "foo.bar" would return null because it represents an incomplete path.
   *
   * @param queryString a dot-separated list of string keys
   */
  fun getValue(queryString: String): String? {
    val hasDot = queryString.contains('.')
    val name = queryString.substringAfterLast('.')
    val node = this.takeUnless { hasDot } ?: getNode(queryString.substringBeforeLast('.'))
    return node?.properties?.get(name)
  }

  /**
   * Returns a sub-node with the given path.
   *
   * @param queryString dot-separated sequence of node names
   * @return the sub-node, or null if not found
   */
  fun getNode(queryString: String): SkinLayoutDefinition? {
    var result: SkinLayoutDefinition? = null
    var node: SkinLayoutDefinition? = this
    for (name in QUERY_SPLITTER.split(queryString)) {
      if (node == null) {
        return null
      }
      node = node.children[name]
      result = node
    }
    return result
  }

  override fun toString(): String = StringBuilder().apply {
    makeString(this, 1)
  }.toString()

  /**
   * @param depth number of 2-space indents to apply
   */
  private fun makeString(buf: StringBuilder, depth: Int): Unit = with(buf) {
    append("{\n")
    for (key in properties.keys) {
      appendSpace(buf, depth)
      append(key)
      append("    ")
      append(properties[key])
      append("\n")
    }
    for ((key, value) in children) {
      appendSpace(buf, depth)
      append(key)
      append("    ")
      value.makeString(this, depth + 1)
    }
    appendSpace(this, depth - 1)
    append("}\n")
  }

  companion object {
    private val TOKEN_SPLITTER = Splitter.on(Pattern.compile("\\s+")).omitEmptyStrings().trimResults()
    private val QUERY_SPLITTER = Splitter.on('.')
    @JvmStatic
    fun parseFile(file: File, fop: FileOp): SkinLayoutDefinition? {
      val contents: String = try {
        fop.toString(file, Charsets.UTF_8)
      }
      catch (e: IOException) {
        return null
      }
      return parseString(contents)
    }

    fun parseString(contents: String): SkinLayoutDefinition = loadFromTokens(TOKEN_SPLITTER.split(contents).iterator())

    /**
     * Creates a SkinLayoutDefinition from the token stream.
     *
     * @param tokens a sequence of string tokens
     */
    private fun loadFromTokens(tokens: Iterator<String>): SkinLayoutDefinition {
      // Both [children] and [properties] are [TreeMap]s because we want keys to be sorted
      val children = TreeMap<String, SkinLayoutDefinition>()
      val properties = TreeMap<String, String>()
      while (tokens.hasNext()) {
        val key = tokens.next()
        if (key == "}") { // We're done with this block, return.
          break
        }
        val value = tokens.next()
        if (value == "{") { // Start of a nested block, recursively load that block.
          children[key] = loadFromTokens(tokens)
        }
        else {                // Otherwise, it's a string property, and we'll store it.
          properties[key] = value
        }
      }
      return SkinLayoutDefinition(properties, children)
    }

    private fun appendSpace(buf: StringBuilder, depth: Int) = repeat(depth) { buf.append("  ")}
  }
}