/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.android.facet.AndroidFacet
import java.io.PrintWriter
import java.io.StringWriter

private val LOG = Logger.getInstance(ShowRoomSchemaAction::class.java)

/**
 * Opens a scratch file with a description of the Room schema for every module.
 *
 * Keep in mind that creating the scratch file modifies the PSI structure and thus invalidates the schema. This means that when calling this
 * action multiple times in a row, the schema is recomputed every time.
 */
class ShowRoomSchemaAction : AnAction("Show Room schema") {
  override fun actionPerformed(e: AnActionEvent?) {
    val project = e!!.project!!
    val output = StringWriter()
    val writer = PrintWriter(output)

    ModuleManager.getInstance(project).modules
        .filter { AndroidFacet.getInstance(it) != null }
        .forEach { module ->
          val schema = RoomSchemaManager.getInstance(module)?.schema

          if (schema == null) {
            writer.println("${module.name}: failed to get Room schema.")
          }
          else {
            writer.println("${module.name}:")
            schema.databases.forEach(writer::println)
            schema.entities.forEach(writer::println)
            schema.daos.forEach(writer::println)
          }

          writer.println()
        }

    ScratchRootType.getInstance()
        .createScratchFile(
            project, "roomSchema.txt", PlainTextLanguage.INSTANCE, output.toString(), ScratchFileService.Option.create_new_always)
        .let { FileEditorManager.getInstance(project).openFile(it!!, true) }
  }
}
