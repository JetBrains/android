/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.testutils.TestUtils
import org.junit.Test
import org.objectweb.asm.ClassReader
import java.nio.file.Files
import java.nio.file.Path
import junit.framework.Assert


class KeyMetaDecoderTest {
  private val dataPath = TestUtils.getWorkspaceRoot().resolve("tools/adt/idea/android/testData/liveEdit/keyMeta").toString()

  @Test
  fun basicActivity() {
    val (sourceFile, groupsOffSets)= computeGroups(ClassReader(Files.readAllBytes(Path.of("$dataPath/MainActivityKt\$KeyMeta.class"))))
    Assert.assertTrue(sourceFile!!.endsWith("java/com/example/myapplication/MainActivity.kt"))
    Assert.assertEquals(4, groupsOffSets.size)

    // The actual values of the keys and offsets is allowed to be unstable across compose versions.
    // If the value changes quite a bit across Compose compiler, there is not much harm in removing these assertion provided
    // we continue to make sure the KeyMetaDecoder is able to decode a total of 4 keys
    assertContainKey(groupsOffSets,-721276027,684,1047)
    assertContainKey(groupsOffSets,2100227153,717,1037)
    assertContainKey(groupsOffSets,-114909162,965,1023)
    assertContainKey(groupsOffSets,785590350,1069,1190)
  }

  @Test
  fun basicTheme() {
    val (sourceFile, groupsOffSets)= computeGroups(ClassReader(Files.readAllBytes(Path.of("$dataPath/ThemeKt\$KeyMeta.class"))))
    Assert.assertTrue(sourceFile!!.endsWith("java/com/example/myapplication/ui/theme/Theme.kt"))
    Assert.assertEquals(1, groupsOffSets.size)
  }

  private fun assertContainKey(entries: List<InvalidateGroupEntry>, key: Int, start: Int, end: Int) {
    var entry = entries.find { e -> e.key == key }
    Assert.assertNotNull("Cannot find $key in groupOffSets.", entry)
    Assert.assertEquals("Unmatched startOffset in $key", start, entry!!.startOffset)
    Assert.assertEquals("Unmatched endOffset in $key", end, entry!!.endOffSet)
  }
}