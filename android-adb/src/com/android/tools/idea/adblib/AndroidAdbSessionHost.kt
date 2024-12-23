/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbSessionHost
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.androidCoroutineExceptionHandler
import com.android.tools.idea.flags.StudioFlags
import com.intellij.application.subscribe
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [AdbSessionHost] that integrates with the IntelliJ/Android Studio platform.
 *
 * See also [AndroidAdbLoggerFactory] and [AndroidDispatchers].
 */
internal class AndroidAdbSessionHost : AdbSessionHost() {
  private val log = thisLogger()

  private val overriddenProperties = ConcurrentHashMap<Property<*>, Any>()

  private val delegatedProperties = ConcurrentHashMap<Property<*>, () -> Any>()

  private val myActivationListener = MyActivationListener()

  private val disposable = Disposer.newDisposable("AndroidAdbSessionHost disposable")

  override val loggerFactory = AndroidAdbLoggerFactory()

  override val usageTracker = AndroidAdbUsageTracker()

  override val asynchronousChannelGroup: AsynchronousChannelGroup? =
    AsynchronousChannelGroup.withCachedThreadPool(
      AppExecutorUtil.createBoundedScheduledExecutorService("AndroidAdbSessionHost", 4), 1)

  override val parentContext = androidCoroutineExceptionHandler

  init {
    ApplicationActivationListener.TOPIC.subscribe(disposable, myActivationListener)
    myActivationListener.boostJdwpProcessPropertiesCollector(ApplicationManager.getApplication().isActive)
    delegatePropertyValue(
      property = AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_USE_APP_INFO_IF_AVAILABLE,
      valueProvider = { StudioFlags.ADBLIB_USE_APP_INFO_IF_AVAILABLE.get() })
  }

  fun <T: Any> overridePropertyValue(property: Property<T>, value: T) {
    if (!property.isVolatile) {
      throw IllegalArgumentException("Non volatile property value cannot be changed at runtime")
    }
    logger.debug { "Override property value '${property.name}' to '$value'" }
    overriddenProperties[property] = value
  }

  fun <T: Any> delegatePropertyValue(property: Property<T>, valueProvider: () -> T) {
    logger.debug { "Delegating property value '${property.name}' to a provider" }
    delegatedProperties[property] = valueProvider
  }

  override fun <T : Any> getPropertyValue(property: Property<T>): T {
    // First, look in overridden properties...
    val value = overriddenProperties[property]
    if (value != null) {
      @Suppress("UNCHECKED_CAST")
      return value as T
    }

    // ...then in delegated properties...
    val valueProvider = delegatedProperties[property]
    if (valueProvider != null) {
      @Suppress("UNCHECKED_CAST")
      return valueProvider() as T
    }

    // ...then in regular properties
    return super.getPropertyValue(property)
  }

  override fun close() {
    Disposer.dispose(disposable)
  }

  inner class MyActivationListener : ApplicationActivationListener {
    /**
     * Notifies `adblib` whether to use the shortest possible delay to track
     * and monitor new processes, so that this instance of Studio will get priority
     * when running/debugging new JDWP processes.
     *
     * See b/271572555 for more context.
     */
    fun boostJdwpProcessPropertiesCollector(value: Boolean) {
      log.debug { "boostJdwpProcessPropertiesCollector($value)" }
      overridePropertyValue(AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT, value)
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
      boostJdwpProcessPropertiesCollector(true)
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
      boostJdwpProcessPropertiesCollector(false)
    }
  }
}
