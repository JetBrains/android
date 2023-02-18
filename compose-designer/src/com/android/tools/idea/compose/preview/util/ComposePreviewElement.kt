/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_MIN_HEIGHT
import com.android.SdkConstants.ATTR_MIN_WIDTH
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.ide.common.resources.Locale
import com.android.resources.Density
import com.android.resources.ScreenRound
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.compose.pickers.preview.utils.findOrParseFromDefinition
import com.android.tools.idea.compose.pickers.preview.utils.getDefaultPreviewDevice
import com.android.tools.idea.compose.preview.defaultFilePreviewElementFinder
import com.android.tools.idea.compose.preview.hasPreviewElements
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.psiFileChangeFlow
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.Wallpaper
import com.android.tools.idea.preview.DisplayPositioning
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElement
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.preview.xml.PreviewXmlBuilder
import com.android.tools.idea.preview.xml.XmlSerializable
import com.android.tools.idea.projectsystem.isTestFile
import com.android.tools.idea.projectsystem.isUnitTestFile
import com.android.tools.idea.uibuilder.model.updateConfigurationScreenSize
import com.android.tools.sdk.CompatibilityRenderTarget
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import java.awt.Dimension
import java.util.Objects
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleRenderContext
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

// Max allowed API
@VisibleForTesting const val MAX_WIDTH = 2000

@VisibleForTesting const val MAX_HEIGHT = 2000

/** Default background to be used by the rendered elements when showBackground is set to true. */
private const val DEFAULT_PREVIEW_BACKGROUND = "?android:attr/windowBackground"

/** Value to use for the wallpaper attribute when none has been specified. */
private const val NO_WALLPAPER_SELECTED = -1

/**
 * Method name to be used when we fail to load a PreviewParameterProvider. In this case, we should
 * create a fake [PreviewElement] and pass this fake method + the PreviewParameterProvider as the
 * composable FQN. `ComposeRenderErrorContributor` should handle the resulting
 * [NoSuchMethodException] that will be thrown.
 */
@VisibleForTesting
const val FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD = "${'$'}FailToLoadPreviewParameterProvider"

/** [InMemoryLayoutVirtualFile] for composable functions. */
internal class ComposeAdapterLightVirtualFile(
  name: String,
  content: String,
  originFileProvider: () -> VirtualFile?
) : InMemoryLayoutVirtualFile("compose-$name", content, originFileProvider)

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the
 * dimension is [UNDEFINED_DIMENSION], the value is converted to `wrap_content`. Otherwise, the
 * value is returned concatenated with `dp`.
 * @param dimension the dimension in dp or [UNDEFINED_DIMENSION]
 * @param defaultValue the value to be used when the given dimension is [UNDEFINED_DIMENSION]
 */
fun dimensionToString(dimension: Int, defaultValue: String = VALUE_WRAP_CONTENT) =
  if (dimension == UNDEFINED_DIMENSION) {
    defaultValue
  } else {
    "${dimension}dp"
  }

private fun KtClass.hasDefaultConstructor() =
  allConstructors.isEmpty().or(allConstructors.any { it.getValueParameters().isEmpty() })

/**
 * Returns whether a `@Composable` [COMPOSE_PREVIEW_ANNOTATION_FQN] is defined in a valid location,
 * which can be either:
 * 1. Top-level functions
 * 2. Non-nested functions defined in top-level classes that have a default (no parameter)
 * constructor
 */
internal fun KtNamedFunction.isValidPreviewLocation(): Boolean {
  if (isTopLevel) {
    return true
  }

  if (parentOfType<KtNamedFunction>() == null) {
    // This is not a nested method
    val containingClass = containingClass()
    if (containingClass != null) {
      // We allow functions that are not top level defined in top level classes that have a default
      // (no parameter) constructor.
      if (containingClass.isTopLevel() && containingClass.hasDefaultConstructor()) {
        return true
      }
    }
  }
  return false
}

internal fun KtNamedFunction.isInTestFile() =
  isTestFile(this.project, this.containingFile.virtualFile)

internal fun KtNamedFunction.isInUnitTestFile() =
  isUnitTestFile(this.project, this.containingFile.virtualFile)

/**
 * Whether this function is not in a test file and is properly annotated with
 * [COMPOSE_PREVIEW_ANNOTATION_FQN], considering indirect annotations when the Multipreview flag is
 * enabled, and validating the location of Previews
 *
 * @see [isValidPreviewLocation]
 */
fun KtNamedFunction.isValidComposePreview() =
  !isInTestFile() &&
    isValidPreviewLocation() &&
    this.toUElementOfType<UMethod>()?.let { it.hasPreviewElements() } == true

/**
 * Truncates the given dimension value to fit between the [min] and [max] values. If the receiver is
 * null, this will return null.
 */
private fun Int?.truncate(min: Int, max: Int): Int? {
  if (this == null) {
    return null
  }

  if (this == UNDEFINED_DIMENSION) {
    return UNDEFINED_DIMENSION
  }

  return minOf(maxOf(this, min), max)
}

/** Empty device spec when the user has not specified any. */
private const val NO_DEVICE_SPEC = ""

/** Returns if the device has any state with [ScreenRound.ROUND] configuration. */
private fun Device.hasRoundFrame(): Boolean =
  allStates.any { it.hardware.screen.screenRound == ScreenRound.ROUND }

/** Returns the same device without any round screen frames. */
private fun Device.withoutRoundScreenFrame(): Device =
  if (hasRoundFrame()) {
    Device.Builder(this).build().also { newDevice ->
      newDevice.allStates.filter { it.hardware.screen.screenRound == ScreenRound.ROUND }.onEach {
        it.hardware.screen.screenRound = ScreenRound.NOTROUND
      }
    }
  } else this

/**
 * Applies the [PreviewConfiguration] to the given [Configuration].
 *
 * [highestApiTarget] should return the highest api target available for a given [Configuration].
 * [devicesProvider] should return all the devices available for a [Configuration].
 * [defaultDeviceProvider] should return which device to use for a [Configuration] if the device
 * specified in the [PreviewConfiguration.deviceSpec] is not available or does not exist in the
 * devices returned by [devicesProvider].
 *
 * If [useDeviceFrame] is false, the device frame configuration will be not used. For example, if
 * the frame is round, this will be ignored and a regular square frame will be applied. This can be
 * used when the `@Preview` element is not displaying the device decorations so the device frame
 * sizes and ratios would not match.
 *
 * If [customSize] is not null, the dimensions will be forced in the resulting configuration.
 */
private fun PreviewConfiguration.applyTo(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?,
  @AndroidDpCoordinate customSize: Dimension? = null,
  useDeviceFrame: Boolean = false
) {
  fun updateRenderConfigurationTargetIfChanged(newTarget: CompatibilityRenderTarget) {
    if ((renderConfiguration.target as? CompatibilityRenderTarget)?.hashString() !=
        newTarget.hashString()
    ) {
      renderConfiguration.target = newTarget
    }
  }

  renderConfiguration.startBulkEditing()
  if (apiLevel != UNDEFINED_API_LEVEL) {
    highestApiTarget(renderConfiguration)?.let {
      updateRenderConfigurationTargetIfChanged(CompatibilityRenderTarget(it, apiLevel, it))
    }
  } else {
    // Use the highest available one when not defined.
    highestApiTarget(renderConfiguration)?.let {
      updateRenderConfigurationTargetIfChanged(
        CompatibilityRenderTarget(it, it.version.apiLevel, it)
      )
    }
  }

  if (theme != null) {
    renderConfiguration.setTheme(theme)
  }

  renderConfiguration.locale = Locale.create(locale)
  renderConfiguration.uiModeFlagValue = uiMode
  renderConfiguration.fontScale = max(0f, fontScale)
  renderConfiguration.wallpaperPath =
    if (wallpaper in Wallpaper.values().indices) Wallpaper.values()[wallpaper].resourcePath
    else null

  val allDevices = devicesProvider(renderConfiguration)
  val device =
    allDevices.findOrParseFromDefinition(deviceSpec) ?: defaultDeviceProvider(renderConfiguration)
  if (device != null) {
    // Ensure the device is reset
    renderConfiguration.setEffectiveDevice(null, null)
    // If the user is not using the device frame, we never want to use the round frame around. See
    // b/215362733
    renderConfiguration.setDevice(device, false)
  }

  customSize?.let {
    // When the device frame is not being displayed and the user has given us some specific sizes,
    // we want to apply those to the
    // device itself.
    // This is to match the intuition that those sizes always determine the size of the composable.
    renderConfiguration.device?.let { device ->
      // The PX are converted to DP by multiplying it by the dpiFactor that is the ratio of the
      // current dpi vs the default dpi (160).
      val dpiFactor = renderConfiguration.density.dpiValue / Density.DEFAULT_DENSITY
      updateConfigurationScreenSize(
        renderConfiguration,
        it.width * dpiFactor,
        it.height * dpiFactor,
        device
      )
    }
  }
  renderConfiguration.finishBulkEditing()
}

/**
 * If specified in the [ComposePreviewElement], this method will return the `widthDp` and `heightDp`
 * dimensions as a [Pair] as long as the device frame is disabled (i.e. `showDecorations` is false).
 */
@AndroidDpCoordinate
private fun ComposePreviewElement.getCustomDeviceSize(): Dimension? =
  if (!displaySettings.showDecoration && configuration.width != -1 && configuration.height != -1) {
    Dimension(configuration.width, configuration.height)
  } else null

/** Applies the [ComposePreviewElement] settings to the given [renderConfiguration]. */
fun ComposePreviewElement.applyTo(renderConfiguration: Configuration) {
  configuration.applyTo(
    renderConfiguration,
    { it.configurationManager.highestApiTarget },
    { it.configurationManager.devices },
    { it.configurationManager.getDefaultPreviewDevice() },
    getCustomDeviceSize(),
    this.displaySettings.showDecoration
  )
}

@TestOnly
fun PreviewConfiguration.applyConfigurationForTest(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?,
  useDeviceFrame: Boolean = false
) {
  applyTo(
    renderConfiguration,
    highestApiTarget,
    devicesProvider,
    defaultDeviceProvider,
    null,
    useDeviceFrame
  )
}

@TestOnly
fun ComposePreviewElement.applyConfigurationForTest(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?
) {
  configuration.applyTo(
    renderConfiguration,
    highestApiTarget,
    devicesProvider,
    defaultDeviceProvider,
    getCustomDeviceSize()
  )
}

/** Contains settings for rendering. */
data class PreviewConfiguration
internal constructor(
  val apiLevel: Int,
  val theme: String?,
  val width: Int,
  val height: Int,
  val locale: String,
  val fontScale: Float,
  val uiMode: Int,
  val deviceSpec: String,
  val wallpaper: Int,
) {
  companion object {
    /**
     * Cleans the given values and creates a PreviewConfiguration. The cleaning ensures that the
     * user inputted value are within reasonable values before the PreviewConfiguration is created
     */
    @JvmStatic
    fun cleanAndGet(
      apiLevel: Int? = null,
      theme: String? = null,
      width: Int? = null,
      height: Int? = null,
      locale: String? = null,
      fontScale: Float? = null,
      uiMode: Int? = null,
      device: String? = null,
      wallpaper: Int? = null,
    ): PreviewConfiguration =
      // We only limit the sizes. We do not limit the API because using an incorrect API level will
      // throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(
        apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
        theme = theme,
        width = width.truncate(1, MAX_WIDTH) ?: UNDEFINED_DIMENSION,
        height = height.truncate(1, MAX_HEIGHT) ?: UNDEFINED_DIMENSION,
        locale = locale ?: "",
        fontScale = fontScale ?: 1f,
        uiMode = uiMode ?: 0,
        deviceSpec = device ?: NO_DEVICE_SPEC,
        wallpaper = wallpaper ?: NO_WALLPAPER_SELECTED,
      )
  }
}

/**
 * Definition of a preview parameter provider. This is defined by annotating parameters with
 * `PreviewParameter`
 *
 * @param name the name of the parameter using the provider
 * @param index the parameter position
 * @param providerClassFqn the class name for the provider
 * @param limit the limit passed to the annotation
 */
data class PreviewParameter(
  val name: String,
  val index: Int,
  val providerClassFqn: String,
  val limit: Int
)

/** Definition of a Composable preview element */
interface ComposePreviewElement : PreviewElement {
  /** Fully Qualified Name of the composable method */
  val composableMethodFqn: String

  /** Preview element configuration that affects how LayoutLib resolves the resources */
  val configuration: PreviewConfiguration
}
/**
 * Definition of a preview element template. This element can dynamically spawn one or more
 * [ComposePreviewElementInstance]s.
 */
interface ComposePreviewElementTemplate : ComposePreviewElement {
  fun instances(): Sequence<ComposePreviewElementInstance>
}

/** Definition of a preview element */
abstract class ComposePreviewElementInstance : ComposePreviewElement, XmlSerializable {
  /** Unique identifier that can be used for filtering. */
  abstract val instanceId: String

  /**
   * Whether the Composable being previewed contains animations. If true, the Preview should allow
   * opening the animation inspector.
   */
  var hasAnimations = false

  override fun toPreviewXml(): PreviewXmlBuilder {
    val matchParent = displaySettings.showDecoration
    val width =
      dimensionToString(
        configuration.width,
        if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT
      )
    val height =
      dimensionToString(
        configuration.height,
        if (matchParent) SdkConstants.VALUE_MATCH_PARENT else VALUE_WRAP_CONTENT
      )
    val xmlBuilder =
      PreviewXmlBuilder(COMPOSE_VIEW_ADAPTER_FQN)
        .androidAttribute(ATTR_LAYOUT_WIDTH, width)
        .androidAttribute(ATTR_LAYOUT_HEIGHT, height)
        // Compose will fail if the top parent is 0,0 in size so avoid that case by setting a min
        // 1x1 parent (b/169230467).
        .androidAttribute(ATTR_MIN_WIDTH, "1px")
        .androidAttribute(ATTR_MIN_HEIGHT, "1px")
        // [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call
        .toolsAttribute("composableName", composableMethodFqn)

    if (displaySettings.showBackground) {
      xmlBuilder.androidAttribute(
        ATTR_BACKGROUND,
        displaySettings.backgroundColor ?: DEFAULT_PREVIEW_BACKGROUND
      )
    }

    return xmlBuilder
  }

  final override fun equals(other: Any?): Boolean {
    // PreviewElement objects can be repeated in the same element. They are considered equals only
    // if they annotate exactly the same
    // element with the same configuration.
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ComposePreviewElementInstance

    return composableMethodFqn == other.composableMethodFqn &&
      instanceId == other.instanceId &&
      displaySettings == other.displaySettings &&
      configuration == other.configuration
  }

  override fun hashCode(): Int =
    Objects.hash(composableMethodFqn, displaySettings, configuration, instanceId)
}

/**
 * Definition of a single preview element instance. This represents a `Preview` with no parameters.
 */
class SingleComposePreviewElementInstance(
  override val composableMethodFqn: String,
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
  override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
  override val configuration: PreviewConfiguration
) : ComposePreviewElementInstance() {
  override val instanceId: String = composableMethodFqn

  companion object {
    @JvmStatic
    @TestOnly
    fun forTesting(
      composableMethodFqn: String,
      displayName: String = "",
      groupName: String? = null,
      showDecorations: Boolean = false,
      showBackground: Boolean = false,
      backgroundColor: String? = null,
      displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL,
      configuration: PreviewConfiguration = PreviewConfiguration.cleanAndGet()
    ) =
      SingleComposePreviewElementInstance(
        composableMethodFqn,
        PreviewDisplaySettings(
          displayName,
          groupName,
          showDecorations,
          showBackground,
          backgroundColor,
          displayPositioning
        ),
        null,
        null,
        configuration
      )
  }
}

private class ParametrizedComposePreviewElementInstance(
  private val basePreviewElement: ComposePreviewElement,
  parameterName: String,
  val providerClassFqn: String,
  val index: Int,
  val maxIndex: Int
) : ComposePreviewElementInstance(), ComposePreviewElement by basePreviewElement {
  override val instanceId: String = "$composableMethodFqn#$parameterName$index"

  override val displaySettings: PreviewDisplaySettings =
    PreviewDisplaySettings(
      "${basePreviewElement.displaySettings.name} ($parameterName ${
        // Make all index numbers to use the same number of digits,
        // so that they can be properly sorted later.
        index.toString().padStart(maxIndex.toString().length, '0')
      })",
      basePreviewElement.displaySettings.group,
      basePreviewElement.displaySettings.showDecoration,
      basePreviewElement.displaySettings.showBackground,
      basePreviewElement.displaySettings.backgroundColor
    )

  override fun toPreviewXml(): PreviewXmlBuilder {
    return super.toPreviewXml()
      // The index within the provider of the element to be rendered
      .toolsAttribute("parameterProviderIndex", index.toString())
      // The FQN of the ParameterProvider class
      .toolsAttribute("parameterProviderClass", providerClassFqn)
  }
}

/**
 * If the [ComposePreviewElement] is a [ParametrizedComposePreviewElementInstance], returns the
 * provider class FQN and the target value index.
 */
internal fun ComposePreviewElement.previewProviderClassAndIndex() =
  if (this is ParametrizedComposePreviewElementInstance) Pair(providerClassFqn, index) else null

/**
 * Definition of a preview element that can spawn multiple [ComposePreviewElement]s based on
 * parameters.
 */
class ParametrizedComposePreviewElementTemplate(
  private val basePreviewElement: ComposePreviewElement,
  val parameterProviders: Collection<PreviewParameter>
) : ComposePreviewElementTemplate, ComposePreviewElement by basePreviewElement {
  /**
   * Returns a [Sequence] of "instantiated" [ComposePreviewElement]s. The will be
   * [ComposePreviewElement] populated with data from the parameter providers.
   */
  override fun instances(): Sequence<ComposePreviewElementInstance> {
    assert(parameterProviders.isNotEmpty()) { "ParametrizedPreviewElement used with no parameters" }

    val file = basePreviewElement.containingFile ?: return sequenceOf()
    if (parameterProviders.size > 1) {
      Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
        .warn("Currently only one ParameterProvider is supported, rest will be ignored")
    }

    val moduleRenderContext = ModuleRenderContext.forFile(file)
    val classLoader =
      StudioModuleClassLoaderManager.get()
        .getPrivate(
          ParametrizedComposePreviewElementTemplate::class.java.classLoader,
          moduleRenderContext,
          this
        )
    try {
      return parameterProviders
        .map { previewParameter ->
          try {
            val parameterProviderClass =
              classLoader.loadClass(previewParameter.providerClassFqn).kotlin
            val parameterProviderSizeMethod =
              parameterProviderClass.functions.single { "getCount" == it.name }.also {
                it.isAccessible = true
              }
            val parameterProvider =
              parameterProviderClass
                .constructors
                .single { it.parameters.isEmpty() } // Find the default constructor
                .also { it.isAccessible = true }
                .call()
            val providerCount =
              min(
                (parameterProviderSizeMethod.call(parameterProvider) as? Int ?: 0),
                previewParameter.limit
              )

            return (0 until providerCount)
              .map { index ->
                ParametrizedComposePreviewElementInstance(
                  basePreviewElement = basePreviewElement,
                  parameterName = previewParameter.name,
                  index = index,
                  maxIndex = providerCount - 1,
                  providerClassFqn = previewParameter.providerClassFqn
                )
              }
              .asSequence()
          } catch (e: Throwable) {
            Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
              .warn(
                "Failed to instantiate ${previewParameter.providerClassFqn} parameter provider",
                e
              )
          }
          // Return a fake SingleComposePreviewElementInstance here. ComposeRenderErrorContributor
          // should handle the exception that will be
          // thrown for this method not being found.
          // TODO(b/238315228): propagate the exception so it's shown on the issues panel.
          val fakeElementFqn =
            "${previewParameter.providerClassFqn}.$FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD"
          return sequenceOf(
            SingleComposePreviewElementInstance(
              fakeElementFqn,
              PreviewDisplaySettings(
                basePreviewElement.displaySettings.name,
                null,
                false,
                false,
                null
              ),
              null,
              null,
              PreviewConfiguration.cleanAndGet()
            )
          )
        }
        .first()
    } finally {
      StudioModuleClassLoaderManager.get().release(classLoader, this)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ParametrizedComposePreviewElementTemplate

    return basePreviewElement == other.basePreviewElement &&
      parameterProviders == other.parameterProviders
  }

  override fun hashCode(): Int = Objects.hash(basePreviewElement, parameterProviders)
}

/**
 * A [PreviewElementProvider] that instantiates any [ComposePreviewElementTemplate]s in the
 * [delegate].
 */
class PreviewElementTemplateInstanceProvider(
  private val delegate: PreviewElementProvider<ComposePreviewElement>
) : PreviewElementProvider<ComposePreviewElementInstance> {
  override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
    delegate.previewElements().flatMap {
      when (it) {
        is ComposePreviewElementTemplate -> it.instances()
        is ComposePreviewElementInstance -> sequenceOf(it)
        else -> {
          Logger.getInstance(PreviewElementTemplateInstanceProvider::class.java)
            .warn("Class was not instance or template ${it::class.qualifiedName}")
          emptySequence()
        }
      }
    }
}

/**
 * Interface to be implemented by classes able to find [ComposePreviewElement]s on [VirtualFile]s.
 */
interface FilePreviewElementFinder {
  /**
   * Returns whether this Preview element finder might apply to the given Kotlin file. The main
   * difference with [findPreviewMethods] is that method might be called on Dumb mode so it must not
   * use any indexes.
   */
  fun hasPreviewMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns if this file contains `@Composable` methods. This is similar to [hasPreviewMethods] but
   * allows deciding if this file might allow previews to be added.
   */
  fun hasComposableMethods(project: Project, vFile: VirtualFile): Boolean

  /**
   * Returns all the [ComposePreviewElement]s present in the passed Kotlin [VirtualFile].
   *
   * This method always runs on smart mode.
   */
  suspend fun findPreviewMethods(
    project: Project,
    vFile: VirtualFile
  ): Collection<ComposePreviewElement>
}

/**
 * Creates a new [StateFlow] containing all the [ComposePreviewElement]s contained in the given
 * [psiFilePointer]. The given [FilePreviewElementFinder] is used to parse the file and obtain the
 * [ComposePreviewElement]s.
 */
@OptIn(FlowPreview::class)
suspend fun previewElementFlowForFile(
  parentDisposable: Disposable,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder
): StateFlow<Set<ComposePreviewElement>> {
  val scope = AndroidCoroutineScope(parentDisposable, coroutineContext)
  val state = MutableStateFlow<Set<ComposePreviewElement>>(emptySet())

  val previewProvider =
    object : PreviewElementProvider<ComposePreviewElement> {
      override suspend fun previewElements(): Sequence<ComposePreviewElement> =
        withContext(workerThread) {
          filePreviewElementProvider()
            .findPreviewMethods(psiFilePointer.project, psiFilePointer.virtualFile)
            .asSequence()
        }
    }

  scope.launch(workerThread) {
    psiFileChangeFlow(psiFilePointer.project, parentDisposable)
      // filter only for the file we care about
      .filter {
        PsiManager.getInstance(psiFilePointer.project)
          .areElementsEquivalent(psiFilePointer.element, it)
      }
      // do not generate events if there has not been modifications to the file since the last time
      .distinctUntilChangedBy { it.modificationStamp }
      // debounce to avoid many equality comparisons of the set
      .debounce(250)
      .collect { state.update { previewProvider.previewElements().toSet() } }
  }

  // Set the initial state to the first elements found
  state.update { previewProvider.previewElements().toSet() }
  return state
}
