/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.rendering.security

/**
 * A [RenderSandbox] that only delegates the call to [delegate] if the [check] returns true.
 *
 * This can be used to selectively disable the sandbox depending on the thread or a setting value.
 */
class PreCheckRenderSandboxDelegate(private val delegate: RenderSandbox, private val check: () -> Boolean) : RenderSandbox {
  override fun checkFileRead(absolutePath: String) {
    if (!check()) return
    delegate.checkFileRead(absolutePath)
  }

  override fun checkFileWrite(absolutePath: String) {
    if (!check()) return
    delegate.checkFileWrite(absolutePath)
  }

  override fun checkClassLoad(classFqn: String) {
    if (!check()) return
    delegate.checkClassLoad(classFqn)
  }

  override fun checkSystemExit() {
    if (!check()) return
    delegate.checkSystemExit()
  }

  override fun checkPropertyAccess() {
    if (!check()) return
    delegate.checkPropertyAccess()
  }

  override fun checkEnvAccess() {
    if (!check()) return
    delegate.checkEnvAccess()
  }

  override fun checkSystemIoSet() {
    if (!check()) return
    delegate.checkSystemIoSet()
  }

  override fun checkResourceLoad(resourceName: String) {
    if (!check()) return
    delegate.checkResourceLoad(resourceName)
  }

  override fun checkConnection() {
    if (!check()) return
    delegate.checkConnection()
  }

  override fun checkProcessExec() {
    if (!check()) return
    delegate.checkProcessExec()
  }

  override fun checkLoadLibrary(library: String) {
    if (!check()) return
    delegate.checkLoadLibrary(library)
  }

  override fun checkCreateClassLoader() {
    if (!check()) return
    delegate.checkCreateClassLoader()
  }

  override fun checkClipboard() {
    if (!check()) return
    delegate.checkClipboard()
  }

  override fun checkEventQueue() {
    if (!check()) return
    delegate.checkEventQueue()
  }

  override fun checkPrintJob() {
    if (!check()) return
    delegate.checkPrintJob()
  }

  override fun checkPropertyRead(propertyName: String) {
    if (!check()) return
    delegate.checkPropertyRead(propertyName)
  }

  override fun checkPropertyWrite(propertyName: String) {
    if (!check()) return
    delegate.checkPropertyWrite(propertyName)
  }

  override fun checkReflectionInvoke(owner: String, name: String) {
    if (!check()) return
    delegate.checkReflectionInvoke(owner, name)
  }

  override fun checkUnsafeAccess() {
    if (!check()) return
    delegate.checkUnsafeAccess()
  }

  override fun checkDefineClass() {
    if (!check()) return
    delegate.checkDefineClass()
  }

  override fun checkObjectInputStream(ois: java.io.ObjectInputStream) {
    if (!check()) return
    delegate.checkObjectInputStream(ois)
  }
}
