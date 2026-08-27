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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.Constants
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection
import java.io.StringWriter
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import org.jetbrains.annotations.TestOnly

private const val MAX_DEVICE_TYPES = 200
private const val PROMOTION_THRESHOLD = 1000
private const val BIT_RATE_REACHED_SCORE = 334
private const val BIT_RATE_NOT_REACHED_SCORE = 16
private const val DEFAULT_BIT_RATE = 10000000 // See display_streamer.cc
private val BIT_RATE_RECOVERY_INTERVAL_MILLIS = 1.days.inWholeMilliseconds
private val MAX_BIT_RATE_AGE_MILLIS = 365.days.inWholeMilliseconds
private val SQRT_2 = sqrt(2.0)
private val SQRT_10 = sqrt(10.0)

/**
 * Rounds the given number to the closest on logarithmic scale value of the form `n * 10^k`, where `n` is one of 1, 2 or 5 and `k` is an
 * integer number.
 */
private fun roundToOneTwoFiveScale(x: Double): Int {
  val exp = floor(log10(x))
  val u = 10.0.pow(exp)
  val f = x / u
  val n =
    when {
      f < SQRT_2 -> 1
      f < SQRT_10 -> 2
      f < 5 * SQRT_2 -> 5
      else -> 10
    }
  return (n * u).roundToInt()
}

/**
 * Keeps track of per-device-type bit rates of video encoding.
 *
 * The bit rate starts as unspecified (represented by zero) delegating the choice to the Screen Sharing Agent. Every call to
 * [bitRateReduced] increments the scores of the matching and higher bit rate candidates by [BIT_RATE_REACHED_SCORE]. If a candidate bit
 * rate reaches [PROMOTION_THRESHOLD], that bit rate becomes the default for the device type. Every call to [bitRateStable] reduces scores
 * of all bit rate candidates by [BIT_RATE_NOT_REACHED_SCORE]. Candidates with negative scores are dropped.
 */
@Service
@State(name = "BitRates", storages = [(Storage("device.mirroring.bit.rates.xml"))])
internal class BitRateManager : PersistentStateComponent<BitRateManager> {

  @GuardedBy("bitRateTrackers") var bitRateTrackers = linkedMapOf<String, BitRateTracker>() // Mutable for deserialization.
  @GuardedBy("bitRateTrackers") @Transient private var needsPruning = true

  /** Returns the video encoding bit rate for the given device type. */
  fun getBitRate(deviceProperties: DeviceProperties): Int {
    synchronized(bitRateTrackers) {
      if (needsPruning) {
        pruneOldBitRates()
        needsPruning = false
      }
      val key = deviceProperties.key()
      val tracker = bitRateTrackers[key] ?: return 0
      val currentTime = System.currentTimeMillis()
      if (tracker.bitRate > 0 && currentTime - tracker.lastModifiedTime > BIT_RATE_RECOVERY_INTERVAL_MILLIS) {
        val newBitRate = roundToOneTwoFiveScale(tracker.bitRate * 2.0)
        if (newBitRate >= DEFAULT_BIT_RATE) {
          tracker.bitRate = 0
          tracker.lastModifiedTime = 0L
          if (tracker.isEmpty()) {
            bitRateTrackers.remove(key)
            return 0
          }
        } else {
          tracker.bitRate = newBitRate
          tracker.lastModifiedTime = currentTime
        }
      }
      bitRateTrackers.remove(key)
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
      } else {
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

  @GuardedBy("bitRateTrackers")
  private fun pruneOldBitRates() {
    val currentTime = System.currentTimeMillis()
    val iterator = bitRateTrackers.iterator()
    while (iterator.hasNext()) {
      val tracker = iterator.next().value
      if (currentTime - tracker.lastModifiedTime > MAX_BIT_RATE_AGE_MILLIS) {
        iterator.remove()
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
    synchronized(bitRateTrackers) { bitRateTrackers.clear() }
  }

  @TestOnly
  fun toXmlString(): String {
    val element =
      synchronized(bitRateTrackers) {
        serialize(this@BitRateManager, createElementIfEmpty = true) ?: throw RuntimeException("Unable to serialize ${this@BitRateManager}")
      }
    val writer = StringWriter()
    JbXmlOutputter().output(element, writer)
    return writer.toString()
  }

  @TestOnly internal fun key(deviceProperties: DeviceProperties): String = deviceProperties.key()

  private fun DeviceProperties.key(): String =
    "${manufacturer ?: ""}|${model ?: ""}|${primaryAbi ?: ""}|${androidVersion?.featureLevel ?: 0}"

  override fun toString(): String = synchronized(bitRateTrackers) { "BitRateManager(bitRateTrackers=$bitRateTrackers)" }

  companion object {
    fun getInstance(): BitRateManager = service<BitRateManager>()
  }

  /** Candidate bit rates are kept in descending order. */
  @ConsistentCopyVisibility
  data class BitRateTracker
  private constructor(
    var bitRate: Int,
    var lastModifiedTime: Long,
    @XCollection(propertyElementName = "candidates", valueAttributeName = Constants.LIST) val candidates: MutableList<CandidateBitRate>,
  ) {

    constructor(candidate: CandidateBitRate) : this(0, 0L, mutableListOf(candidate))

    @Suppress("unused") // For deserialization
    private constructor() : this(0, 0L, mutableListOf<CandidateBitRate>())

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
          lastModifiedTime = System.currentTimeMillis()
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
          lastModifiedTime = System.currentTimeMillis()
        } else {
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

    fun isEmpty(): Boolean = bitRate == 0 && candidates.isEmpty()
  }

  data class CandidateBitRate(var bitRate: Int, var score: Int) {

    @Suppress("unused") // For deserialization
    private constructor() : this(0, 0)
  }
}
