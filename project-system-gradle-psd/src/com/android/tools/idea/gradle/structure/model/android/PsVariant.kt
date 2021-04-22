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

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeVariant
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.utils.combineAsCamelCase
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import icons.StudioIcons
import javax.swing.Icon

private val LOG = Logger.getInstance(PsVariant::class.java)

data class PsVariantKey(val buildType: String, val productFlavors: List<String>) {
  val name: String get() = (productFlavors + buildType).combineAsCamelCase()
}

open class PsVariant(
  override val parent: PsAndroidModule,
  val key: PsVariantKey
) : PsChildModel() {
  final override var name: String = key.name; private set

  val buildTypeName: String get() = key.buildType
  val productFlavorNames: List<String> get() = key.productFlavors
  var resolvedModel: IdeVariant? = null ; private set
  private var myArtifactCollection: PsAndroidArtifactCollection? = null

  val artifacts: Collection<PsAndroidArtifact> get() = artifactCollection
  open val buildType: PsBuildType get() = parent.findBuildType(buildTypeName)!!

  internal fun init(resolvedModel: IdeVariant?) {
    this.resolvedModel = resolvedModel
    if (resolvedModel != null) {
      if (name != resolvedModel.name) {
        LOG.warn("Predicted variant name $name differs from resolved name ${resolvedModel.name}")
      }
      name = resolvedModel.name
    }
    myArtifactCollection?.refresh()
  }

  @VisibleForTesting
  val artifactCollection: PsAndroidArtifactCollection
    get() = myArtifactCollection ?: PsAndroidArtifactCollection(this).also { myArtifactCollection = it }

  override val isDeclared: Boolean = false

  override val icon: Icon? = StudioIcons.Misc.PROJECT_SYSTEM_VARIANT

  fun findArtifact(name: IdeArtifactName): PsAndroidArtifact? {
    return artifactCollection.findElement(name)
  }

  fun forEachArtifact(consumer: (PsAndroidArtifact) -> Unit) {
    artifactCollection.forEach(consumer)
  }

  open fun forEachProductFlavor(consumer: (PsProductFlavor) -> Unit) {
    // Pre AGP 3.0 project might not have any dimensions.
    val effectiveDimensions = parent.flavorDimensions.takeUnless { it.isEmpty() } ?: listOf(null)
    for ((dimension, name) in effectiveDimensions zip productFlavorNames) {
      val productFlavor = parent.findProductFlavor(dimension?.name.orEmpty(), name)
      consumer(productFlavor!!)
    }
  }
}
