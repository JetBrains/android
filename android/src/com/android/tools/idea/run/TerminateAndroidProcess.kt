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
package com.android.tools.idea.run

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice


/**
 * Terminates a given [client] (android process). It uses [finishAndroidProcessCallback] if passed or default option - "force stop".
 *
 * If a new process with the same name have started, method doesn't kill it.
 */
@WorkerThread
fun terminateAndroidProcess(client: Client, finishAndroidProcessCallback: ((Client) -> Unit)?) {
  val processName: String = client.clientData.clientDescription ?: return
  val device: IDevice = client.device
  val currentClients = DeploymentApplicationService.instance.findClient(device, processName)
  if (currentClients.isNotEmpty() && currentClients.none { it.clientData.pid == client.clientData.pid }) {
    // a new process has been launched for the same package name, we aren't interested in killing this
    return
  }
  if (finishAndroidProcessCallback != null) {
    finishAndroidProcessCallback(client)
  }
  else {
    ApplicationTerminator(device, processName).killApp()
  }
}