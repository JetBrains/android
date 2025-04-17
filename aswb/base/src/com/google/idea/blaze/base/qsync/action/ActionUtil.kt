@file:JvmName("ActionUtil")

package com.google.idea.blaze.base.qsync.action

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

fun AnActionEvent.getVirtualFiles(): List<VirtualFile>? =
  getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.filterNotNull()?.takeUnless { it.isEmpty() }?.toList()

fun AnActionEvent.getWorkspaceRelativePaths(): List<Path>? =
  getVirtualFiles()?.let { WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, it) }?.toList()
