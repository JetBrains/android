/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.intellij.configurationStore.JbXmlOutputter
import com.intellij.configurationStore.serialize
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.OptionTag
import java.io.StringWriter
import javax.swing.SwingConstants
import org.jetbrains.annotations.TestOnly

/** Keeps track of layouts used to display paired devices in the Running Devices tool window. */
@Service
@State(name = "PairedLayouts", storages = [(Storage("paired.devices.layouts.xml"))])
internal class PairedDevicesLayoutStorage : PersistentStateComponent<PairedDevicesLayoutStorage> {

  /** Visible and mutable for serialization only. Do not access directly. */
  @get:OptionTag("layouts")
  @get:MapAnnotation
  var layouts = linkedMapOf<String, PairLayoutImpl>()
    private set

  fun getLayout(key: String): PairLayout? = synchronized(layouts) { layouts[key] }

  fun setLayout(key: String, side: Int, splitRatio: Float) {
    synchronized(layouts) { layouts[key] = PairLayoutImpl(side, splitRatio) }
  }

  fun removeLayout(key: String) {
    synchronized(layouts) { layouts.remove(key) }
  }

  override fun getState(): PairedDevicesLayoutStorage = this

  override fun loadState(state: PairedDevicesLayoutStorage) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PairedDevicesLayoutStorage
    synchronized(layouts) {
      return layouts == other.layouts
    }
  }

  override fun hashCode(): Int {
    synchronized(layouts) {
      return layouts.hashCode()
    }
  }

  @TestOnly
  internal fun clear() {
    synchronized(layouts) { layouts.clear() }
  }

  @TestOnly
  fun toXmlString(): String {
    val element =
      synchronized(layouts) {
        serialize(this@PairedDevicesLayoutStorage, createElementIfEmpty = true)
          ?: throw RuntimeException("Unable to serialize ${this@PairedDevicesLayoutStorage}")
      }
    val writer = StringWriter()
    JbXmlOutputter().output(element, writer)
    return writer.toString()
  }

  override fun toString(): String = synchronized(layouts) { "SplitLayoutStorage(layouts=$layouts)" }

  companion object {
    fun getInstance(): PairedDevicesLayoutStorage = service<PairedDevicesLayoutStorage>()
  }

  /** Serializable implementation of [PairLayout]. */
  data class PairLayoutImpl(override var side: Int = 0, override var splitRatio: Float = 0.5f) : PairLayout
}

/** Defines the tool window layout when displaying two paired devices. */
interface PairLayout {
  /**
   * The part of the layout occupied by the first device. One of TOP, LEFT, BOTTOM, RIGHT, CENTER or NONE.
   * - TOP means that the first device occupies the top part of the available space.
   * - LEFT means that the first device occupies the left part of the available space.
   * - BOTTOM means that the first device occupies the bottom part of the available space.
   * - RIGHT means that the first device occupies the right part of the available space.
   * - CENTER means that the first device occupies the entire available space.
   * - NONE means that the first device is not visible and that the second device occupies the entire available space.
   */
  val side: Int
  /** The ratio of the space occupied by the first device to the total available space. The value is between 0.0 and 1.0. */
  val splitRatio: Float

  companion object {
    const val TOP = SwingConstants.TOP
    const val LEFT = SwingConstants.LEFT
    const val BOTTOM = SwingConstants.BOTTOM
    const val RIGHT = SwingConstants.RIGHT
    const val CENTER = SwingConstants.CENTER
    const val NONE = -1
  }
}
