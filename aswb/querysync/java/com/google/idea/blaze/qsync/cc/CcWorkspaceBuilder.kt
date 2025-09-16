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
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.deps.CcCompilationInfo
import com.google.idea.blaze.qsync.deps.CcToolchain
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.LanguageClassProto
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilationContext
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlag
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerFlagSet
import com.google.idea.blaze.qsync.project.ProjectProto.CcCompilerSettings
import com.google.idea.blaze.qsync.project.ProjectProto.CcLanguage
import com.google.idea.blaze.qsync.project.ProjectProto.CcSourceFile
import com.google.idea.blaze.qsync.project.ProjectProto.CcWorkspace
import com.google.idea.blaze.qsync.project.ProjectTarget
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** Updates the project proto with the output from a CC dependencies build.  */
class CcWorkspaceBuilder(
  private val ccDependenciesInfo: CcDependenciesInfo,
  private val graph: BuildGraphData,
  private val context: Context<*>
) {
  private val nextFlagSetId = AtomicInteger(0)

  /* Map from toolchain ID -> language -> flags for that toolchain & language. */
  private val toolchainLanguageFlags: MutableMap<String, Map<CcLanguage, List<CcCompilerFlag>>> = hashMapOf()

  /* Map from toolchain ID -> compiler executable. */
  private val compilerExecutableMap: MutableMap<String, ProjectPath> = hashMapOf()

  /* Map of unique sets of compiler flags to an ID to identify them.
   * We do this as the downstream code turns each set of flags into a CidrCompilerSwitches instance
   * which can have a large memory footprint. */
  private val uniqueFlagSetIds: MutableMap<Set<CcCompilerFlag>, String> = hashMapOf()

  /* Workspace builder, populated incrementally from within the various visitXxxx methods. */
  var workspaceBuilder: CcWorkspace.Builder = CcWorkspace.newBuilder()

  fun updateProjectProtoForCcDeps(projectProto: ProjectProto.Project): ProjectProto.Project {
    return createWorkspace()
      ?.let {
        projectProto
          .toBuilder()
          .setCcWorkspace(it)
          .addActiveLanguages(LanguageClassProto.LanguageClass.LANGUAGE_CLASS_CC)
        .build()
      } ?: projectProto
  }

  private fun createWorkspace(): CcWorkspace? {
    if (ccDependenciesInfo.targetInfoMap().isEmpty()) {
      return null
    }

    visitToolchainMap(ccDependenciesInfo.toolchainInfoMap())
    visitTargetMap(ccDependenciesInfo.targetInfoMap())

    return workspaceBuilder.build()
  }

  private fun visitToolchainMap(toolchainInfoMap: Map<String, CcToolchain>) {
    toolchainInfoMap.values.forEach { toolchainInfo -> this.visitToolchain(toolchainInfo) }
  }

  private fun visitToolchain(toolchainInfo: CcToolchain) {
    compilerExecutableMap[toolchainInfo.id()] = toolchainInfo.compilerExecutable()

    val commonFlags =
      toolchainInfo.builtInIncludeDirectories()
        .map { makePathFlag("-I", it) }

    toolchainLanguageFlags[toolchainInfo.id()] = mapOf<CcLanguage, List<CcCompilerFlag>>(
      CcLanguage.C to commonFlags + toolchainInfo.cOptions().map { makeStringFlag(it, "") },
      CcLanguage.CPP to commonFlags + toolchainInfo.cppOptions().map { makeStringFlag(it, "") },
    )
  }

  private fun visitTargetMap(targetMap: Map<Label, CcCompilationInfo>) {
    targetMap.forEach { (label, target) -> this.visitTarget(label, target) }
  }

  private fun visitTarget(label: Label, target: CcCompilationInfo) {
    val projectTarget = graph.getProjectTarget(label)
                        // This target is no longer present in the project. Ignore it.
                        // We should really clean up the dependency cache itself to remove any artifacts relating to
                        // no-longer-present targets, but that will be a lot more work. For now, just ensure we don't
                        // crash.
                        ?: return

    val toolchain = ccDependenciesInfo.toolchainInfoMap().get(target.toolchainId())

    val targetFlags = buildList {
      addAll(projectTarget.copts().map { makeStringFlag(it, "") })
      addAll(target.defines().map { makeStringFlag("-D", it) })
      addAll(target.includeDirectories().map { makePathFlag("-I", it) })
      addAll(target.quoteIncludeDirectories().map { makePathFlag("-iquote", it) })
      addAll(target.systemIncludeDirectories().map { makePathFlag("-isystem", it) })
      addAll(target.frameworkIncludeDirectories().map { makePathFlag("-F", it) })
    }

    val srcs = buildList {
      // TODO(mathewi): The handling of flag sets here is not optimal, since we recalculate an
      //  identical flag set for each source of the same language, then immediately de-dupe them in
      //  the addFlagSet call. For large flag sets this may be slow.
      for (srcPath in graph.getTargetSources(label, *ProjectTarget.SourceType.all())) {
        val lang = getLanguage(srcPath)
        if (lang != null) {
          this@buildList.add(
            CcSourceFile.newBuilder()
              .setLanguage(lang)
              .setWorkspacePath(srcPath.toString())
              .setCompilerSettings(
                CcCompilerSettings.newBuilder()
                  .setCompilerExecutablePath(
                    compilerExecutableMap[target.toolchainId()]!!.toProto()
                  )
                  .setFlagSetId(
                    addFlagSet(targetFlags +
                               toolchainLanguageFlags[target.toolchainId()]!![lang].orEmpty()
                    )
                  )
              )
              .build()
          )
        }
      }
    }

    val targetContext =
      CcCompilationContext.newBuilder()
        .setId(label.toString() + "%" + toolchain!!.targetGnuSystemName())
        .setHumanReadableName(label.toString() + " - " + toolchain.targetGnuSystemName())
        .addAllSources(srcs)
        .putAllLanguageToCompilerSettings(
          toolchainLanguageFlags[toolchain.id()]!!
            .entries
            .associate {
              it.key.valueDescriptor.name to CcCompilerSettings.newBuilder()
                .setCompilerExecutablePath(compilerExecutableMap[toolchain.id()]!!.toProto())
                .setFlagSetId(addFlagSet(it.value))
                .build()
            }
        )
        .build()
    workspaceBuilder.addContexts(targetContext)
  }

  /** Ensure that the given flagset exists, adding it if necessary, and return its unique ID.  */
  private fun addFlagSet(flags: Collection<CcCompilerFlag>): String {
    // Create a set so that two flags sets are considered equivalent if their flag order differs.
    val canonicalFlagSet = flags.toSet()
    var flagSetId = uniqueFlagSetIds[canonicalFlagSet]

    if (flagSetId == null) {
      flagSetId = nextFlagSetId.incrementAndGet().toString()
      uniqueFlagSetIds[canonicalFlagSet] = flagSetId
      workspaceBuilder.putFlagSets(
        flagSetId, CcCompilerFlagSet.newBuilder().addAllFlags(flags).build()
      )
    }
    return flagSetId
  }

  private fun makeStringFlag(flag: String, value: String): CcCompilerFlag {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPlainValue(value).build()
  }

  private fun makePathFlag(flag: String, path: ProjectPath): CcCompilerFlag {
    return CcCompilerFlag.newBuilder().setFlag(flag).setPath(path.toProto()).build()
  }

  private fun getLanguage(srcPath: Path): CcLanguage? {
    // logic in here based on https://bazel.build/reference/be/c-cpp#cc_library.srcs
    val lastDot = srcPath.fileName.toString().lastIndexOf('.')
    if (lastDot < 0) {
      // default to cpp
      context.output(PrintOutput.log("No extension for c/c++ source file %s; assuming cpp", srcPath))
      return CcLanguage.CPP
    }
    val ext = srcPath.fileName.toString().substring(lastDot + 1)
    if (IGNORE_SRC_FILE_EXTENSIONS.contains(ext)) {
      return null
    }
    if (EXTENSION_TO_LANGUAGE_MAP.containsKey(ext)) {
      return EXTENSION_TO_LANGUAGE_MAP.get(ext)
    }
    context.output(PrintOutput.log("Unrecognized extension %s for c/c++ source file %s; assuming cpp", ext, srcPath))
    return CcLanguage.CPP
  }

  companion object {
    private val EXTENSION_TO_LANGUAGE_MAP =
      mapOf(
        "c" to CcLanguage.C,
        "cc" to CcLanguage.CPP,
        "cpp" to CcLanguage.CPP,
        "cxx" to CcLanguage.CPP,
        "c++" to CcLanguage.CPP,
        "C" to CcLanguage.C,
      )

    /* Files we ignore because they are not top level source files: */
    private val IGNORE_SRC_FILE_EXTENSIONS =
      setOf(
        "h",
        "hh",
        "hpp",
        "hxx",
        "inc",
        "inl",
        "H",
        "S",
        "a",
        "lo",
        "so",
        "o"
      )
  }
}
