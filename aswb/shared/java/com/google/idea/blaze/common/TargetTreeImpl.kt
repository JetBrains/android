/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.common

import com.google.auto.value.extension.memoized.Memoized
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterators
import com.google.common.collect.Maps
import com.google.common.graph.SuccessorsFunction
import com.google.common.graph.Traverser
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.nio.file.Path

/**
 * Encapsulates a set of targets, represented as Labels.
 *
 *
 * This class uses a tree to store the set of targets so that finding all the child targets of a
 * given directory is fast.
 */
class TargetTreeImpl private constructor(private val root: Node) : TargetTree {
  override fun getTargets(): Sequence<Label> = LabelIterator.Companion.ofAllSubpackageTargets(root).asSequence()

  /** Returns the set of labels at the given path, excluding any labels in child packages.  */
  override fun getDirectTargets(packagePath: Path): Sequence<Label> {
    return root.find(packagePath.iterator())
      ?.let { LabelIterator.Companion.ofDirectTargets(it) }
      ?.asSequence()
      .orEmpty()
  }

  override fun getSubpackages(pkg: Path): Sequence<Label> {
    return root
      .find(pkg.iterator())
      ?.let { Node.Companion.forPath(pkg, it) }
      ?.let { TargetTreeImpl(it) }
      ?.getTargets()
      .orEmpty()
      .asSequence()
  }

  override val targetCountForStatsOnly: Int
    get() = this.root.size()

  private class LabelIterator(private val nodes: Iterator<Node>) : Iterator<Label> {
    private var currentNode: Node? = null
    private var targetNames: Iterator<String>
    private var next: Label? = null

    init {
      targetNames = emptyList<String>().iterator()
      moveToNext()
    }

    fun moveToNext() {
      next = null
      while (!targetNames.hasNext()) {
        if (!nodes.hasNext()) {
          return
        }
        currentNode = nodes.next()
        targetNames = currentNode!!.targets.iterator()
      }
      next =
        Label.fromWorkspacePackageAndName( // TODO: b/334110669 - Consider multi workspace-builds.
          Label.ROOT_WORKSPACE,
          Path.of(PATH_JOINER.join(currentNode!!.path())),
          targetNames.next()
        )
    }

    override fun hasNext(): Boolean {
      return next != null
    }

    override fun next(): Label {
      val current = next
      moveToNext()
      return current!!
    }

    companion object {
      fun ofAllSubpackageTargets(root: Node): LabelIterator {
        return LabelIterator(
          Traverser.forTree<Node>(SuccessorsFunction { it.children })
            .depthFirstPreOrder(root)
            .iterator()
        )
      }

      fun ofDirectTargets(root: Node): LabelIterator = TargetTreeImpl.LabelIterator(setOf(root).iterator())
    }
  }


  class Node(var parent: Node?, val name: String, val targets: Set<String>, val children: Set<Node>){

    @Memoized
    fun childMap(): ImmutableMap<String, Node> {
      return Maps.uniqueIndex(children, com.google.common.base.Function { it.name })
    }

    fun path(): Iterator<String> {
      if (parent == null) {
        return emptyList<String>().iterator()
      }
      return Iterators.concat<String>(parent!!.path(), setOf(name).iterator())
    }

    @Memoized
    fun size(): Int = targets.size + children.sumOf { it.size() }

    @get:Memoized
    val isEmpty: Boolean
      get() = targets.isEmpty() && children.all { it.isEmpty }

    fun find(path: Iterator<Path>): Node? {
      if (!path.hasNext()) {
        return this
      }
      val childKey = path.next().toString()
      val child = childMap().get(childKey)
      if (child == null) {
        return null
      }
      return child.find(path)
    }

    companion object {
      const val ROOT_NAME: String = ""

      val EMPTY: Node = Node(
        parent = null,
        name = ROOT_NAME,
        targets = emptySet(),
        children = emptySet()
      )

      fun create(
        name: String,
        children: Set<Node>,
        content: Set<String>
      ): Node {
        return Node(parent = null, name, content, children)
      }

      /** Constructs a new node for the given path with an existing node as its only child.  */
      fun forPath(path: Path, child: Node): Node {
        // iterate backwards through the path elements to construct the new nodes bottom up, as
        // required the the immutable data structure.
        // The parent is filled in upon it's construction, see create(...) above.
        var child = child
        for (i in path.nameCount - 1 downTo 0) {
          // when i == 0, we're creating a root node
          val name = if (i > 0) path.getName(i - 1).toString() else ROOT_NAME
          child = create(name, children = setOf(child), content = emptySet())
        }
        return child
      }
    }
  }

  /** Builder for [TargetTreeImpl].  */
  private class Builder(private val name: String) {
    private val content: MutableSet<String> = mutableSetOf()
    private val children: MutableMap<String, Builder> = hashMapOf()

    fun build(): TargetTree = TargetTreeImpl(buildNode())

    fun buildNode(): Node {
      val parent =
        Node.create(name, children.values.map { it.buildNode() }.toSet(), content)
      parent.children.forEach { it.parent = parent }
      return parent
    }

    @CanIgnoreReturnValue
    fun add(target: Label): Builder {
      return add(target.getPackage().iterator(), target.name.toString())
    }

    @CanIgnoreReturnValue
    fun add(pkg: Iterator<Path>, targetName: String): Builder {
      if (!pkg.hasNext()) {
        content.add(targetName)
        return this
      }

      children.computeIfAbsent(pkg.next().toString()) { name -> Builder(name) }
        .add(pkg, targetName)
      return this
    }
  }

  companion object {
    @JvmField
    val EMPTY: TargetTreeImpl = TargetTreeImpl(Node.Companion.EMPTY)
    private val PATH_JOINER: Joiner = Joiner.on('/')

    fun create(targets: Collection<Label>): TargetTree {
      val builder: Builder = Builder(Node.Companion.ROOT_NAME)
      for (target in targets) {
        builder.add(target)
      }
      return builder.build()
    }
  }
}
