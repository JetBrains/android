/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.model

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.uibuilder.model.isOrHasSuperclass
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileDocumentManager

/**
 * Tests for [NlModel] as used in the navigation editor
 */
class NavNlModelTest : NavTestCase() {

  fun testAddChild() {
    val treeDumper = NlTreeDumper()
    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val model = modelBuilder.build()

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<fragment>, instance=2}",
        treeDumper.toTree(model.treeReader.components))

    // Add child
    val parent = modelBuilder.findByPath(NavTestCase.TAG_NAVIGATION)!! as NavModelBuilderUtil.NavigationComponentDescriptor
    assertThat(parent).isNotNull()
    parent.action("action", "fragment1")
    modelBuilder.updateModel(model)

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<fragment>, instance=2}\n" +
        "    NlComponent{tag=<action>, instance=3}",
        treeDumper.toTree(model.treeReader.components))
  }

  fun testDeleteChild() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1") {
          action("a1", destination = "f2")
        }
        fragment("f2")
      }
    }

    model.treeWriter.delete(listOf(model.treeReader.find("a1")))
    FileDocumentManager.getInstance().saveAllDocuments()
    val result = String(model.virtualFile.contentsToByteArray())
    // ensure that we end up with a self-closing tag
    assertThat(result.replace("\n *".toRegex(), "\n")).contains("<fragment\nandroid:id=\"@+id/f1\"/>\n")
  }

  fun testTooltips() {
    val model = model("nav.xml") {
      navigation {
        action("global", destination = "f1")
        fragment("f1") {
          action("exit", destination = "foo")
        }
      }
    }

    assertEquals("global", model.treeReader.find("global")?.tooltipText)
    assertEquals("exit", model.treeReader.find("exit")?.tooltipText)
  }

  fun testIsOrHasSuperclass() {
    val model = modelBuilder("nav.xml") {
      navigation("root")
    }.build()

    val root = model.treeReader.find("root")!!
    assertFalse(root.isOrHasSuperclass("foo"))
  }
}
