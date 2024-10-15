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
package com.android.tools.idea.gradle.project.sync.issues

import com.google.wireless.android.sdk.stats.GradleFailureDetails.GradleExceptionInfo
import com.intellij.util.ReflectionUtil
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.PlaceholderExceptionSupport
import org.jetbrains.annotations.VisibleForTesting

class GradleExceptionAnalyticsSupport @VisibleForTesting constructor(val packagesAllowList: List<String>) {

  constructor() : this(defaultPackagesAllowList)

  companion object {
    private val defaultPackagesAllowList = listOf(
      "java.",
      "javax.",
      "jdk.",
      "com.sun.",
      "sun.",
      "kotlin.",
      "kotlinx.",
      //Gradle packages:
      "org.gradle.",
      "org.codehaus.groovy.",
      "groovy.",
      "groovyjarjar", // There are bunch of jarjar-ed exceptions in groovy package, e.g. groovyjarjarasm., groovyjarjarantlr4.
      // AGP, Android, Google packages
      "com.android.",
      "com.google.",
      "androidx.",
      //Used in AGP
      "io.grpc.",
      "io.netty.",
      "net.bytebuddy.asm.",
      // Jetbrains
      "org.jetbrains.",
      "com.intellij.",
      // used in Gradle
      "com.github.javaparser.",
      "org.apache.",
      "org.bouncycastle.",
      "org.w3c.dom.",
      "org.junit.",
      "org.xml.",
      // Known Gradle Plugins
      "butterknife.",
      "com.apollographql.apollo.",
      "com.autonomousapps.",
      "com.bugsnag.",
      "com.crashlytics.",
      "com.diffplug.",
      "com.github.triplet.gradle.",
      "com.hiya.",
      "com.jfrog.",
      "com.newrelic.",
      "com.onesignal.",
      "com.squareup.",
      "com.vanniktech.",
      "dagger.",
      "de.mannodermaus.",
      "de.undercouch.",
      "io.invertase.",
      "io.realm.",
      "io.sentry.",
      "org.jfrog.",
      "org.jlleitschuh.",
      "org.jmailen.",
      "org.koin.",
      "org.sonarqube.",
    )

    private const val hiddenNameReplacement = "<hidden>"
  }

  data class GradleException(val exceptionClass: String) {
    fun toAnalyticsMessage(): GradleExceptionInfo {
      return GradleExceptionInfo
        .newBuilder().setExceptionClassName(exceptionClass).build()
    }
  }
  data class GradleError(val exceptions: List<GradleException>) {
    fun toAnalyticsMessage(): com.google.wireless.android.sdk.stats.GradleFailureDetails.GradleErrorInfo {
      return com.google.wireless.android.sdk.stats.GradleFailureDetails.GradleErrorInfo
        .newBuilder().addAllExceptions(exceptions.map { it.toAnalyticsMessage() }).build()
    }
  }
  data class GradleFailureDetails(val errors: List<GradleError>) {
    fun toAnalyticsMessage(): com.google.wireless.android.sdk.stats.GradleFailureDetails {
      return com.google.wireless.android.sdk.stats.GradleFailureDetails
        .newBuilder().addAllErrors(errors.map { it.toAnalyticsMessage() }).build()
    }
  }

  fun extractFailureDetails(gradleError: Throwable): GradleFailureDetails {
    val errors = convertError(gradleError)
    return GradleFailureDetails(errors)
  }

  private fun convertError(e: Throwable): List<GradleError> {
    val exception = GradleException(clearClassName(getClassName(e)))
    val convertedCauses = getConvertedCauses(e)
    if (convertedCauses.isEmpty()) {
      return listOf(GradleError(listOf(exception)))
    }
    else {
      val errors = convertedCauses.map { errorCause ->
        GradleError(listOf(exception) + errorCause.exceptions)
      }
      return errors
    }
  }

  private fun getConvertedCauses(e: Throwable): List<GradleError> {
    return getCauses(e).flatMap { cause ->
      convertError(cause)
    }
  }

  private fun getCauses(e: Throwable): List<Throwable> {
    try {
      if (checkIsInstance(e, MultiCauseException::class.java)) {
        val multiCauses = ReflectionUtil.getMethod(e::class.java, MultiCauseException::getCauses.name)
          ?.invoke(e)
          ?.uncheckedCast<List<Throwable>>()
        if (multiCauses != null) return multiCauses
      }
    } catch (t: Exception) { /* Failed attempt, fallback to usual cause. */ }
    val cause = e.cause ?: return emptyList()
    return listOf(cause)
  }

  private fun checkIsInstance(e: Throwable, iClass: Class<*>): Boolean {
    val eClass = e::class.java
    if (iClass.isAssignableFrom(eClass)) return true
    // Try reload interface by name from the same classLoader as the tested class.
    try {
      val iClassReloaded = Class.forName(iClass.name, false, eClass.classLoader)
      return iClassReloaded.isAssignableFrom(eClass)
    } catch (t: Exception) {
      return false
    }
  }

  private fun getClassName(e: Throwable): String {
    try {
      if (checkIsInstance(e, PlaceholderExceptionSupport::class.java)) {
        val exceptionClassName = ReflectionUtil.getMethod(e::class.java, PlaceholderExceptionSupport::getExceptionClassName.name)
          ?.invoke(e)
          ?.uncheckedCast<String>()
        if (exceptionClassName != null) return exceptionClassName
      }
    } catch (t: Exception) { /* Failed attempt, fallback to usual name. */ }
    return e::class.java.name
  }

  private fun clearClassName(className: String): String {
    if (packagesAllowList.any { allowedPackagePrefix ->  className.startsWith(allowedPackagePrefix) }) {
      return className
    }
    return hiddenNameReplacement
  }
}