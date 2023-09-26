/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.testutils.junit4

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.io.path.Path

/**
 * Test that evaluates that all [OldAgpTest] annotations with specified version pairs in the provided test jar have a matching
 * bazel test target that will be able to run it.
 *
 * Jar with tests to analyze and list of declared bazel targets are passed in via system properties.
 *
 * Usage of [OldAgpTest] annotation with version pairs generate test parameters tuple: (versions pair, locations). Parametrized
 * check then just checks if such version pair is covered by targets. If it is not covered, failure lists the locations where
 * this pair was requested.
 */
@RunWith(Parameterized::class)
class OldAgpTestTargetsChecker(
  private val versionsPair: OldAgpTestVersionsPair,
  private val appliedLocations: List<String>
) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun requestedTests(): List<Array<Any>> {
      val testJarPath = System.getProperty("old.agp.tests.check.jar") ?: return emptyList()
      return parseAllAnnotatedLocations(loadClasses(Path(testJarPath))).map { arrayOf(it.key, it.value.sorted()) }
    }

    fun parseAllAnnotatedLocations(classes: Set<Class<*>>): Map<OldAgpTestVersionsPair, List<String>> {
      val annotationTargets = mutableListOf<Pair<OldAgpTestVersionsPair, String>>()
      classes.forEach { testClass ->
        testClass.getAnnotation(OldAgpTest::class.java)?.let { classAnnotation ->
          classAnnotation.agpVersions.forEach { agpVersion ->
            classAnnotation.gradleVersions.forEach { gradleVersion ->
              annotationTargets.add(OldAgpTestVersionsPair(agpVersion, gradleVersion) to testClass.name)
            }
          }
        }
        testClass.methods.forEach { method ->
          method.getAnnotation(OldAgpTest::class.java)?.let { methodAnnotation ->
            methodAnnotation.agpVersions.forEach { agpVersion ->
              methodAnnotation.gradleVersions.forEach { gradleVersion ->
                annotationTargets.add(OldAgpTestVersionsPair(agpVersion, gradleVersion) to "${testClass.name}#${method.name}")
              }
            }
          }
        }
      }
      return annotationTargets.groupBy(keySelector = { it.first }, valueTransform = { it.second} )
    }

    private fun loadClasses(jar: Path): Set<Class<*>> {
      val urlClassLoader = URLClassLoader.newInstance(arrayOf(jar.toUri().toURL()))
      val classes: MutableSet<Class<*>> = HashSet()
      JarInputStream(FileInputStream(jar.toString())).use { jis ->
        while (true) {
          val jarEntry = jis.nextJarEntry ?: break
          if (jarEntry.name.endsWith(".class")) {
            val clsName = jarEntry.name.replace("/".toRegex(), ".").replace(".class$".toRegex(), "")
            val clz = urlClassLoader.loadClass(clsName)
            classes.add(clz)
          }
        }
      }
      return classes
    }
  }

  @Test
  fun check() {
    val definedVersionPairTargets = System.getProperty("agp.gradle.version.pair.targets")?.split(":") ?: emptyList()
    val ignoreLocations = System.getProperty("old.agp.tests.check.ignore.list")?.split(":") ?: emptyList()

    if (definedVersionPairTargets.none { it == "${versionsPair.agpVersion}@${versionsPair.gradleVersion}"}
        && appliedLocations.any { !ignoreLocations.contains(it) }) {
      Assert.fail("$versionsPair is not covered by old_agp_test targets but requested in:\n" + appliedLocations.joinToString(separator = "\n"))
    }
  }

  data class OldAgpTestVersionsPair(
    val agpVersion: String,
    val gradleVersion: String
  )
}
