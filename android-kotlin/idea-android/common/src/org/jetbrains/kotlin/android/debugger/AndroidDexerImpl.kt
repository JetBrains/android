/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.debugger

import com.android.tools.r8.ByteDataView
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.origin.Origin
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.AndroidDexer
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.ClassToLoad

// Used to compile fragments for evaluate expression from class files to
// dex files. This only happens on Android O+ devices. Therefore, it is
// OK to hardcode min-api 26.
class AndroidDexerImpl(val project: Project) : AndroidDexer {

    private class DexConsumer : DexIndexedConsumer {
        var bytes: ByteArray? = null

        @Synchronized
        override fun accept(
          fileIndex: Int, data: ByteDataView, descriptors: Set<String>, handler: DiagnosticsHandler) {
            if (bytes != null) throw IllegalStateException("Multidex not supported")
            bytes = data.copyByteData()
        }

        override fun finished(handler: DiagnosticsHandler) {
        }
    }

    override fun dex(classes: Collection<ClassToLoad>): ByteArray? {
        try {
            val builder: D8Command.Builder = D8Command.builder()
            val consumer = DexConsumer()
            for ((_, _, bytes) in classes) {
                builder.addClassProgramData(bytes, Origin.unknown());
            }
            builder.mode = CompilationMode.DEBUG
            builder.programConsumer = consumer
            builder.minApiLevel = 26  // Android O+
            D8.run(builder.build())
            return consumer.bytes
        } catch (e: Exception) {
            return null
        }
    }
}