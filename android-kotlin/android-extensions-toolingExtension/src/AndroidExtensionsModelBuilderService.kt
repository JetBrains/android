// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.android.synthetic.idea

import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.Serializable

private const val DEFAULT_CACHE_IMPLEMENTATION_DEFAULT_VALUE = "hashMap"

interface AndroidExtensionsGradleModel : Serializable {
    val hasAndroidExtensionsPlugin: Boolean
    val isExperimental: Boolean
    val defaultCacheImplementation: String
}

class AndroidExtensionsGradleModelImpl(
        override val hasAndroidExtensionsPlugin: Boolean,
        override val isExperimental: Boolean,
        override val defaultCacheImplementation: String
) : AndroidExtensionsGradleModel

class AndroidExtensionsModelBuilderService : ModelBuilderService {

    override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
        context.messageReporter.createMessage()
          .withGroup(this)
          .withKind(Message.Kind.WARNING)
          .withTitle("Gradle import errors")
          .withText("Unable to build Android Extensions plugin configuration")
          .withException(exception)
          .reportMessage(project)
    }

    override fun canBuild(modelName: String?): Boolean = modelName == AndroidExtensionsGradleModel::class.java.name

    override fun buildAll(modelName: String?, project: Project): Any {
        val androidExtensionsPlugin = project.plugins.findPlugin("kotlin-android-extensions")

        val androidExtensionsExtension = project.extensions.findByName("androidExtensions")

        val isExperimental = androidExtensionsExtension?.let { ext ->
            val isExperimentalMethod = ext::class.java.methods
                    .firstOrNull { it.name == "isExperimental" && it.parameterCount == 0 }
                    ?: return@let false

            isExperimentalMethod.invoke(ext) as? Boolean
        } ?: false

        val defaultCacheImplementation = androidExtensionsExtension?.let { ext ->
            val defaultCacheImplementationMethod = ext::class.java.methods.firstOrNull {
                it.name == "getDefaultCacheImplementation" && it.parameterCount == 0
            } ?: return@let DEFAULT_CACHE_IMPLEMENTATION_DEFAULT_VALUE

            val enumValue = defaultCacheImplementationMethod.invoke(ext) ?: return@let DEFAULT_CACHE_IMPLEMENTATION_DEFAULT_VALUE

            val optionNameMethod = enumValue::class.java.methods.firstOrNull { it.name == "getOptionName" && it.parameterCount == 0 }
                                   ?: return@let DEFAULT_CACHE_IMPLEMENTATION_DEFAULT_VALUE

            optionNameMethod.invoke(enumValue) as? String
        } ?: DEFAULT_CACHE_IMPLEMENTATION_DEFAULT_VALUE

        return AndroidExtensionsGradleModelImpl(androidExtensionsPlugin != null, isExperimental, defaultCacheImplementation)
    }
}