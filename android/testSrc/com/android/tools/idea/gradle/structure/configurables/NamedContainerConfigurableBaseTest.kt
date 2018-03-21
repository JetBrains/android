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

import com.intellij.openapi.ui.NamedConfigurable
import org.hamcrest.CoreMatchers
import org.junit.Test

import org.junit.Assert.*
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode

class NamedContainerConfigurableBaseTest {

  class TestConfigurable(val name: String) : NamedConfigurable<String>() {
    override fun getEditableObject(): String = name
    override fun isModified(): Boolean = false
    override fun getDisplayName(): String = name
    override fun setDisplayName(name: String?) = Unit
    override fun createOptionsPanel(): JComponent? = null
    override fun apply() = Unit
    override fun getBannerSlogan(): String = name
  }

  @Test
  fun createConfigurablesTree() {
    val configurableA1 = TestConfigurable("A1")
    val configurableA2 = TestConfigurable("A2")
    val configurableB = TestConfigurable("B")

    val configurableA = object : NamedContainerConfigurableBase<String>("A") {
      override fun getChildren() = listOf(configurableA1, configurableA2)
    }

    val root = object : NamedContainerConfigurableBase<String>("ROOT") {
      override fun getChildren() = listOf(configurableA, configurableB)
    }

    val tree: DefaultMutableTreeNode = createConfigurablesTree(root)

    assertThat(tree.userObject, CoreMatchers.sameInstance(root as Any))
    assertThat((tree.getChildAt(0) as DefaultMutableTreeNode).userObject, CoreMatchers.sameInstance(configurableA as Any))
    assertThat(
        ((tree.getChildAt(0) as DefaultMutableTreeNode).getChildAt(1) as DefaultMutableTreeNode).userObject,
        CoreMatchers.sameInstance(configurableA2 as Any))
    assertThat((tree.getChildAt(1) as DefaultMutableTreeNode).userObject, CoreMatchers.sameInstance(configurableB as Any))
  }
}