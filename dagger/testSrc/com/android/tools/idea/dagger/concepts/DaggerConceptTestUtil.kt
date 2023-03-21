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
package com.android.tools.idea.dagger.concepts

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.DaggerDataIndexer
import com.android.tools.idea.dagger.index.IndexValue
import com.intellij.util.indexing.FileContent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

fun serializeAndDeserializeIndexValue(indexValue: IndexValue): IndexValue =
  serializeAndDeserializeIndexValues(setOf(indexValue)).single()

fun serializeAndDeserializeIndexValues(indexValues: Set<IndexValue>): Set<IndexValue> {
  val bytes =
    ByteArrayOutputStream().use { baos ->
      DataOutputStream(baos).use { dos -> IndexValue.Externalizer.save(dos, indexValues) }
      baos.toByteArray()
    }

  return ByteArrayInputStream(bytes).use { bais ->
    DataInputStream(bais).use { dis -> IndexValue.Externalizer.read(dis) }
  }
}

fun DaggerConceptIndexers.runIndexerOn(ktFile: KtFile): Map<String, Set<IndexValue>> {
  val dataIndexer = DaggerDataIndexer(this)
  val fileContent: FileContent = mock()
  whenever(fileContent.psiFile).thenReturn(ktFile)
  whenever(fileContent.fileType).thenReturn(KotlinFileType.INSTANCE)
  return dataIndexer.map(fileContent)
}
