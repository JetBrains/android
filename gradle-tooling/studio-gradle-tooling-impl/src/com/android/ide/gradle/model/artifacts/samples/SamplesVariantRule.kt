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
package com.android.ide.gradle.model.artifacts.samples

import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel.SAMPLE_SOURCE_CLASSIFIER
import org.gradle.api.Named
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

private inline fun <reified T : Named> ObjectFactory.named(name: String): T = named(T::class.java, name)

/**
 * Adds metadata about samples for a library.
 *
 * The variant made with this rule is consumed by [AdditionalModelBuilder].
 * Assumes that sources of the samples are in "${id.name}-${id.version}-$SAMPLE_SOURCE_CLASSIFIER.jar"
 */
@CacheableRule
abstract class SamplesVariantRule : ComponentMetadataRule {
  @get: Inject
  abstract val objectFactory: ObjectFactory

  override fun execute(ctx: ComponentMetadataContext) {
    if (ctx.getDescriptor(IvyModuleDescriptor::class.java) != null) {
      // ignore for Ivy dependencies - b/161405989
      return
    }

    val category: Category = objectFactory.named(Category.DOCUMENTATION)
    val docsType: DocsType = objectFactory.named(SAMPLE_SOURCE_CLASSIFIER)

    val id = ctx.details.id
    ctx.details.addVariant(SAMPLE_SOURCE_CLASSIFIER) { vm: VariantMetadata ->
      vm.attributes {
        it.attribute(Category.CATEGORY_ATTRIBUTE, category)
        it.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, docsType)
      }
      vm.withFiles {
        it.addFile("${id.name}-${id.version}-$SAMPLE_SOURCE_CLASSIFIER.jar")
      }
    }
  }
}