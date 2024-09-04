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
import com.android.annotations.TestOnly
import com.android.tools.environment.Logger
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.useWithClassLoader
import com.google.common.annotations.VisibleForTesting
import java.util.Objects
import kotlin.math.min

/** Default background to be used by the rendered elements when showBackground is set to true. */
private const val DEFAULT_PREVIEW_BACKGROUND = "?android:attr/windowBackground"

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

/**
 * Definition of a Composable preview element. [T] represents a generic type specifying the location
 * of the code. See [PreviewElement] for more details.
 */
interface ComposePreviewElement<T> : MethodPreviewElement<T>, ConfigurablePreviewElement<T> {
  /**
   * [ComposePreviewElementInstance]s that this [ComposePreviewElement] can be resolved into. A
   * single [ComposePreviewElement] can produce multiple [ComposePreviewElementInstance]s for
   * example if @Composable method has parameters.
   */
  fun resolve(): Sequence<ComposePreviewElementInstance<T>>
}

/** Definition of a preview element */
abstract class ComposePreviewElementInstance<T> :
  ComposePreviewElement<T>, XmlSerializable, PreviewElementInstance<T> {
  /**
   * Whether the Composable being previewed contains animations. If true, the Preview should allow
   * opening the animation inspector.
   */
  override var hasAnimations = false

  override fun resolve(): Sequence<ComposePreviewElementInstance<T>> = sequenceOf(this)

  abstract override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ): ComposePreviewElementInstance<T>

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
        displaySettings.backgroundColor ?: DEFAULT_PREVIEW_BACKGROUND,
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

    other as ComposePreviewElementInstance<T>

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
class SingleComposePreviewElementInstance<T>(
  override val methodFqn: String,
  override val displaySettings: PreviewDisplaySettings,
  override val previewElementDefinition: T?,
  override val previewBody: T?,
  override val configuration: PreviewConfiguration,
) : ComposePreviewElementInstance<T>() {
  override val instanceId: String = methodFqn

  override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ) =
    SingleComposePreviewElementInstance(
      methodFqn,
      displaySettings,
      previewElementDefinition,
      previewBody,
      config,
    )

  companion object {
    @JvmStatic
    @TestOnly
    fun <T> forTesting(
      composableMethodFqn: String,
      displayName: String = "",
      groupName: String? = null,
      showDecorations: Boolean = false,
      showBackground: Boolean = false,
      backgroundColor: String? = null,
      displayPositioning: DisplayPositioning = DisplayPositioning.NORMAL,
      configuration: PreviewConfiguration = PreviewConfiguration.cleanAndGet(),
    ) =
      SingleComposePreviewElementInstance<T>(
        composableMethodFqn,
        PreviewDisplaySettings(
          displayName,
          groupName,
          showDecorations,
          showBackground,
          backgroundColor,
          displayPositioning,
        ),
        null,
        null,
        configuration,
      )
  }
}

class ParametrizedComposePreviewElementInstance<T>(
  private val basePreviewElement: ComposePreviewElement<T>,
  parameterName: String?,
  val providerClassFqn: String,
  val index: Int,
  val maxIndex: Int,
) : ComposePreviewElementInstance<T>(), ComposePreviewElement<T> by basePreviewElement {
  override var hasAnimations = false
  override val instanceId: String = "$methodFqn#$parameterName$index"

  override fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ): ParametrizedComposePreviewElementInstance<T> {
    val singleInstance =
      SingleComposePreviewElementInstance(
        methodFqn,
        displaySettings,
        previewElementDefinition,
        previewBody,
        config,
      )
    return ParametrizedComposePreviewElementInstance(
      singleInstance,
      null,
      providerClassFqn,
      index,
      maxIndex,
    )
  }

  override val displaySettings: PreviewDisplaySettings =
    PreviewDisplaySettings(
      getDisplayName(parameterName),
      basePreviewElement.displaySettings.group,
      basePreviewElement.displaySettings.showDecoration,
      basePreviewElement.displaySettings.showBackground,
      basePreviewElement.displaySettings.backgroundColor,
    )

  override fun toPreviewXml(): PreviewXmlBuilder {
    return super.toPreviewXml()
      // The index within the provider of the element to be rendered
      .toolsAttribute("parameterProviderIndex", index.toString())
      // The FQN of the ParameterProvider class
      .toolsAttribute("parameterProviderClass", providerClassFqn)
  }

  private fun getDisplayName(parameterName: String?): String {
    return if (parameterName == null) {
      // This case corresponds to the parameter already having been added to the display name,
      // so it should not be added again.
      basePreviewElement.displaySettings.name
    } else {
      "${basePreviewElement.displaySettings.name} ($parameterName ${
        // Make all index numbers to use the same number of digits,
        // so that they can be properly sorted later.
        index.toString().padStart(maxIndex.toString().length, '0')
      })"
    }
  }
}

/**
 * Definition of a preview element that can spawn multiple [ComposePreviewElement]s based on
 * parameters. [ModuleClassLoader] constructed with the provided [parentClassLoader] should be able
 * to load classes of [PreviewParameter.providerClassFqn] and create instances of those. Therefore,
 * the caller should make sure [parentClassLoader] is aware of Android platform classes as those
 * might be referenced by [parameterProviders]s.
 */
open class ParametrizedComposePreviewElementTemplate<T>(
  private val basePreviewElement: ComposePreviewElement<T>,
  val parameterProviders: Collection<PreviewParameter>,
  private val parentClassLoader: ClassLoader =
    ParametrizedComposePreviewElementTemplate::class.java.classLoader,
  private val renderContextFactory: (ComposePreviewElement<T>) -> ModuleRenderContext?,
) : ComposePreviewElement<T> by basePreviewElement {
  /**
   * Returns a [Sequence] of "instantiated" [ComposePreviewElement]s. The [ComposePreviewElement]s
   * will be populated with data from the parameter providers.
   */
  override fun resolve(): Sequence<ComposePreviewElementInstance<T>> {
    assert(parameterProviders.isNotEmpty()) { "ParametrizedPreviewElement used with no parameters" }

    if (parameterProviders.size > 1) {
      Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
        .warn("Currently only one ParameterProvider is supported, rest will be ignored")
    }

    val moduleRenderContext = renderContextFactory(basePreviewElement) ?: return sequenceOf()
    ModuleClassLoaderManager.get()
      .getPrivate(parentClassLoader, moduleRenderContext)
      .useWithClassLoader { classLoader ->
        return parameterProviders
          .map { previewParameter -> loadPreviewParameterProvider(classLoader, previewParameter) }
          .first()
      }
  }

  private fun loadPreviewParameterProvider(
    classLoader: ClassLoader,
    previewParameter: PreviewParameter,
  ): Sequence<ComposePreviewElementInstance<T>> {
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
            providerClassFqn = previewParameter.providerClassFqn,
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
              providerClassFqn = previewParameter.providerClassFqn,
            )
          }
          .asSequence()
      }
    } catch (e: Throwable) {
      Logger.getInstance(ParametrizedComposePreviewElementTemplate::class.java)
        .warn("Failed to instantiate ${previewParameter.providerClassFqn} parameter provider", e)
    }
    // Return a fake SingleComposePreviewElementInstance here. ComposeRenderErrorContributor
    // should handle the exception that will be thrown for this method not being found.
    // TODO(b/238315228): propagate the exception so it's shown on the issues panel.
    val fakeElementFqn =
      "${previewParameter.providerClassFqn}.$FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD"
    return sequenceOf(
      SingleComposePreviewElementInstance(
        fakeElementFqn,
        PreviewDisplaySettings(basePreviewElement.displaySettings.name, null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ParametrizedComposePreviewElementTemplate<*>

    return basePreviewElement == other.basePreviewElement &&
      parameterProviders == other.parameterProviders
  }

  override fun hashCode(): Int = Objects.hash(basePreviewElement, parameterProviders)
}
