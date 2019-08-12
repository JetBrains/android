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
package com.android.tools.idea.res.binding

import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.model.MergedManifestManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException

/**
 * Information for a single, target layout XML file that is useful for generating a Binding or BindingImpl class
 * (assuming it is a data binding or a view binding layout).
 *
 * See also: [BindingLayoutGroup], which owns one (or more) related [BindingLayoutInfo] instances.
 */
class BindingLayoutInfo(var data: BindingLayoutData) : ModificationTracker {
  internal var modificationCount: Long = 0

  /**
   * The PSI element representing this layout file, useful if a user wants to navigate
   * when their cursor is on an "XyzBinding" class name.
   */
  val navigationElement: PsiElement = BindingLayoutInfoFile()

  /**
   * The PSI for a "BindingImpl" class generated for this layout file. It is created externally so
   * it can potentially be null until it is set.
   *
   * NOTE: This is a code smell - it's a field that this class never uses, but rather it relies on
   * an external class to set it. Ideally, we can fix this, perhaps by removing the field
   * completely (and having somewhere else own it). The main consumer of this class at the time
   * of writing this comment is `BindingLayoutInfoFile`. It may no longer be necessary if we end
   * up backing a LightBindingClass with its own light file, instead of with its XML file.
   *
   * See also: `DataBindingClassFactory.getOrCreateBindingClassesFor`
   */
  // TODO(davidherman): The DataBindingClassFactory class mentioned above doesn't exist.
  var psiClass: PsiClass? = null

  /**
   * Note: This backing field is lazily set by []getBindingClassName] but potentially reset by the [setData] method.
   */
  private var bindingClassNameCached: BindingClassName? = null

  private fun getBindingClassName(): BindingClassName {
    var className = bindingClassNameCached
    if (className == null) {
      className = computeBindingClassName()
      bindingClassNameCached = className
    }

    return className
  }

  private fun computeBindingClassName(): BindingClassName {
    val modulePackage = MergedManifestManager.getSnapshot(data.facet).getPackage()

    if (data.customBindingName.isNullOrEmpty()) {
      return BindingClassName("$modulePackage.databinding",
                                    DataBindingUtil.convertToJavaClassName(data.file.name) + "Binding")
    }
    else {
      val customBindingName = data.customBindingName!!
      val firstDotIndex = customBindingName.indexOf('.')

      if (firstDotIndex < 0) {
        return BindingClassName("$modulePackage.databinding", customBindingName)
      }
      else {
        val lastDotIndex = customBindingName.lastIndexOf('.')
        val packageName = if (firstDotIndex == 0) {
          // A custom name like ".ExampleBinding" generates a binding class in the module package.
          modulePackage + customBindingName.substring(0, lastDotIndex)
        }
        else {
          customBindingName.substring(0, lastDotIndex)
        }
        val simpleClassName = customBindingName.substring(lastDotIndex + 1)
        return BindingClassName(packageName, simpleClassName)
      }
    }
  }

  val packageName
    get() = getBindingClassName().packageName
  val className
    get() = getBindingClassName().className
  val qualifiedClassName
    get() = getBindingClassName().qualifiedClassName

  /**
   * Returns the unique "Impl" suffix for this specific layout configuration.
   *
   * In multi-layout configurations, a general "Binding" class will be generated as well as a
   * unique "Impl" version for each configuration. This method returns what that exact "Impl"
   * suffix should be, which can safely be appended to [qualifiedClassName] or [className].
   */
  fun getImplSuffix(): String {
    val folderName = data.file.parent.name
    return when {
      folderName.isEmpty() -> "Impl"
      folderName.startsWith("layout-") ->
          DataBindingUtil.convertToJavaClassName(folderName.substringAfter("layout-")) + "Impl"
      folderName.startsWith("layout") -> "Impl"
      else -> DataBindingUtil.convertToJavaClassName(folderName) + "Impl"
    }
  }

  /**
   * Replaces the value of the [data] property and sets a new modification count if the new value of
   * the [data] property is different from the old one.
   */
  fun setData(newData: BindingLayoutData, modificationCount: Long) {
    if (newData != this.data) {
      this.data = newData
      this.modificationCount = modificationCount
      bindingClassNameCached = null
    }
  }

  override fun getModificationCount(): Long = modificationCount

  /**
   * The package + name for the binding class generated for this layout.
   *
   * The package for a binding class is usually a subpackage of module's package, but it can be
   * fully customized based on the value of [BindingLayoutData.customBindingName].
   *
   * See also: [getImplSuffix], if you want to generate the path to a binding impl class instead.
   */
  private class BindingClassName(val packageName: String, val className: String) {
    val qualifiedClassName
      get() = "${packageName}.${className}"
  }

  /**
   * This class represents an XML file that hosts binding logic - in other words, in addition
   * to being a regular XML file, it is also aware that it owns a binding class.
   *
   * Additional notes:
   *
   * We create a custom [PsiFile] implementation for [BindingLayoutInfo] files, to work around
   * the fact that binding otherwise causes the IntelliJ code coverage runner to crash.
   *
   * The reason this happens is that binding code is somewhat special, since binding classes are
   * generated from an XML file (e.g. `activity_main.xml` generates `ActivityMainBinding`). These
   * generated binding classes point back at the XML as its parent file.
   *
   * The IntelliJ code coverage runner, however, assumes that every class it iterates over belongs
   * to a containing file which is a [PsiClassOwner] - in other words, a class belongs to a file that
   * contains one or more classes. This would usually make sense, such as a Java class living inside
   * a Java file, except in our case, XML files are NOT owners of classes.
   *
   * Therefore, we create a special-case file which is REALLY just an XML file that also implements
   * [PsiClassOwner] to indicate the fact that this XML file does, indeed, own a class.
   *
   * For even more context, see also https://issuetracker.google.com/120561619.
   */
  inner class BindingLayoutInfoFile : PsiFile by DataBindingUtil.findXmlFile(data)!!, PsiClassOwner {

    override fun getContainingFile(): PsiFile {
      // Return ourselves instead of delegating to the target XML file, since we're the containing
      // file that also implements PsiClassOwner.
      return this
    }

    override fun getClasses(): Array<PsiClass> = arrayOf(psiClass!!)

    override fun getPackageName(): String {
      return psiClass!!.qualifiedName?.substringBeforeLast('.') ?: ""
    }

    override fun setPackageName(packageName: String?) {
      throw IncorrectOperationException("Cannot set package name for generated binding classes")
    }
  }
}

