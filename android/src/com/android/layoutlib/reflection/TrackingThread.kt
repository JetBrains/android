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
package com.android.layoutlib.reflection

open class TrackingThread : Thread {
  constructor() : super()

  constructor(target: Runnable) : super(target)

  constructor(group: ThreadGroup, target: Runnable) : super(group, target)

  constructor(name: String) : super(name)

  constructor(group: ThreadGroup, name: String) : super(group, name)

  constructor(target: Runnable, name: String) : super(target, name)

  constructor(group: ThreadGroup, target: Runnable, name: String) : super(group, target, name)

  constructor(group: ThreadGroup, target: Runnable, name: String, stackSize: Long) : super(group, target, name, stackSize)

  constructor(group: ThreadGroup, target: Runnable, name: String, stackSize: Long, inheritThreadLocals: Boolean) :
    super(group, target, name, stackSize, inheritThreadLocals)
}