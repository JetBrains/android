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

fun CodeInsightTestFixture.stubComposableAnnotation(modulePath: String = "") {
  addFileToProject(
    "$modulePath/src/androidx/compose/runtime/Composable.kt",
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
    """
      .trimIndent()
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

    @Target(
        AnnotationTarget.FILE,
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.TYPE,
        AnnotationTarget.TYPE_PARAMETER,
    )
    annotation class ComposableTarget(val applier: String)

    @Target(AnnotationTarget.ANNOTATION_CLASS)
    annotation class ComposableTargetMarker(val description: String = "")

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

    interface IntState : State<Int> {
        override val value: Int
          get() = intValue
        val intValue: Int
    }

    interface MutableIntState : IntState, MutableState<Int> {
      override var value: Int
        get() = intValue
        set(value) { intValue = value }
      override var intValue: Int
    }

    fun mutableIntStateOf(value: int): MutableIntState = object : MutableIntState {
      override var intValue = value
    }

    interface LongState : State<Long> {
        override val value: Long
          get() = longValue
        val longValue: Long
    }

    interface MutableLongState : LongState, MutableState<Long> {
      override var value: Long
        get() = longValue
        set(value) { longValue = value }
      override var longValue: Long
    }

    fun mutableLongStateOf(value: Long): MutableLongState = object : MutableLongState {
      override var longValue = value
    }

    interface FloatState : State<Float> {
        override val value: Float
          get() = floatValue
        val floatValue: Float
    }

    interface MutableFloatState : FloatState, MutableState<Float> {
      override var value: Float
        get() = floatValue
        set(value) { floaValue = value }
      override var floatValue: Float
    }

    fun mutableFloatStateOf(value: Float): MutableFloatState = object : MutableFloatState {
      override var floatValue = value
    }

    interface DoubleState : State<Double> {
        override val value: Double
          get() = doubleValue
        val doubleValue: Double
    }

    interface MutableDoubleState : DoubleState, MutableState<Double> {
      override var value: Double
        get() = doubleValue
        set(value) { doubleValue = value }
      override var doubleValue: Double
    }

    fun mutableDoubleStateOf(value: Double): MutableDoubleState = object : MutableDoubleState {
      override var doubleValue = value
    }

    inline operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

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
    """
      .trimIndent()
  )
  addFileToProject(
    "src/androidx/compose/runtime/saveable/RememberSaveable.kt",
    // language=kotlin
    """
    package androidx.compose.runtime.saveable

    @Composable
    fun <T : Any> rememberSaveable(
      vararg inputs: Any?,
      saver: Saver<T, out Any> = autoSaver(),
      key: String? = null,
      init: () -> T
    ): T = init()
    """
      .trimIndent()
  )
}

fun CodeInsightTestFixture.stubKotlinStdlib() {
  addFileToProject(
    "src/kotlin/io/Console.kt",
    // language=kotlin
    """
    package kotlin.io
    fun print(message: Any?) {}
    fun println(message: Any?) {}
    """
      .trimIndent()
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
    """
      .trimIndent()
  )

  addFileToProject(
    "src/kotlin/math/Math.kt",
    // language=kotlin
    """
    package kotlin.math

    object Math {
      fun random(): Float = 0.5
    }
    """
      .trimIndent()
  )

  addFileToProject(
    "src/kotlin/util/Lazy.kt",
    // language=kotlin
    """
    package kotlin

    interface Lazy<out T> {
      val value: T
    }

    inline operator fun <T> Lazy<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

    fun <T> lazy(init: () -> T): Lazy<T> = SynchronizedLazyImpl(init)

    private class SynchronizedLazyImpl<out T>(init: () -> T) : Lazy<T> {
      private var _value: Any? = null
      override val value: T
        get() {
          if (_value == null) {
            _value = init()
          }
          return _value
        }
    }
    """
      .trimIndent()
  )

  addFileToProject(
    "src/kotlin/util/Standard.kt",
    // language=kotlin
    """
    package kotlin

    import java.lang.Exception

    inline fun TODO(): Nothing = throw Exception()
    """
      .trimIndent()
  )
}

fun CodeInsightTestFixture.stubPreviewAnnotation(modulePath: String = "") {
  addFileToProject(
    "$modulePath/src/androidx/compose/ui/tooling/preview/Preview.kt",
    // language=kotlin
    """
    package androidx.compose.ui.tooling.preview

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
      val locale: String = "",
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
    """
      .trimIndent()
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
    """
      .trimIndent()
  this.stubClassAsLibrary(
    "configuration",
    SdkConstants.CLASS_CONFIGURATION,
    JavaFileType.INSTANCE,
    fileContents
  )
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
    """
      .trimIndent()
  this.stubClassAsLibrary(
    "devices",
    "$devicesPackageName.Devices",
    KotlinFileType.INSTANCE,
    fileContents
  )
}

/** Seems to only work properly in memory/light fixtures. Such as AndroidProjectRule#inMemory */
private fun CodeInsightTestFixture.stubClassAsLibrary(
  libraryName: String,
  fqClassName: String,
  fileType: FileType,
  fileContents: String
) {
  val filePath = fqClassName.replace('.', '/') + '.' + fileType.defaultExtension
  tempDirFixture.createFile("external/$libraryName/$filePath", fileContents)
  val libraryDir = tempDirFixture.findOrCreateDir("external/$libraryName")
  PsiTestUtil.addProjectLibrary(module, libraryName, emptyList(), listOf(libraryDir))
}
