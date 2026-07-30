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
package com.android.tools.idea.actions.internal

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.utils.cxx.io.hasExtensionIgnoreCase
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.lang.UrlClassLoader
import java.net.URI
import java.net.URL
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.collections.map
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/** Test whether any plugins have multiple instances of the same class (by name) in their classloader hierarchy at runtime. */
@Suppress("UnstableApiUsage")
class TestDuplicateClassesAction : DumbAwareAction("Test Duplicate Classes") {

  // Plugin name to set of packages to ignore
  // TODO (476432139): move this into a separate file
  val exceptions =
    mapOf(
      "org.jetbrains.android" to
        setOf(
          "com.android.annotations.concurrency",
          "com.android.annotations",
          "com.android.tools.instrumentation.threading.agent.callback",
          "javax.inject",
          "org.objectweb.asm.commons",
          "org.objectweb.asm.signature",
          "org.objectweb.asm.tree.analysis",
          "org.objectweb.asm.tree",
          "org.objectweb.asm",
          "org.xmlpull.v1",
        ),
      "com.android.tools.design" to setOf("org.json", "com.google.errorprone.annotations", "android.annotation"),
      "com.google.tools.ij.aiplugin" to
        setOf(
          "androidx.annotation",
          "kotlinx.atomicfu.locks",
          "kotlinx.atomicfu",
          "io.github.oshai.kotlinlogging",
          "io.github.oshai.kotlinlogging.coroutines",
          "io.github.oshai.kotlinlogging.internal",
          "io.github.oshai.kotlinlogging.slf4j",
          "io.github.oshai.kotlinlogging.slf4j.internal",
          "io.github.oshai.kotlinlogging.jul.internal",
        ),
    )

  override fun actionPerformed(e: AnActionEvent) {
    executeOnPooledThread {
      try {
        if (PluginManager.getLoadedPlugins().none { checkPlugin(it) }) {
          thisLogger().warn("No duplicate classes found!")
        }
        thisLogger().warn("duplicate classes scan done")
      } finally {
        classCache.clear()
      }
    }
  }

  private fun checkPlugin(plugin: IdeaPluginDescriptor): Boolean {
    // We're only interested in plugins we're providing.
    if (!isGooglePlugin(plugin.pluginId)) return false

    // First look for duplicates within the current plugin itself.
    val myClasses = getAllClasses(plugin.classLoader)
    val internalDuplicates =
      myClasses
        .filter { it.first.toPackageName() !in (exceptions[plugin.pluginId.idString] ?: setOf()) }
        .groupBy { it.first }
        .filterValues { it.size > 1 }
        .map { (c, jars) -> "$c duplicated in ${jars.map { it.second } }" }
        .joinToString("\n")

    // At this point we know there aren't duplicates, so we can create a map of class file to jar
    val myClassMap = myClasses.map { it.first to it.second }.toMap()

    // class name to jar for conflicts found below
    val conflictsWithParents = mutableMapOf<String, MutableSet<JarReference>>()
    val conflictsBetweenParents = mutableMapOf<String, MutableSet<JarReference>>()

    // Classes from the core classloader are always going to be present for all dependant plugins,
    // so just load them once at the start.
    val coreClassLoader =
      (plugin.classLoader as PluginClassLoader).getAllParentsClassLoaders().filterNot { it is PluginClassLoader }.first()
    val allParentClasses =
      getAllClasses(coreClassLoader).associate { (c, j, cl) -> c to JarReference(PluginManagerCore.CORE_ID, j, cl) }.toMutableMap()

    // Check whether any classes in dependant plugins conflict with classes in this plugin or each
    // other.
    plugin.dependencies
      .mapNotNull { PluginManager.getInstance().findEnabledPlugin(it.pluginId) }
      .toSet()
      .forEach { parentPlugin ->
        val parentClasses =
          getAllClasses(parentPlugin).filter {
            // Exclude exceptions for this plugin
            it.key.toPackageName() !in (exceptions[plugin.pluginId.idString] ?: setOf())
          }
        // Check for conflicts between classes from this plugin and dependant plugins
        parentClasses.keys.intersect(myClassMap.keys).forEach { c ->
          conflictsWithParents
            .getOrPut(c) { mutableSetOf(JarReference(plugin.pluginId, myClassMap[c]!!, plugin.classLoader)) }
            .add(parentClasses[c]!!)
        }
        // Check for conflicts between classes in different dependant plugins
        parentClasses.keys.intersect(allParentClasses.keys).forEach { c ->
          val allParentsJar = allParentClasses[c]!!
          val directParentJar = parentClasses[c]!!
          if (
            directParentJar.jar != allParentsJar.jar &&
              c.toPackageName() !in
                (
                // We also exclude exception classes based on the dependant plugin
                (exceptions[(directParentJar.classLoader as PluginClassLoader).pluginId.idString] ?: setOf()) +
                  (exceptions[(allParentsJar.classLoader as? PluginClassLoader)?.pluginId?.idString ?: "com.intellij"] ?: setOf()))
          ) {
            if (isGooglePlugin(allParentsJar.plugin) || isGooglePlugin(directParentJar.plugin)) {
              conflictsBetweenParents.getOrPut(c) { mutableSetOf(allParentsJar) }.add(directParentJar)
            }
          }
        }
        allParentClasses.putAll(parentClasses)
      }

    if (internalDuplicates.isNotEmpty() || conflictsWithParents.isNotEmpty() || conflictsBetweenParents.isNotEmpty()) {
      thisLogger().warn("Duplicate classes found in ${plugin.name} (${plugin.pluginId.idString}):")
      if (internalDuplicates.isNotEmpty()) {
        thisLogger().warn("Duplicates within the plugin:\n$internalDuplicates")
      }
      if (conflictsWithParents.isNotEmpty()) {
        thisLogger()
          .warn(
            "Conflicts between plugin libs and parent plugins:\n" +
              conflictsWithParents.entries.take(10).joinToString("\n") { (c, j) -> "$c duplicated in $j" }
          )
        if (conflictsWithParents.entries.size > 10) {
          thisLogger().warn("and ${conflictsWithParents.entries.size - 10} more")
        }
      }
      if (conflictsBetweenParents.isNotEmpty()) {
        thisLogger()
          .warn(
            "Conflicts between parent plugins:\n" +
              conflictsBetweenParents.entries.take(10).joinToString("\n") { (c, j) -> "$c duplicated in $j" }
          )
        if (conflictsBetweenParents.entries.size > 10) {
          thisLogger().warn("and ${conflictsBetweenParents.entries.size - 10} more")
        }
        println(conflictsBetweenParents.map { it.key.toPackageName() }.toSet())
      }
      return true
    }
    return false
  }

  private fun isGooglePlugin(pluginId: PluginId): Boolean {
    val descriptor = PluginManager.getInstance().findEnabledPlugin(pluginId) ?: return false
    return "Google" in (descriptor.vendor ?: "")
  }

  private val classCache = mutableMapOf<URI, List<String>>()

  /** Get a list of all class filenames (like /foo/bar/MyClass.class) present in jar referenced by the given URL. */
  private fun getAllClasses(url: URL): List<String> =
    classCache.getOrPut(url.toURI()) {
      val jarFile = Paths.get(url.toURI())
      if (url.protocol == "file" && url.file.hasExtensionIgnoreCase("jar") && Files.exists(jarFile)) {
        FileSystems.newFileSystem(jarFile).use { fileSystem: FileSystem ->
          fileSystem.rootDirectories.flatMap { root ->
            Files.walk(root).use { paths ->
              paths
                .filter { it.extension == "class" && it.fileName.nameWithoutExtension !in setOf("module-info", "package-info") }
                .map { it.toString() }
                .toList()
            }
          }
        }
      } else emptyList()
    }

  private data class JarReference(val plugin: PluginId, val jar: URL, val classLoader: ClassLoader)

  // returns class file path to jar url. Classes can be duplicated.
  private fun <T : ClassLoader> getAllClasses(classLoader: T): List<Triple<String, URL, T>> =
    (classLoader as? UrlClassLoader)?.urls?.flatMap { url -> getAllClasses(url).map { Triple(it, url, classLoader) } } ?: listOf()

  // Map if class file path to jar reference. Note only one jar per class will be reported, and the
  // core classloader is excluded.
  private fun getAllClasses(plugin: IdeaPluginDescriptor): Map<String, JarReference> =
    (plugin.classLoader as? PluginClassLoader)?.let { cl ->
      (listOf(cl) + cl.getAllParentsClassLoaders())
        .filterIsInstance<PluginClassLoader>()
        .flatMap { getAllClasses(it) }
        .associate { (c, j, cl) -> c to JarReference(cl.pluginId, j, cl) }
    } ?: mapOf()
}

private fun String.toPackageName() = substringBeforeLast("/").drop(1).replace("/", ".")
