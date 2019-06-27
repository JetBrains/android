/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.profilers.commands

import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Transport

class GcCommandHandler(val device: IDevice) : TransportProxy.ProxyCommandHandler {

  override fun execute(command: Commands.Command): Transport.ExecuteResponse {
    if (device.isOnline()) {
      for (client in device.getClients()) {
        if (command.pid == client.clientData.pid) {
          client.executeGarbageCollector()
          break
        }
      }
    }

    return Transport.ExecuteResponse.getDefaultInstance()
  }

}