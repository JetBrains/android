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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.Multimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.ArtifactDirectories
import com.google.idea.blaze.qsync.deps.ArtifactTracker.State
import com.google.idea.blaze.qsync.deps.CcCompilationInfo
import com.google.idea.blaze.qsync.deps.CcToolchain
import com.google.idea.blaze.qsync.deps.DependencyBuildContext
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdate
import com.google.idea.blaze.qsync.deps.ProjectProtoUpdateOperation
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.intellij.util.containers.orNull
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** Adds C/C++ compilation information and headers to the project proto. */
class ConfigureCcCompilation(private val artifactState: State, private val update: ProjectProtoUpdate) {
  /** An update operation to configure CC compilation. */
  class UpdateOperation : ProjectProtoUpdateOperation {
    override fun update(
      update: ProjectProtoUpdate,
      artifactState: State,
      context: Context<*>,
    ) {
      ConfigureCcCompilation(artifactState, update).update();
    }
  }

  /* Map from toolchain ID -> language -> flags for that toolchain & language. */
  private val toolchainLanguageFlags: MutableMap<String, Multimap<CcLanguage, CcCompilerFlag>> = hashMapOf()

  /* Map of unique sets of compiler flags to an ID to identify them.
   * We do this as the downstream code turns each set of flags into a CidrCompilerSwitches instance
   * which can have a large memory footprint. */
  private val uniqueFlagSetIds: MutableMap<Set<CcCompilerFlag>, String> = hashMapOf()

  @Throws(BuildException::class)
  fun update() {
    visitToolchainMap(artifactState.ccToolchainMap())

    for (target in artifactState.targets()) {
      val ccInfo = target.ccInfo().orNull() ?: continue
      visitTarget(ccInfo, target.buildContext())
    }
    if (update.project().getCcWorkspaceBuilder().getContextsCount() > 0) {
      update.project().addActiveLanguages(LanguageClass.LANGUAGE_CLASS_CC)
    }
  }

  private fun visitToolchainMap(toolchainInfoMap: Map<String, CcToolchain>) {
    toolchainInfoMap.values.forEach(this::visitToolchain)
  }

  private fun visitToolchain(toolchain: CcToolchain) {
    val commonFlags =
      toolchain.builtInIncludeDirectories().map { makePathFlag("-I", it) }

    toolchainLanguageFlags.put(
      toolchain.id(),
      ImmutableListMultimap.builder<CcLanguage, CcCompilerFlag>()
        .putAll(
          CcLanguage.C,
          commonFlags + toolchain.cOptions().map { makeStringFlag(it, "") }
        )
        .putAll(
          CcLanguage.CPP,
          commonFlags + toolchain.cppOptions().map { makeStringFlag(it, "") }
        )
        .build()
    )
  }

  private fun visitTarget(ccInfo: CcCompilationInfo, buildContext: DependencyBuildContext) {
    val projectTarget =
      update.buildGraph().getProjectTarget(ccInfo.target())
      // This target is no longer present in the project. Ignore it.
      // We should really clean up the dependency cache itself to remove any artifacts relating to
      // no-longer-present targets, but that will be a lot more work. For now, just ensure we
      // don't crash.
      ?: return
    val toolchain =
      Preconditions.checkNotNull(
        artifactState.ccToolchainMap().get(ccInfo.toolchainId()), ccInfo.toolchainId());

    val targetFlags =
      buildList {
        addAll(projectTarget.copts().map { makeStringFlag(it, "") })
        addAll(ccInfo.defines().map { makeStringFlag("-D", it) })
        addAll(ccInfo.includeDirectories().map { makePathFlag("-I", it) })
        addAll(ccInfo.quoteIncludeDirectories().map { p -> makePathFlag("-iquote", p) })
        addAll(ccInfo.systemIncludeDirectories().map { p -> makePathFlag("-isystem", p) })
        addAll(ccInfo.frameworkIncludeDirectories().map { p -> makePathFlag("-F", p) })
      }

    // TODO(mathewi): The handling of flag sets here is not optimal, since we recalculate an
    //  identical flag set for each source of the same language, then immediately de-dupe them in
    //  the addFlagSet call. For large flag sets this may be slow.
    val srcs = update.buildGraph().getTargetSources(ccInfo.target(), *SourceType.all())
      .mapNotNull { srcPath ->
        val lang = getLanguage(srcPath) ?: return@mapNotNull null
        CcSourceFile.newBuilder()
          .setLanguage(lang)
          .setWorkspacePath(srcPath.toString())
          .setCompilerSettings(
            CcCompilerSettings.newBuilder()
              .setCompilerExecutablePath(toolchain.compilerExecutable().toProto())
              .setFlagSetId(addFlagSet(targetFlags + toolchainLanguageFlags[toolchain.id()]?.get(lang).orEmpty()))
          )
          .build()
      }


    val targetContext =
      CcCompilationContext.newBuilder()
        .setId(ccInfo.target().toString() + "%" + toolchain.targetGnuSystemName())
        .setHumanReadableName(ccInfo.target().toString() + " - " + toolchain.targetGnuSystemName())
        .addAllSources(srcs)
        .putAllLanguageToCompilerSettings(
          toolchainLanguageFlags[toolchain.id()]?.asMap()?.entries?.associate {
            it.key.getValueDescriptor().name to CcCompilerSettings.newBuilder()
              .setCompilerExecutablePath(
                toolchain.compilerExecutable().toProto())
              .setFlagSetId(addFlagSet(it.value))
              .build()
          }
            .orEmpty()
        )
        .build()
    update.project().getCcWorkspaceBuilder().addContexts(targetContext)

    val headersDir = update.artifactDirectory(ArtifactDirectories.GEN_CC_HEADERS)
    for (artifact in ccInfo.genHeaders()) {
      headersDir.addIfNewer(artifact.artifactPath(), artifact, buildContext)
    }
  }

  /** Ensure that the given flagset exists, adding it if necessary, and return its unique ID. */
  private fun addFlagSet(flags: Collection<CcCompilerFlag>): String {
    // Create a set so that two flags sets are considered equivalent if their flag order differs.
    val canonicalFlagSet: kotlin.collections.Set<CcCompilerFlag> = flags.toSet()
    return uniqueFlagSetIds[canonicalFlagSet] ?: nextFlagSetId.incrementAndGet().toString().also { flagSetId ->
      uniqueFlagSetIds[canonicalFlagSet] = flagSetId
      update
        .project()
        .getCcWorkspaceBuilder()
        .putFlagSets(flagSetId, CcCompilerFlagSet.newBuilder().addAllFlags(flags).build())
    }
  }

  private fun makeStringFlag(flag: String, value: String): CcCompilerFlag {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPlainValue(value).build()
  }

  private fun makePathFlag(flag: String, path: ProjectPath): CcCompilerFlag {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPath(path.toProto()).build()
  }

  companion object {
    private val nextFlagSetId = AtomicInteger(0);

    private val EXTENSION_TO_LANGUAGE_MAP =
      mapOf(
        "c" to CcLanguage.C,
        "cc" to CcLanguage.CPP,
        "cpp" to CcLanguage.CPP,
        "cxx" to CcLanguage.CPP,
        "c++" to CcLanguage.CPP,
        "C" to CcLanguage.C)

    /* Files we ignore because they are not top level source files: */
    private val IGNORE_SRC_FILE_EXTENSIONS =
      setOf("h", "hh", "hpp", "hxx", "inc", "inl", "H", "S", "a", "lo", "so", "o")
  }

  private fun getLanguage(srcPath: Path): CcLanguage? {
    // logic in here based on https://bazel.build/reference/be/c-cpp#cc_library.srcs
    val lastDot = srcPath.fileName.toString().lastIndexOf('.');
    if (lastDot < 0) {
      // default to cpp
      update
        .context()
        .output(PrintOutput.log("No extension for c/c++ source file %s; assuming cpp", srcPath))
      return CcLanguage.CPP
    }
    val ext = srcPath.fileName.toString().substring(lastDot + 1);
    if (IGNORE_SRC_FILE_EXTENSIONS.contains(ext)) {
      return null
    }
    if (EXTENSION_TO_LANGUAGE_MAP.containsKey(ext)) {
      return EXTENSION_TO_LANGUAGE_MAP[ext]
    }
    update
      .context()
      .output(
        PrintOutput.log(
          "Unrecognized extension %s for c/c++ source file %s; assuming cpp", ext, srcPath))
    return CcLanguage.CPP
  }
}
