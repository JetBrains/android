/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.QUICK_FIX_PATH_TYPE
import com.intellij.icons.AllIcons.Actions.Download
import com.intellij.icons.AllIcons.General.BalloonError
import com.intellij.icons.AllIcons.General.BalloonInformation
import com.intellij.icons.AllIcons.General.BalloonWarning
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.GRAY
import com.intellij.ui.JBColor.RED
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import javax.swing.Icon

interface PsIssue {
  val text: String
  val path: PsPath
  val type: PsIssueType
  val severity: Severity

  val description: String?
  val quickFixes: List<PsQuickFix>

  enum class Severity constructor(val text: String, val pluralText: String, val icon: Icon, val color: Color, val priority: Int) {
    ERROR("Error", "Errors", BalloonError, RED, 0),
    WARNING("Warning", "Warnings", BalloonWarning, warningColor, 1),
    INFO("Information", "Information", BalloonInformation, GRAY, 3),
    UPDATE("Update", "Updates", Download, GRAY, 2)
  }
}

interface PsQuickFix : Serializable {
  val text: String
  fun execute(context: PsContext)

  companion object {
    fun deserialize(data: String): PsQuickFix =
      ObjectInputStream(ByteArrayInputStream(StringUtil.parseHexString(data))).readObject() as PsQuickFix
  }
}

fun PsQuickFix.serialize(): String {
  val byteArrayOutputStream = ByteArrayOutputStream()
  ObjectOutputStream(byteArrayOutputStream).use { objectOutputStream ->
    objectOutputStream.writeObject(this)
  }
  return StringUtil.toHexString(byteArrayOutputStream.toByteArray())
}

fun PsQuickFix.getHyperlinkDestination(): String = "$QUICK_FIX_PATH_TYPE${serialize()}"

data class PsGeneralIssue(
  override val text: String,
  override val description: String?,
  override val path: PsPath,
  override val type: PsIssueType,
  override val severity: PsIssue.Severity,
  override val quickFixes: List<PsQuickFix> = listOf()
) : PsIssue {
  constructor (text: String, path: PsPath, type: PsIssueType, severity: PsIssue.Severity, quickFix: PsQuickFix? = null) :
    this(text, null, path, type, severity, listOfNotNull(quickFix))

  override fun toString(): String = "${severity.name}: $text"
}

@Suppress("UnregisteredNamedColor")
private val warningColor = JBColor.namedColor("NewPSD.warning", JBColor(0xF49810, 0xF49810))
