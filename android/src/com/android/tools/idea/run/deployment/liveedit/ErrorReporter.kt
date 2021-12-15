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

import com.android.tools.deployer.tasks.LiveUpdateDeployer
import com.intellij.openapi.diagnostic.Logger

/**
 * Centralized place to handle errors reporting and metrics.
 */

private val log = Logger.getInstance(AndroidLiveEditDeployMonitor::class.java)

fun reportLiveEditError(exception: LiveEditUpdateException) {
  // TODO: Temp solution. These probably need to go somewhere when we have a UI.
  report("E: Live Edit " + errorMessage(exception))
}

fun errorMessage(exception: LiveEditUpdateException) : String {
  return "${exception.error.message}: \n ${exception.details} \n"
}

/**
 * Centralized place to handle errors reporting and metrics.
 */
fun reportDeployerError(error: LiveUpdateDeployer.UpdateLiveEditError) {
  // TODO: Temp solution. These probably need to go somewhere when we have a UI.
  report("E: Live Edit ${error.msg}\n")
}

fun reportDeployPerformance(metric: PerformanceTracker) {
  // These are the 3 that is most interesting to monitor.
  println("analysis = ${metric["analysis"]}")
  println("codegen = ${metric["codegen"]}")
  println("deploy = ${metric["deploy"]}")
}

private fun report(message: String) {
  print(message)
  log.warn(message)
}

