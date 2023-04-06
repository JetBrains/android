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
package com.android.tools.asdriver.tests

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class EmulatorRule : TestRule {
  lateinit var adb: Adb
    private set
  lateinit var emulator: Emulator
    private set

  override fun apply(base: Statement, description: Description): Statement {
    val androidSystem = AndroidSystem.basic()
    return androidSystem.apply(object : Statement() {
      override fun evaluate() {
        androidSystem.runAdb().use { adb ->
          this@EmulatorRule.adb = adb
          androidSystem.runEmulator().use { emulator ->
            this@EmulatorRule.emulator = emulator
            emulator.waitForBoot()
            adb.waitForDevice(emulator)
            base.evaluate()
          }
        }
      }
    }, description)
  }
}