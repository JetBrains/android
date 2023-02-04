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
package com.android.tools.idea.dagger.index

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType

class DaggerIndex : FileBasedIndexExtension<String, Set<IndexValue>>() {
  companion object {
    private val NAME: ID<String, Set<IndexValue>> = ID.create("com.android.tools.idea.dagger.index.DaggerIndex")

    fun getValues(key: String, scope: GlobalSearchScope): Set<IndexValue> {
      return FileBasedIndex.getInstance().getValues(NAME, key, scope).flatten().toSet()
    }
  }

  override fun getName(): ID<String, Set<IndexValue>> = NAME
  override fun dependsOnFileContent() = true
  override fun getVersion() = 0
  override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinFileType.INSTANCE, JavaFileType.INSTANCE)
  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
  override fun getValueExternalizer(): DataExternalizer<Set<IndexValue>> = IndexValue.Externalizer
  override fun getIndexer(): DataIndexer<String, Set<IndexValue>, FileContent> = DaggerDataIndexer.INSTANCE
}
