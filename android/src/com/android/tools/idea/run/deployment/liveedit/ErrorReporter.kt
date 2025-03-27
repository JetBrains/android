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
package com.android.tools.idea.run.deployment.liveedit

/**
 * Centralized place to construct user-friendly error message.
 */
public fun leErrorMessage(type: LiveEditUpdateException.Error, source : String?) = "${type.message} ${source?.let {" in ${it}"}}. Live Edit is temporarily paused until all build errors are fixed."

fun errorMessage(exception: LiveEditUpdateException) : String {
  val error = exception.error.message
  var details = exception.details
  if (!details.isEmpty()) {
    details = "\n${truncateDetail(details)}."
  }
  var inLocation = exception.sourceFilename?.let { " in $it" } ?: ""
  var cause = exception.cause?.let { "\n${it.stackTraceToString()}" } ?: ""
  return "$error$inLocation.$details$cause"
}

private fun String.findNthIndexOf(char: Char, nth: Int) : Int {
  var occurr = nth;

  var index = -1;
  while (occurr > 0) {
    val newIndex = this.indexOf(char, index + 1)
    if (newIndex == -1) {
      return index;
    } else {
      index = newIndex
    }
    occurr--;
  }
  return index
}

private fun truncateDetail(detail: String) : String{
  val index = detail.findNthIndexOf('\n', 20)
  return if (index == -1) {
    detail
  } else {
    detail.substring(0, index) + "\n..."
  }
}
