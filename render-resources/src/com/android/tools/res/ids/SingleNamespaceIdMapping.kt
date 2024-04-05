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
package com.android.tools.res.ids

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.EnumMap

/**
 * Keeps a bidirectional mapping between type+name and a numeric id, for a known namespace.
 */
internal class SingleNamespaceIdMapping(private val namespace: ResourceNamespace) {
  var toIdMap = EnumMap<ResourceType, Object2IntOpenHashMap<String>>(ResourceType::class.java)
  var fromIdMap = Int2ObjectOpenHashMap<Pair<ResourceType, String>>()

  /**
   * Returns the id of the given resource or 0 if not known.
   */
  fun getId(resourceReference: ResourceReference): Int = toIdMap[resourceReference.resourceType]?.get(resourceReference.name) ?: 0

  /**
   * Returns the [ResourceReference] for the given id, if known.
   */
  fun findById(id: Int): ResourceReference? = fromIdMap[id]?.let { (type, name) -> ResourceReference(namespace, type, name) }
}