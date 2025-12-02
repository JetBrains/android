/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.android.sdklib.AndroidVersion
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.DexIndexedConsumer
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.origin.Origin
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DexDebugFacility
import com.intellij.debugger.impl.JdiHelperClassLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.sun.jdi.ClassLoaderReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference

/*
 * Serves as a debug process wise cache of helper classes that were
 * loaded to a user VM by `AndroidJdiHelperClassLoader`.
 */
private class AndroidHelperClassCache {
  companion object {
    val ANDROID_HELPER_CLASS_CACHE_KEY =
      Key.create<AndroidHelperClassCache>("ANDROID_HELPER_CLASS_CACHE_KEY")
  }

  val classToJDIType = mutableMapOf<Class<*>, ClassType>()
}

/*
 * Implements the `JdiHelperClassLoader` extension point to
 * allow loading Intellij debugger helper classes on ART.
 */
class AndroidJdiHelperClassLoader : JdiHelperClassLoader {
  override fun isApplicable(evaluationContext: EvaluationContextImpl): Boolean {
    return DexDebugFacility.isDex(evaluationContext.virtualMachineProxy.virtualMachine)
  }

  override fun getHelperClass(
    cls: Class<*>,
    context: EvaluationContextImpl,
    vararg additionalClassesToLoad: String,
  ): ClassType? {
    val vmProxy = context.virtualMachineProxy
    val cache =
      vmProxy.getOrCreateUserData(AndroidHelperClassCache.ANDROID_HELPER_CLASS_CACHE_KEY) {
        AndroidHelperClassCache()
      }
    cache.classToJDIType[cls]?.let {
      return it
    }

    val inMemoryClassLoader = findOrLoadInMemoryClassLoaderSafe(context) ?: return null
    val loadedClass = loadClasses(cls, context, inMemoryClassLoader, *additionalClassesToLoad)
    if (loadedClass != null) {
      cache.classToJDIType[cls] = loadedClass
    }
    return loadedClass
  }
}

private fun loadClasses(
  cls: Class<*>,
  context: EvaluationContextImpl,
  inMemoryClassLoader: ClassType,
  vararg additionalClassesToLoad: String,
): ClassType? {
  val constructorMethod =
    inMemoryClassLoader.concreteMethodByName(
      JVMNameUtil.CONSTRUCTOR_NAME,
      "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V",
    ) ?: return null

  val classesBytes = getBytes(cls, *additionalClassesToLoad) ?: return null
  val dexBytes = dex(context, classesBytes) ?: return null
  val dexByteBuffer = wrapToByteBuffer(dexBytes, context)
  val classLoader = context.computeAndKeep {
    context.debugProcess.newInstance(
      context,
      inMemoryClassLoader,
      constructorMethod,
      listOf(dexByteBuffer, context.classLoader),
      0,
      true
    )
  } as? ClassLoaderReference ?: return null
  return context.debugProcess.findClass(context, cls.name, classLoader) as? ClassType
}

private fun getBytes(
  cls: Class<*>,
  vararg additionalClassesToLoad: String,
): Collection<ByteArray>? {
  val classesBytes =
    listOf(cls.name, *additionalClassesToLoad).map { name ->
      val resource = fullyQualifiedClassNameToBinaryName(name)
      cls.getResourceAsStream(resource)?.use { it.readBytes() } ?: return null
    }
  return classesBytes
}

private fun fullyQualifiedClassNameToBinaryName(name: String): String {
  return "/${name.replace('.', '/')}.class"
}

private fun dex(context: EvaluationContextImpl, classesBytes: Collection<ByteArray>): ByteArray? {
  try {
    val builder = D8Command.builder()
    val consumer =
      object : DexIndexedConsumer {
        var encodedBytes: ByteArray? = null

        override fun accept(
          fileIndex: Int,
          data: ByteArray,
          descriptors: Set<String>,
          handler: DiagnosticsHandler,
        ) {
          if (encodedBytes != null) {
            throw IllegalStateException("Only one dex file is supported")
          }
          encodedBytes = data
        }

        override fun finished(handler: DiagnosticsHandler) {}
      }

    for (bytes in classesBytes) {
      builder.addClassProgramData(bytes, Origin.unknown())
    }

    builder.mode = CompilationMode.RELEASE
    builder.programConsumer = consumer
    builder.minApiLevel =
      context.debugProcess.connectedDevice?.version?.androidApiLevel?.majorVersion
        ?: AndroidVersion.VersionCodes.O
    D8.run(builder.build())
    return consumer.encodedBytes
  } catch (_: Exception) {
    return null
  }
}

private fun findOrLoadInMemoryClassLoaderSafe(context: EvaluationContextImpl): ClassType? {
  return try {
    context.debugProcess.findClass(
      context,
      "dalvik.system.InMemoryDexClassLoader",
      context.classLoader,
    ) as? ClassType
  } catch (_: Exception) {
    null
  }
}

private fun wrapToByteBuffer(bytes: ByteArray, context: EvaluationContextImpl): ObjectReference? {
  val bytesMirror = DebuggerUtilsEx.mirrorOfByteArray(bytes, context)
  val debugProcess = context.debugProcess
  val byteBufferClass =
    debugProcess.findClass(context, "java.nio.ByteBuffer", context.classLoader) as? ClassType
      ?: return null
  val wrapMethod =
    byteBufferClass.concreteMethodByName("wrap", "([B)Ljava/nio/ByteBuffer;") ?: return null
  return context.computeAndKeep {
    debugProcess.invokeMethod(context, byteBufferClass, wrapMethod, listOf(bytesMirror), true) as? ObjectReference
  }
}
