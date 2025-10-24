package com.google.idea.blaze.qsync.cc

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectTarget
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdate
import java.nio.file.Path

/** Adds C/C++ compilation information and headers to the project proto. */
class ConfigureCcSources {
  fun update(
    update: ProjectProtoUpdate,
    buildGraph: BuildGraphData,
    context: Context<*>,
  ) {
    update.ccWorkspace {
      val visitor = Visitor(context, this)

      val ccSources = buildGraph.getSourceFilesByRuleKindAndType(ruleKindPredicate = { true }, ProjectTarget.SourceType.REGULAR_CC)
      for ((target, sources) in ccSources) {
        visitor.visitTargetSourceFiles(target, sources)
      }
    }
  }

  private class Visitor(
    private val context: Context<*>,
    private val workspaceUpdater: ProjectProtoUpdate.CcWorkspaceUpdater,
  ) {
    fun visitTargetSourceFiles(target: Label, srcPaths: Collection<Path>) {
      workspaceUpdater.target(target) {
        for (srcPath in srcPaths) {
          val lang = getLanguage(srcPath) ?: continue
          addSourceFile(
            ProjectProto.CcSourceFile(
              workspacePath = ProjectPath.WorkspaceRelativeProjectPath(srcPath, EMPTY_PATH),
              language = lang,
            )
          )
        }
      }
    }

    private fun getLanguage(srcPath: Path): ProjectProto.CcLanguage? {
      // logic in here based on https://bazel.build/reference/be/c-cpp#cc_library.srcs
      val lastDot = srcPath.fileName.toString().lastIndexOf('.')
      if (lastDot < 0) {
        // default to cpp
        context.output(PrintOutput.log("No extension for c/c++ source file %s; assuming cpp", srcPath))
        return ProjectProto.CcLanguage.CPP
      }
      val ext = srcPath.fileName.toString().substring(lastDot + 1)
      if (IGNORE_SRC_FILE_EXTENSIONS.contains(ext)) {
        return null
      }
      if (EXTENSION_TO_LANGUAGE_MAP.containsKey(ext)) {
        return EXTENSION_TO_LANGUAGE_MAP[ext]
      }
      context.output(
        PrintOutput.log(
          "Unrecognized extension %s for c/c++ source file %s; assuming cpp", ext, srcPath))
      return ProjectProto.CcLanguage.CPP
    }
  }

  companion object {
    private val EXTENSION_TO_LANGUAGE_MAP =
      mapOf(
        "c" to ProjectProto.CcLanguage.C,
        "cc" to ProjectProto.CcLanguage.CPP,
        "cpp" to ProjectProto.CcLanguage.CPP,
        "cxx" to ProjectProto.CcLanguage.CPP,
        "c++" to ProjectProto.CcLanguage.CPP,
        "C" to ProjectProto.CcLanguage.C)

    /* Files we ignore because they are not top level source files: */
    private val IGNORE_SRC_FILE_EXTENSIONS =
      setOf("h", "hh", "hpp", "hxx", "inc", "inl", "H", "S", "a", "lo", "so", "o")
  }
}

private val EMPTY_PATH = Path.of("")