/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.ide.common.gradle.model.IdeVariant
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.google.common.collect.ImmutableList
import icons.AndroidIcons
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

open class PsVariant(override val parent: PsAndroidModule,
                     override val name: String) : PsChildModel() {

  private var myBuildType: String = ""
  private var myProductFlavors: List<String> = listOf()
  var resolvedModel: IdeVariant? = null ; private set
  private var myArtifactCollection: PsAndroidArtifactCollection? = null
  val artifacts: List<PsAndroidArtifact> get() = orCreateArtifactCollection.items()

  internal fun init(buildType: String, productFlavors: List<String>, resolvedModel: IdeVariant?) {
    myBuildType = buildType
    myProductFlavors = productFlavors
    this.resolvedModel = resolvedModel

    myArtifactCollection?.refresh()
  }

  open val buildType: PsBuildType get() = parent.findBuildType(myBuildType)!!
  val buildTypeName get() = myBuildType

  private val orCreateArtifactCollection: PsAndroidArtifactCollection
    get() = myArtifactCollection ?: PsAndroidArtifactCollection(this).also { myArtifactCollection = it }

  val productFlavors: List<String>
    @TestOnly
    get() = ImmutableList.copyOf(myProductFlavors)

  override val isDeclared: Boolean = false

  override val icon: Icon? = AndroidIcons.Variant

  fun findArtifact(name: String): PsAndroidArtifact? {
    return orCreateArtifactCollection.findElement(name)
  }

  fun forEachArtifact(consumer: (PsAndroidArtifact) -> Unit) {
    orCreateArtifactCollection.forEach(consumer)
  }

  open fun forEachProductFlavor(consumer: (PsProductFlavor) -> Unit) {
    for (name in myProductFlavors) {
      val productFlavor = parent.findProductFlavor(name)
      consumer(productFlavor!!)
    }
  }
}
