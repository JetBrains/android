/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.emulator.control.KeyboardEvent
import com.android.tools.idea.protobuf.Empty
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm

/**
 * Simulates pressing the Power button on an Android virtual device.
 */
class EmulatorPowerAction : AbstractEmulatorAction() {
  var inProgress = false

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    // TODO: Standalone Emulator sends KeyEventType.keydown on MOUSE_PRESSED and KeyEventType.keyup on MOUSE_RELEASED.
    //       Implement this behavior by extending a CustomComponentAction.
    // For now we send keydown and keyup events with PRESS_DURATION_MILLIS delay in between to simulate a long button press.
    if (!inProgress) {
      emulatorController.sendKey(createHardwareKeyEvent(KEY_NAME, eventType = KeyboardEvent.KeyEventType.keydown),
                                 KeyDownObserver(emulatorController))
      inProgress = true
    }
  }


  private inner class KeyDownObserver(val emulatorController: EmulatorController) : DummyStreamObserver<Empty>() {

    override fun onNext(response: Empty) {
      val disposable = Disposable { inProgress = false }
      Disposer.register(emulatorController, disposable)
      val alarm = Alarm(disposable)
      val runnable = {
        // This block of code is executed on the UI thread.
        emulatorController.sendKey(createHardwareKeyEvent(KEY_NAME, eventType = KeyboardEvent.KeyEventType.keyup))
        Disposer.dispose(disposable)
      }
      alarm.addRequest(runnable, PRESS_DURATION_MILLIS, ModalityState.any())
    }

    override fun onError(t: Throwable) {
      ApplicationManager.getApplication().invokeLater({ inProgress = false }, ModalityState.any())
    }
  }

  companion object {
    private const val KEY_NAME = "Power"
    private const val PRESS_DURATION_MILLIS = 800L
  }
}