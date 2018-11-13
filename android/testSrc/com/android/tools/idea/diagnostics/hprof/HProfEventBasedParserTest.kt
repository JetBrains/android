/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof

import com.android.testutils.TestUtils
import com.android.tools.idea.diagnostics.hprof.analysis.HProfAnalysis
import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.navigator.RootReason
import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.android.tools.idea.diagnostics.hprof.visitors.CollectRootReasonsVisitor
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import gnu.trove.TObjectIntHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class HProfEventBasedParserTest {

  private var file: File = TestUtils.getWorkspaceFile("tools/adt/idea/android/testData/profiling/sample.hprof")
  private val channel: FileChannel = FileChannel.open(Paths.get(file.toString()), StandardOpenOption.READ)
  private val parser = HProfEventBasedParser(channel)
  private val tmpFolder: TemporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    tmpFolder.create()
  }

  @After
  fun tearDown() {
    channel.close()
    tmpFolder.delete()
  }

  @Test
  fun testIdSize() {
    assertEquals(8, parser.idSize)
  }

  @Test
  fun testCollectRoots() {
    val visitor = CollectRootReasonsVisitor()
    parser.accept(visitor, null)
    val roots = visitor.roots
    val counts = TObjectIntHashMap<RootReason>()
    roots.forEachValue {
      if (!counts.containsKey(it)) {
        counts.put(it, 0)
      }
      counts.increment(it)
      true
    }
    assertEquals(2, counts[RootReason.rootGlobalJNI])
    assertEquals(19, counts[RootReason.rootJavaFrame])
    assertEquals(1, counts[RootReason.rootLocalJNI])
    assertEquals(2, counts[RootReason.rootMonitorUsed])
    assertEquals(0, counts[RootReason.rootNativeStack])
    assertEquals(621, counts[RootReason.rootStickyClass])
    assertEquals(0, counts[RootReason.rootThreadBlock])
    assertEquals(3, counts[RootReason.rootThreadObject])
    assertEquals(0, counts[RootReason.rootUnknown])
  }

  @Test
  fun testClassStore() {
    val classStore = ClassStore.create(parser)
    assertEquals(702, classStore.size())

    val stringDef = classStore["java.lang.String"]
    val objectDef = classStore["java.lang.Object"]

    assertEquals("java.lang.String", stringDef.name)
    assertEquals("java.lang.Object", objectDef.name)

    assertEquals(0, stringDef.constantFields.size)
    assertEquals(12, stringDef.instanceSize)
    assertEquals(1, stringDef.refInstanceFields.size)
    assertEquals(2, stringDef.staticFields.size)

    assertEquals(objectDef.id, stringDef.superClassId)
    assertEquals(28991101024, objectDef.id)

    assertEquals(0, objectDef.superClassId)

    assertEquals("value", stringDef.refInstanceFields[0].name)
  }

  @Test
  fun testReport() {
    val analysis = HProfAnalysis(channel, object : HProfAnalysis.TempFilenameSupplier {
      override fun getTempFilePath(type: String): Path {
        return tmpFolder.newFile().toPath()
      }
    })
    analysis.setIncludeMetaInfo(false)
    val progress = object : AbstractProgressIndicatorBase() {
    }
    progress.isIndeterminate = false
    val generatedReport = analysis.analyze(progress)
    val baselineReport = String(Files.readAllBytes(
      TestUtils.getWorkspaceFile("tools/adt/idea/android/testData/profiling/sample-report.txt").toPath()), StandardCharsets.UTF_8)

    assertEquals(baselineReport, generatedReport)
  }
}