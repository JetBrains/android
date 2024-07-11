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

import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ClassVisitorUniqueIdProvider
import com.android.tools.rendering.classloading.MethodInterceptTransform
import java.net.Socket
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.jvm.javaMethod
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/** [ClassVisitor] that uses [MethodInterceptTransform] to intercept the relevant calls. */
private class RenderSandboxTransform(delegate: ClassVisitor) :
  ClassVisitor(
    Opcodes.ASM9,
    MethodInterceptTransform(
      delegate,
      RenderSandboxTransformTrampoline::invoke.javaMethod!!,
      RenderSandboxTransformTrampoline::invokeStatic.javaMethod!!,
      shouldInstrument = { _, _ -> true },
      shouldIntercept = RenderSandboxTransformTrampoline::shouldIntercept,
    ),
  ),
  ClassVisitorUniqueIdProvider {
  override val uniqueId: String = RenderSandboxTransform::class.qualifiedName!!
}

/**
 * An interface to be implemented to sandbox certain IO/System operations in the rendering pipeline. This is meant to be used in place of
 * the [RenderSecurityManager]. This [RenderSandbox] is not meant as a security measure but more as a barrier to avoid mistakes and stop
 * operations that could cause the Preview to misbehave.
 *
 * The [RenderSandbox] is used to intercept the following operations:
 * - File operations
 * - URI/URL operations
 * - Socket operations
 * - Path operations
 * - System operations
 * - Class loading
 * - Execution of commands
 *
 * The `check*` operations might throw a [SecurityException] if the operation is not allowed.
 */
interface RenderSandbox {
  /** Checks write operations to the given [absolutePath]. */
  fun checkFileRead(absolutePath: String)

  /** Checks write operations to the given [absolutePath]. */
  fun checkFileWrite(absolutePath: String)

  /** Guards class loading operations like [Class.forName] or [ClassLoader.loadClass] */
  fun checkClassLoad(classFqn: String)

  /** Guards the access to [System.exit]. */
  fun checkSystemExit()

  /** Guards the access to [System.getProperties], [System.setProperty] and [System.getProperty]. */
  fun checkPropertyAccess()

  /** Guards the access to [System.getenv]. */
  fun checkEnvAccess()

  /** Guards the access to [System.setIn], [System.setOut] and [System.setErr]. */
  fun checkSystemIoSet()

  /** Guards [Class.getResource] and [Class.getResourceAsStream]. */
  fun checkResourceLoad(resourceName: String)

  /**
   * Guards against API making remote connections like:
   * - [URL.openConnection] and [URL.getContent].
   * - [Socket.connect] and [Socket.bind]
   */
  fun checkConnection()

  /** Guards [Runtime.exec] and [ProcessBuilder.start]. */
  fun checkProcessExec()

  /** Guards [Runtime.load] and [Runtime.loadLibrary]. */
  fun checkLoadLibrary(library: String)

  /** Guards creation of custom class loaders. */
  fun checkCreateClassLoader()

  /** Guards access to the system clipboard. */
  fun checkClipboard()

  /** Guards access to the system event queue. */
  fun checkEventQueue()

  /** Guards access to print jobs. */
  fun checkPrintJob()

  /** Guards access to read individual system properties. */
  fun checkPropertyRead(propertyName: String)

  /** Guards access to write individual system properties. */
  fun checkPropertyWrite(propertyName: String)

  /** Guards reflective access to restricted methods. */
  fun checkReflectionInvoke(owner: String, name: String)

  /** Guards access to sun.misc.Unsafe. */
  fun checkUnsafeAccess()

  /** Guards dynamic class definition via MethodHandles.Lookup. */
  fun checkDefineClass()

  /** Guards ObjectInputStream deserialization. */
  fun checkObjectInputStream(ois: java.io.ObjectInputStream)

  companion object {
    /**
     * Currently active [RenderSandbox]. Optimized for runtime access using @Volatile to avoid lock overhead on every check. Updates are
     * infrequent and handled via [lock].
     */
    @Volatile private var instance: RenderSandbox = AllowAllRenderSandbox
    private val lock = ReentrantLock()

    /** Set the current active [RenderSandbox] and returns the previous one. */
    @VisibleForTesting
    @JvmStatic
    fun setRenderSandbox(renderSandbox: RenderSandbox): RenderSandbox {
      lock.withLock {
        val previous = instance
        instance = renderSandbox
        return previous
      }
    }

    /** Get the current active [RenderSandbox]. */
    @JvmStatic fun getRenderSandbox(): RenderSandbox = instance

    /** Runs the given [block] with the given sandbox. */
    @JvmStatic
    fun <T> computeWithSandbox(sandbox: RenderSandbox, block: () -> T): T {
      val previousSandbox = setRenderSandbox(sandbox)
      try {
        return block()
      } finally {
        setRenderSandbox(previousSandbox)
      }
    }

    /** Runs the given [block] with the given sandbox. */
    @JvmStatic fun runWithSandbox(sandbox: RenderSandbox, block: () -> Unit) = computeWithSandbox(sandbox, block)

    /** Runs the given [block] without a sandbox enabled. */
    @JvmStatic fun <T> computeWithoutSandbox(block: () -> T): T = computeWithSandbox(AllowAllRenderSandbox, block)

    /** Runs the given [block] without a sandbox enabled. */
    @JvmStatic
    fun runWithoutSandbox(block: () -> Unit) {
      runWithSandbox(AllowAllRenderSandbox, block)
    }

    /** Returns the [ClassVisitor] that applies the transformations required to call into the [RenderSandbox]. */
    @JvmStatic fun getClassTransform(delegate: ClassVisitor): ClassVisitor = RenderSandboxTransform(delegate)

    /**
     * Returns the [ClassTransform] that applies the transformations required to call into the [RenderSandbox]. This version is optimized
     * with a `shouldRewrite` predicate that performs a fast constant-pool scan to skip classes that don't need instrumentation.
     */
    @JvmStatic
    fun getClassTransform(): ClassTransform =
      ClassTransform(
        listOf(java.util.function.Function<ClassVisitor, ClassVisitor> { visitor -> RenderSandboxTransform(visitor) }),
        RenderSandboxTransformTrampoline::couldIntercept,
      )
  }
}

open class RenderSandboxDelegate(private val delegate: RenderSandbox) : RenderSandbox {
  override fun checkFileRead(absolutePath: String) = delegate.checkFileRead(absolutePath)

  override fun checkFileWrite(absolutePath: String) = delegate.checkFileWrite(absolutePath)

  override fun checkClassLoad(classFqn: String) = delegate.checkClassLoad(classFqn)

  override fun checkSystemExit() = delegate.checkSystemExit()

  override fun checkPropertyAccess() = delegate.checkPropertyAccess()

  override fun checkEnvAccess() = delegate.checkEnvAccess()

  override fun checkSystemIoSet() = delegate.checkSystemIoSet()

  override fun checkResourceLoad(resourceName: String) = delegate.checkResourceLoad(resourceName)

  override fun checkConnection() = delegate.checkConnection()

  override fun checkProcessExec() = delegate.checkProcessExec()

  override fun checkLoadLibrary(library: String) = delegate.checkLoadLibrary(library)

  override fun checkCreateClassLoader() = delegate.checkCreateClassLoader()

  override fun checkClipboard() = delegate.checkClipboard()

  override fun checkEventQueue() = delegate.checkEventQueue()

  override fun checkPrintJob() = delegate.checkPrintJob()

  override fun checkPropertyRead(propertyName: String) = delegate.checkPropertyRead(propertyName)

  override fun checkPropertyWrite(propertyName: String) = delegate.checkPropertyWrite(propertyName)

  override fun checkReflectionInvoke(owner: String, name: String) = delegate.checkReflectionInvoke(owner, name)

  override fun checkUnsafeAccess() = delegate.checkUnsafeAccess()

  override fun checkDefineClass() = delegate.checkDefineClass()

  override fun checkObjectInputStream(ois: java.io.ObjectInputStream) = delegate.checkObjectInputStream(ois)
}

/** A [RenderSandbox] implementation that denies everything by default. */
object DenyAllRenderSandbox : RenderSandbox {
  override fun checkFileWrite(absolutePath: String) {
    throw SecurityException("checkFileWrite $absolutePath")
  }

  override fun checkFileRead(absolutePath: String) {
    throw SecurityException("checkFileRead $absolutePath")
  }

  override fun checkClassLoad(classFqn: String) {
    throw SecurityException("checkClassLoad $classFqn")
  }

  override fun checkSystemExit() {
    throw SecurityException("checkSystemExit")
  }

  override fun checkPropertyAccess() {
    throw SecurityException("checkPropertyAccess")
  }

  override fun checkEnvAccess() {
    throw SecurityException("checkEnvAccess")
  }

  override fun checkSystemIoSet() {
    throw SecurityException("checkSystemIoSet")
  }

  override fun checkResourceLoad(resourceName: String) {
    throw SecurityException("checkResourceLoad $resourceName")
  }

  override fun checkConnection() {
    throw SecurityException("checkConnection")
  }

  override fun checkProcessExec() {
    throw SecurityException("checkProcessExec")
  }

  override fun checkLoadLibrary(library: String) {
    throw SecurityException("checkLoadLibrary $library")
  }

  override fun checkCreateClassLoader() {
    throw SecurityException("checkCreateClassLoader")
  }

  override fun checkClipboard() {
    throw SecurityException("checkClipboard")
  }

  override fun checkEventQueue() {
    throw SecurityException("checkEventQueue")
  }

  override fun checkPrintJob() {
    throw SecurityException("checkPrintJob")
  }

  override fun checkPropertyRead(propertyName: String) {
    throw SecurityException("checkPropertyRead $propertyName")
  }

  override fun checkPropertyWrite(propertyName: String) {
    throw SecurityException("checkPropertyWrite $propertyName")
  }

  override fun checkReflectionInvoke(owner: String, name: String) {
    // By default we simply block any reflection access to any "suspicious" methods.
    // In general it should be safe to do this since we do not expect reflection calls to things
    // like File.
    if (RenderSandboxTransformTrampoline.shouldIntercept(owner, name)) {
      throw SecurityException("Reflection access to restricted method: $owner#$name")
    }
  }

  override fun checkUnsafeAccess() {
    throw SecurityException("Access to sun.misc.Unsafe is denied")
  }

  override fun checkDefineClass() {
    throw SecurityException("checkDefineClass")
  }

  override fun checkObjectInputStream(ois: java.io.ObjectInputStream) {
    throw SecurityException("Access to ObjectInputStream is denied")
  }
}

/** A default [RenderSandbox] does not do anything. */
object AllowAllRenderSandbox : RenderSandbox {
  override fun checkFileRead(absolutePath: String) {}

  override fun checkFileWrite(absolutePath: String) {}

  override fun checkClassLoad(classFqn: String) {}

  override fun checkSystemExit() {}

  override fun checkPropertyAccess() {}

  override fun checkEnvAccess() {}

  override fun checkSystemIoSet() {}

  override fun checkResourceLoad(resourceName: String) {}

  override fun checkConnection() {}

  override fun checkProcessExec() {}

  override fun checkLoadLibrary(library: String) {}

  override fun checkCreateClassLoader() {}

  override fun checkClipboard() {}

  override fun checkEventQueue() {}

  override fun checkPrintJob() {}

  override fun checkPropertyRead(propertyName: String) {}

  override fun checkPropertyWrite(propertyName: String) {}

  override fun checkReflectionInvoke(owner: String, name: String) {}

  override fun checkUnsafeAccess() {}

  override fun checkDefineClass() {}

  override fun checkObjectInputStream(ois: java.io.ObjectInputStream) {}
}
