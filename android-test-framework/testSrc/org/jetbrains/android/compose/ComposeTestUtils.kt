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
package org.jetbrains.android.compose

import com.android.SdkConstants
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinFileType

fun CodeInsightTestFixture.stubComposableAnnotation(composableAnnotationPackage: String = "androidx.compose") {
  addFileToProject(
    "src/${composableAnnotationPackage.replace(".", "/")}/Composable.kt",
    // language=kotlin
    """
    package $composableAnnotationPackage

    annotation class Composable
    """.trimIndent()
  )
}

fun CodeInsightTestFixture.stubComposeRuntime() {
  addFileToProject(
    "src/androidx/compose/runtime/Runtime.kt",
    // language=kotlin
    """
    package androidx.compose.runtime

    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.TYPE_USAGE,
        AnnotationTarget.TYPE,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.PROPERTY_GETTER
    )
    annotation class Composable

    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER
    )
    annotation class ReadOnlyComposable

    @Target(AnnotationTarget.TYPE)
    annotation class DisallowComposableCalls


    @Composable
    inline fun <T> remember(calculation: @DisallowComposableCalls () -> T): T = calculation()

    interface State<T> {
        val value: T
    }

    interface MutableState<T> : State<T> {
        override var value: T
        operator fun component1(): T
        operator fun component2(): (T) -> Unit
    }

    inline operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    interface SnapshotMutationPolicy<T> {
        fun equivalent(a: T, b: T): Boolean
        fun merge(previous: T, current: T, applied: T): T? = null
    }

    private object StructuralEqualityPolicy : SnapshotMutationPolicy<Any?> {
        override fun equivalent(a: Any?, b: Any?) = a == b
    }

    fun <T> mutableStateOf(
        value: T,
        policy: SnapshotMutationPolicy<T> = StructuralEqualityPolicy
    ): MutableState<T> = SnapshotMutableStateImpl(value)

    private class SnapshotMutableStateImpl<T>(override var value: T) {
      override operator fun component1(): T = value
      override operator fun component2(): (T) -> Unit = { value = it }
    }
    """.trimIndent()
  )
}

fun CodeInsightTestFixture.stubKotlinStdlib() {
  addFileToProject(
    "src/kotlin/io/Console.kt",
    // language=kotlin
    """
    package kotlin.io
    fun print(message: Any?) {}
    """.trimIndent()
  )
  addFileToProject(
    "src/kotlin/collections/JVMCollections.kt",
    // language=kotlin
    """
    package kotlin.collections

    fun <T> listOf(element: T): List<T> = java.util.Collections.singletonList(element)
    fun <T> listOf(vararg elements: T): List<T> = if (elements.size > 0) elements.asList() else emptyList()

    inline fun <T> Iterable<T>.forEach(action: (T) -> Unit) {
        for (element in this) action(element)
    }
    """.trimIndent()
  )

  addFileToProject(
    "src/kotlin/math/Math.kt",
    // language=kotlin
    """
    package kotlin.math

    object Math {
      fun random(): Float = 0.5
    }
    """.trimIndent()
  )
}

fun CodeInsightTestFixture.stubPreviewAnnotation(previewAnnotationPackage: String = "androidx.compose.ui.tooling.preview") {
  addFileToProject(
    "src/${previewAnnotationPackage.replace(".", "/")}/Preview.kt",
    // language=kotlin
    """
    package $previewAnnotationPackage

    import kotlin.reflect.KClass

    object Devices {
        const val DEFAULT = ""

        const val NEXUS_7 = "id:Nexus 7"
        const val NEXUS_10 = "name:Nexus 10"
    }


    @Repeatable
    annotation class Preview(
      val name: String = "",
      val group: String = "",
      val apiLevel: Int = -1,
      val theme: String = "",
      val widthDp: Int = -1,
      val heightDp: Int = -1,
      val locale: String = ""
      val fontScale: Float = 1f,
      val showDecoration: Boolean = false,
      val showBackground: Boolean = false,
      val backgroundColor: Long = 0,
      val uiMode: Int = 0,
      val device: String = ""
    )

    interface PreviewParameterProvider<T> {
        val values: Sequence<T>
        val count get() = values.count()
    }

    annotation class PreviewParameter(
        val provider: KClass<out PreviewParameterProvider<*>>,
        val limit: Int = Int.MAX_VALUE
    )
    """.trimIndent()
  )
}

/**
 * Memory/light fixture only.
 *
 * @see stubClassAsLibrary
 */
fun CodeInsightTestFixture.stubConfigurationAsLibrary() {
  val packageName = SdkConstants.CLASS_CONFIGURATION.substringBefore(".Configuration")

  @Language("JAVA")
  val fileContents =
    """
    package $packageName;
    public final class Configuration {
        public static final int UI_MODE_TYPE_UNDEFINED = 0x00;
        public static final int UI_MODE_TYPE_NORMAL = 0x01;
        public static final int UI_MODE_TYPE_DESK = 0x02;
        public static final int UI_MODE_TYPE_CAR = 0x03;
    }
    """.trimIndent()
  this.stubClassAsLibrary("configuration", SdkConstants.CLASS_CONFIGURATION, JavaFileType.INSTANCE, fileContents)
}

/**
 * Memory/light fixture only.
 *
 * @see stubClassAsLibrary
 */
fun CodeInsightTestFixture.stubDevicesAsLibrary(devicesPackageName: String) {
  @Language("kotlin")
  val fileContents =
    """
    package $devicesPackageName
    object Devices {
        const val DEFAULT = ""

        const val NEXUS_7 = "id:Nexus 7"
        const val PIXEL = "id:pixel"
        const val AUTOMOTIVE_1024p = "id:automotive_1024p_landscape"
        const val NEXUS_10 = "name:Nexus 10"
        const val PIXEL_4 = "id:pixel_4"
    }
    """.trimIndent()
  this.stubClassAsLibrary("devices", "$devicesPackageName.Devices", KotlinFileType.INSTANCE, fileContents)
}

/**
 * Seems to only work properly in memory/light fixtures. Such as AndroidProjectRule#inMemory
 */
private fun CodeInsightTestFixture.stubClassAsLibrary(libraryName: String, fqClassName: String, fileType: FileType, fileContents: String) {
  val filePath = fqClassName.replace('.', '/') + '.' + fileType.defaultExtension
  tempDirFixture.createFile("external/$libraryName/$filePath", fileContents)
  val libraryDir = tempDirFixture.findOrCreateDir("external/$libraryName")
  PsiTestUtil.addProjectLibrary(module, libraryName, emptyList(), listOf(libraryDir))
}
