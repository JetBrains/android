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
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex

class DaggerIndex : FileBasedIndexExtension<String, Set<IndexValue>>() {
  companion object {
    private val NAME: ID<String, Set<IndexValue>> = ID.create("com.android.tools.idea.dagger.index.DaggerIndex")

    internal fun getValues(key: String, scope: GlobalSearchScope): Set<IndexValue> {
      return FileBasedIndex.getInstance().getValues(NAME, key, scope).flatten().toSet()
    }

    /**
     * Returns the list of index keys to search for a given type in priority order.
     *
     * The index stores values using the type name as the key, but that type might be fully-qualified, just a simple name, or in some cases
     * the "unknown" type represented by an empty string. Additionally, Kotlin allows type aliases that need to be looked at as well.
     */
    internal fun getIndexKeys(fqName: String, project: Project, scope: GlobalSearchScope): List<String> {
      val simpleName = fqName.substringAfterLast(".")
      val aliasFqNames = KotlinTypeAliasByExpansionShortNameIndex.get(simpleName, project, scope).mapNotNull { it.fqName?.asString() }

      return buildList {
        // All fully-qualified names should go first, since they're most specific.
        add(fqName)
        addAll(aliasFqNames)

        // All simple names next.
        add(simpleName)
        addAll(aliasFqNames.map { it.substringAfterLast(".") })

        // The unknown type last, since it's most generic.
        add("")
      }.distinct()
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
