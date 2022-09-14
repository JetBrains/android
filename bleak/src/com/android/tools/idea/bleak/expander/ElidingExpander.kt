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
package com.android.tools.idea.bleak.expander

import com.android.tools.idea.bleak.ReflectionUtil
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * An expander that elides some internal structure of an object so that its children align
 * more closely with some more abstract notion of ownership (e.g. collections should have
 * the objects they contain as their children, despite not necessarily having direct references
 * to them).
 */
class ElidingExpander(private val baseTypeName: String, val childFinder: Any.() -> List<Any>): Expander() {
  override fun canExpand(obj: Any) = obj.javaClass.name == baseTypeName

  override fun expand(n: Node) {
    for (child in childFinder(n.obj)) {
      n.addEdgeTo(child, ObjectLabel(child))
    }
  }

  override fun canPotentiallyGrowIndefinitely(n: Node) = true

  companion object {
    private fun List<Any>.fields(vararg names: String): List<Any> = flatMap { obj ->
      names.mapNotNull { name -> ReflectionUtil.getAllFields(obj.javaClass).find { it.name == name }?.get(obj) } }

    private fun List<Any>.expandArrays(): List<Any> = flatMap { obj ->
      if (obj is Array<*>) {
        obj.toList().filterNotNull()
      } else {
        listOf(obj)
      }
    }

    private fun Any.recFields(vararg names: String): List<Any> {
      val seen = IdentityHashMap<Any, Any>()
      val queue = ArrayDeque<Any>()
      queue.add(this)
      while (queue.isNotEmpty()) {
        val cur = queue.pop()
        if (cur in seen) continue
        seen[cur] = cur
        listOf(cur).fields(*names).forEach { queue.push(it) }
      }
      return seen.keys.toList()
    }

    private fun List<Any>.recFields(vararg names: String): List<Any> = flatMap { it.recFields(*names) }

    private val childFinders = listOf<Pair<String, (Any) -> List<Any>>>(
      "java.util.HashMap" to { hm -> listOf(hm).fields("table").expandArrays().recFields("next").fields("key", "value") },
      "java.util.concurrent.ConcurrentHashMap" to { hm -> listOf(hm).fields("table").expandArrays().recFields("next").fields("key", "val") },
      "java.util.TreeMap" to { tm -> listOf(tm).fields("root").recFields("left", "right").fields("key", "value") },
      "java.util.LinkedList" to { ll -> listOf(ll).fields("first").recFields("next").fields("item") }
    )

    fun getExpanders() = childFinders.map { ElidingExpander(it.first, it.second) }
  }
}
