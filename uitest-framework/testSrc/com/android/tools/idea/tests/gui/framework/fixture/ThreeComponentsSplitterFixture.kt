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
package com.android.tools.idea.tests.gui.framework.fixture

import com.intellij.openapi.ui.ThreeComponentsSplitter
import org.fest.swing.core.Robot

/**
 * Fixture for a [ThreeComponentsSplitter].
 */
class ThreeComponentsSplitterFixture(
  robot: Robot,
  target: ThreeComponentsSplitter
) : JComponentFixture<ThreeComponentsSplitterFixture, ThreeComponentsSplitter>(ThreeComponentsSplitterFixture::class.java, robot, target) {

  var firstSize: Int
    get() = target().firstSize
    set(value) { target().firstSize = value }

  var lastSize: Int
    get() = target().lastSize
    set(value) { target().lastSize = value }
}
