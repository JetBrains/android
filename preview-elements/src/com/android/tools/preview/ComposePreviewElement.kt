/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.preview

import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_MIN_HEIGHT
import com.android.SdkConstants.ATTR_MIN_WIDTH
import com.android.SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.ide.common.resources.Locale
import com.android.resources.Density
import com.android.sdklib.AndroidDpCoordinate
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.Wallpaper
import com.android.tools.configurations.updateScreenSize
import com.android.tools.preview.config.findOrParseFromDefinition
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.useWithClassLoader
import com.android.tools.sdk.CompatibilityRenderTarget
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.Objects
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

const val UNDEFINED_API_LEVEL = -1
const val UNDEFINED_DIMENSION = -1

const val MAX_WIDTH = 2000
const val MAX_HEIGHT = 2000

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

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the
 * dimension is [UNDEFINED_DIMENSION], the value is converted to `wrap_content`. Otherwise, the
 * value is returned concatenated with `dp`.
 *
 * @param dimension the dimension in dp or [UNDEFINED_DIMENSION]
 * @param defaultValue the value to be used when the given dimension is [UNDEFINED_DIMENSION]
 */
fun dimensionToString(dimension: Int, defaultValue: String = VALUE_WRAP_CONTENT) =
  if (dimension == UNDEFINED_DIMENSION) {
    defaultValue
  } else {
    "${dimension}dp"
  }

/** Empty device spec when the user has not specified any. */
private const val NO_DEVICE_SPEC = ""

/**
 * Applies the [PreviewConfiguration] to the given [Configuration].
 *
 * [highestApiTarget] should return the highest api target available for a given [Configuration].
 * [devicesProvider] should return all the devices available for a [Configuration].
 * [defaultDeviceProvider] should return which device to use for a [Configuration] if the device
 * specified in the [PreviewConfiguration.deviceSpec] is not available or does not exist in the
 * devices returned by [devicesProvider].
 *
 * If [customSize] is not null, the dimensions will be forced in the resulting configuration.
 */
private fun PreviewConfiguration.applyTo(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?,
  @AndroidDpCoordinate customSize: Dimension? = null,
) {
  fun updateRenderConfigurationTargetIfChanged(newTarget: IAndroidTarget) {
    if (renderConfiguration.target?.hashString() != newTarget.hashString()) {
      renderConfiguration.target = newTarget
    }
  }

  renderConfiguration.startBulkEditing()
  renderConfiguration.imageTransformation = imageTransformation
  if (apiLevel != UNDEFINED_API_LEVEL) {
    val newTarget =
      renderConfiguration.settings.targets.firstOrNull { it.version.apiLevel == apiLevel }
    highestApiTarget(renderConfiguration)?.let {
      updateRenderConfigurationTargetIfChanged(CompatibilityRenderTarget(it, apiLevel, newTarget))
    }
  } else {
    // Use the highest available one when not defined.
    highestApiTarget(renderConfiguration)?.let { updateRenderConfigurationTargetIfChanged(it) }
  }

  if (theme != null) {
    renderConfiguration.setTheme(theme)
  }

  renderConfiguration.locale = Locale.create(locale)
  renderConfiguration.uiModeFlagValue = uiMode
  renderConfiguration.fontScale = max(0f, fontScale)
  renderConfiguration.setWallpaper(Wallpaper.values().getOrNull(wallpaper))

  val allDevices = devicesProvider(renderConfiguration)
  val device =
    allDevices.findOrParseFromDefinition(deviceSpec) ?: defaultDeviceProvider(renderConfiguration)
  if (device != null) {
    // Ensure the device is reset
    renderConfiguration.setEffectiveDevice(null, null)
    // If the user is not using the device frame, we never want to use the round frame around. See
    // b/215362733
    renderConfiguration.setDevice(device, false)
    // If there is no application theme set, we might need to change the theme when changing the
    // device, because different devices might
    // have different default themes.
    renderConfiguration.setTheme(renderConfiguration.getPreferredTheme())
  }

  customSize?.let {
    // When the device frame is not being displayed and the user has given us some specific sizes,
    // we want to apply those to the
    // device itself.
    // This is to match the intuition that those sizes always determine the size of the composable.
    renderConfiguration.device?.let { device ->
      // The PX are converted to DP by multiplying it by the dpiFactor that is the ratio of the
      // current dpi vs the default dpi (160).
      val dpiFactor = 1.0 * renderConfiguration.density.dpiValue / Density.DEFAULT_DENSITY
      renderConfiguration.updateScreenSize((it.width * dpiFactor).toInt(), (it.height * dpiFactor).toInt(), device)
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
    { it.settings.highestApiTarget },
    { it.settings.devices },
    { it.settings.getDefaultPreviewDevice() },
    getCustomDeviceSize()
  )
}

@TestOnly
fun PreviewConfiguration.applyConfigurationForTest(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?,
) {
  applyTo(renderConfiguration, highestApiTarget, devicesProvider, defaultDeviceProvider)
}

@TestOnly
fun ComposePreviewElement.applyConfigurationForTest(
  renderConfiguration: Configuration,
  highestApiTarget: (Configuration) -> IAndroidTarget?,
  devicesProvider: (Configuration) -> Collection<Device>,
  defaultDeviceProvider: (Configuration) -> Device?,
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
  val imageTransformation: Consumer<BufferedImage>?,
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
      imageTransformation: Consumer<BufferedImage>? = null,
    ): PreviewConfiguration =
      // We only limit the sizes. We do not limit the API because using an incorrect API level will
      // throw an exception that
      // we will handle and any other error.
      PreviewConfiguration(
        apiLevel = apiLevel ?: UNDEFINED_API_LEVEL,
        theme = theme,
        width = width?.takeIf { it != UNDEFINED_DIMENSION }?.coerceIn(1, MAX_WIDTH)
            ?: UNDEFINED_DIMENSION,
        height = height?.takeIf { it != UNDEFINED_DIMENSION }?.coerceIn(1, MAX_HEIGHT)
            ?: UNDEFINED_DIMENSION,
        locale = locale ?: "",
        fontScale = fontScale ?: 1f,
        uiMode = uiMode ?: 0,
        deviceSpec = device ?: NO_DEVICE_SPEC,
        wallpaper = wallpaper ?: NO_WALLPAPER_SELECTED,
        imageTransformation = imageTransformation,
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
  val limit: Int,
)

/** Definition of a Composable preview element */
interface ComposePreviewElement : MethodPreviewElement {
  /** Fully Qualified Name of the composable method */
  override val methodFqn: String

  /** Preview element configuration that affects how LayoutLib resolves the resources */
  val configuration: PreviewConfiguration

  /**
   * [ComposePreviewElementInstance]s that this [ComposePreviewElement] can be resolved into. A single [ComposePreviewElement] can produce
   * multiple [ComposePreviewElementInstance]s for example if @Composable method has parameters.
   */
  fun resolve(): Sequence<ComposePreviewElementInstance>
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

  override fun resolve(): Sequence<ComposePreviewElementInstance> = sequenceOf(this)

  override fun toPreviewXml(): PreviewXmlBuilder {
    val width = dimensionToString(configuration.width, VALUE_WRAP_CONTENT)
    val height = dimensionToString(configuration.height, VALUE_WRAP_CONTENT)
    val xmlBuilder =
      PreviewXmlBuilder(CLASS_COMPOSE_VIEW_ADAPTER)
        .androidAttribute(ATTR_LAYOUT_WIDTH, width)
        .androidAttribute(ATTR_LAYOUT_HEIGHT, height)
        // Compose will fail if the top parent is 0,0 in size so avoid that case by setting a min
        // 1x1 parent (b/169230467).
        .androidAttribute(ATTR_MIN_WIDTH, "1px")
        .androidAttribute(ATTR_MIN_HEIGHT, "1px")
        // [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call
        .toolsAttribute("composableName", methodFqn)

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

    return methodFqn == other.methodFqn &&
      instanceId == other.instanceId &&
      displaySettings == other.displaySettings &&
      configuration == other.configuration
  }

  override fun hashCode(): Int = Objects.hash(methodFqn, displaySettings, configuration, instanceId)
}

/**
 * Definition of a single preview element instance. This represents a `Preview` with no parameters.
 */
class SingleComposePreviewElementInstance(
  override val methodFqn: String,
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
  override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
  override val configuration: PreviewConfiguration,
) : ComposePreviewElementInstance() {
  override val instanceId: String = methodFqn

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
      configuration: PreviewConfiguration = PreviewConfiguration.cleanAndGet(),
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

class
ParametrizedComposePreviewElementInstance(
  private val basePreviewElement: ComposePreviewElement,
  parameterName: String,
  val providerClassFqn: String,
  val index: Int,
  val maxIndex: Int,
) : ComposePreviewElementInstance(), ComposePreviewElement by basePreviewElement {
  override val instanceId: String = "$methodFqn#$parameterName$index"

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
 * Definition of a preview element that can spawn multiple [ComposePreviewElement]s based on
 * parameters.
 */
open class ParametrizedComposePreviewElementTemplate(
  private val basePreviewElement: ComposePreviewElement,
  val parameterProviders: Collection<PreviewParameter>,
  private val renderContextFactory: (PsiFile?) -> ModuleRenderContext?,
) : ComposePreviewElement by basePreviewElement {
  /**
   * Returns a [Sequence] of "instantiated" [ComposePreviewElement]s. The [ComposePreviewElement]s
   * will be populated with data from the parameter providers.
   */
  override fun resolve(): Sequence<ComposePreviewElementInstance> {
    assert(parameterProviders.isNotEmpty()) { "ParametrizedPreviewElement used with no parameters" }

    if (parameterProviders.size > 1) {
      Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
        .warn("Currently only one ParameterProvider is supported, rest will be ignored")
    }

    val moduleRenderContext = renderContextFactory(basePreviewElement.containingFile) ?: return sequenceOf()
    ModuleClassLoaderManager.get()
      .getPrivate(
        ParametrizedComposePreviewElementTemplate::class.java.classLoader,
        moduleRenderContext
      )
      .useWithClassLoader { classLoader ->
        return parameterProviders
          .map { previewParameter -> loadPreviewParameterProvider(classLoader, previewParameter) }
          .first()
      }
  }

  private fun loadPreviewParameterProvider(
    classLoader: ClassLoader,
    previewParameter: PreviewParameter,
  ): Sequence<ComposePreviewElementInstance> {
    try {
      val parameterProviderClass = classLoader.loadClass(previewParameter.providerClassFqn)
      val parameterProviderSizeMethod =
        parameterProviderClass.methods
          .single { "getCount" == it.name }
          .also { it.isAccessible = true }
      val parameterProvider =
        parameterProviderClass.constructors
          .single { it.parameters.isEmpty() } // Find the default constructor
          .also { it.isAccessible = true }
          .newInstance()
      val parameterProviderSize = parameterProviderSizeMethod.invoke(parameterProvider) as? Int ?: 0
      val providerCount = min(parameterProviderSize, previewParameter.limit)

      if (providerCount == 0) {
        // Returns a ParametrizedComposePreviewElementInstance with the error:
        // "IndexOutOfBoundsException: Sequence doesn't contain element at index 0."
        // In case providerCount is 0 we want to show an error instance that there are no
        // PreviewParameters instead of showing nothing.
        // TODO(b/238315228): propagate the exception so it's shown on the issues panel instead of
        // forcing the error changing the index.
        Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
          .warn(
            "Failed to instantiate ${previewParameter.providerClassFqn} parameter provider: no parameters found"
          )
        return sequenceOf(
          ParametrizedComposePreviewElementInstance(
            basePreviewElement = basePreviewElement,
            parameterName = previewParameter.name,
            index = 0,
            maxIndex = 0,
            providerClassFqn = previewParameter.providerClassFqn
          )
        )
      } else {
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
      }
    } catch (e: Throwable) {
      Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
        .warn("Failed to instantiate ${previewParameter.providerClassFqn} parameter provider", e)
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
        PreviewDisplaySettings(basePreviewElement.displaySettings.name, null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )
    )
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
