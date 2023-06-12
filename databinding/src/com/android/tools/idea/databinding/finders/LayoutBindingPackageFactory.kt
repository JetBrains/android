/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.databinding.finders

import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.util.toIoFile
import com.google.common.collect.Maps
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

@Service
class LayoutBindingPackageFactory(val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(LayoutBindingPackageFactory::class.java)!!
  }

  private val layoutBindingPsiPackages = Maps.newConcurrentMap<String, PsiPackage>()

  private class FakeDirectory(file: File) : LightVirtualFileBase(file.absolutePath, null, -1) {
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw NotImplementedError()
    override fun getInputStream() = throw NotImplementedError()
    override fun contentsToByteArray() = ByteArray(0)
  }

  /**
   * Returns a [PsiPackage] instance for the given package name.
   *
   * If it does not exist in the cache, a new one is created.
   *
   * @param facet The facet within which we'll be creating this package, used for finding a root
   *   source directory.
   * @param packageName The qualified package name
   * @return A [PsiPackage] that represents the given qualified name
   */
  @Synchronized
  fun getOrCreatePsiPackage(facet: AndroidFacet, packageName: String): PsiPackage {
    return layoutBindingPsiPackages.computeIfAbsent(packageName) {
      object : PsiPackageImpl(PsiManager.getInstance(project), packageName) {
        override fun isValid(): Boolean = true
        override fun getDirectories(scope: GlobalSearchScope): Array<PsiDirectory> {
          // Hack alert: Since JDK9, IntelliJ APIs expect a package to be associated with a corresponding directory.
          // However, for data binding classes, the folder *will* exist at some point but not until compilation time.
          // It seems we can fake out the IntelliJ APIs for now with a pretend directory. If we don't do this, then
          // when users upgrade past JDK9, all the "com.xyz.databinding" packages will look like they aren't resolving.
          // It doesn't matter to us that this won't be the actual, final generated directory; it's just enough to fool
          // the resolution system.
          // See also: https://issuetracker.google.com/180946610
          val srcDir = SourceProviderManager.getInstance(facet).sources.javaDirectories.firstOrNull()?.toIoFile() ?: return emptyArray()
          val databindingDir = FakeDirectory(File(srcDir, packageName.replace('.', '/')))
          // The following line creates a PsiDirectory but doesn't actually create a directory on disk
          return arrayOf(PsiDirectoryFactory.getInstance(project).createDirectory(databindingDir))
        }
      }
    }
  }
}
