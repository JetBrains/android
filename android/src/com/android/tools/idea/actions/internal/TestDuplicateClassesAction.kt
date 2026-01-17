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

/**
 * Test whether any plugins have multiple instances of the same class (by name) in their classloader
 * hierarchy at runtime.
 */
@Suppress("UnstableApiUsage")
class TestDuplicateClassesAction : DumbAwareAction("Test Duplicate Classes") {

  // Plugin name to set of packages to ignore
  // TODO (476432139): move this into a separate file
  val exceptions =
    mapOf(
      "org.jetbrains.android" to
        setOf(
          "com.google.devrel.gmscore.tools.apk.arsc", // b/476427677
          // b/476430566 below here
          "io.opencensus.common",
          "io.opencensus.metrics",
          "io.opencensus.metrics.data",
          "io.opencensus.metrics.export",
          "io.opencensus.resource",
          "io.opencensus.stats",
          "io.opencensus.tags",
          "io.opencensus.tags.unsafe",
          "io.opencensus.trace",
          "io.opencensus.trace.config",
          "io.opencensus.trace.export",
          "io.opencensus.trace.internal",
          "io.opencensus.trace.propagation",
          "io.opencensus.trace.samplers",
          "io.opencensus.trace.unsafe",
          "io.opencensus.internal",
          "io.opencensus.tags.propagation",
          // end b/476430566
          "com.android.annotations.concurrency",
          "com.android.annotations",
          "com.android.tools.instrumentation.threading.agent.callback",
          "javax.inject",
          // b/476501574 below here
          "org.apache.commons.lang.builder",
          "org.apache.commons.lang.exception",
          "org.apache.commons.lang.math",
          "org.apache.commons.lang.text",
          "org.apache.commons.lang.time",
          "org.apache.commons.lang",
          // end b/476501574
          "org.HdrHistogram.packedarray", // b/476488908
          "org.HdrHistogram",  // b/476488908
          "org.objectweb.asm.commons",
          "org.objectweb.asm.signature",
          "org.objectweb.asm.tree.analysis",
          "org.objectweb.asm.tree",
          "org.objectweb.asm",
          "org.xmlpull.v1",
          "com.google.errorprone.annotations",
        ),
      "com.android.tools.design" to
        setOf("org.json", "com.google.errorprone.annotations", "android.annotation"),
      "com.google.tools.ij.aiplugin" to
        setOf(
          "androidx.annotation",
          "com.android.tools.journeys.proto", // b/476493309
          // b/476493791 below here
          "com.google.api",
          "com.google.apps.card.v1",
          "com.google.cloud.audit",
          "com.google.cloud.location",
          "com.google.cloud",
          "com.google.geo.type",
          "com.google.logging.type",
          "com.google.longrunning",
          "com.google.shopping.type",
          // end b/476493791
          "com.google.type",
          "com.squareup.wire", // b/476432509
          "com.squareup.wire.internal", // b/476432509
          "kotlinx.atomicfu.locks",
          "kotlinx.atomicfu",
          // b/476502383 below here
          "org.sqlite.core",
          "org.sqlite.date",
          "org.sqlite.javax",
          "org.sqlite.jdbc3",
          "org.sqlite.jdbc4",
          "org.sqlite.util",
          "org.sqlite",
          // end b/476502383
        ),

      // The below are all coming from the platform: we can't do anything about them directly.
      "com.intellij.cidr.base" to
        setOf(
          "org.objenesis.strategy",
          "org.objenesis.instantiator.util",
          "org.objenesis.instantiator.sun",
          "org.objenesis.instantiator.perc",
          "org.objenesis.instantiator.gcj",
          "org.objenesis.instantiator.basic",
          "org.objenesis.instantiator.annotations",
          "org.objenesis.instantiator.android",
          "org.objenesis.instantiator",
          "org.objenesis",
        ),
      "com.intellij.java" to
        setOf(
          "com.jetbrains.jdi",
          "com.jgoodies.common.swing",
          "com.jgoodies.common.internal",
          "com.jgoodies.common.format",
          "com.jgoodies.common.collect",
          "com.jgoodies.common.bean",
          "com.jgoodies.common.base",
        ),
      "org.jetbrains.kotlin" to
        setOf(
          "kotlin.metadata.jvm.internal",
          "kotlin.metadata.jvm",
          "kotlin.metadata.internal.protobuf",
          "kotlin.metadata.internal.metadata.serialization",
          "kotlin.metadata.internal.metadata.jvm.serialization",
          "kotlin.metadata.internal.metadata.jvm.deserialization",
          "kotlin.metadata.internal.metadata.jvm",
          "kotlin.metadata.internal.metadata.deserialization",
          "kotlin.metadata.internal.metadata.builtins",
          "kotlin.metadata.internal.metadata",
          "kotlin.metadata.internal.extensions",
          "kotlin.metadata.internal.common",
          "kotlin.metadata.internal",
          "kotlin.metadata",
        ),
      "com.intellij" to
        setOf(
          "com.intellij.openapi.util",
          "org.jetbrains.annotations",
          "org.objectweb.asm.tree",
          "org.objectweb.asm.signature",
          "org.objectweb.asm",
          "org.jetbrains.coverage.org.objectweb.asm",
          "org.jetbrains.coverage.gnu.trove",
          "com.intellij.rt.coverage.util",
          "com.intellij.rt.coverage.instrumentation",
          "com.intellij.rt.coverage.data",
          "org.jetbrains.coverage.org.objectweb.asm",
          "org.jetbrains.coverage.gnu.trove",
          "com.intellij.rt.coverage.util",
          "com.intellij.rt.coverage.instrumentation",
          "com.intellij.rt.coverage.data",
          "com.intellij.lang.properties",
        ),
      "com.intellij.properties" to setOf("com.intellij.lang.properties"),
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
    if ("Google" !in (plugin.vendor ?: "")) return false

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
      (plugin.classLoader as PluginClassLoader)
        .getAllParentsClassLoaders()
        .filterNot { it is PluginClassLoader }
        .first()
    val allParentClasses =
      getAllClasses(coreClassLoader)
        .associate { (c, j, cl) -> c to JarReference(PluginManagerCore.CORE_ID, j, cl) }
        .toMutableMap()

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
            .getOrPut(c) {
              mutableSetOf(JarReference(plugin.pluginId, myClassMap[c]!!, plugin.classLoader))
            }
            .add(parentClasses[c]!!)
        }
        // Check for conflicts between classes in different dependant plugins
        parentClasses.keys.intersect(allParentClasses.keys).forEach { c ->
          if (
            parentClasses[c]?.jar != allParentClasses[c]?.jar &&
              c.toPackageName() !in
                (
                // We also exclude exception classes based on the dependant plugin
                (exceptions[(parentClasses[c]?.classLoader as PluginClassLoader).pluginId.idString]
                  ?: setOf()) +
                  (exceptions[
                    (allParentClasses[c]?.classLoader as? PluginClassLoader)?.pluginId?.idString
                      ?: "com.intellij"] ?: setOf()))
          ) {
            conflictsBetweenParents
              .getOrPut(c) { mutableSetOf(allParentClasses[c]!!) }
              .add(parentClasses[c]!!)
          }
        }
        allParentClasses.putAll(parentClasses)
      }

    if (
      internalDuplicates.isNotEmpty() ||
        conflictsWithParents.isNotEmpty() ||
        conflictsBetweenParents.isNotEmpty()
    ) {
      thisLogger().warn("Duplicate classes found in ${plugin.name} (${plugin.pluginId.idString}):")
      if (internalDuplicates.isNotEmpty()) {
        thisLogger().warn("Duplicates within the plugin:\n$internalDuplicates")
      }
      if (conflictsWithParents.isNotEmpty()) {
        thisLogger()
          .warn(
            "Conflicts between plugin libs and parent plugins:\n" +
              conflictsWithParents.entries.take(10).joinToString("\n") { (c, j) ->
                "$c duplicated in $j"
              }
          )
        if (conflictsWithParents.entries.size > 10) {
          thisLogger().warn("and ${conflictsWithParents.entries.size - 10} more")
        }
      }
      if (conflictsBetweenParents.isNotEmpty()) {
        thisLogger()
          .warn(
            "Conflicts between parent plugins:\n" +
              conflictsBetweenParents.entries.take(10).joinToString("\n") { (c, j) ->
                "$c duplicated in $j"
              }
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

  private val classCache = mutableMapOf<URI, List<String>>()

  /**
   * Get a list of all class filenames (like /foo/bar/MyClass.class) present in jar referenced by
   * the given URL.
   */
  private fun getAllClasses(url: URL): List<String> =
    classCache.getOrPut(url.toURI()) {
      val jarFile = Paths.get(url.toURI())
      if (
        url.protocol == "file" && url.file.hasExtensionIgnoreCase("jar") && Files.exists(jarFile)
      ) {
        FileSystems.newFileSystem(jarFile).use { fileSystem: FileSystem ->
          fileSystem.rootDirectories.flatMap { root ->
            Files.walk(root).use { paths ->
              paths
                .filter {
                  it.extension == "class" &&
                    it.fileName.nameWithoutExtension !in setOf("module-info", "package-info")
                }
                .map { it.toString() }
                .toList()
            }
          }
        }
      } else emptyList()
    }

  private data class JarReference(val plugin: PluginId, val jar: URL, val classLoader: ClassLoader)

  // returns class file path to jar url. Classes can be duplicated.
  private fun getAllClasses(classLoader: ClassLoader): List<Triple<String, URL, ClassLoader>> =
    (classLoader as? UrlClassLoader)?.urls?.flatMap { url ->
      getAllClasses(url).map { Triple(it, url, classLoader) }
    } ?: listOf()

  // Map if class file path to jar reference. Note only one jar per class will be reported, and the
  // core classloader is excluded.
  private fun getAllClasses(plugin: IdeaPluginDescriptor): Map<String, JarReference> =
    (plugin.classLoader as? PluginClassLoader)?.let { cl ->
      (listOf(cl) + cl.getAllParentsClassLoaders())
        .filterIsInstance<PluginClassLoader>()
        .flatMap { getAllClasses(it) }
        .associate { (c, j, cl) -> c to JarReference(plugin.pluginId, j, cl) }
    } ?: mapOf()
}

private fun String.toPackageName() = substringBeforeLast("/").drop(1).replace("/", ".")
