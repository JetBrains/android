/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.adtui.validation

import com.intellij.icons.AllIcons
import java.util.function.Function
import javax.swing.Icon

/**
 * A class which is used to validate some input.
 */
interface Validator<T> {
  /**
   * Returns [Result.OK] if the input is valid, or a result with some other [Severity] otherwise.
   */
  fun validate(value: T): Result

  /**
   * Indicates the severity of a validation violation. [Severity.OK] should be used if no violation has occurred.
   */
  enum class Severity(val icon: Icon?) {
    OK(null),
    INFO(AllIcons.General.BalloonInformation),
    WARNING(AllIcons.General.BalloonWarning),
    ERROR(AllIcons.General.BalloonError)
  }

  /**
   * The result of a call to [Validator.validate]. Test against [Result.OK] to see if the input is fine,
   * or otherwise call [Result.message] to get a readable error / warning string which can be displayed to the user.
   */
  data class Result @JvmOverloads constructor(val severity: Severity, val message: String, val detailedMessage: String? = null) {
    companion object {
      @JvmField
      val OK = Result(Severity.OK, "")

      /**
       * Returns an error result, if given an error message, or an OK result if given a null or an empty message.
       *
       * @param errorMessage an error message, or null or an empty string to produce an OK result
       */
      @JvmStatic
      fun fromNullableMessage(errorMessage: String?): Result =
        if (errorMessage.isNullOrEmpty()) OK else Result(Severity.ERROR, errorMessage)

      /**
       * Returns an error result for the given throwable.
       *
       * @param throwable a throwable to produce error validation result for
       */
      @JvmStatic
      fun fromThrowable(throwable: Throwable): Result =
        fromNullableMessage(throwable.message ?: "Error (${throwable.javaClass.simpleName})")
    }
  }
}

/**
 * Kotlin friendly helper function
 */
fun <T> createValidator(function: Function<T, Validator.Result>): Validator<T> = object : Validator<T> {
  override fun validate(value: T): Validator.Result = function.apply(value)
}
