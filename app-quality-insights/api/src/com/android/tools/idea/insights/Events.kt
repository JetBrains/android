/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights

import java.time.Instant

/** The title & subtitle of each thread. */
data class Caption(
  val title: String = "",
  val subtitle: String = "",
)

/** Used to indicate that the analysis blames this Stacktrace and/or Frame. */
enum class Blames {
  UNKNOWN_BLAMED,
  BLAMED,
  NOT_BLAMED
}

/** Represents a Frame of the stack, as well as metadata & analysis results. */
data class Frame(
  // Line number of the code
  val line: Long = 0,

  // Filename of the code
  val file: String = "",

  // The hydrated symbol, or the raw symbol if it's unhydrateable
  val symbol: String = "",

  // Byte offset into the binary image which contains the code
  val offset: Long = 0,

  // Address in the binary image which contains the code
  val address: Long = 0,

  // Display name of the library
  val library: String = "",

  // Indicates whether analysis blames this frame as the cause of the crash
  // or error
  val blame: Blames = Blames.UNKNOWN_BLAMED
) {
  fun matches(qualifiedClassName: String, methodName: String): Boolean {
    return symbol.startsWith(qualifiedClassName) && symbol.contains(methodName)
  }

  fun matches(regex: Regex): Boolean {
    return symbol.matches(regex)
  }
}

/** The frames of the thread's stack, along with name & analysis results. */
data class Stacktrace(
  // The title & subtitle of this thread
  val caption: Caption = Caption(),

  // Used to indicate that the analysis blames this Stacktrace
  val blames: Blames = Blames.UNKNOWN_BLAMED,

  // The frames of the Stacktrace
  val frames: List<Frame> = listOf()
)

/** A representation of a single Exception and its stacktrace. */
data class ExceptionStack(
  // The stacktrace & its frames
  val stacktrace: Stacktrace = Stacktrace(),

  // Type of the exception, e.g. com.google.RpcException
  val type: String = "",

  // The error message included in the exception
  val exceptionMessage: String = "",
)

/**
 * A container for the errors / exceptions / stacktraces which describe a fatal crash or a non-fatal
 * error or exception. Logged errors have only the errors field set; fatals have both the exceptions
 * & thread_stack fields set.
 */
data class StacktraceGroup(
  // Nested exceptions are broken up & represented as peers in this list. See 'nested' field.
  val exceptions: List<ExceptionStack> = listOf()
)

/** Metadata about the device running an app with Crashlytics. */
data class Device(
  val manufacturer: String,
  val model: String,
  val displayName: String = "$manufacturer $model"
) : Comparable<Device> {
  companion object {
    val ALL = Device(manufacturer = "", model = "")
  }

  override fun compareTo(other: Device): Int =
    compareValuesBy(this, other, { it.model }, { it.manufacturer })
}

/** Describes a mobile device's OS. */
data class OperatingSystemInfo(
  val displayVersion: String,
  val displayName: String,
) {
  companion object {
    val ALL = OperatingSystemInfo(displayVersion = "", displayName = "")
  }
}

data class WithCount<T>(val count: Long, val value: T)

enum class PlayTrack(val displayName: String) : GroupAware<PlayTrack> {
  PRODUCTION("Production"),
  OPEN_TESTING("Open"),
  CLOSED_TESTING("Closed"),
  INTERNAL("Internal");

  override val groupName: String
    get() = displayName
}

/** Represents the Version of an App. */
data class Version(
  val buildVersion: String = "",
  val displayVersion: String = "",
  val displayName: String = "",
  val tracks: Set<PlayTrack> = emptySet()
) {
  companion object {
    val ALL = Version(buildVersion = "", displayVersion = "ALL", displayName = "ALL")
  }

  // TODO(vkryachko): remove equals and hashCode.
  override fun equals(other: Any?): Boolean {
    if (other !is Version) return false
    return other.buildVersion == buildVersion &&
      other.displayVersion == displayVersion &&
      other.displayName == displayName
  }

  override fun hashCode(): Int {
    var result = buildVersion.hashCode()
    result = 31 * result + displayVersion.hashCode()
    result = 31 * result + displayName.hashCode()
    return result
  }

  override fun toString(): String {
    if (this == ALL) {
      return "All app versions"
    }
    return displayVersion
  }
}

/** Event metadata captured at the time of the event, plus additional analysis. */
data class EventData(
  // Metadata about the device.
  val device: Device = Device("", ""),

  // Metadata about operating system.
  val operatingSystemInfo: OperatingSystemInfo = OperatingSystemInfo("", ""),

  // Time of the event occurrence
  val eventTime: Instant = Instant.EPOCH,
)

/** Representation of an App crash or logged error, having been processed by Crashlytics. */
data class Event(
  // Event metadata
  val eventData: EventData = EventData(),

  // Describes the crash or non-fatal error / exception, and potentially the
  // state of the other threads in the process at time of the Event.
  val stacktraceGroup: StacktraceGroup = StacktraceGroup()
) {
  companion object
}

/**
 * Provides a way to customize sorting of groups in the UI.
 *
 * The main use case is to be able to sort Play Tracks so that they appear in the order of
 * importance.
 */
interface GroupAware<T : GroupAware<T>> : Comparable<T> {
  val groupName: String

  object Empty : GroupAware<Empty> {
    override fun compareTo(other: Empty): Int = 0

    override val groupName: String = ""
  }
}
