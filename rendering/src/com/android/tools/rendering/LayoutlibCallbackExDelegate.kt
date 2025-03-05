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
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver.EMPTY_RESOLVER
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.rendering.parsers.TagSnapshot
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicReference
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * Implementation of [LayoutlibCallbackEx] that disconnects the [delegate] when [parentDisposable]
 * is disposed. This is helpful since [BridgeContext] might retain a pointer to
 * [LayoutlibCallbackEx] while the dispose is executed. Using this delegate allows to disconnect the
 * actual [LayoutlibCallbackEx] from the memory tree allowing for it to be released even if the
 * dispose tasks take longer.
 */
internal class LayoutlibCallbackExDelegate(
  parentDisposable: Disposable,
  delegate: LayoutlibCallbackEx,
) : LayoutlibCallbackEx(), Disposable {
  private var delegate: AtomicReference<LayoutlibCallbackEx?> = AtomicReference(delegate)

  init {
    if (!Disposer.tryRegister(parentDisposable, this)) {
      dispose()
    }
  }

  private fun getDelegate(): LayoutlibCallbackEx? =
    delegate.get().let {
      if (it == null) {
        thisLogger().warn("LayoutlibCallback use after dispose")
      }

      it
    }

  override fun loadView(
    name: String,
    constructorSignature: Array<out Class<*>?>,
    constructorArgs: Array<out Any?>?,
  ): Any? = getDelegate()?.loadView(name, constructorSignature, constructorArgs)

  override fun resolveResourceId(id: Int): ResourceReference? = getDelegate()?.resolveResourceId(id)

  override fun getOrGenerateResourceId(resource: ResourceReference): Int =
    getDelegate()?.getOrGenerateResourceId(resource) ?: 0

  override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? =
    getDelegate()?.getParser(layoutResource)

  override fun getAdapterBinding(
    viewObject: Any?,
    attributes: Map<String?, String?>?,
  ): AdapterBinding? = getDelegate()?.getAdapterBinding(viewObject, attributes)

  override fun getActionBarCallback(): ActionBarCallback? = getDelegate()?.getActionBarCallback()

  override fun createXmlParserForPsiFile(fileName: String): XmlPullParser? =
    getDelegate()?.createXmlParserForPsiFile(fileName)

  override fun createXmlParserForFile(fileName: String): XmlPullParser? =
    getDelegate()?.createXmlParserForFile(fileName)

  override fun createXmlParser(): XmlPullParser = getDelegate()?.createXmlParser() ?: KXmlParser()

  override fun dispose() {
    thisLogger().debug("LayoutlibCallbackDelegate.dispose")
    delegate.set(null)
  }

  override fun isUsed(): Boolean = getDelegate()?.isUsed() == true

  override fun loadAndParseRClass(): Unit = getDelegate()?.loadAndParseRClass() ?: Unit

  override fun setLogger(logger: IRenderLogger): Unit = getDelegate()?.setLogger(logger) ?: Unit

  override fun getLayoutEmbeddedParser(): ILayoutPullParser? =
    getDelegate()?.getLayoutEmbeddedParser()

  override fun reset(): Unit = getDelegate()?.reset() ?: Unit

  override fun setAaptDeclaredResources(resources: MutableMap<String, TagSnapshot>): Unit =
    getDelegate()?.setAaptDeclaredResources(resources) ?: Unit

  override fun setLayoutParser(layoutName: String, modelParser: ILayoutPullParser): Unit =
    getDelegate()?.setLayoutParser(layoutName, modelParser) ?: Unit

  override fun getAdapterItemValue(
    adapterView: ResourceReference?,
    adapterCookie: Any?,
    itemRef: ResourceReference?,
    fullPosition: Int,
    positionPerType: Int,
    fullParentPosition: Int,
    parentPositionPerType: Int,
    viewRef: ResourceReference?,
    viewAttribute: ViewAttribute?,
    defaultValue: Any?,
  ): Any? =
    getDelegate()
      ?.getAdapterItemValue(
        adapterView,
        adapterCookie,
        itemRef,
        fullPosition,
        positionPerType,
        fullParentPosition,
        parentPositionPerType,
        viewRef,
        viewAttribute,
        defaultValue,
      )

  override fun loadClass(
    name: String,
    constructorSignature: Array<out Class<*>?>?,
    constructorArgs: Array<out Any?>?,
  ): Any? = getDelegate()?.loadClass(name, constructorSignature, constructorArgs)

  override fun getApplicationId(): String? = getDelegate()?.applicationId

  override fun getResourcePackage(): String? = getDelegate()?.resourcePackage

  override fun findClass(name: String): Class<*> =
    getDelegate()?.findClass(name) ?: throw ClassNotFoundException(name)

  override fun isClassLoaded(name: String): Boolean = getDelegate()?.isClassLoaded(name) ?: false

  override fun getImplicitNamespaces(): ResourceNamespace.Resolver =
    getDelegate()?.implicitNamespaces ?: EMPTY_RESOLVER

  override fun hasLegacyAppCompat(): Boolean = getDelegate()?.hasLegacyAppCompat() == true

  override fun hasAndroidXAppCompat(): Boolean = getDelegate()?.hasAndroidXAppCompat() == true

  override fun isResourceNamespacingRequired(): Boolean =
    getDelegate()?.isResourceNamespacingRequired == true

  override fun shouldUseCustomInflater(): Boolean = getDelegate()?.shouldUseCustomInflater() == true

  override fun error(message: String, vararg details: String): Unit {
    getDelegate()?.error(message, *details)
  }

  override fun error(message: String, t: Throwable?): Unit =
    getDelegate()?.error(message, t) ?: Unit

  override fun error(t: Throwable): Unit = getDelegate()?.error(t) ?: Unit

  override fun warn(message: String, t: Throwable?) = getDelegate()?.warn(message, t) ?: Unit

  override fun warn(t: Throwable) = getDelegate()?.warn(t) ?: Unit
}
