/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel

import java.util.regex.Pattern

/**
 * This Factory returns instances of {@link SystemTraceNodeModel}s, guaranteeing that nodes that
 * represents a same object would be mapped to a single instance.
 */
class SystemTraceNodeFactory {

  // We have two possible levels of caching, based on the raw name and based on the computed one from the regex below.
  // regex can get very expensive and become a hotspot, so we use nameMap to avoid it if we can.
  private val canonicalMap = mutableMapOf<String, CanonicalNodeId>()
  private val nodeMap = mutableMapOf<CanonicalNodeId, SystemTraceNodeModel>()

  companion object {
    // Pattern to match names with the format Letters Number. Eg: Frame 1234, Choreographer#doFrame 1234.
    private val ID_GROUP = Pattern.compile("^([A-Za-z\\s#]*)(\\d+)")
  }

  fun getNode(name: String): SystemTraceNodeModel {
    val canonicalId = canonicalMap.getOrPut(name) { computeCanonicalId(name) }
    return nodeMap.getOrPut(canonicalId) { SystemTraceNodeModel(canonicalId.id, canonicalId.name) }
  }

  private fun computeCanonicalId(name: String): CanonicalNodeId {
    // We match the numbers at the end of a tag so the UI can group elements that have an incrementing number at the end as the same thing.
    // This means that "Frame 1", and "Frame 2" will appear as a single element "Frame ###". This allows us to collect the stats, and
    // colorize these elements as if they represent the same thing.
    val matches = ID_GROUP.matcher(name)

    // If we have a group 0 that is not the name then something went wrong. Fallback to the name.
    if (matches.matches() && matches.group(0) == name) {
      // If we find numbers in the group then instead of using the numbers use "###"
      return CanonicalNodeId(matches.group(1), "${matches.group(1)}###")
    }
    else {
      return CanonicalNodeId(name, name)
    }
  }

  private data class CanonicalNodeId(val id: String, val name: String)
}