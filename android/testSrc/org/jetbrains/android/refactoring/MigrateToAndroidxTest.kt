/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*

private const val BASE_PATH = "refactoring/migrateToAndroidx/"

class MigrateToAndroidxTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    // This is needed for resolving framework classes
    myFixture.allowTreeAccessForAllFiles()
  }

  /**
   * Test to ensure that a [ClassMigrationEntry] takes precedence over a [PackageMigrationEntry]
   * which in this case is the CoordinatorLayout.
   */
  fun testMigrateWithConflictingClassAndPackageNames() {
    AndroidxMigrationBuilder()
        .withFileInProject("MainActivity.java", "src/p1/p2/MainActivity.java")
        .withEntry(ClassMigrationEntry("android.support.design.widget.CoordinatorLayout", "androidx.widget.CoordinatorLayout"))
        .withEntry(PackageMigrationEntry("android.support.v7", "androidx"))
        .withEntry(PackageMigrationEntry("android.support.design", "androidx.design"))
        .run(myFixture)
  }

  fun testMigrateLayoutXml() {
    AndroidxMigrationBuilder()
        .withEntry(ClassMigrationEntry("android.support.design.widget.CoordinatorLayout", "androidx.widget.CoordinatorLayout"))
        .withEntry(PackageMigrationEntry("android.support.v7", "androidx"))
        .withEntry(PackageMigrationEntry("android.support", "androidx"))
        .withFileInProject("app_bar_main.xml", "res/layout/app_bar_main.xml")
        .run(myFixture)
  }

  fun testMigrateBuildDependencies() {
    // test both map notation as well as compact notation
    AndroidxMigrationBuilder()
        .withEntry(GradleDependencyMigrationEntry("com.android.support", "appcompat-v7",
            "androidx.appcompat", "base", "1.0.0-alpha1"))
        .withEntry(GradleDependencyMigrationEntry("com.android.support.constraint", "constraint-layout",
            "androidx.constraint", "base", "2.0.0-alpha1"))
        .withFileInProject("buildDependencies.gradle", "build.gradle")
        .run(myFixture)
  }

  /**
   * Helper class for testing [MigrateToAndroidxProcessor] that sets up and executes
   * a refactoring with specified [AppCompatMigrationEntry] on the given [paths]
   */
  internal class AndroidxMigrationBuilder {
    val paths = mutableMapOf<String, String>()
    val entries = mutableListOf<AppCompatMigrationEntry>()

    fun withFileInProject(pathRelativeToBase: String, targetPath: String) = apply {
      paths[pathRelativeToBase] = targetPath
    }

    fun withEntry(entry: AppCompatMigrationEntry) = apply {
      entries += entry
    }

    fun run(fixture: JavaCodeInsightTestFixture) {
      TestCase.assertTrue("Requires entries", !entries.isEmpty())
      for ((key, value) in paths.entries) {
        fixture.copyFileToProject(BASE_PATH + key, value)
      }

      PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()

      MigrateToAndroidxProcessor(fixture.project, entries).run()

      // validate results
      for ((key, value) in paths.entries) {
        val ext = key.substring(key.lastIndexOf('.'))
        val afterFile = key.substring(0, key.indexOf(ext)) + "_after" + ext
        fixture.checkResultByFile(value, BASE_PATH + afterFile, true)
      }
    }
  }
}
