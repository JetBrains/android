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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.GRAY
import com.intellij.ui.JBColor.RED
import java.awt.Color
import javax.swing.Icon
import org.jetbrains.annotations.VisibleForTesting

interface PsIssue {
  val text: String
  val path: PsPath
  val type: PsIssueType
  val severity: Severity

  val description: String?
  val quickFixes: List<PsQuickFix>
  // Allow granular ordering for issues with the same Severity
  val priority: Priority

  enum class Severity constructor(val text: String, val pluralText: String, val icon: Icon, val color: Color, val priority: Int) {
    ERROR("Error", "Errors", BalloonError, RED, 0),
    WARNING("Warning", "Warnings", BalloonWarning, warningColor, 1),
    INFO("Information", "Information", BalloonInformation, GRAY, 3),
    UPDATE("Update", "Updates", Download, GRAY, 2),
  }

  enum class Priority constructor(val priority: Int) {
    HIGH_PRIORITY(0),
    NORMAL_PRIORITY(1),
  }
}

abstract class PsQuickFix {
  abstract val text: String

  abstract fun execute(context: PsContext)

  fun serialize(): String = serializedInfo().joinToString("|") { escape(it) }

  abstract fun serializedInfo(): List<String>

  object NoOpPsQuickFix : PsQuickFix() {
    override val text: String = ""

    override fun execute(context: PsContext) {}

    override fun serializedInfo() = listOf("NO_OP")
  }

  companion object {
    private val LOG = Logger.getInstance(PsQuickFix::class.java)
    private val deserializers: MutableMap<String, (List<String>) -> PsQuickFix> = mutableMapOf()

    @VisibleForTesting
    fun escape(value: String): String {
      return value.replace("\\", "\\\\").replace("|", "\\|")
    }

    @VisibleForTesting
    fun splitEscaped(data: String): List<String> {
      val result = mutableListOf<String>()
      var currentSegment = StringBuilder()
      var i = 0
      while (i < data.length) {
        val char = data[i]
        if (char == '\\') {
          if (i + 1 < data.length) {
            currentSegment.append(data[i + 1])
            i++ // Skip next char
          } else {
            currentSegment.append(char) // Trailing backslash
          }
        } else if (char == '|') {
          result.add(currentSegment.toString())
          currentSegment.setLength(0)
        } else {
          currentSegment.append(char)
        }
        i++
      }
      result.add(currentSegment.toString())
      return result
    }

    fun registerDeserializer(type: String, deserializer: (List<String>) -> PsQuickFix) {
      if (deserializers.containsKey(type)) {
        LOG.error("Duplicate PsQuickFix deserializer registration for type '$type'")
      } else {
        deserializers[type] = deserializer
      }
    }

    init {
      registerDeserializer("NO_OP") { NoOpPsQuickFix }
    }

    fun deserialize(data: String): PsQuickFix {
      try {
        val parts = splitEscaped(data)
        val type = parts[0]
        val args = parts.drop(1)
        val deserializer = deserializers[type]
        return deserializer?.invoke(args) ?: NoOpPsQuickFix.also { LOG.warn("No deserializer found for type '$type'") }
      } catch (e: Exception) {
        LOG.warn("Failed to deserialize PsQuickFix data: $data", e)
        return NoOpPsQuickFix
      }
    }
  }
}

fun PsQuickFix.getHyperlinkDestination(): String = "$QUICK_FIX_PATH_TYPE${serialize()}"

data class PsGeneralIssue(
  override val text: String,
  override val description: String?,
  override val path: PsPath,
  override val type: PsIssueType,
  override val severity: PsIssue.Severity,
  override val quickFixes: List<PsQuickFix> = listOf(),
  override val priority: PsIssue.Priority = PsIssue.Priority.NORMAL_PRIORITY,
) : PsIssue {
  constructor(
    text: String,
    path: PsPath,
    type: PsIssueType,
    severity: PsIssue.Severity,
    quickFix: PsQuickFix? = null,
  ) : this(text, null, path, type, severity, listOfNotNull(quickFix))

  override fun toString(): String = "${severity.name}: $text"
}

@Suppress("UnregisteredNamedColor") private val warningColor = JBColor.namedColor("NewPSD.warning", JBColor(0xF49810, 0xF49810))
