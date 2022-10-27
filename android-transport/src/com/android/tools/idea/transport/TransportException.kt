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
package com.android.tools.idea.transport

abstract class TransportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Attempt to push file to device failed because the file doesn't exist.
 */
class TransportNonExistingFileException(message: String, val path: String) : TransportException(message)

/**
 * The daemon process could not be started on the device.
 */
class FailedToStartServerException(reason: String) : TransportException(reason)
