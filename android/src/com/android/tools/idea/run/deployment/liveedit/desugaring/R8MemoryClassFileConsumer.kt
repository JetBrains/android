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

import com.android.tools.idea.run.deployment.liveedit.LiveEditLogger
import com.android.tools.r8.ByteDataView
import com.android.tools.r8.ClassFileConsumer
import com.android.tools.r8.DiagnosticsHandler
import java.util.concurrent.ConcurrentHashMap
import com.android.tools.r8.references.Reference

class R8MemoryClassFileConsumer(private val logger: LiveEditLogger): ClassFileConsumer {
  private val inClasses : MutableMap<String, ByteArray> = ConcurrentHashMap<String, ByteArray>()
  val classes: Map<String, ByteArray> = inClasses

  override fun finished(handler: DiagnosticsHandler?) {
  }

  // Warning: This may be called from multiple threads. R8 defaults to running in an Executor
  // with one thread per core. This MUST be thread-safe.
  override fun accept(data : ByteDataView, desc : String, handler :  DiagnosticsHandler) {
    // We don't use 'L' prefix and ';' suffix in LE so we need to remove them if they are here.
    var binaryName = Reference.classFromDescriptor(desc).binaryName

    // Data view is only valid during the extent of the accept callback. We must copy it
    inClasses[binaryName] = data.copyByteData()
  }
}