// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.projectsystem.gradle

import com.android.builder.model.*
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.gradle.model.ModelCache
import com.android.projectmodel.*
import com.android.projectmodel.AndroidProject
import com.android.projectmodel.Variant
import com.android.resources.ResourceType
import com.android.sdklib.AndroidVersion
import java.io.File

// This file contains utilities for converting Gradle model types (from builder-model) into project model types.

val ApiVersion.androidVersion: AndroidVersion
  get() = AndroidVersion(apiLevel, codename)

/**
 * Returns the [ProjectType] for the given type ID. Type ids must be one of the values defined by AndroidProject.PROJECT_TYPE_*.
 */
fun getProjectType(typeId: Int): ProjectType {
  return when (typeId) {
    0 -> ProjectType.APP
    1 -> ProjectType.LIBRARY
    2 -> ProjectType.TEST
    3 -> ProjectType.ATOM
    4 -> ProjectType.INSTANT_APP
    5 -> ProjectType.FEATURE
    else -> null
  } ?: throw IllegalArgumentException("The value $typeId is not a valid project type ID")
}

/**
 * Converts this [IdeAndroidProject] to an [AndroidProject]. The given [cache] determines the scope for de-duping.
 * If the same [ModelCache] is used for multiple conversions, duplicate objects will be merged into the same instance
 * across those conversions. In most situations there will be many duplicate objects within the same project and
 * few duplicate objects between projects, so using the default value will be sufficient.
 */
fun IdeAndroidProject.toProjectModel(cache: ModelCache = ModelCache()): AndroidProject =
    GradleModelConverter(this, cache).convert()

/** Name assigned to the dimension that contains all flavors that aren't explicitly associated with a dimension. */
const val DIM_UNNAMED_FLAVOR = "unnamedFlavorDimension"
/** Name assigned to the dimension that contains build types. */
const val DIM_BUILD_TYPE = "buildType"
/** Name assigned to the dimension that contains artifacts. */
const val DIM_ARTIFACTS = "artifact"
/** Name of the main artifact. */
const val MAIN_ARTIFACT_NAME = "main"

data class VariantContext(val parent: IdeAndroidProject, val variant: IdeVariant)
data class ArtifactContext(val parent: VariantContext, val artifact: IdeBaseArtifact)
data class BuildTypeContext(val buildType: BuildType)
data class FlavorContext(val flavor: ProductFlavor)
data class ConfigTableContext(val parent: IdeAndroidProject)

class GradleModelConverter(
    val project: IdeAndroidProject,
    val cache: ModelCache
) {
  private val schema = getConfigTableSchema(project)

  fun convert(): AndroidProject =
      compute(project) {
        val variants = ArrayList<Variant>()
        forEachVariant {
          variants.add(convert(VariantContext(project, it)))
        }

        AndroidProject(
            name = name,
            type = getProjectType(projectType),
            variants = variants,
            configTable = convert(ConfigTableContext(project))
        )
      }

  fun convert(buildType: BuildTypeContext): Config =
      compute(buildType) {
        val baseValues = getBaseConfig(this.buildType)
        with(this.buildType) {
          baseValues.copy(
              manifestValues = getManifestAttributes(this),
              minifyEnabled = isMinifyEnabled
          )
        }
      }

  fun convert(flavor: FlavorContext): Config =
      compute(flavor) {
        val baseValues = getBaseConfig(this.flavor)
        with(this.flavor) {
          baseValues.copy(
              manifestValues = getManifestAttributes(this),
              testInstrumentationRunner = testInstrumentationRunner,
              testInstrumentationRunnerArguments = testInstrumentationRunnerArguments,
              resourceConfigurations = resourceConfigurations,
              usingSupportLibVectors = vectorDrawables.useSupportLibrary == true
          )
        }
      }

  private inline fun forEachArtifact(variant: IdeVariant, block: (ConfigPath, BaseArtifact) -> Unit) {
    block(schema.matchArtifact(MAIN_ARTIFACT_NAME), variant.mainArtifact)
    variant.extraAndroidArtifacts.forEach {
      block(schema.matchArtifact(it.name), it)
    }
    variant.extraJavaArtifacts.forEach {
      block(schema.matchArtifact(it.name), it)
    }
  }

  fun convert(configTable: ConfigTableContext): ConfigTable =
      compute(configTable) {
        with(parent) {
          // Set up the config table
          val configSchema = getConfigTableSchema(this)
          val configs = ArrayList<ConfigAssociation>()

          // Add the main config
          configs.addAll(configsFor(matchAllArtifacts(), defaultConfig))

          // Add the flavor configs
          productFlavors.forEach {
            configs.addAll(configsFor(configSchema.pathFor(it.productFlavor.name), it))
          }

          // Add the multi-flavor configs
          val multiFlavorConfigs = HashMap<ConfigPath, Config>()
          forEachVariant {
            val multiFlavorPath = matchArtifactsWith(it.productFlavors)
            forEachArtifact(it) { path, artifact ->
              val sourceProvider = artifact.multiFlavorSourceProvider
              if (sourceProvider != null) {
                val artifactPath = multiFlavorPath.intersect(path)
                multiFlavorConfigs.getOrPut(artifactPath) {
                  val cfg = Config(sources = convert(sourceProvider))
                  configs.add(ConfigAssociation(artifactPath, cfg))
                  cfg
                }
              }
            }
          }

          // Add the build types
          buildTypes.forEach {
            configs.addAll(configsFor(matchBuildType(it.buildType.name), it))
          }

          // Add the per-variant configs
          forEachVariant {
            val variantPath = matchArtifactsForVariant(it)
            forEachArtifact(it) { path, artifact ->
              val sourceProvider = artifact.variantSourceProvider
              if (sourceProvider != null) {
                val artifactPath = variantPath.intersect(path)
                configs.add(ConfigAssociation(artifactPath, Config(sources = convert(sourceProvider))))
              }
            }
          }

          ConfigTable(
              schema = configSchema,
              associations = configs
          )
        }
      }

  fun convert(sourceProvider: SourceProvider): SourceSet =
      compute(sourceProvider) {
        SourceSet(mapOf(
            AndroidPathType.MANIFEST to listOf(PathString(sourceProvider.manifestFile)),
            AndroidPathType.JAVA to filesToPathStrings(sourceProvider.javaDirectories),
            AndroidPathType.RESOURCE to filesToPathStrings(sourceProvider.resourcesDirectories),
            AndroidPathType.AIDL to filesToPathStrings(sourceProvider.aidlDirectories),
            AndroidPathType.RENDERSCRIPT to filesToPathStrings(sourceProvider.renderscriptDirectories),
            AndroidPathType.C to filesToPathStrings(sourceProvider.cDirectories),
            AndroidPathType.CPP to filesToPathStrings(sourceProvider.cppDirectories),
            AndroidPathType.RES to filesToPathStrings(sourceProvider.resDirectories),
            AndroidPathType.ASSETS to filesToPathStrings(sourceProvider.assetsDirectories),
            AndroidPathType.JNI_LIBS to filesToPathStrings(sourceProvider.jniLibsDirectories),
            AndroidPathType.SHADERS to filesToPathStrings(sourceProvider.shadersDirectories)
        ))
      }

  fun convert(variant: VariantContext): Variant =
      compute(variant) {
        with(variant.variant) {
          val androidTestArtifact = androidTestArtifact
          Variant(
              name = name,
              displayName = displayName,
              mainArtifact = convert(ArtifactContext(variant, mainArtifact)),
              androidTestArtifact = androidTestArtifact?.let { convert(ArtifactContext(variant, it)) },
              unitTestArtifact = unitTestArtifact?.let { convert(ArtifactContext(variant, it)) },
              extraArtifacts = extraAndroidArtifacts
                  .filter { it != mainArtifact && it != androidTestArtifact }
                  .mapNotNull { it as? IdeBaseArtifact }
                  .map { convert(ArtifactContext(variant, it)) },
              extraJavaArtifacts = extraJavaArtifacts
                  .filter { it != unitTestArtifact }
                  .mapNotNull { it as? IdeBaseArtifact }
                  .map { convert(ArtifactContext(variant, it)) },
              configPath = matchArtifactsForVariant(this)
          )
        }
      }

  /**
   * Converts a builder-model's Artifact into a project model [Artifact].
   */
  fun convert(artifact: ArtifactContext): Artifact =
      compute(artifact) {
        with(artifact.artifact) {
          val artifactName = if (this == artifact.parent.variant.mainArtifact) MAIN_ARTIFACT_NAME else name
          val configTable = convert(ConfigTableContext(artifact.parent.parent))
          val variantPath = matchArtifactsForVariant(artifact.parent.variant)
          val artifactPath = variantPath.intersect(configTable.schema.matchArtifact(artifactName))

          // Compute the resolved configuration for this artifact. There's two ways to compute the resolved configuration:
          // 1. Iterate over the constituent configs in the config table and merge them all.
          // 2. Make use of the "mergedFlavor" attribute provided by Gradle.
          //
          // Approach 1 is simpler since it would just be a trivial for loop, but it assumes the IDE's merge logic is exactly the same
          // as Gradle's. Approach 2 is preferred since - if gradle adds any special cases to its merge algorithm - it would report those
          // special cases as part of mergedFlavor and we'd automatically take them into account. Unfortunately, this approach is also
          // a lot more complicated due to special cases in builder-model.
          // - The mergedFlavor structure does not include a source provider, so we need to compute the source inclusions manually
          //   using approach 1.
          // - The mergedFlavor structure does not include config information from the build type or any variant-specific overloads.
          //
          // So the algorithm is: first compute the source inclusions corresponding to the mergedFlavor configuration (that's the main
          // source configurations along with any flavor-specific inclusions, minus any variant-specific inclusions). Then convert
          // mergedFlavor to a Config and attach those source inclusions. Then do a manual merge of that config with the build type and
          // any variant-specific Configs. Finally, we override any metadata (like the application ID) that Gradle has attached directly
          // to the artifact. Even if that disagrees with the merged values we computed via the algorithm above, we always prefer any
          // information supplied directly by Gradle.

          val associationsToProcess = ArrayList<ConfigAssociation>()

          // First, compute the sources for the global config or any config that is flavor-specific.
          var mergedSource = SourceSet()
          for (config in configTable.associations) {
            // Skip configs that don't apply to this artifact
            if (!config.path.intersects(artifactPath)) {
              continue
            }

            // If this is something that would be included in the "merged flavor", include its sources here.
            if ((matchesAllVariants(config.path) || !matchesAllFlavors(config.path)) && !isVariantSpecific(config.path)) {
              mergedSource += config.config.sources
            }
            else {
              associationsToProcess.add(config)
            }
          }

          // Compute the merged flavor configuration. This won't include any sources that came from source sets, so
          // we merge it with the merged sources we computed, above
          val flavorCombinationConfig = convert(FlavorContext(artifact.parent.variant.mergedFlavor))
          var mergedConfig = flavorCombinationConfig.copy(sources = mergedSource + flavorCombinationConfig.sources)

          // Finally, merge the additional configurations with the merged flavor.
          for (config in associationsToProcess) {
            mergedConfig = mergedConfig.mergeWith(config.config)
          }

          if (this is AndroidArtifact) {
            mergedConfig = mergedConfig.copy(
                manifestValues = mergedConfig.manifestValues.copy(
                    applicationId = applicationId
                ),
                resValues = mergedConfig.resValues + getResValues(resValues)
            )
          }

          Artifact(
              name = artifactName,
              classFolders = listOf(PathString(classesFolder)) + filesToPathStrings(additionalClassesFolders),
              resolved = mergedConfig
          )
        }
      }

  /**
   * Returns true if the given path only applies to one variant
   */
  private fun isVariantSpecific(path: ConfigPath): Boolean {
    val segments = path.segments ?: return false

    val firstNull = segments.indexOfFirst { it == null }
    return (firstNull == -1 || firstNull >= schema.dimensions.count() - 2)
  }

  private fun matchesAllFlavors(path: ConfigPath) =
      matchesAllInDimension(path, schema.dimensions.count() - 2)

  private fun matchesAllVariants(path: ConfigPath) =
      matchesAllInDimension(path, schema.dimensions.count() - 1)

  /**
   * Returns true iff the given patch matches everything in the first n dimensions.
   */
  private fun matchesAllInDimension(path: ConfigPath, dim: Int): Boolean {
    val segments = path.segments ?: return false
    // The first dim-1 segments of the path correspond to the variant name. If there are no non-null values in any
    // of these segments then the path applies to all variants.
    val firstNonNull = segments.indexOfFirst { it != null }
    if (firstNonNull == -1) {
      return true
    }
    return firstNonNull >= dim
  }

  private fun matchBuildType(buildType: String) =
      matchDimension(schema.dimensions.size - 2, buildType)

  private fun matchArtifactsForVariant(variant: IdeVariant): ConfigPath =
      matchArtifactsWith(variant.productFlavors + variant.buildType)

  private fun configsFor(variantPath: ConfigPath, flavor: ProductFlavorContainer): List<ConfigAssociation> {
    val result = ArrayList<ConfigAssociation>()
    val configWithoutSources = convert(FlavorContext(flavor.productFlavor))

    result.add(
        // The ConfigPath for the main configuration is a path that matches both the main artifact and the current variant (if any).
        ConfigAssociation(
            variantPath.intersect(schema.matchArtifact(MAIN_ARTIFACT_NAME)),
            configWithoutSources.copy(sources = configWithoutSources.sources + convert(flavor.sourceProvider))
        )
    )
    for (next in flavor.extraSourceProviders) {
      result.add(
          ConfigAssociation(
              variantPath.intersect(schema.matchArtifact(next.artifactName)),
              configWithoutSources.copy(sources = configWithoutSources.sources + convert(next.sourceProvider))
          )
      )
    }

    return result
  }

  private fun configsFor(variantPath: ConfigPath, buildType: BuildTypeContainer): List<ConfigAssociation> {
    val result = ArrayList<ConfigAssociation>()
    val configWithoutSources = convert(BuildTypeContext(buildType.buildType))

    result.add(
        // The ConfigPath for the main configuration is a path that matches both the main artifact and the current variant (if any).
        ConfigAssociation(
            variantPath.intersect(schema.matchArtifact(MAIN_ARTIFACT_NAME)),
            configWithoutSources.copy(sources = configWithoutSources.sources + convert(buildType.sourceProvider))
        )
    )
    for (next in buildType.extraSourceProviders) {
      result.add(
          ConfigAssociation(
              variantPath.intersect(schema.matchArtifact(next.artifactName)),
              configWithoutSources.copy(sources = configWithoutSources.sources + convert(next.sourceProvider))
          )
      )
    }

    return result
  }

  /**
   * Computes the [ConfigTableSchema] for the given project.
   */
  private fun getConfigTableSchema(input: IdeAndroidProject): ConfigTableSchema {
    val builder = ConfigTableSchema.Builder()
    with(input) {
      flavorDimensions.forEach {
        builder.getOrPutDimension(it)
      }
      productFlavors.forEach {
        builder.getOrPutDimension(it.productFlavor.dimension ?: DIM_UNNAMED_FLAVOR).add(it.productFlavor.name)
      }
      val buildTypeDimension = builder.getOrPutDimension(DIM_BUILD_TYPE)
      buildTypes.forEach {
        buildTypeDimension.add(it.buildType.name)
      }
      val artifactDimension = builder.getOrPutDimension(DIM_ARTIFACTS)
      artifactDimension.add(MAIN_ARTIFACT_NAME)
      forEachVariant {
        it.extraAndroidArtifacts.forEach {
          artifactDimension.add(it.name)
        }
        it.extraJavaArtifacts.forEach {
          artifactDimension.add(it.name)
        }
      }
    }

    return builder.build()
  }

  private fun getResValues(classFields: Map<String, ClassField>): Map<String, ResValue> {
    val result = HashMap<String, ResValue>()
    for (field in classFields.values) {
      val resourceType = ResourceType.getEnum(field.type)
      if (resourceType != null) {
        result.put(field.name, ResValue(resourceType, field.value))
      }
    }
    return result
  }

  private fun getBaseConfig(config: BaseConfig): Config {
    with(config) {
      val sources = HashMap<AndroidPathType, List<PathString>>()
      sources.put(AndroidPathType.PROGUARD_FILE, filesToPathStrings(proguardFiles))
      sources.put(AndroidPathType.CONSUMER_PROGUARD_FILE, filesToPathStrings(consumerProguardFiles))

      return Config(
          applicationIdSuffix = applicationIdSuffix,
          versionNameSuffix = versionNameSuffix,
          manifestPlaceholderValues = manifestPlaceholders,
          sources = SourceSet(sources),
          resValues = getResValues(resValues)
      )
    }
  }

  private fun getManifestAttributes(flavor: ProductFlavor): ManifestAttributes {
    with(flavor) {
      return ManifestAttributes(
          applicationId = applicationId,
          versionCode = versionCode,
          versionName = versionName,
          minSdkVersion = minSdkVersion?.androidVersion,
          maxSdkVersion = maxSdkVersion?.let { AndroidVersion(it) },
          targetSdkVersion = targetSdkVersion?.androidVersion
      )
    }
  }

  private fun getManifestAttributes(buildType: BuildType): ManifestAttributes {
    return ManifestAttributes(
        debuggable = buildType.isDebuggable
    )
  }

  private fun <K, V> compute(key: K, lambda: K.() -> V): V {
    return cache.computeIfAbsent(key, { key.lambda() })
  }
}

fun filesToPathStrings(files: Collection<File>): List<PathString> =
       files.map { PathString(it) }
