/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.bleak

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.lang.reflect.Field

/**
 * [DisposerInfo] tracks child counts by class for each [Disposable]. This class-level granularity is not
 * exposed by the normal Bleak check, and is more robust in the face of removal of unrelated children (that
 * is, if a Disposable loses a child of type A and gains one of type B over the course of one iteration, the
 * growth in the number of B children will be discovered by this check, but Bleak would discard the parent
 * as not growing).
 */
class DisposerInfo private constructor (val growingCounts: Map<Key, Int> = mapOf()) {

  class Key(val disposable: Disposable, val klass: Class<*>) {
    override fun equals(other: Any?): Boolean {
      if (other is Key) {
        return disposable === other.disposable && klass === other.klass
      }
      return false
    }

    override fun hashCode() = System.identityHashCode(disposable) xor System.identityHashCode(klass)
  }

  companion object {
    fun createBaseline() = DisposerInfo(getClassCountsMap())

    fun propagateFrom(prevDisposerInfo: DisposerInfo): DisposerInfo {
      val currentClassCounts = getClassCountsMap()
      val growingCounts = mutableMapOf<Key, Int>()
      prevDisposerInfo.growingCounts.forEach { key, prevCount ->
        val newCount = currentClassCounts[key]
        if (newCount != null && newCount > prevCount) {
          growingCounts[key] = newCount
        }
      }
      return DisposerInfo(growingCounts)
    }

    private fun getClassCountsMap(): MutableMap<Key, Int> {
      val counts = mutableMapOf<Key, Int>()
      visitTree (object: Visitor {
        override fun visit(disposable: Disposable) {
          getChildren(disposable).forEach { child ->
            counts.compute(Key(disposable, child.javaClass)) { k, v -> if (v == null) 1 else v + 1 }
          }
        }
      })
      return counts
    }

    private val DISPOSABLE_CLASS = Disposable::class.java
    private val objectTree = ReflectionUtil.getField(Class.forName("com.intellij.openapi.util.Disposer"), "ourTree").get(null)

    private val OBJECT_TREE_CLASS = Class.forName("com.intellij.openapi.util.ObjectTree")
    private val OBJECT_TREE_ROOT_NODE_FIELD = ReflectionUtil.getField(OBJECT_TREE_CLASS, "myRootNode")
    private val OBJECT_TREE_OBJECT2PARENTNODE_FIELD = ReflectionUtil.getField(OBJECT_TREE_CLASS, "myObject2ParentNode")
    private val OBJECT_NODE_CLASS = Class.forName("com.intellij.openapi.util.ObjectNode")
    private val OBJECT_NODE_CHILDREN_FIELD = ReflectionUtil.getField(OBJECT_NODE_CLASS, "myChildren")
    private val OBJECT_NODE_OBJECT_FIELD = ReflectionUtil.getField(OBJECT_NODE_CLASS, "myObject")
    private val OBJECT_NODE_ROOT_DISPOSABLE_FIELD = ReflectionUtil.getField(OBJECT_NODE_CLASS, "ROOT_DISPOSABLE")

    private val rootNode: Any = getFieldValue(OBJECT_TREE_ROOT_NODE_FIELD, objectTree)
    private val object2ParentNode: Map<Any, Any> = getFieldValue(OBJECT_TREE_OBJECT2PARENTNODE_FIELD, objectTree)
    val rootDisposable: Disposable = getFieldValue(OBJECT_NODE_ROOT_DISPOSABLE_FIELD, null)

    private fun <T> getFieldValue(field: Field, obj: Any?): T {
      try {
        return field.get(obj) as T
      }
      catch (e: IllegalAccessException) {
        throw Error(e)
      }
    }

    private fun findChildNode(objectNode: Any, disposable: Any): Any? {
      val childrenObject: Any = getFieldValue(OBJECT_NODE_CHILDREN_FIELD, objectNode)
      val method = childrenObject.javaClass.getDeclaredMethod("findChildNode", DISPOSABLE_CLASS)
      method.isAccessible = true
      return method.invoke(childrenObject, disposable)
    }

    private fun getObjectNode(disposable: Any): Any? {
      var parent = object2ParentNode[disposable]
      if (parent == null) {
        parent = rootNode
      }
      return findChildNode(parent, disposable)
    }

    private fun getChildren(disposable: Any): Collection<Disposable> {
      val objectNode = getObjectNode(disposable) ?: return listOf()
      return getChildrenOfNode(objectNode)
    }

    private fun getChildrenOfNode(node: Any): Collection<Disposable> {
      val childrenObject: Any = getFieldValue(OBJECT_NODE_CHILDREN_FIELD, node)
      val method = childrenObject.javaClass.getDeclaredMethod("getAllNodes")
      method.isAccessible = true
      val childNodes = method.invoke(childrenObject) as Collection<*>
      return childNodes.map { node ->
        getFieldValue(OBJECT_NODE_OBJECT_FIELD, node) as Disposable
      }
    }

    private fun visitTree(visitor: Visitor) {
      synchronized(rootNode) {
        getChildrenOfNode(rootNode).forEach {
          visitObject(it, visitor)
        }
      }
    }

    private fun visitObject(disposable: Disposable, visitor: Visitor) {
      visitor.visit(disposable)
      getChildren(disposable).forEach {
        visitObject(it, visitor)
      }
    }

    private interface Visitor {
      fun visit(disposable: Disposable)
    }
  }
}

class DisposerLeakInfo(val key: DisposerInfo.Key, val count: Int) {
  override fun toString(): String {
    return if (key.disposable === DisposerInfo.rootDisposable) {
      "There are an increasing number ($count) of Disposer roots of type ${key.klass.name}"
    } else {
      "Disposable of type ${key.disposable.javaClass.name} has an increasing number ($count) of children of type ${key.klass.name}"
    }
  }
}

class DisposerCheck(w: IgnoreList<DisposerLeakInfo> = IgnoreList()): BleakCheck<Nothing?, DisposerLeakInfo>(null, w) {
  private var disposerInfo: DisposerInfo? = null

  override fun firstIterationFinished() {
    disposerInfo = DisposerInfo.createBaseline()
  }

  override fun middleIterationFinished() {
    disposerInfo = DisposerInfo.propagateFrom(disposerInfo!!);
  }

  override fun lastIterationFinished() = middleIterationFinished()

  override fun getResults(ignoreList: IgnoreList<DisposerLeakInfo>) =
    disposerInfo?.growingCounts?.map { (key, count) -> DisposerLeakInfo(key, count) }?.filterNot { ignoreList.matches(it) } ?: listOf()

}