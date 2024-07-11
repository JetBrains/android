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

import java.awt.Toolkit
import java.awt.print.PrinterJob
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.RandomAccessFile
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import org.jetbrains.annotations.TestOnly
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Type
import sun.misc.Unsafe

private val tmpDir = File(System.getProperty("java.io.tmpdir"))

private fun checkFileRead(absolutePath: String) = RenderSandbox.getRenderSandbox().checkFileRead(absolutePath)

private fun checkFileWrite(absolutePath: String) = RenderSandbox.getRenderSandbox().checkFileWrite(absolutePath)

private fun checkExit(): Unit = RenderSandbox.getRenderSandbox().checkSystemExit()

private fun checkPropertyAccess(): Unit = RenderSandbox.getRenderSandbox().checkPropertyAccess()

private fun checkEnvAccess(): Unit = RenderSandbox.getRenderSandbox().checkEnvAccess()

private fun checkSystemIoSet(): Unit = RenderSandbox.getRenderSandbox().checkSystemIoSet()

@Suppress("UNUSED_PARAMETER")
private fun checkPropertyRead(propertyName: String) = RenderSandbox.getRenderSandbox().checkPropertyRead(propertyName)

private fun checkPropertyWrite(propertyName: String) = RenderSandbox.getRenderSandbox().checkPropertyWrite(propertyName)

@Suppress("UNUSED_PARAMETER")
private fun checkCreateTemp(owner: String, args: Array<Any>?) {
  val parentTmpDir = args?.getOrElse(2) { tmpDir } as File
  checkFileWrite(parentTmpDir.absolutePath)
}

private fun checkConnection() {
  RenderSandbox.getRenderSandbox().checkConnection()
}

private fun checkProcessExec() {
  RenderSandbox.getRenderSandbox().checkProcessExec()
}

private fun checkLoadLibrary(library: String) {
  RenderSandbox.getRenderSandbox().checkLoadLibrary(library)
}

/** For `Files.createTemp*` */
@Suppress("UNUSED_PARAMETER")
private fun checkCreateTempFromFiles(owner: String, args: Array<Any>?) {
  val parentTmpDir = args?.getOrElse(0) { tmpDir.toPath() } as Path
  checkFileWrite(parentTmpDir.toString())
}

private fun checkClassLoad(classFqn: String) = RenderSandbox.getRenderSandbox().checkClassLoad(classFqn)

@Suppress("UNUSED_PARAMETER")
private fun checkForNameCalls(owner: String, args: Array<Any>?): Unit {
  // In Class.forName the class path can be in the first or second argument
  val className = args?.firstOrNull() as? String ?: args?.getOrNull(1) as? String ?: return
  RenderSandbox.getRenderSandbox().checkClassLoad(className)
}

@Suppress("UNUSED_PARAMETER")
private fun checkResourceLoad(owner: Class<*>, args: Array<Any>?): Unit {
  val resourceName = args?.firstOrNull() as? String ?: return
  RenderSandbox.getRenderSandbox().checkResourceLoad(resourceName)
}

private fun checkCreateClassLoader() = RenderSandbox.getRenderSandbox().checkCreateClassLoader()

private fun checkClipboard() = RenderSandbox.getRenderSandbox().checkClipboard()

private fun checkEventQueue() = RenderSandbox.getRenderSandbox().checkEventQueue()

private fun checkPrintJob() = RenderSandbox.getRenderSandbox().checkPrintJob()

private fun checkMethodInvoke(method: Method, args: Array<Any>?) {
  val owner = method.declaringClass.name.replace(".", "/")
  val name = method.name
  RenderSandbox.getRenderSandbox().checkReflectionInvoke(owner, name)
}

private fun checkUnsafeAccess() {
  RenderSandbox.getRenderSandbox().checkUnsafeAccess()
}

private fun checkFindStatic(owner: Any, args: Array<Any>?) {
  val refc = args?.getOrNull(0) as? Class<*> ?: return
  val name = args.getOrNull(1) as? String ?: return
  val classInternalName = refc.name.replace(".", "/")
  RenderSandbox.getRenderSandbox().checkReflectionInvoke(classInternalName, name)
}

private fun checkUnreflect(owner: Any, args: Array<Any>?) {
  val method = args?.getOrNull(0) as? Method ?: return
  val classInternalName = method.declaringClass.name.replace(".", "/")
  val name = method.name
  RenderSandbox.getRenderSandbox().checkReflectionInvoke(classInternalName, name)
}

private fun checkDefineClass() {
  RenderSandbox.getRenderSandbox().checkDefineClass()
}

private fun checkReadObject(owner: Any, args: Array<Any>?) {
  val ois = owner as? ObjectInputStream ?: return
  RenderSandbox.getRenderSandbox().checkObjectInputStream(ois)
}

@Suppress("UNUSED_PARAMETER")
private fun checkRandomAccessFileInit(owner: String, args: Array<Any>?) {
  val mode = args?.getOrNull(1) as? String ?: return
  val pathObj = args.getOrNull(0)
  val path =
    when (pathObj) {
      is File -> pathObj.absolutePath
      is String -> pathObj
      else -> return
    }
  if (mode.contains("w")) {
    checkFileWrite(path)
  } else {
    checkFileRead(path)
  }
}

@Suppress("UNUSED_PARAMETER")
private fun checkFileChannelOpen(owner: String, args: Array<Any>?) {
  val path = args?.getOrNull(0) as? Path ?: return
  val options = args.getOrNull(1)
  val isWrite =
    when (options) {
      is Array<*> -> options.any { it == StandardOpenOption.WRITE || it == StandardOpenOption.APPEND }
      is java.util.Set<*> -> options.any { it == StandardOpenOption.WRITE || it == StandardOpenOption.APPEND }
      else -> false
    }
  if (isWrite) {
    checkFileWrite(path.toString())
  } else {
    checkFileRead(path.toString())
  }
}

private fun checkFileInstance(call: (String) -> Unit): (File, Array<Any>?) -> Unit {
  return { file: File, args: Array<Any>? -> call(file.absolutePath) }
}

private fun checkPathInstance(call: (String) -> Unit): (Path, Array<Any>?) -> Unit {
  return { path: Path, _: Array<Any>? -> call(path.toString()) }
}

/** Calls [call] with the path given in the nth parameter of the call if it's a file. */
private fun <T> checkNthFileArgument(n: Int, call: (String) -> Unit): (T, Array<Any>?) -> Unit {
  return { _: T, args: Array<Any>? -> (args?.drop(n)?.firstOrNull() as? File)?.let { file -> call(file.absolutePath) } }
}

private fun <T> checkFirstFileArgument(call: (String) -> Unit): (T, Array<Any>?) -> Unit = checkNthFileArgument(0, call)

private fun checkFirstFileOrStringArgument(call: (String) -> Unit): (Any, Array<Any>?) -> Unit {
  return { _: Any, args: Array<Any>? ->
    when (val first = args?.firstOrNull()) {
      is File -> call(first.absolutePath)
      is String -> call(first)
    }
  }
}

private fun <T> checkNthPathArgument(@Suppress("SameParameterValue") n: Int, call: (String) -> Unit): (T, Array<Any>?) -> Unit {
  return { _: T, args: Array<Any>? -> (args?.drop(n)?.firstOrNull() as? Path)?.let { file -> call(file.toString()) } }
}

private fun <T> checkFirstPathArgument(call: (String) -> Unit): (T, Array<Any>?) -> Unit = checkNthPathArgument(0, call)

private fun checkSourceAndDestinationPaths(sourceCall: (String) -> Unit, destinationCall: (String) -> Unit): (String, Array<Any>?) -> Unit {
  return { _: String, args: Array<Any>? ->
    val source = args?.getOrNull(0)
    val destination = args?.getOrNull(1)

    source?.let { sourceCall(it.toString()) }
    destination?.let { destinationCall(it.toString()) }
  }
}

private fun <T> checkAllPathArguments(call: (String) -> Unit): (T, Array<Any>?) -> Unit {
  return { _: T, args: Array<Any>? -> args?.filterIsInstance<Path>()?.forEach { file -> call(file.toString()) } }
}

private fun <T> checkFirstStringArgument(call: (String) -> Unit): (T, Array<Any>?) -> Unit {
  return { _: T, args: Array<Any>? -> (args?.firstOrNull() as? String)?.let { string -> call(string) } }
}

private fun checkStaticPath(call: (String) -> Unit): (String, Array<Any>?) -> Unit {
  return { _, args -> (args?.firstOrNull() as? String)?.let { path -> call(path) } }
}

/** Calls [call] ignoring the arguments of the call. */
private fun <T> checkInstanceCallIgnoreArgs(call: () -> Unit): (T, Array<Any>?) -> Unit {
  return { _: T, _: Array<Any>? -> call() }
}

/** Calls [call] ignoring the arguments of the call. */
private fun checkStaticNoArgsCall(call: () -> Unit): (String, Array<Any>?) -> Unit {
  return { _: String, _: Array<Any>? -> call() }
}

/** Base class to define interceptors for certain operations in the render sandbox. */
internal sealed class Intercept {
  abstract val classInternalName: String
  abstract val methodName: String

  /**
   * [Intercept] representing a virtual call intercept. When it happens [intercept] will be called with the first parameter being the
   * instance of the class being invoked and the second the arguments of the call.
   */
  internal class VirtualIntercept(
    override val classInternalName: String,
    override val methodName: String,
    val intercept: (Any, Array<Any>?) -> Unit,
  ) : Intercept()

  /**
   * [Intercept] representing a static call intercept. When it happens [intercept] will be called with the first parameter being the
   * internal name of the class being invoked and the second the arguments of the call.
   */
  internal class StaticIntercept(
    override val classInternalName: String,
    override val methodName: String,
    val intercept: (String, Array<Any>?) -> Unit,
  ) : Intercept()

  companion object {
    /**
     * Creates an interceptor for the given instance [methodName]. When the [methodName] is invoked, [intercept] will be invoked giving the
     * opportunity to stop the operation. [intercept] will be called with the instance of the class being invoked and the arguments of the
     * call as arguments.
     */
    inline fun <reified T> instance(methodName: String, noinline intercept: (T, Array<Any>?) -> Unit): Intercept {
      return VirtualIntercept(Type.getInternalName(T::class.java), methodName) { type, args -> intercept(type as T, args) }
    }

    /**
     * Creates an interceptor for the given instance [method]. When the [method] is invoked, [intercept] will be invoked giving the
     * opportunity to stop the operation. [intercept] will be called with the instance of the class being invoked and the arguments of the
     * call as arguments.
     */
    inline fun <reified T> instance(method: KFunction<*>, noinline intercept: (T, Array<Any>?) -> Unit): Intercept =
      instance(method.name, intercept)

    /**
     * Creates an interceptor for the given static [methodName]. When the [methodName] is invoked, [intercept] will be invoked giving the
     * opportunity to stop the operation. [intercept] will be called with the name of the class being invoked and the arguments of the call
     * as arguments.
     */
    inline fun <reified T> static(methodName: String, noinline intercept: (String, Array<Any>?) -> Unit): Intercept {
      return StaticIntercept(Type.getInternalName(T::class.java), methodName) { owner, args -> intercept(owner, args) }
    }

    /**
     * Creates an interceptor for the given static [method]. When the [method] is invoked, [intercept] will be invoked giving the
     * opportunity to stop the operation. [intercept] will be called with the name of the class being invoked and the arguments of the call
     * as arguments.
     */
    inline fun <reified T> static(method: KFunction<*>, noinline intercept: (String, Array<Any>?) -> Unit): Intercept =
      static<T>(method.name, intercept)

    /**
     * Creates an interceptor for the given Kotlin static [method] (e.g. a top-level function). Uses reflection to extract the compiled Java
     * class name containing the method.
     */
    inline fun kotlinStatic(method: KFunction<*>, noinline intercept: (String, Array<Any>?) -> Unit): Array<Intercept> {
      val javaMethod = method.javaMethod ?: throw IllegalArgumentException("Method has no Java representation")
      val ownerClass = javaMethod.declaringClass
      var owner = Type.getInternalName(ownerClass)

      // Handle Kotlin multi-file facade parts (e.g., FilesKt__FileReadWriteKt -> FilesKt)
      val doubleUnderscoreIndex = owner.indexOf("__")
      if (doubleUnderscoreIndex != -1) {
        owner = owner.substring(0, doubleUnderscoreIndex)
      }

      val result = mutableListOf<Intercept>()
      result.add(StaticIntercept(owner, javaMethod.name, intercept))

      if (method.parameters.any { it.isOptional }) {
        result.add(StaticIntercept(owner, javaMethod.name + "\$default", intercept))
      }

      return result.toTypedArray()
    }
  }
}

/**
 * A key used to look up interceptors in the flattened map.
 *
 * This class is designed to be mutable so it can be reused via a [ThreadLocal] to avoid allocating a new key object on every lookup. This
 * reduces garbage collection pressure and improves performance for high-frequency intercepted calls.
 */
private class CallKey(var owner: String = "", var method: String = "") {
  fun set(o: String, m: String): CallKey {
    owner = o
    method = m
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CallKey) return false
    return owner == other.owner && method == other.method
  }

  override fun hashCode(): Int {
    return owner.hashCode() * 31 + method.hashCode()
  }
}

/**
 * Static class invoked from user code to check the number of allocations per render action. Every new render action will increment
 * `RenderAsyncActionExecutor#executedRenderActionCount` so this class can check if a new action has started executing.
 */
object RenderSandboxTransformTrampoline {
  /** Validates the input interceptors list to ensure that there are no duplicate entries that were added by accident. */
  private fun assertValidInterceptorListOf(vararg interceptors: Intercept): List<Intercept> {
    assert(interceptors.groupingBy { "${it.classInternalName}#${it.methodName}" }.eachCount().all { it.value == 1 }) {
      "Interceptors should only be defined once per class and method"
    }

    return interceptors.toList()
  }

  private fun forEachBlockRef(f: kotlin.reflect.KFunction2<File, (ByteArray, Int) -> Unit, Unit>) = f

  private val defaultInterceptors: List<Intercept> =
    assertValidInterceptorListOf(
      // File operations
      Intercept.instance<File>(File::createNewFile, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::canWrite, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::canRead, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::canExecute, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::isDirectory, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::isFile, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::isHidden, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::isAbsolute, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::lastModified, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::length, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::delete, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::deleteOnExit, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getName, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getAbsoluteFile, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getAbsolutePath, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getCanonicalFile, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getCanonicalPath, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getParent, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getPath, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::toPath, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::compareTo, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::exists, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>("list", checkFileInstance(::checkFileRead)),
      Intercept.instance<File>("listFiles", checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::mkdir, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::mkdirs, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::renameTo, checkFirstFileArgument(::checkFileWrite)),
      Intercept.instance<File>(File::getParentFile, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::setLastModified, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::setReadOnly, checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>("setWritable", checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>("setReadable", checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>("setExecutable", checkFileInstance(::checkFileWrite)),
      Intercept.instance<File>(File::getTotalSpace, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getFreeSpace, checkFileInstance(::checkFileRead)),
      Intercept.instance<File>(File::getUsableSpace, checkFileInstance(::checkFileRead)),
      Intercept.static<File>("createTempFile", ::checkCreateTemp),

      // Kotlin Stdlib operations
      *Intercept.kotlinStatic(File::readText, checkFirstFileArgument(::checkFileRead)),
      // Reads
      *Intercept.kotlinStatic(File::readBytes, checkFirstFileArgument(::checkFileRead)),
      *Intercept.kotlinStatic(forEachBlockRef(File::forEachBlock), checkFirstFileArgument(::checkFileRead)),
      *Intercept.kotlinStatic(File::forEachLine, checkFirstFileArgument(::checkFileRead)),
      *Intercept.kotlinStatic(File::readLines, checkFirstFileArgument(::checkFileRead)),
      // Writes
      *Intercept.kotlinStatic(File::writeBytes, checkFirstFileArgument(::checkFileWrite)),
      *Intercept.kotlinStatic(File::appendBytes, checkFirstFileArgument(::checkFileWrite)),
      *Intercept.kotlinStatic(File::writeText, checkFirstFileArgument(::checkFileWrite)),
      *Intercept.kotlinStatic(File::appendText, checkFirstFileArgument(::checkFileWrite)),
      // Utils.kt
      *Intercept.kotlinStatic(File::copyRecursively, checkSourceAndDestinationPaths(::checkFileRead, ::checkFileWrite)),
      *Intercept.kotlinStatic(File::deleteRecursively, checkFirstFileArgument(::checkFileWrite)),
      // FileTreeWalk.kt
      *Intercept.kotlinStatic(File::walk, checkFirstFileArgument(::checkFileRead)),
      *Intercept.kotlinStatic(File::walkTopDown, checkFirstFileArgument(::checkFileRead)),
      *Intercept.kotlinStatic(File::walkBottomUp, checkFirstFileArgument(::checkFileRead)),

      // FileInputStream/FileOutputStream constructors
      Intercept.static<FileInputStream>("<init>", checkFirstFileOrStringArgument(::checkFileRead)),
      Intercept.static<FileOutputStream>("<init>", checkFirstFileOrStringArgument(::checkFileWrite)),
      Intercept.static<RandomAccessFile>("<init>", ::checkRandomAccessFileInit),

      // URI/URL operations
      Intercept.instance<URL>("openConnection", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<URL>("getContent", checkInstanceCallIgnoreArgs(::checkConnection)),

      // Socket operations
      Intercept.instance<Socket>(Socket::bind, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<Socket>("connect", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<SSLSocket>(Socket::bind, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<SSLSocket>("connect", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<ServerSocket>(ServerSocket::accept, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<ServerSocket>(ServerSocket::getChannel, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<ServerSocket>("bind", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<SSLServerSocket>(ServerSocket::accept, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<SSLServerSocket>(ServerSocket::getChannel, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<SSLServerSocket>("bind", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpURLConnection>(HttpURLConnection::connect, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpURLConnection>("getContent", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpURLConnection>(HttpURLConnection::getInputStream, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpURLConnection>(HttpURLConnection::getErrorStream, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpsURLConnection>(HttpsURLConnection::connect, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpsURLConnection>("getContent", checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpsURLConnection>(HttpsURLConnection::getInputStream, checkInstanceCallIgnoreArgs(::checkConnection)),
      Intercept.instance<HttpsURLConnection>(HttpsURLConnection::getErrorStream, checkInstanceCallIgnoreArgs(::checkConnection)),

      // DatagramSocket operations
      Intercept.static<DatagramSocket>("<init>", checkStaticNoArgsCall(::checkConnection)),
      Intercept.static<MulticastSocket>("<init>", checkStaticNoArgsCall(::checkConnection)),

      // Path operations
      Intercept.static<Paths>("get", checkStaticPath(::checkFileRead)),
      Intercept.static<Files>(Files::newOutputStream, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("newBufferedReader", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::newInputStream, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("newBufferedWriter", checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("newByteChannel", checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("newDirectoryStream", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::createFile, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::createDirectory, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::createDirectories, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("createTempFile", ::checkCreateTempFromFiles),
      Intercept.static<Files>("createTempDirectory", ::checkCreateTempFromFiles),
      Intercept.static<Files>(Files::createLink, checkSourceAndDestinationPaths(::checkFileRead, ::checkFileWrite)),
      Intercept.static<Files>(Files::createSymbolicLink, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::delete, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::deleteIfExists, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::exists, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("copy", checkSourceAndDestinationPaths(::checkFileRead, ::checkFileWrite)),
      Intercept.static<Files>(Files::move, checkSourceAndDestinationPaths(::checkFileRead, ::checkFileWrite)),
      Intercept.static<Files>(Files::mismatch, checkAllPathArguments(::checkFileRead)),
      Intercept.static<Files>(Files::isHidden, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isReadable, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isWritable, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isExecutable, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isSameFile, checkAllPathArguments(::checkFileRead)),
      Intercept.static<Files>(Files::probeContentType, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("getFileAttributeView", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("readAttributes", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::getAttribute, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::setAttribute, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::getPosixFilePermissions, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::setPosixFilePermissions, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::getOwner, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::setOwner, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>(Files::isSymbolicLink, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isDirectory, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::isRegularFile, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::getLastModifiedTime, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::setLastModifiedTime, checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("lines", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::notExists, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::readAllBytes, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("readAllLines", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::size, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("write", checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("writeString", checkFirstPathArgument(::checkFileWrite)),
      Intercept.static<Files>("readString", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::find, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("walk", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>("walkFileTree", checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::list, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::readSymbolicLink, checkFirstPathArgument(::checkFileRead)),
      Intercept.static<Files>(Files::getFileStore, checkFirstPathArgument(::checkFileRead)),
      Intercept.instance<Path>(Path::toRealPath, checkPathInstance(::checkFileRead)),
      Intercept.instance<Path>(Path::getParent, checkPathInstance(::checkFileRead)),
      Intercept.instance<Path>(Path::toAbsolutePath, checkPathInstance(::checkFileRead)),

      // System operations
      Intercept.static<System>(System::exit, checkStaticNoArgsCall(::checkExit)),
      Intercept.static<System>("getProperty", checkFirstStringArgument(::checkPropertyRead)),
      Intercept.static<System>("setProperty", checkFirstStringArgument(::checkPropertyWrite)),
      Intercept.static<System>(System::clearProperty, checkFirstStringArgument(::checkPropertyWrite)),
      Intercept.static<System>(System::getProperties, checkStaticNoArgsCall(::checkPropertyAccess)),
      Intercept.static<System>(System::setProperties, checkStaticNoArgsCall(::checkPropertyAccess)),
      Intercept.static<System>("getenv", checkStaticNoArgsCall(::checkEnvAccess)),
      Intercept.static<System>(System::setIn, checkStaticNoArgsCall(::checkSystemIoSet)),
      Intercept.static<System>(System::setOut, checkStaticNoArgsCall(::checkSystemIoSet)),
      Intercept.static<System>(System::setErr, checkStaticNoArgsCall(::checkSystemIoSet)),

      // Class loading
      Intercept.instance<ClassLoader>(ClassLoader::loadClass, checkFirstStringArgument(::checkClassLoad)),
      Intercept.static<Class<*>>("forName", ::checkForNameCalls),
      Intercept.instance<Class<*>>(Class<*>::getResource, ::checkResourceLoad),
      Intercept.instance<Class<*>>(Class<*>::getResourceAsStream, ::checkResourceLoad),

      // ClassLoader creation
      Intercept.static<ClassLoader>("<init>", checkStaticNoArgsCall(::checkCreateClassLoader)),
      Intercept.static<java.net.URLClassLoader>("<init>", checkStaticNoArgsCall(::checkCreateClassLoader)),
      Intercept.static<java.security.SecureClassLoader>("<init>", checkStaticNoArgsCall(::checkCreateClassLoader)),

      // Process execution
      Intercept.instance<Runtime>(Runtime::exit, checkInstanceCallIgnoreArgs(::checkExit)),
      Intercept.instance<Runtime>(Runtime::halt, checkInstanceCallIgnoreArgs(::checkExit)),
      Intercept.instance<Runtime>("exec", checkInstanceCallIgnoreArgs(::checkProcessExec)),
      Intercept.instance<ProcessBuilder>(ProcessBuilder::start, checkInstanceCallIgnoreArgs(::checkProcessExec)),

      // Library loading
      Intercept.instance<Runtime>(Runtime::load, checkFirstStringArgument(::checkLoadLibrary)),
      Intercept.instance<Runtime>(Runtime::loadLibrary, checkFirstStringArgument(::checkLoadLibrary)),

      // AWT operations
      Intercept.instance<Toolkit>("getSystemClipboard", checkInstanceCallIgnoreArgs(::checkClipboard)),
      Intercept.instance<Toolkit>("getSystemEventQueue", checkInstanceCallIgnoreArgs(::checkEventQueue)),

      // Print operations
      Intercept.static<PrinterJob>("getPrinterJob", checkStaticNoArgsCall(::checkPrintJob)),

      // FileChannel
      Intercept.static<FileChannel>("open", ::checkFileChannelOpen),

      // ZipFile
      Intercept.static<ZipFile>("<init>", checkFirstFileOrStringArgument(::checkFileRead)),

      // URL.openStream
      Intercept.instance<URL>("openStream", checkInstanceCallIgnoreArgs(::checkConnection)),

      // Reflection
      Intercept.instance<Method>("invoke", ::checkMethodInvoke),

      // Unsafe
      Intercept.static<Unsafe>("getUnsafe", checkStaticNoArgsCall(::checkUnsafeAccess)),

      // MethodHandles.Lookup
      Intercept.instance<MethodHandles.Lookup>("findStatic", ::checkFindStatic),
      Intercept.instance<MethodHandles.Lookup>("unreflect", ::checkUnreflect),
      Intercept.instance<MethodHandles.Lookup>("defineClass", checkInstanceCallIgnoreArgs(::checkDefineClass)),

      // ObjectInputStream
      Intercept.instance<ObjectInputStream>("readObject", ::checkReadObject),
    )

  private val localKey = ThreadLocal.withInitial { CallKey() }

  /** Index by class name and method name to allow for quick lookup of interceptors. */
  private val interceptorIndex: Map<CallKey, Intercept> = defaultInterceptors.associateBy { CallKey(it.classInternalName, it.methodName) }

  private val ownerStrings: Set<String> = defaultInterceptors.map { it.classInternalName }.toSet()

  private val ownerBytes: List<ByteArray> = ownerStrings.map { it.toByteArray(Charsets.UTF_8) }

  @get:TestOnly
  val classesToIntercept: Set<String>
    get() = ownerStrings

  fun shouldIntercept(owner: String, method: String): Boolean = interceptorIndex.containsKey(localKey.get().set(owner, method))

  /**
   * Returns whether any of the methods called by the class defined in [classData] could be intercepted.
   *
   * This is an optimization to avoid expensive ASM transformations for classes that don't need them. It performs a fast scan of the class's
   * constant pool to see if it references any of the "interesting" owner classes tracked by the sandbox.
   *
   * This method is optimized to be zero-allocation by comparing UTF8 bytes directly in the constant pool instead of allocating String
   * objects for every class reference.
   */
  fun couldIntercept(classData: ByteArray): Boolean {
    val reader = ClassReader(classData)
    // Scan the constant pool for Class references
    for (i in 1 until reader.itemCount) {
      val offset = reader.getItem(i)
      if (offset > 0 && reader.readByte(offset - 1) == 7) { // CONSTANT_Class
        // Read 2 bytes for name index
        val b1 = reader.readByte(offset)
        val b2 = reader.readByte(offset + 1)
        val nameIndex = ((b1.toInt() and 0xFF) shl 8) or (b2.toInt() and 0xFF)

        val utf8Offset = reader.getItem(nameIndex)
        // Read 2 bytes for length
        val l1 = reader.readByte(utf8Offset)
        val l2 = reader.readByte(utf8Offset + 1)
        val len = ((l1.toInt() and 0xFF) shl 8) or (l2.toInt() and 0xFF)

        for (targetBytes in ownerBytes) {
          if (targetBytes.size == len && matchBytes(reader, utf8Offset + 2, targetBytes)) {
            return true
          }
        }
      }
    }
    return false
  }

  private fun matchBytes(reader: ClassReader, offset: Int, target: ByteArray): Boolean {
    for (i in target.indices) {
      if (reader.readByte(offset + i) != target[i].toInt()) return false
    }
    return true
  }

  @TestOnly
  fun hasStaticIntercept(owner: String, method: String): Boolean =
    interceptorIndex[localKey.get().set(owner, method)] is Intercept.StaticIntercept

  @TestOnly
  fun hasInstanceIntercept(owner: String, method: String): Boolean =
    interceptorIndex[localKey.get().set(owner, method)] is Intercept.VirtualIntercept

  @JvmStatic
  fun invoke(owner: Any, ownerClass: String, method: String, params: Array<Any>?): Unit {
    (interceptorIndex[localKey.get().set(ownerClass, method)] as? Intercept.VirtualIntercept)?.intercept?.invoke(owner, params)
  }

  @JvmStatic
  fun invokeStatic(ownerClass: String, method: String, params: Array<Any>?): Unit {
    (interceptorIndex[localKey.get().set(ownerClass, method)] as? Intercept.StaticIntercept)?.intercept?.invoke(ownerClass, params)
  }
}
