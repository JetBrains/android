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
import com.android.testutils.TestUtils
import com.android.tools.idea.util.toIoFile
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.io.ZipUtil
import java.io.File
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinFileType

private const val COMPOSE_LIB_VERSION = "1.7.1"

private enum class ComposeLib(val libPath: String) {
  Runtime(
    "androidx/compose/runtime/runtime-android/$COMPOSE_LIB_VERSION/runtime-android-$COMPOSE_LIB_VERSION.aar"
  ),
  RuntimeSaveable(
    "androidx/compose/runtime/runtime-saveable-android/$COMPOSE_LIB_VERSION/runtime-saveable-android-$COMPOSE_LIB_VERSION.aar"
  ),
  Ui("androidx/compose/ui/ui-android/$COMPOSE_LIB_VERSION/ui-android-$COMPOSE_LIB_VERSION.aar"),
  UiGraphics(
    "androidx/compose/ui/ui-graphics-android/$COMPOSE_LIB_VERSION/ui-graphics-android-$COMPOSE_LIB_VERSION.aar"
  ),
}

fun CodeInsightTestFixture.addComposeRuntimeDep() {
  addLibDep(ComposeLib.Runtime)
}

fun CodeInsightTestFixture.addComposeRuntimeSaveableDep() {
  addLibDep(ComposeLib.RuntimeSaveable)
}

fun CodeInsightTestFixture.addComposeUiDep() {
  addLibDep(ComposeLib.Ui)
}

fun CodeInsightTestFixture.addComposeUiGraphicsDep() {
  addLibDep(ComposeLib.UiGraphics)
}

private fun CodeInsightTestFixture.addLibDep(composeLib: ComposeLib) {
  val aarPath = File(TestUtils.getLocalMavenRepoFile(composeLib.libPath).toString()).toPath()

  val libName = composeLib.libPath.split("/")[3]
  val tempDir = tempDirFixture.findOrCreateDir("composeTestLib_$libName").toIoFile()
  ZipUtil.extract(aarPath, tempDir.toPath()) { _, filename -> filename == "classes.jar" }
  val jarPath = File(tempDir, "classes.jar").path

  LocalFileSystem.getInstance().refreshAndFindFileByPath(jarPath)
  PsiTestUtil.addLibrary(module, jarPath)
}

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
      .trimIndent(),
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
      .trimIndent(),
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
    fileContents,
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
    fileContents,
  )
}

/** Seems to only work properly in memory/light fixtures. Such as AndroidProjectRule#inMemory */
private fun CodeInsightTestFixture.stubClassAsLibrary(
  libraryName: String,
  fqClassName: String,
  fileType: FileType,
  fileContents: String,
) {
  val filePath = fqClassName.replace('.', '/') + '.' + fileType.defaultExtension
  tempDirFixture.createFile("external/$libraryName/$filePath", fileContents)
  val libraryDir = tempDirFixture.findOrCreateDir("external/$libraryName")
  PsiTestUtil.addProjectLibrary(module, libraryName, emptyList(), listOf(libraryDir))
}
