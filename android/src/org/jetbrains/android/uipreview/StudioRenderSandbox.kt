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
package org.jetbrains.android.uipreview

import com.android.tools.rendering.security.DenyAllRenderSandbox
import com.android.tools.rendering.security.RenderSandboxDelegate
import com.android.tools.rendering.security.RenderSandboxTransformTrampoline
import com.android.tools.rendering.security.isPropertyAccessAllowed
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class StudioRenderSandbox(val sdkPath: String?, val projectPath: String?, val appTempDir: String? = null) :
  RenderSandboxDelegate(DenyAllRenderSandbox) {

  private val tempDir = System.getProperty("java.io.tmpdir")
  private val normalizedTempDir = File(tempDir).path
  private val canonicalTempDir: String? =
    try {
      Paths.get(normalizedTempDir).normalize().toFile().canonicalPath
    } catch (e: IOException) {
      null
    }

  override fun checkPropertyAccess() {
    if (isPropertyAccessAllowed()) return

    super.checkPropertyAccess()
  }

  override fun checkPropertyRead(propertyName: String) {
    // RenderSecurityManager allowed reading individual properties by default.
  }

  override fun checkPropertyWrite(propertyName: String) {
    if (isPropertyWriteAllowed(propertyName)) return
    throw SecurityException("Write access not allowed during rendering ($propertyName)")
  }

  private fun isPropertyWriteAllowed(name: String): Boolean {
    // Linux sets this on fontmanager load; allow it since even if code points
    // to their own classes they don't get additional privileges, it's just like
    // using reflection
    if (name == "sun.font.fontmanager") return true

    // Toolkit initializations
    if (name.startsWith("sun.awt.") || name.startsWith("apple.awt.")) return true

    if (name == "user.timezone") return true

    return false
  }

  override fun checkReflectionInvoke(owner: String, name: String) {
    if (RenderSandboxTransformTrampoline.shouldIntercept(owner, name)) {
      throw SecurityException("Reflection access to restricted method: $owner#$name")
    }
  }

  override fun checkUnsafeAccess() {
    throw SecurityException("Access to sun.misc.Unsafe is denied")
  }

  override fun checkDefineClass() {
    throw SecurityException("Dynamic class definition is denied")
  }

  override fun checkObjectInputStream(ois: java.io.ObjectInputStream) {
    throw SecurityException("ObjectInputStream.readObject is denied")
  }

  override fun checkCreateClassLoader() {
    // Layoutlib makes heavy use of this, so we can't block it yet.
    // To fix this we should make a local class loader, passed to layoutlib, which
    // knows how to reset the security manager
  }

  override fun checkClassLoad(classFqn: String) {}

  private fun isTempDirPath(path: String): Boolean =
    path.startsWith(tempDir) ||
      path.startsWith(normalizedTempDir) ||
      appTempDir?.let { path.startsWith(it) } == true ||
      canonicalTempDir?.let { path.startsWith(it) || canonicalize(path).startsWith(it) } == true

  private fun canonicalize(path: String): String =
    try {
      Paths.get(path).normalize().toFile().canonicalPath
    } catch (e: IOException) {
      path // fallback
    }

  private fun isReadingAllowed(path: String): Boolean {
    val canonicalPath = canonicalize(path)

    // Allow reading files in the SDK install (fonts etc)
    if (sdkPath?.let { canonicalPath.startsWith(it) } == true) return true

    // Allowing reading resources in the project, such as icons
    if (projectPath?.let { canonicalPath.startsWith(it) } == true) return true

    // Needed by layoutlib's ResourceHelper.getColorStateList which calls isFile()
    // on values to see if it's a file or a color.
    if (path.startsWith("#") && !path.contains(File.separator)) return true

    // Needed by layoutlib's class loader to load classes.
    if (path.endsWith(".class") || path.endsWith(".jar")) return true

    // Allow reading files in temp
    if (isTempDirPath(canonicalPath)) return true

    val javaHome = System.getProperty("java.home")
    // Allow JDK to load its own classes
    if (canonicalPath.startsWith(javaHome)) return true
    if (javaHome.endsWith("/Contents/Home")) {
      // On Mac, Home lives two directory levels down from the real home, and we
      // sometimes need to read from sibling directories (e.g. ../Libraries/ etc)
      if (canonicalPath.regionMatches(0, javaHome, 0, javaHome.length - "Contents/Home".length)) {
        return true
      }
    }

    return false
  }

  override fun checkFileRead(absolutePath: String) {
    // This is meant as a barrier to avoid mistakes and stop operations that could cause the Preview to misbehave.
    // We allow reading from SDK and Project paths to allow rendering resources and loading classes.
    if (!isReadingAllowed(absolutePath)) {
      throw SecurityException("Read access not allowed during rendering ($absolutePath)")
    }
  }

  private fun isWritingAllowed(path: String): Boolean {
    val canonicalPath = canonicalize(path)
    return !Files.isSymbolicLink(Paths.get(canonicalPath)) && isTempDirPath(canonicalPath)
  }

  override fun checkFileWrite(absolutePath: String) {
    // This is meant as a barrier to avoid mistakes and stop operations that could cause the Preview to misbehave.
    // We allow writing to temp directories to allow custom views to create temporary files if needed.
    if (!isWritingAllowed(absolutePath)) {
      throw SecurityException("Write access not allowed during rendering ($absolutePath)")
    }
  }
}
