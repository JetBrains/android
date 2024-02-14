/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.intellij.configurationStore.JbXmlOutputter
import com.intellij.configurationStore.serialize
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.Constants
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.TestOnly
import java.io.StringWriter

private const val MAX_DEVICE_TYPES = 200
private const val PROMOTION_THRESHOLD = 1000
private const val BIT_RATE_REACHED_SCORE = 334
private const val BIT_RATE_NOT_REACHED_SCORE = 16

/**
 * Keeps track of per-device-type bit rates of video encoding.

 * The bit rate starts as unspecified (represented by zero) delegating the choice to the Screen Sharing Agent.
 * Every call to [bitRateReduced] increments the scores of the matching and higher bit rate candidates by
 * [BIT_RATE_REACHED_SCORE]. If a candidate bit rate reaches [PROMOTION_THRESHOLD], that bit rate becomes
 * the default for the device type. Every call to [bitRateStable] reduces scores of all bit rate candidates by
 * [BIT_RATE_NOT_REACHED_SCORE]. Candidates with negative scores are dropped.
 */
@Service
@State(name = "BitRates", storages = [(Storage("device.mirroring.bit.rates.xml"))])
internal class BitRateManager : PersistentStateComponent<BitRateManager> {

  @GuardedBy("bitRateTrackers")
  var bitRateTrackers = linkedMapOf<String, BitRateTracker>() // Mutable for deserialization.

  /** Returns the video encoding bit rate for the given device type. */
  fun getBitRate(deviceProperties: DeviceProperties): Int {
    synchronized(bitRateTrackers) {
      val key = deviceProperties.key()
      val tracker = bitRateTrackers.remove(key) ?: return 0
      bitRateTrackers[key] = tracker // Add the last accessed BitRateTracker to the end of the map.
      return tracker.bitRate
    }
  }

  /** Records a bit rate reduction performed by the Screen Sharing Agent. */
  fun bitRateReduced(newBitRate: Int, deviceProperties: DeviceProperties) {
    synchronized(bitRateTrackers) {
      val key = deviceProperties.key()
      val tracker = bitRateTrackers[key]
      if (tracker == null) {
        while (bitRateTrackers.size >= MAX_DEVICE_TYPES) {
          bitRateTrackers.iterator().remove()
        }
        bitRateTrackers[key] = BitRateTracker(CandidateBitRate(newBitRate, BIT_RATE_REACHED_SCORE))
      }
      else {
        tracker.bitRateReduced(newBitRate)
      }
    }
  }

  /** Records that the bit rate remained unchanged over a certain period of time. */
  fun bitRateStable(bitRate: Int, deviceProperties: DeviceProperties) {
    synchronized(bitRateTrackers) {
      val key = deviceProperties.key()
      val tracker = bitRateTrackers[key] ?: return
      tracker.bitRateStable(bitRate)
      if (tracker.isEmpty()) {
        bitRateTrackers.remove(key)
      }
    }
  }

  override fun getState(): BitRateManager = this

  override fun loadState(state: BitRateManager) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BitRateManager
    synchronized(bitRateTrackers) {
      return bitRateTrackers == other.bitRateTrackers
    }
  }

  override fun hashCode(): Int {
    synchronized(bitRateTrackers) {
      return bitRateTrackers.hashCode()
    }
  }

  @TestOnly
  internal fun clear() {
    synchronized(bitRateTrackers) {
      bitRateTrackers.clear()
    }
  }

  @TestOnly
  fun toXmlString(): String {
    val element = synchronized(bitRateTrackers) {
      serialize(this@BitRateManager) ?: throw RuntimeException("Unable to serialize ${this@BitRateManager}")
    }
    val writer = StringWriter()
    JbXmlOutputter().output(element, writer)
    return writer.toString()
  }

  private fun DeviceProperties.key(): String =
      "${manufacturer ?: ""}|${model ?: ""}|${primaryAbi ?: ""}|${androidVersion?.featureLevel ?: 0}"

  companion object {
    fun getInstance(): BitRateManager = ApplicationManager.getApplication().service<BitRateManager>()
  }

  /** Candidate bit rates are kept in descending order. */
  data class BitRateTracker private constructor(
    var bitRate: Int,
    @XCollection(propertyElementName = "candidates", valueAttributeName = Constants.LIST)
    val candidates: MutableList<CandidateBitRate>
  ) {

    constructor(candidate: CandidateBitRate) : this(0, mutableListOf(candidate))
    @Suppress("unused") // For deserialization
    private constructor() : this(0, mutableListOf<CandidateBitRate>())

    fun bitRateReduced(newBitRate: Int) {
      if (bitRate > 0 && newBitRate >= bitRate) {
        return
      }
      var i = 0
      while (i < candidates.size) {
        val candidate = candidates[i]
        if (candidate.bitRate < newBitRate) {
          break
        }
        candidate.score += BIT_RATE_REACHED_SCORE
        if (candidate.score >= PROMOTION_THRESHOLD) {
          bitRate = candidate.bitRate
          candidates.removeIf { it.bitRate >= bitRate }
          if (bitRate > 0 && newBitRate >= bitRate) {
            return
          }
          i = 0
          continue
        }
        i++
      }

      if (i == 0 || candidates[i - 1].bitRate > newBitRate) {
        val score = if (i < candidates.size) BIT_RATE_REACHED_SCORE + candidates[i].score else BIT_RATE_REACHED_SCORE
        if (score >= PROMOTION_THRESHOLD) {
          // There should be no prior candidates in the list because otherwise a prior candidate
          // would have already reached PROMOTION_THRESHOLD.
          assert(i == 0)
          bitRate = newBitRate
        }
        else {
          candidates.add(i, CandidateBitRate(newBitRate, score))
        }
      }
    }

    fun bitRateStable(bitRate: Int) {
      var i = candidates.size
      while (--i >= 0) {
        val candidate = candidates[i]
        if (candidate.bitRate >= bitRate) {
          break
        }
        candidate.score -= BIT_RATE_NOT_REACHED_SCORE
        if (candidate.score <= 0) {
          candidates.removeAt(i)
        }
      }
    }

    fun isEmpty(): Boolean =
        bitRate == 0 && candidates.isEmpty()
  }

  data class CandidateBitRate(var bitRate: Int, var score: Int) {

    @Suppress("unused") // For deserialization
    private constructor() : this(0, 0)
  }
}