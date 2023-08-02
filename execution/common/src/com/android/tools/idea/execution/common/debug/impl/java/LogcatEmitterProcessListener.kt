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
package com.android.tools.idea.execution.common.debug.impl.java

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.sun.jdi.ClassType

private const val ANDROID_LOG_CLASS_NAME = "android.util.Log"
private const val ANDROID_LOG_METHOD_NAME = "i"
private const val ANDROID_LOG_METHOD_SIGNATURE = "(Ljava/lang/String;Ljava/lang/String;)I"
private const val ANDROID_LOG_TAG = "Debugger"

/**
 * A [ProcessListener] that emits available text directly to the devices Logcat by invoking `android.util.Log.i()` in the process virtual
 * machine.
 *
 * The purpose of this class is to emit text logged by breakpoints to the device Logcat. Normally, this text is only emitted to the debug
 * console. The text is emitted using [ProcessListener.onTextAvailable], so we intercept this callback and try to invoke
 * `android.util.Log.i()` on the process virtual machine.
 *
 * Note that [ProcessListener.onTextAvailable] is called from other places as well. Most notably, it's called to log the
 * `Connected to the target VM` and `Disconnected from the target VM` but there are other cases too.
 *
 * We make a best-effort attempt to emit the text to the devices Logcat. If we can't get an `EvaluationContext` from the process, we abort
 * silently. This can happen when `onTextAvailable` is called while the process is not in the proper state, for example, the
 * `connect/disconnect` messages above will not get logged because there won't be `context`.
 *
 * If we get any kind of exception while attempting to invoke the logger on the process VM, we log the exception at `DEBUG` level. There's
 * no need to log at a higher level since this can happen under normal operation, for example, the process terminates while we are in the
 * middle of this function.
 *
 * Note that this only works for Java/Kotlin debugging. Native (JNI) also have logging breakpoints but use a different mechanism to emit
 * them to the console.
 */
internal class LogcatEmitterProcessListener(private val process: DebugProcessImpl) : ProcessListener {
  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    try {
      val eventContext = process.suspendManager.eventContexts.firstOrNull() ?: return
      val vm = process.virtualMachineProxy
      val logClass = vm.classesByName(ANDROID_LOG_CLASS_NAME).first() as ClassType
      val logMethod = logClass.methodsByName(ANDROID_LOG_METHOD_NAME, ANDROID_LOG_METHOD_SIGNATURE).first()

      val evaluationContext = EvaluationContextImpl(eventContext, eventContext.frameProxy)
      process.invokeMethod(evaluationContext, logClass, logMethod, listOf(vm.mirrorOf(ANDROID_LOG_TAG), vm.mirrorOf(event.text)))

    } catch (e: Exception) {
      thisLogger().debug(e) { "Failed to emit text to process Logcat: '${event.text}'" }
    }
  }
}
