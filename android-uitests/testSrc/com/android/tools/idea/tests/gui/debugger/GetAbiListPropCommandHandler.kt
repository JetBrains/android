/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.debugger

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.shellcommandhandlers.GetPropCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.ShellCommandHandler
import com.google.common.base.Charsets
import java.io.IOException
import java.net.Socket

class GetAbiListPropCommandHandler(private val abiList: List<String>) : ShellCommandHandler() {
  init {
    if (abiList.isEmpty()) {
      throw IllegalArgumentException("abiList can not by an empty list")
    }
  }

  override fun invoke(
    fakeAdbServer: FakeAdbServer,
    responseSocket: Socket,
    device: DeviceState,
    args: String?
  ): Boolean {
    // Collect the base properties from the default getprop command handler:
    GetPropCommandHandler().invoke(fakeAdbServer, responseSocket, device, args)

    try {
      val response = responseSocket.getOutputStream()
      response.write("[ro.product.cpu.abilist]: [${abiList.joinToString(separator = ",")}]\n".toByteArray(Charsets.UTF_8))
    } catch (ignored: IOException) {
      // Unable to respond to client. Unable to do anything. Swallow exception and move on
    }
    return false
  }
}
