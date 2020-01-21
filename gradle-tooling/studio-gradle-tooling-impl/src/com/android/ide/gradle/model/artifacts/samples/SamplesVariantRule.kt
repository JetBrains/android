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
import com.android.ide.gradle.model.artifacts.builder.DocsType
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/*
 * Currently AS builds against Gradle tooling-api 5.2, but SamplesModelBuilder is enabled for Gradle 6+.
 * We copied some gradle constants from newer version.
 *
 * TODO(b/148289718): delete when studio compiles against Gradle tooling-api 5.3+
*/
interface Category : Named {
  companion object {
    val CATEGORY_ATTRIBUTE: Attribute<Category> = Attribute.of("org.gradle.category", Category::class.java)
    /**
     * The library category
     */
    const val LIBRARY = "library"
    /**
     * The documentation category
     *
     * @since 5.6
     */
    const val DOCUMENTATION = "documentation"
  }
}

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

  /**
   *  Currently AS builds against Gradle 5.2, but SamplesVariantRule is enabled for Gradle 6+.
   *
   *  To build AS we use reflection.
   *  Original code:
   *   override fun execute(ctx: ComponentMetadataContext) {
   *    val id = ctx.details.id
   *     ctx.details.addVariant(SAMPLE_SOURCE_CLASSIFIER) {
   *       attributes {
   *         attribute(org.gradle.internal.impldep.aQute.bnd.annotation.headers.Category.CATEGORY_ATTRIBUTE, getObjects().named(
   *        org.gradle.internal.impldep.aQute.bnd.annotation.headers.Category.DOCUMENTATION))
   *         attribute(DocsType.DOCS_TYPE_ATTRIBUTE, getObjects().named(SAMPLE_SOURCE_CLASSIFIER))
   *       }
   *       withFiles {
   *         addFile("${id.name}-${id.version}-$SAMPLE_SOURCE_CLASSIFIER.jar")
   *       }
   *       }
   *     }
   *
   *     TODO(b/148289718): replace with original code when studio compiles against Gradle tooling-api 5.3+
   */
  override fun execute(ctx: ComponentMetadataContext) {
    val id = ctx.details.id
    val addVariant = ctx.details.javaClass.getMethod("addVariant", String::class.java, Action::class.java)

    val action: Action<VariantMetadata> = object : Action<VariantMetadata> {
      override fun execute(vm: VariantMetadata) {
        vm.attributes(object : Action<AttributeContainer> {
          override fun execute(ac: AttributeContainer) {
            ac.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.DOCUMENTATION))
            ac.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(SAMPLE_SOURCE_CLASSIFIER))
          }
        })
        val withFiles = vm.javaClass.getMethod("withFiles", Action::class.java)

        withFiles.invoke(vm, object : Action<Any> {
          override fun execute(a: Any) {
            val addFile = a.javaClass.getMethod("addFile", String::class.java)
            addFile.invoke(a, "${id.name}-${id.version}-$SAMPLE_SOURCE_CLASSIFIER.jar")
          }
        })
      }
    }

    addVariant.invoke(ctx.details, SAMPLE_SOURCE_CLASSIFIER, action)
  }
}