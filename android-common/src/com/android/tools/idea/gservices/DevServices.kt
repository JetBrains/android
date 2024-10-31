/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gservices

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/**
 * Status of services that rely on backend APIs for the current build of
 * the IDE. Our SLA is to support features that rely on backend APIs for a
 * period of about a year past the release of a specific IDE version. Beyond
 * that timeline, users must upgrade to a newer IDE version to continue using
 * those features.
 *
 * See go/android-studio-developer-services-compat-policy and
 * go/as-kill-feature-past-deadline.
 *
 * @property enableServices Whether services should be enabled.
 * @property showGoingAwayNotice Whether a notice should be shown to users saying that
 *   the features may stop working soon.
 */
enum class DevServicesSupportStatus(val enableServices: Boolean, val showGoingAwayNotice: Boolean) {
  /** Developer services are still within the support window. */
  SUPPORTED(enableServices = true, showGoingAwayNotice = false),

  /** Developer Services are within the support window, but will expire soon.*/
  DEPRECATED(enableServices = true, showGoingAwayNotice = true),

  /** Developer Services are no longer supported. */
  UNSUPPORTED(enableServices = false, showGoingAwayNotice = true)
}

interface DevServicesCompatibilityProvider {
  fun currentSupportStatus(): DevServicesSupportStatus

  companion object {
    fun getInstance() = service<DevServicesCompatibilityProvider>()
  }
}

/** Provides [DevServicesSupportStatus] based on server flags. */
class FlagBasedDevServicesCompatibilityProvider(private val clock : Clock = Clock.systemUTC()) : DevServicesCompatibilityProvider {
  override fun currentSupportStatus(): DevServicesSupportStatus {
    val supportedFlag = StudioFlags.DEV_SERVICES_SUPPORTED_V1.get()
    val deprecatedFlag = StudioFlags.DEV_SERVICES_DEPRECATED_V1.get()

    // 2x the current proposed SLA of 1 year compat window
    val twoYearsAgo = LocalDateTime.now(clock).minusYears(2)
    val buildDate = ApplicationInfo.getInstance().buildDate.toLocalDateTime()
    val buildWayTooOld = buildDate.isBefore(twoYearsAgo)

    return when {
      buildWayTooOld || !supportedFlag -> DevServicesSupportStatus.UNSUPPORTED
      deprecatedFlag -> DevServicesSupportStatus.DEPRECATED
      else -> DevServicesSupportStatus.SUPPORTED
    }
  }
}

private fun Calendar.toLocalDateTime(): LocalDateTime {
  return LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())
}
