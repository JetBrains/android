/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.rendering.classloading.TestClassLoader
import com.android.tools.rendering.classloading.fromBinaryNameToPackageName
import com.android.tools.rendering.classloading.setupTestClassLoaderWithTransformation
import com.intellij.openapi.util.io.FileUtil
import java.awt.Toolkit
import java.awt.print.PrinterJob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Modifier
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.objectweb.asm.Type

private val testingDirectory = Files.createTempDirectory("renderSandbox")
private val allowedDirectory = Files.createDirectory(testingDirectory.resolve("allowed"))
private val disallowedDirectory = Files.createDirectory(testingDirectory.resolve("disallowed"))
private val allowedFile = Files.createFile(allowedDirectory.resolve("file"))
private val disallowedFile = Files.createFile(disallowedDirectory.resolve("file"))
private val testingDirectoryPath = testingDirectory.toAbsolutePath().toString()
private val allowedFilePath = allowedFile.toAbsolutePath().toString()
private val disallowedFilePath = disallowedFile.toAbsolutePath().toString()

/** Common methods to be ignored when intercepting. */
private val commonMethods = setOf("toString", "equals", "hashCode", "getClass", "notify", "notifyAll", "wait", "clone", "finalize")

private fun removeTestingDirPrefix(path: String): String =
  FileUtil.toSystemIndependentName(path).replace(FileUtil.toSystemIndependentName(testingDirectoryPath), "")

class MaliciousSerializable : java.io.Serializable {
  private fun readObject(stream: java.io.ObjectInputStream) {
    stream.defaultReadObject()
    System.exit(0)
  }
}

class TestSupplier : Supplier<String> {
  override fun get(): String = "test"
}

class TestRunnable : Runnable {
  override fun run() {}
}

interface ClassToCheck {
  fun checkFileRead1()

  fun checkFileRead2()

  fun checkFileRead3()

  fun checkFileRead4()

  fun checkFileRead5()

  fun checkReadText()

  fun checkReadTextWithCharset()

  fun checkReadBytes()

  fun checkWriteText()

  fun checkFileWrite1()

  fun checkFileWrite2()

  fun checkFileWrite3()

  fun checkFileWrite4()

  fun checkFileWrite5()

  fun checkTempCreation()

  fun checkSystemExit1()

  fun checkSystemExit2()

  fun checkSystemExit3()

  fun checkPropertyRead()

  fun checkPropertyWrite()

  fun checkListProperties()

  fun checkGetEnv1()

  fun checkGetEnv2()

  fun checkIoSet1()

  fun checkIoSet2()

  fun checkIoSet3()

  fun checkClassForName1()

  fun checkClassForName2()

  fun checkResourceLoading1()

  fun checkResourceLoading2()

  fun checkProcessExec1()

  fun checkProcessExec2()

  fun checkProcessExec3()

  fun checkLoadLibrary1()

  fun checkLoadLibrary2()

  fun checkRandomAccessFile()

  fun checkDatagramSocket()

  fun checkClassLoaderCreation()

  fun checkAllowlistedPropertyWrite()

  fun checkClipboard()

  fun checkEventQueue()

  fun checkPrintJob()

  fun checkFileChannelOpen(path: Path)

  fun checkZipFile()

  fun checkURLOpenStream()

  fun checkReflectionInvoke()

  fun checkUnsafe()

  fun tryDefineClass(): Class<*>

  fun tryInvokeMethodHandle()

  fun checkFindStatic()

  fun checkUnreflect()

  fun tryDeserialization(bytes: ByteArray)

  fun tryConnect()

  fun checkCompletableFuture()

  fun checkForkJoinPool()

  fun checkThreadPoolExecutor()
}

class ClassToCheckImpl : ClassToCheck {
  private val disallowedFile = File(disallowedFilePath)
  private val allowedFile = File(allowedFilePath)

  override fun checkFileRead1() {
    disallowedFile.isDirectory()
  }

  override fun checkFileRead2() {
    disallowedFile.isFile()
  }

  override fun checkFileRead3() {
    disallowedFile.length()
  }

  override fun checkFileRead4() {
    disallowedFile.list()
  }

  override fun checkFileRead5() {
    disallowedFile.parentFile
  }

  override fun checkReadText() {
    disallowedFile.readText()
  }

  override fun checkReadTextWithCharset() {
    disallowedFile.readText(java.nio.charset.Charset.defaultCharset())
  }

  override fun checkReadBytes() {
    disallowedFile.readBytes()
  }

  override fun checkWriteText() {
    disallowedFile.writeText("test")
  }

  override fun checkFileWrite1() {
    disallowedFile.mkdir()
  }

  override fun checkFileWrite2() {
    disallowedFile.mkdirs()
  }

  override fun checkFileWrite3() {
    disallowedFile.setLastModified(0)
  }

  override fun checkFileWrite4() {
    allowedFile.renameTo(disallowedFile)
  }

  override fun checkFileWrite5() {
    disallowedFile.createNewFile()
  }

  override fun checkTempCreation() {
    File.createTempFile("test", "a")
  }

  override fun checkSystemExit1() {
    System.exit(0)
  }

  override fun checkSystemExit2() {
    Runtime.getRuntime().exit(0)
  }

  override fun checkSystemExit3() {
    Runtime.getRuntime().halt(0)
  }

  override fun checkPropertyRead() {
    System.getProperty("property.test")
  }

  override fun checkPropertyWrite() {
    System.setProperty("property.test", "A")
  }

  override fun checkListProperties() {
    System.getProperties()
  }

  override fun checkGetEnv1() {
    System.getenv()
  }

  override fun checkGetEnv2() {
    System.getenv("env")
  }

  override fun checkIoSet1() {
    System.setIn(ByteArrayInputStream(ByteArray(1)))
  }

  override fun checkIoSet2() {
    System.setOut(PrintStream(ByteArrayOutputStream(1)))
  }

  override fun checkIoSet3() {
    System.setErr(PrintStream(ByteArrayOutputStream(1)))
  }

  override fun checkClassForName1() {
    Class.forName("java.lang.String")
  }

  override fun checkClassForName2() {
    Class.forName("java.lang.String", false, null)
  }

  override fun checkResourceLoading1() {
    this.javaClass.getResource("resource1")
  }

  override fun checkResourceLoading2() {
    this.javaClass.getResourceAsStream("resource2")
  }

  override fun checkProcessExec1() {
    @Suppress("DEPRECATION") Runtime.getRuntime().exec("ls")
  }

  override fun checkProcessExec2() {
    Runtime.getRuntime().exec(arrayOf("ls"))
  }

  override fun checkProcessExec3() {
    ProcessBuilder().command("ls").start()
  }

  override fun checkLoadLibrary1() {
    Runtime.getRuntime().load("library")
  }

  override fun checkLoadLibrary2() {
    Runtime.getRuntime().loadLibrary("library")
  }

  override fun checkRandomAccessFile() {
    java.io.RandomAccessFile(disallowedFilePath, "r")
  }

  override fun checkDatagramSocket() {
    java.net.DatagramSocket()
  }

  override fun checkClassLoaderCreation() {
    java.net.URLClassLoader(arrayOf())
  }

  override fun checkAllowlistedPropertyWrite() {
    System.setProperty("user.timezone", "GMT")
  }

  override fun checkClipboard() {
    Toolkit.getDefaultToolkit().systemClipboard
  }

  override fun checkEventQueue() {
    Toolkit.getDefaultToolkit().systemEventQueue
  }

  override fun checkPrintJob() {
    PrinterJob.getPrinterJob()
  }

  override fun checkFileChannelOpen(path: Path) {
    FileChannel.open(path)
  }

  override fun checkZipFile() {
    ZipFile(disallowedFilePath)
  }

  override fun checkURLOpenStream() {
    @Suppress("DEPRECATION") URL("http://localhost").openStream()
  }

  override fun checkReflectionInvoke() {
    val method = System::class.java.getMethod("exit", Int::class.javaPrimitiveType)
    method.invoke(null, 0)
  }

  override fun checkUnsafe() {
    sun.misc.Unsafe.getUnsafe()
  }

  override fun tryDefineClass(): Class<*> {
    val cw = org.objectweb.asm.ClassWriter(0)
    cw.visit(
      org.objectweb.asm.Opcodes.V1_7,
      org.objectweb.asm.Opcodes.ACC_PUBLIC + org.objectweb.asm.Opcodes.ACC_SUPER,
      "Malicious",
      null,
      "java/lang/Object",
      null,
    )
    val mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC + org.objectweb.asm.Opcodes.ACC_STATIC, "run", "()V", null, null)
    mv.visitCode()
    mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0)
    mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, "java/lang/System", "exit", "(I)V", false)
    mv.visitInsn(org.objectweb.asm.Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()
    cw.visitEnd()
    val bytes = cw.toByteArray()
    return java.lang.invoke.MethodHandles.lookup().defineClass(bytes)
  }

  override fun tryInvokeMethodHandle() {
    val lookup = java.lang.invoke.MethodHandles.lookup()
    val type = java.lang.invoke.MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType)
    val handle = lookup.findStatic(System::class.java, "exit", type)
    handle.invoke(0)
  }

  override fun checkFindStatic() {
    val lookup = java.lang.invoke.MethodHandles.lookup()
    val type = java.lang.invoke.MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType)
    lookup.findStatic(System::class.java, "exit", type)
  }

  override fun checkUnreflect() {
    val lookup = java.lang.invoke.MethodHandles.lookup()
    val method = System::class.java.getMethod("exit", Int::class.javaPrimitiveType)
    lookup.unreflect(method)
  }

  override fun tryDeserialization(bytes: ByteArray) {
    val ois = java.io.ObjectInputStream(java.io.ByteArrayInputStream(bytes))
    ois.readObject()
  }

  override fun tryConnect() {
    val socket = java.net.Socket()
    socket.connect(java.net.InetSocketAddress("localhost", 8080), 100)
  }

  // Suppressed because we want to test that the sandbox intercepts implicit executors.
  @Suppress("ImplicitExecutor")
  override fun checkCompletableFuture() {
    CompletableFuture.supplyAsync(TestSupplier())
  }

  // Suppressed because we want to test that the sandbox intercepts common ForkJoinPool usage.
  @Suppress("CommonForkJoinPool")
  override fun checkForkJoinPool() {
    ForkJoinPool.commonPool().submit(TestRunnable())
  }

  override fun checkThreadPoolExecutor() {
    ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
  }
}

class RenderSandboxTest {
  private lateinit var testClassLoader: TestClassLoader

  @Before
  fun setUp() {
    testClassLoader =
      setupTestClassLoaderWithTransformation(mapOf("Test" to ClassToCheckImpl::class.java)) { visitor ->
        RenderSandbox.getClassTransform(visitor)
      }
    RenderSandbox.setRenderSandbox(DenyAllRenderSandbox)
  }

  @After
  fun testDown() {
    RenderSandbox.setRenderSandbox(AllowAllRenderSandbox)
  }

  /** Verifies that the [block] throws a [SecurityException] with the given message. */
  private inline fun verifyThrowsSecurityException(expectedMessage: String, block: () -> Unit) {
    try {
      block()
      fail("Expected SecurityException('$expectedMessage')")
    } catch (e: SecurityException) {
      assertEquals(FileUtil.toSystemIndependentName(expectedMessage), removeTestingDirPrefix(e.message!!))
    }
  }

  private fun getInterceptedMethodsForClass(clazz: Class<*>) =
    clazz.methods
      .filter { it.name !in commonMethods }
      .map { Type.getType(clazz).internalName to it.name }
      .filter { (className, methodName) -> !RenderSandboxTransformTrampoline.shouldIntercept(className, methodName) }
      .map { (className, methodName) -> "$className#$methodName" }
      .toSet()
      .sorted()

  @Test
  fun `verify interceptor type`() {
    RenderSandboxTransformTrampoline.classesToIntercept
      .flatMap {
        val clazz = Class.forName(it.fromBinaryNameToPackageName())

        // Verify static intercepts
        clazz.methods
          .map { Type.getType(clazz).internalName to it }
          .filter { (className, method) -> RenderSandboxTransformTrampoline.shouldIntercept(className, method.name) }
      }
      .forEach { (className, method) ->
        val isStaticMethod = (method.modifiers and Modifier.STATIC) != 0
        if (isStaticMethod) {
          assertTrue(
            "$className#${method.name}  should have a STATIC interceptor but has a VIRTUAL interceptor",
            RenderSandboxTransformTrampoline.hasStaticIntercept(className, method.name),
          )
        } else {
          assertTrue(
            "$className#${method.name} should have a VIRTUAL interceptor but has a STATIC interceptor",
            RenderSandboxTransformTrampoline.hasInstanceIntercept(className, method.name),
          )
        }
      }
  }

  @Test
  fun `verify uncovered File method calls`() {
    assertEquals(
      """
      java/io/File#listRoots
      java/io/File#toURI
      java/io/File#toURL
      """
        .trimIndent(),
      getInterceptedMethodsForClass(File::class.java).joinToString("\n").trim(),
    )
  }

  @Test
  fun `verify uncovered Files method calls`() {
    assertTrue("All Files static methods should be intercepted", getInterceptedMethodsForClass(Files::class.java).isEmpty())
  }

  @Test
  fun `check file operations fail`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    verifyThrowsSecurityException("checkFileWrite ${System.getProperty("java.io.tmpdir")}") { methodIntercept.checkTempCreation() }

    // Read operations
    run {
      val readMsg = "checkFileRead ${removeTestingDirPrefix(disallowedFilePath)}"
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkFileRead1() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkFileRead2() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkFileRead3() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkFileRead4() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkFileRead5() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkReadText() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkReadTextWithCharset() }
      verifyThrowsSecurityException(readMsg) { methodIntercept.checkReadBytes() }
    }

    // Write operations
    run {
      val writeMsg = "checkFileWrite ${removeTestingDirPrefix(disallowedFilePath)}"
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkFileWrite1() }
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkFileWrite2() }
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkFileWrite3() }
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkFileWrite4() }
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkFileWrite5() }
      verifyThrowsSecurityException(writeMsg) { methodIntercept.checkWriteText() }
    }
  }

  @Test
  fun `check system operations fail`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    verifyThrowsSecurityException("checkSystemExit") { methodIntercept.checkSystemExit1() }
    verifyThrowsSecurityException("checkSystemExit") { methodIntercept.checkSystemExit2() }
    verifyThrowsSecurityException("checkSystemExit") { methodIntercept.checkSystemExit3() }

    // Property reads should succeed
    // Property reads should fail in DenyAll
    verifyThrowsSecurityException("checkPropertyRead property.test") { methodIntercept.checkPropertyRead() }

    // Property writes for properties in the deny list should fail
    verifyThrowsSecurityException("checkPropertyWrite property.test") { methodIntercept.checkPropertyWrite() }

    // Allowlisted property write should also fail in DenyAll
    verifyThrowsSecurityException("checkPropertyWrite user.timezone") { methodIntercept.checkAllowlistedPropertyWrite() }

    // Listing all properties should fail
    verifyThrowsSecurityException("checkPropertyAccess") { methodIntercept.checkListProperties() }

    verifyThrowsSecurityException("checkEnvAccess") { methodIntercept.checkGetEnv1() }
    verifyThrowsSecurityException("checkEnvAccess") { methodIntercept.checkGetEnv2() }

    verifyThrowsSecurityException("checkSystemIoSet") { methodIntercept.checkIoSet1() }
    verifyThrowsSecurityException("checkSystemIoSet") { methodIntercept.checkIoSet2() }
    verifyThrowsSecurityException("checkSystemIoSet") { methodIntercept.checkIoSet3() }
  }

  @Test
  fun `check classloading fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    verifyThrowsSecurityException("checkClassLoad java.lang.String") { methodIntercept.checkClassForName1() }
    verifyThrowsSecurityException("checkClassLoad java.lang.String") { methodIntercept.checkClassForName2() }
    verifyThrowsSecurityException("checkResourceLoad resource1") { methodIntercept.checkResourceLoading1() }
    verifyThrowsSecurityException("checkResourceLoad resource2") { methodIntercept.checkResourceLoading2() }
  }

  @Test
  fun `check command execution`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    verifyThrowsSecurityException("checkProcessExec") { methodIntercept.checkProcessExec1() }
    verifyThrowsSecurityException("checkProcessExec") { methodIntercept.checkProcessExec2() }
  }

  @Test
  fun `check load library`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    verifyThrowsSecurityException("checkLoadLibrary library") { methodIntercept.checkLoadLibrary1() }
    verifyThrowsSecurityException("checkLoadLibrary library") { methodIntercept.checkLoadLibrary2() }
  }

  @Test
  fun `check RandomAccessFile fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkFileRead ${removeTestingDirPrefix(disallowedFilePath)}") { methodIntercept.checkRandomAccessFile() }
  }

  @Test
  fun `check DatagramSocket fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConnection") { methodIntercept.checkDatagramSocket() }
  }

  @Test
  fun `check ClassLoader creation fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkCreateClassLoader") { methodIntercept.checkClassLoaderCreation() }
  }

  @Test
  fun `check multithreaded sandbox`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    // Set DenyAll in main thread
    RenderSandbox.setRenderSandbox(DenyAllRenderSandbox)

    var exceptionInThread: Throwable? = null
    val thread = Thread {
      try {
        // In another thread, it should also be DenyAll
        // We use a different check that doesn't trigger System.exit
        verifyThrowsSecurityException("checkPropertyWrite property.test") { methodIntercept.checkPropertyWrite() }
      } catch (t: Throwable) {
        exceptionInThread = t
      }
    }
    thread.start()
    thread.join()
    exceptionInThread?.let { throw it }

    // Change to AllowAll
    RenderSandbox.setRenderSandbox(AllowAllRenderSandbox)

    val thread2 = Thread {
      try {
        // Now it should be AllowAll
        methodIntercept.checkPropertyRead()
      } catch (t: Throwable) {
        exceptionInThread = t
      }
    }
    exceptionInThread = null
    thread2.start()
    thread2.join()
    exceptionInThread?.let { throw it }
  }

  @Test
  fun `check clipboard access fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkClipboard") { methodIntercept.checkClipboard() }
  }

  @Test
  fun `check event queue access fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkEventQueue") { methodIntercept.checkEventQueue() }
  }

  @Test
  fun `check print job access fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkPrintJob") { methodIntercept.checkPrintJob() }
  }

  @Test
  fun `check FileChannel open fails`() {
    val path = Paths.get(disallowedFilePath)
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkFileRead ${removeTestingDirPrefix(disallowedFilePath)}") {
      methodIntercept.checkFileChannelOpen(path)
    }
  }

  @Test
  fun `check ZipFile fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkFileRead ${removeTestingDirPrefix(disallowedFilePath)}") { methodIntercept.checkZipFile() }
  }

  @Test
  fun `check URL openStream fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConnection") { methodIntercept.checkURLOpenStream() }
  }

  @Test
  fun `check critical section disables sandbox`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    val baseSandbox = RenderSandbox.getRenderSandbox()
    val testSandbox = PreCheckRenderSandboxDelegate(DenyAllRenderSandbox, { RenderSecurityManager.sEnabled })

    RenderSandbox.setRenderSandbox(testSandbox)
    try {
      // By default sEnabled is true, so it should fail in DenyAll
      verifyThrowsSecurityException("checkPropertyRead property.test") { methodIntercept.checkPropertyRead() }

      // Enter safe region (disables sEnabled)
      val token = RenderSecurityManager.enterSafeRegion(null)
      try {
        // Now it should succeed because sEnabled is false!
        methodIntercept.checkPropertyRead()
      } finally {
        RenderSecurityManager.exitSafeRegion(token)
      }
    } finally {
      RenderSandbox.setRenderSandbox(baseSandbox)
    }
  }

  @Test
  fun `check reflection invoke fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("Reflection access to restricted method: java/lang/System#exit") {
      methodIntercept.checkReflectionInvoke()
    }
  }

  @Test
  fun `check Unsafe fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("Access to sun.misc.Unsafe is denied") { methodIntercept.checkUnsafe() }
  }

  @Test
  fun `check defineClass fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkDefineClass") { methodIntercept.tryDefineClass() }
  }

  @Test
  fun `check MethodHandle invoke fails or succeeds`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    try {
      methodIntercept.tryInvokeMethodHandle()
      fail("Should have failed to invoke MethodHandle for restricted method")
    } catch (e: SecurityException) {
      println("Sandbox blocked it: ${e.message}")
    }
  }

  @Test
  fun `check findStatic fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("Reflection access to restricted method: java/lang/System#exit") { methodIntercept.checkFindStatic() }
  }

  @Test
  fun `check unreflect fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("Reflection access to restricted method: java/lang/System#exit") { methodIntercept.checkUnreflect() }
  }

  @Test
  fun `check deserialization fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck

    val obj = MaliciousSerializable()
    val baos = java.io.ByteArrayOutputStream()
    val oos = java.io.ObjectOutputStream(baos)
    oos.writeObject(obj)
    val bytes = baos.toByteArray()

    try {
      methodIntercept.tryDeserialization(bytes)
      fail("Should have failed to deserialize malicious object")
    } catch (e: SecurityException) {
      assertEquals("Access to ObjectInputStream is denied", e.message)
    }
  }

  @Test
  fun `check socket connect fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConnection") { methodIntercept.tryConnect() }
  }

  @Test
  fun `check CompletableFuture fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConcurrency") { methodIntercept.checkCompletableFuture() }
  }

  @Test
  fun `check ForkJoinPool fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConcurrency") { methodIntercept.checkForkJoinPool() }
  }

  @Test
  fun `check ThreadPoolExecutor creation fails`() {
    val methodIntercept = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as ClassToCheck
    verifyThrowsSecurityException("checkConcurrency") { methodIntercept.checkThreadPoolExecutor() }
  }
}
