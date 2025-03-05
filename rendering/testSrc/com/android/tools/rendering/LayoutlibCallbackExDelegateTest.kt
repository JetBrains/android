/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.rendering.parsers.TagSnapshot
import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

private class LayoutlibCallbackExThrowingImpl : LayoutlibCallbackEx() {
  override fun isUsed(): Boolean {
    throw IllegalStateException("Delegate was called")
  }

  override fun loadAndParseRClass() {
    throw IllegalStateException("Delegate was called")
  }

  override fun setLogger(logger: IRenderLogger) {
    throw IllegalStateException("Delegate was called")
  }

  override fun getLayoutEmbeddedParser(): ILayoutPullParser? {
    throw IllegalStateException("Delegate was called")
  }

  override fun reset() {
    throw IllegalStateException("Delegate was called")
  }

  override fun setAaptDeclaredResources(resources: MutableMap<String, TagSnapshot>) {
    throw IllegalStateException("Delegate was called")
  }

  override fun setLayoutParser(layoutName: String, modelParser: ILayoutPullParser) {
    throw IllegalStateException("Delegate was called")
  }

  override fun loadView(
    name: String,
    constructorSignature: Array<out Class<*>?>,
    constructorArgs: Array<out Any?>?,
  ): Any? {
    throw IllegalStateException("Delegate was called")
  }

  override fun resolveResourceId(id: Int): ResourceReference? {
    throw IllegalStateException("Delegate was called")
  }

  override fun getOrGenerateResourceId(resource: ResourceReference): Int {
    throw IllegalStateException("Delegate was called")
  }

  override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? {
    throw IllegalStateException("Delegate was called")
  }

  override fun getAdapterBinding(
    viewObject: Any?,
    attributes: Map<String?, String?>?,
  ): AdapterBinding? {
    throw IllegalStateException("Delegate was called")
  }

  override fun getActionBarCallback(): ActionBarCallback? {
    throw IllegalStateException("Delegate was called")
  }

  override fun createXmlParserForPsiFile(fileName: String): XmlPullParser? {
    throw IllegalStateException("Delegate was called")
  }

  override fun createXmlParserForFile(fileName: String): XmlPullParser? {
    throw IllegalStateException("Delegate was called")
  }

  override fun createXmlParser(): XmlPullParser {
    throw IllegalStateException("Delegate was called")
  }
}

class LayoutlibCallbackExDelegateTest {
  private val resourceReference = ResourceReference.style(ResourceNamespace.ANDROID, "Theme")

  @Test
  fun `disposed LayoutlibCallbackExDelegate does not delegate calls`() {
    val parentDisposable = Disposer.newDisposable()
    val delegate = LayoutlibCallbackExDelegate(parentDisposable, LayoutlibCallbackExThrowingImpl())
    Disposer.dispose(parentDisposable)

    delegate.isUsed()
    delegate.loadAndParseRClass()
    delegate.setLogger(IRenderLogger.NULL_LOGGER)
    delegate.getLayoutEmbeddedParser()
    delegate.reset()
    delegate.setAaptDeclaredResources(mutableMapOf())
    delegate.loadView("test", arrayOf(), arrayOf())
    delegate.resolveResourceId(0)
    delegate.getOrGenerateResourceId(resourceReference)
  }

  @Test
  fun `LayoutlibCallbackExDelegate delegates calls`() {
    val parentDisposable = Disposer.newDisposable()
    val delegate = LayoutlibCallbackExDelegate(parentDisposable, LayoutlibCallbackExThrowingImpl())

    assertThrows(IllegalStateException::class.java) { delegate.isUsed() }
    assertThrows(IllegalStateException::class.java) { delegate.loadAndParseRClass() }
    assertThrows(IllegalStateException::class.java) {
      delegate.setLogger(IRenderLogger.NULL_LOGGER)
    }
    assertThrows(IllegalStateException::class.java) { delegate.getLayoutEmbeddedParser() }
    assertThrows(IllegalStateException::class.java) { delegate.reset() }
    assertThrows(IllegalStateException::class.java) {
      delegate.setAaptDeclaredResources(mutableMapOf())
    }
    assertThrows(IllegalStateException::class.java) {
      delegate.loadView("test", arrayOf(), arrayOf())
    }
    assertThrows(IllegalStateException::class.java) { delegate.resolveResourceId(0) }
    assertThrows(IllegalStateException::class.java) {
      delegate.getOrGenerateResourceId(resourceReference)
    }
  }
}
