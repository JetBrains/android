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
package com.android.tools.idea.run.deployment.liveedit.desugaring

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import java.util.Locale



// Port of non-exposed R8 class (see b/271499822 for more explanation):
// - com.android.tools.r8.utils.DescriptorUtils.guessTypeDescriptor
// - com.android.tools.r8.utils.ZipUtils.isClassFile

object R8Utils {
  private const val MODULE_INFO_CLASS = "module-info.class"
  private  const val CLASS_EXTENSION = ".class"
  private const val JAVA_PACKAGE_SEPARATOR = '.'

  internal fun guessTypeDescriptor(name: String): String {
  assert(name!!.endsWith(CLASS_EXTENSION)) { "Name $name must have $CLASS_EXTENSION suffix" }
  val descriptor = name.substring(0, name.length - CLASS_EXTENSION.length)
  if (descriptor.indexOf(JAVA_PACKAGE_SEPARATOR) != -1) {
    desugarFailure("Unexpected class file name: $name")
  }
  return "L$descriptor;"
}

internal fun isClassFile(entry: String): Boolean {
    val name = entry.lowercase(Locale.getDefault())
    if (name.endsWith(MODULE_INFO_CLASS)) {
      return false
    }
    return if (name.startsWith("meta-inf") || name.startsWith("/meta-inf")) {
      false
    }
    else name.endsWith(CLASS_EXTENSION)
  }
}