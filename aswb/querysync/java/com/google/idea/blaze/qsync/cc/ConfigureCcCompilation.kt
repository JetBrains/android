/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.cc

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State
import com.google.idea.blaze.qsync.deps.CcCompilationInfo
import com.google.idea.blaze.qsync.deps.CcToolchain
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage
import com.google.idea.blaze.qsync.project.QuerySyncProjectDirectory
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import com.intellij.util.containers.orNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** Adds C/C++ compilation information and headers to the project proto. */
class ConfigureCcCompilation: ProjectProtoUpdateOperation {

  /* Map from toolchain ID -> language -> flags for that toolchain & language. */
  private val toolchainLanguageFlags: MutableMap<String, Map<CcLanguage, List<CcCompilerFlag>>> = hashMapOf()

  /* Map of unique sets of compiler flags to an ID to identify them.
   * We do this as the downstream code turns each set of flags into a CidrCompilerSwitches instance
   * which can have a large memory footprint. */
  private val uniqueFlagSetIds: MutableMap<Set<CcCompilerFlag>, String> = hashMapOf()

  override fun update(
    update: ProjectProtoUpdate,
    artifactState: State,
    context: Context<*>,
    externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
  ) {
    update.ccWorkspace {
      val visitor = Visitor(update, artifactState, context, this, externalRepositoryFinder)
      visitor.visitToolchainMap(artifactState.ccToolchainMap())

      for (target in artifactState.targets()) {
        val ccInfo = target.ccInfo().orNull() ?: continue
        visitor.visitTarget(ccInfo, target.buildContext())
      }
    }
  }

  private inner class Visitor(
    private val update: ProjectProtoUpdate,
    private val artifactState: State,
    private val context: Context<*>,
    private val workspaceUpdater: ProjectProtoUpdate.CcWorkspaceUpdater,
    private val externalRepositoryFinder: ProjectPath.ExternalRepositoryFinder,
  ) {

    fun visitToolchainMap(toolchainInfoMap: Map<String, CcToolchain>) {
      toolchainInfoMap.values.forEach(this::visitToolchain)
    }

    private fun visitToolchain(toolchain: CcToolchain) {
      val commonFlags =
        toolchain.builtInIncludeDirectories().map { makePathFlag("-I", it) }

      toolchainLanguageFlags[toolchain.id()] = mapOf(
        CcLanguage.C to commonFlags + toolchain.cOptions().map { makeStringFlag(it, "") },
        CcLanguage.CPP to commonFlags + toolchain.cppOptions().map { makeStringFlag(it, "") }
      )
    }

    fun visitTarget(ccInfo: CcCompilationInfo, buildContext: DependencyBuildContext) {
      val toolchain = artifactState.ccToolchainMap()[ccInfo.toolchainId()] ?: let {
        context.output(PrintOutput.error("Cannot find toolchain with id: '${ccInfo.toolchainId()}' referred to from ${ccInfo.target()}"))
        return@visitTarget
      }

      val targetFlags =
        buildList {
          addAll(ccInfo.copts().map { makeStringFlag(it, "") })
          addAll(ccInfo.defines().map { makeStringFlag("-D", it) })
          addAll(ccInfo.includeDirectories().map { makePathFlag("-I", it) })
          addAll(ccInfo.quoteIncludeDirectories().map { p -> makePathFlag("-iquote", p) })
          addAll(ccInfo.systemIncludeDirectories().map { p -> makePathFlag("-isystem", p) })
          addAll(ccInfo.frameworkIncludeDirectories().map { p -> makePathFlag("-F", p) })
        }

      workspaceUpdater.target(ccInfo.target()) {
        val targetContext =
          CcCompilationContext(
            id = ccInfo.target().toString() + "%" + toolchain.targetGnuSystemName(),
            humanReadableName = ccInfo.target().toString() + " - " + toolchain.targetGnuSystemName(),
            languageToCompilerSettings =
              toolchainLanguageFlags[toolchain.id()]?.entries?.associate {
                it.key to CcCompilerSettings(
                  compilerExecutablePath = toolchain.compilerExecutable(),
                  flagSetId = addFlagSet(it.value + targetFlags)
                )
              }
                .orEmpty(),
          )
        addContext(targetContext)

        update.artifactDirectory(ArtifactDirectories.GEN_CC_HEADERS) {
          for (artifact in ccInfo.genHeaders()) {
            addIfNewer(artifact.artifactPath(), artifact, buildContext)
          }
        }
      }
      update.artifactDirectory(ProjectPath.projectRelative(Path.of(QuerySyncProjectDirectory.EXTERNAL_REPOSITORIES.directoryName))){
        (ccInfo.collectArtifactRepositoryNames() + toolchain.collectArtifactRepositoryNames()).forEach { externalRepositoryName ->
          addExternalRepository(
            repositoryName = externalRepositoryName,
            absolutePath = externalRepositoryFinder.find(externalRepositoryName)
                           ?: error("External repository $externalRepositoryName not found"),
            buildContext = buildContext
          )
        }
      }
    }

    /** Ensure that the given flagset exists, adding it if necessary, and return its unique ID. */
    private fun addFlagSet(flags: Collection<CcCompilerFlag>): String {
      // Create a set so that two flags sets are considered equivalent if their flag order differs.
      val canonicalFlagSet: kotlin.collections.Set<CcCompilerFlag> = flags.toSet()
      return uniqueFlagSetIds[canonicalFlagSet] ?: nextFlagSetId.incrementAndGet().toString().also { flagSetId ->
        uniqueFlagSetIds[canonicalFlagSet] = flagSetId
        workspaceUpdater.putFlagSets(flagSetId, CcCompilerFlagSet(flags.toList()))
      }
    }
  }

  private fun makeStringFlag(flag: String, value: String): CcCompilerFlag {
    return ProjectProto.CcCompilerStringFlag(flag = flag, value = value);
  }

  private fun makePathFlag(flag: String, path: ProjectPath): CcCompilerFlag {
    return ProjectProto.CcCompilerPathFlag(flag = flag, path = path);
  }

  companion object {
    private val nextFlagSetId = AtomicInteger(0)
  }
}

private fun CcCompilationInfo.collectArtifactRepositoryNames(): Set<String> {
  return buildSet {
    collectExternalRepositoryNameFrom(frameworkIncludeDirectories())
    collectExternalRepositoryNameFrom(includeDirectories())
    collectExternalRepositoryNameFrom(quoteIncludeDirectories())
    collectExternalRepositoryNameFrom(systemIncludeDirectories())
  }
}

private fun CcToolchain.collectArtifactRepositoryNames(): Set<String> {
  return buildSet {
    collectExternalRepositoryNameFrom(compilerExecutable())
    collectExternalRepositoryNameFrom(builtInIncludeDirectories())
  }
}

private fun MutableSet<String>.collectExternalRepositoryNameFrom(paths: Collection<ProjectPath>) {
  paths.forEach { collectExternalRepositoryNameFrom(it) }
}

private fun MutableSet<String>.collectExternalRepositoryNameFrom(path: ProjectPath) {
  val externalRepositoryName = (path as? ProjectPath.ExternalRepositoryRelativeProjectPath)?.externalRepositoryName
  if (externalRepositoryName != null) {
    add(externalRepositoryName)
  }
}
