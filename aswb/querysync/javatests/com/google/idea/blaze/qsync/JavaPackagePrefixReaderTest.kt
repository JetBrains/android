/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.NoopContext
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.query.PackageSet
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.google.idea.testing.IntellijRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

@RunWith(JUnit4::class)
class JavaPackagePrefixReaderTest {

    @JvmField
    @Rule
    val tempDir = TemporaryFolder()

    companion object {
        @JvmField
        @ClassRule
        val intellij = IntellijRule()
    }

    private lateinit var workspaceRoot: Path
    private val packageReader = PackageReader { _, path -> readPackage(path) }
    private val packages = mutableMapOf<Path, String>()
    private val parallelPackageReader = QuerySyncTestUtils.SIMPLE_PARALLEL_PACKAGE_READER

    @Before
    fun setUp() {
        intellij.registerApplicationService(ExperimentService::class.java, MockExperimentService())
        workspaceRoot = tempDir.root.toPath()
    }

    private fun readPackage(path: Path): String? {
        return packages[path]
    }

    private fun createAndRegisterPackage(path: String, pkg: String): Path {
        val filePath = workspaceRoot.resolve(path)
        filePath.parent.toFile().mkdirs()
        filePath.toFile().createNewFile()
        val relativeFilePath = workspaceRoot.relativize(filePath)
        packages[relativeFilePath] = pkg
        return relativeFilePath
    }

    @Test
    fun readPrefixesSinglePackage() {
        runBlocking<Unit> {
            val file = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes( NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of(file))
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
        }
    }

    @Test
    fun readPrefixesWithSubpackages() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = createAndRegisterPackage("java/com/example/sub/Class2.java", "com.example.sub")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of(file1, file2))
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
        }
    }

    @Test
    fun readPrefixesMultipleTopLevelFilesInSameDir() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = createAndRegisterPackage("java/com/example/Class2.java", "com.example")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of(file1, file2))
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
        }
    }

    @Test
    fun readPrefixesNestedPackages() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = createAndRegisterPackage("java/com/example/sub/Class2.java", "com.example.sub")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example"), Path.of("java/com/example/sub")),
                                             ImmutableList.of(file1, file2))
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example",
                Path.of("java/com/example/sub"), "com.example.sub"
            )
        }
    }

    @Test
    fun readPrefixesEmptyPackage() {
        runBlocking {
            val pkg = workspaceRoot.resolve("java/com/example")
            pkg.toFile().mkdirs()
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of())
            assertThat(result).isEmpty()
        }
    }

    @Test
    fun readPrefixesNoSourceFiles() {
        runBlocking {
            val pkg = workspaceRoot.resolve("java/com/example")
            pkg.toFile().mkdirs()
            workspaceRoot.resolve("java/com/example/NOT_A_SOURCE.txt").toFile().createNewFile()
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of())
            assertThat(result).isEmpty()
        }
    }

    @Test
    fun readPrefixesNonExistentPackage() {
        runBlocking {
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(NoopContext(),
                                             PackageSet.of(Path.of("java/com/example")),
                                             ImmutableList.of())
            assertThat(result).isEmpty()
        }
    }

    @Test
    fun readPrefixesFileExistenceCheck() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = workspaceRoot.relativize(workspaceRoot.resolve("java/com/example/Class2.java")) // Not created
            val file3 = createAndRegisterPackage("java/com/example/Class3.java", "com.example")

            val existingFiles = setOf(file1, file3)
            val readFiles = mutableListOf<Path>()
            val capturingPackageReader = PackageReader { _, path ->
                readFiles.add(path)
                packages[path]
            }
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, capturingPackageReader, parallelPackageReader, fileExistenceCheck = { it in existingFiles })

            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java/com/example")),
                ImmutableList.of(file1, file2, file3)
            )
            // Class2.java should be ignored, Class1.java should be chosen as it's lexicographically first.
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
            assertThat(readFiles).containsExactly(file1)
        }
    }

    @Test
    fun readPrefixesFileExistenceCheckStopsAfterFirst() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = createAndRegisterPackage("java/com/example/Class2.java", "com.example")
            val file3 = createAndRegisterPackage("java/com/example/Class3.java", "com.example")

            val checkedFiles = mutableListOf<Path>()
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { file ->
                checkedFiles.add(file)
                if (file == file1) {
                    return@JavaPackagePrefixReaderImpl true
                }
                throw AssertionError("Should not check other files if the first exists: $file")
            })

            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java/com/example")),
                ImmutableList.of(file1, file2, file3)
            )
            assertThat(result).containsExactly(Path.of("java/com/example"), "com.example")
            assertThat(checkedFiles).containsExactly(file1)
        }
    }

    @Test
    fun readPrefixesSingleFileNotAtPackageRoot() {
        runBlocking<Unit> {
            val file = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java")), // Bazel package is "java"
                ImmutableList.of(file)
            )
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
        }
    }

    @Test
    fun readPrefixesTwoPackageRootsInSameBazelPackage() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example1/Class1.java", "com.example1")
            val file2 = createAndRegisterPackage("java/com/example2/Class2.java", "com.example2")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java")), // Bazel package is "java"
                ImmutableList.of(file1, file2)
            )
            // We expect the longest common parent that contains a source file to be the key.
            // Since both are in different subdirectories of java/com, we get two entries.
            assertThat(result).containsExactly(
                Path.of("java/com/example1"), "com.example1",
                Path.of("java/com/example2"), "com.example2"
            )
        }
    }

    @Test
    fun readPrefixesMismatchedDirectoryAndPackageNames() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/actual1/Class1.java", "com.declared1")
            val file2 = createAndRegisterPackage("java/com/actual2/Class2.java", "com.declared2")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java")), // Bazel package is "java"
                ImmutableList.of(file1, file2)
            )
            // The package prefix is determined by the declared package in the .java file.
            // The directory path is used as the key.
            assertThat(result).containsExactly(
                Path.of("java/com/actual1"), "com.declared1",
                Path.of("java/com/actual2"), "com.declared2"
            )
        }
    }

    @Test
    fun readPrefixesSubPackageNameMismatch() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("java/com/example/Class1.java", "com.example")
            val file2 = createAndRegisterPackage("java/com/example/sub/Class2.java", "com.example.sub_other")
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("java/com/example")),
                ImmutableList.of(file1, file2)
            )
            // file1 establishes the prefix for java/com/example.
            // file2 is in a subdirectory. Since java/com/example is in the PackageSet,
            // isTopLevel for file2 will return false because its parent has a representative file (file1).
            // Thus, only file1 is read.
            assertThat(result).containsExactly(
                Path.of("java/com/example"), "com.example"
            )
        }
    }

    @Test
    fun readPrefixesFileInRootPackageDir() {
        runBlocking<Unit> {
            val file1 = createAndRegisterPackage("a/Class1.java", "a") // Package declared as "a"
            val reader = JavaPackagePrefixReaderImpl(workspaceRoot, packageReader, parallelPackageReader, fileExistenceCheck = { true })
            val result = reader.readPrefixes(
                NoopContext(),
                PackageSet.of(Path.of("")), // Root package
                ImmutableList.of(file1)
            )
            // Since Path.of("") is in packages, a/Class1.java should be considered top level.
            // The key in the map should be the file's parent directory.
            assertThat(result).containsExactly(
                Path.of("a"), "a"
            )
        }
    }
}
