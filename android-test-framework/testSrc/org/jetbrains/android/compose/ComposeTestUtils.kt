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
import com.android.test.testutils.TestUtils
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

private const val DEFAULT_COMPOSE_LIB_VERSION = "1.7.1"

private enum class ComposeLib(private val libPath: String) {
  Runtime(
    "androidx/compose/runtime/runtime-android/%s/runtime-android-%s.aar"
  ),
  RuntimeSaveable(
    "androidx/compose/runtime/runtime-saveable-android/%s/runtime-saveable-android-%s.aar"
  ),
  Ui("androidx/compose/ui/ui-android/%s/ui-android-%s.aar"),
  UiGraphics(
    "androidx/compose/ui/ui-graphics-android/%s/ui-graphics-android-%s.aar"
  ),
  UiToolingPreview(
    "androidx/compose/ui/ui-tooling-preview-android/%s/ui-tooling-preview-android-%s.aar"
  );

  fun getLibPath(version: String) = libPath.format(version, version)
}

fun CodeInsightTestFixture.addComposeRuntimeDep(version: String = DEFAULT_COMPOSE_LIB_VERSION) {
  addLibDep(ComposeLib.Runtime, version)
}

fun CodeInsightTestFixture.addComposeRuntimeSaveableDep(version: String = DEFAULT_COMPOSE_LIB_VERSION) {
  addLibDep(ComposeLib.RuntimeSaveable, version)
}

fun CodeInsightTestFixture.addComposeUiDep(version: String = DEFAULT_COMPOSE_LIB_VERSION) {
  addLibDep(ComposeLib.Ui, version)
}

fun CodeInsightTestFixture.addComposeUiGraphicsDep(version: String = DEFAULT_COMPOSE_LIB_VERSION) {
  addLibDep(ComposeLib.UiGraphics, version)
}

fun CodeInsightTestFixture.addComposeUiToolingPreviewDep(version: String = DEFAULT_COMPOSE_LIB_VERSION) {
  addLibDep(ComposeLib.UiToolingPreview, version)
}

private fun CodeInsightTestFixture.addLibDep(composeLib: ComposeLib, version: String) {
  val libPath = composeLib.getLibPath(version)
  val aarPath = File(TestUtils.getLocalMavenRepoFile(libPath).toString()).toPath()

  val libName = libPath.split("/")[3]
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

    object Wallpapers {
        /** Default value, representing dynamic theming not enabled. */
        const val NONE = -1
        /** Example wallpaper whose dominant colour is red. */
        const val RED_DOMINATED_EXAMPLE = 0
        /** Example wallpaper whose dominant colour is green. */
        const val GREEN_DOMINATED_EXAMPLE = 1
        /** Example wallpaper whose dominant colour is blue. */
        const val BLUE_DOMINATED_EXAMPLE = 2
        /** Example wallpaper whose dominant colour is yellow. */
        const val YELLOW_DOMINATED_EXAMPLE = 3
    }


    @Repeatable
    annotation class Preview(
      val name: String = "",
      val group: String = "",
      val apiLevel: Int = -1,
      val widthDp: Int = -1,
      val heightDp: Int = -1,
      val locale: String = "",
      val fontScale: Float = 1f,
      val showSystemUi: Boolean = false,
      val showBackground: Boolean = false,
      val backgroundColor: Long = 0,
      val uiMode: Int = 0,
      val device: String = Devices.DEFAULT,
      val wallpaper: Int = Wallpapers.NONE,
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
