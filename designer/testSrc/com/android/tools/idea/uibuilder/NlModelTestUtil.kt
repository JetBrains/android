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
package com.android.tools.idea.uibuilder

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.model.NlComponentMixin
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.XmlElementFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.runInEdtAndGet
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import java.util.function.Consumer

/**
 * Utility method that creates a [NlModel] with the provided [xmlContent] as the root component.
 * The [xmlContent] should just include the tag name without the brackets.
 *
 * The model is backed by a [LightVirtualFile] and is virtually located under a `layout` directory which means its
 * [ConfigurationManager] uses the default configuration.
 *
 * The inner model uses a stub implementation of [NlModel.TagSnapshotTreeNode] so any behavior relying on
 * the implementation used in NlModel won't work. Maintainer of this class are free to implement the real behavior if needed.
 *
 * **This file should stay mockito free.**
 */
fun createNlModelFromTagName(androidFacet: AndroidFacet,
                             xmlContent: String = generateRootXml(SdkConstants.LINEAR_LAYOUT))
  : NlModel {
  val configurationManager = ConfigurationManager.getOrCreateInstance(androidFacet.module)
  val file = LightLayoutFile(xmlContent)
  val model = NlModel.builder(androidFacet, file, configurationManager.getConfiguration(file))
    .withParentDisposable(androidFacet.module)
    .withComponentRegistrar { NlComponentRegistrar }
    .build()

  val rootComponent = createComponent(file.content.toString(), model)
  model.syncWithPsi(rootComponent.tagDeprecated, listOf(StubTagSnapshotTreeNode(rootComponent)))
  return model
}

/**
 * Returns the root component (aka, the first child of the model).
 */
fun NlModel.getRoot() = this.components[0]!!

/**
 * Creates a new component from the provided String.
 */
fun NlComponent.addChild(xmlTag: String): NlComponent {
  val nlComponent = createComponent(xmlTag, model)
  this.addChild(nlComponent)
  return nlComponent
}

private fun createComponent(xmlTag: String, nlModel: NlModel): NlComponent {
  val nlComponent = runInEdtAndGet {
    runWriteAction {
      NlComponent(nlModel, XmlElementFactory.getInstance(nlModel.project).createTagFromText(xmlTag))
    }
  }
  val mixin = NlComponentMixin(nlComponent)
  nlComponent.setMixin(mixin)
  return nlComponent
}

private class StubTagSnapshotTreeNode(private val component: NlComponent) : NlModel.TagSnapshotTreeNode {
  override fun getTagSnapshot(): TagSnapshot? = component.snapshot

  override fun getChildren(): MutableList<NlModel.TagSnapshotTreeNode> = component.children
    .map { StubTagSnapshotTreeNode(it) }
    .toMutableList()
}

private class LightLayoutFile(xmlContent: String)
  : LightVirtualFile("layout.xml", XmlFileType.INSTANCE, xmlContent) {
  private val parent = LightVirtualFile("layout")
  override fun getParent(): VirtualFile {
    return parent
  }
}

/**
 * Generate the base xml string containing namespace declaration, layout attributes and default id.
 */
@Language("XML")
fun generateRootXml(rootTag: String = SdkConstants.LINEAR_LAYOUT) = """<?xml version="1.0" encoding="utf-8"?>
<!--suppress XmlUnusedNamespaceDeclaration -->
<$rootTag xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/$rootTag"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
""".trimIndent()
