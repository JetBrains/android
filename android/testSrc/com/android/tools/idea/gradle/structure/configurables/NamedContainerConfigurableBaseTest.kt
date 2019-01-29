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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.model.ChangeDispatcher
import com.android.tools.idea.observable.collections.ObservableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import org.hamcrest.core.IsEqual.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class NamedContainerConfigurableBaseTest {

  private lateinit var rootDisposable: Disposable

  @Before
  fun setUp() {
    rootDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(rootDisposable)
  }


  val testData = mapOf(
    "ROOT" to listOf("A", "B"),
    "A" to listOf("A1", "A2"),
    "A1" to listOf("A1X", "A1Y"),
    "A2" to listOf("A2K", "A2L"),
    "Z" to listOf("Z1", "Z2", "Z3"),
    "B" to listOf()
  )

  inner class TestConfigurable(val name: String) : NamedContainerConfigurableBase<String>(name), ContainerConfigurable<String> {
    val changeDispatcher = ChangeDispatcher()
    val children = ObservableList<String>().also {
      it.addAll(testData[name].orEmpty())
      it.addListener { changeDispatcher.changed() }
    }
    override fun getChildrenModels(): Collection<String> = children
    override fun createChildConfigurable(model: String): NamedConfigurable<String> = TestConfigurable(model).also {
      Disposer.register(this, it)
    }
    override fun onChange(disposable: Disposable, listener: () -> Unit) = changeDispatcher.add(disposable, listener)
    override fun dispose() = Unit
  }

  @Test
  fun createConfigurablesTree() {
    val root = TestConfigurable("ROOT").also { Disposer.register(rootDisposable, it) }
    val model = createTreeModel(root)
    val testTree = model.rootNode.testStructure()
    assertThat(testTree.toString(), equalTo("""
      ROOT
          A
              A1
                  A1X
                  A1Y
              A2
                  A2K
                  A2L
          B""".trimIndent()))
  }

  @Test
  fun nodesRemoved() {
    val root = TestConfigurable("ROOT").also { Disposer.register(rootDisposable, it) }
    val model = createTreeModel(root)
    root.children.removeAt(1)
    model.getConfigurable(0, 1)?.children?.removeAt(1)
    val testTree = model.rootNode.testStructure()
    assertThat(testTree.toString(), equalTo("""
      ROOT
          A
              A1
                  A1X
                  A1Y
              A2
                  A2K
              """.trimIndent()))
  }

  @Test
  fun nodesAdded() {
    val root = TestConfigurable("ROOT").also { Disposer.register(rootDisposable, it) }
    val model = createTreeModel(root)
    root.children.add("Q")
    model.getConfigurable(0, 1)?.children?.add(1, "Z")
    model.getConfigurable(0, 1, 1)?.children?.add(1, "A2")
    val testTree = model.rootNode.testStructure()
    assertThat(testTree.toString(), equalTo("""
      ROOT
          A
              A1
                  A1X
                  A1Y
              A2
                  A2K
                  Z
                      Z1
                      A2
                          A2K
                          A2L
                      Z2
                      Z3
                  A2L
          B
          Q""".trimIndent()))
  }

  @Test
  fun nodesReorderedAndAdded() {
    val root = TestConfigurable("ROOT").also { Disposer.register(rootDisposable, it) }
    val model = createTreeModel(root)
    root.children.beginUpdate()
    root.children.add(0, "B")
    root.children.removeAt(2)
    model.getConfigurable(0 /* still at old index */)?.children?.removeAt(0)
    model.getConfigurable(0 /* still at old index */)?.children?.add("A1")
    model.getConfigurable(0 /* still at old index */)?.children?.add(0, "Z")
    root.children.endUpdate()
    val testTree = model.rootNode.testStructure()
    assertThat(testTree.toString(), equalTo("""
      ROOT
          B
          A
              Z
                  Z1
                  Z2
                  Z3
              A2
                  A2K
                  A2L
              A1
                  A1X
                  A1Y""".trimIndent()))
  }

  private fun ConfigurablesTreeModel.getConfigurable(vararg indexes: Int): TestConfigurable? =
    (indexes.fold(root) { parent, index -> getChild(parent, index) } as? MasterDetailsComponent.MyNode)
      ?.configurable as? TestConfigurable
}

