/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.declarative

import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeService
import com.android.tools.idea.gradle.dcl.lang.sync.BlockFunction
import com.android.tools.idea.gradle.dcl.lang.sync.BuildDeclarativeSchema
import com.android.tools.idea.gradle.dcl.lang.sync.ClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.ClassType
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.DataTypeReference
import com.android.tools.idea.gradle.dcl.lang.sync.EnumModel
import com.android.tools.idea.gradle.dcl.lang.sync.FunctionSemantic
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaMemberFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.android.tools.idea.gradle.project.sync.snapshots.DeclarativeTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.SnapshotContext
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Schema model snapshot comparison tests for declarative projects. (To run tests see
 * [com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest])
 *
 * The pre-recorded sync results can be found in testData/snapshots/declarativeSchema/ *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests ---test_filter=DeclarativeSchemaModelTestDef".
 */

data class DeclarativeSchemaModelTestDef(
  override val testProject: DeclarativeTestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
) : SyncedProjectTestDef {
  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_DECLARATIVE_GRADLE_SNAPSHOT
  }

  override fun runTest(root: File, project: Project) {
    val text = project.dumpDeclarativeSchemaModel()
    val snapshotContext = SnapshotContext(testProject.projectName, agpVersion,
                                          "tools/adt/idea/android/testData/snapshots/declarativeSchema")
    snapshotContext.assertIsEqualToSnapshot(text)
  }

  companion object {
    val tests: List<DeclarativeSchemaModelTestDef> = listOf(
      DeclarativeSchemaModelTestDef(DeclarativeTestProject.DECLARATIVE_ANDROID),
    )
  }
}

enum class ArrayStatus {
  NO_ARRAY, FIRST, NEXT
}

fun Project.dumpDeclarativeSchemaModel(): String {
  val schema = DeclarativeService.getInstance(this).getDeclarativeSchema()
  return buildString {
    var prefix = ""
    var arrayStatus = ArrayStatus.NO_ARRAY

    fun String.smartPad() = this.padEnd(Math.max(30, 10 + this.length / 10 * 10))
    fun updateArrayStatus() {
      when (arrayStatus) {
        ArrayStatus.NO_ARRAY -> return
        ArrayStatus.FIRST -> {
          arrayStatus = ArrayStatus.NEXT
          prefix = prefix.dropLast(2) + "  "
          return
        }

        else -> return
      }
    }

    fun out(s: String) {
      appendLine("$prefix$s")
      updateArrayStatus()
    }

    fun out(key: String, value: String) {
      if (value.isEmpty())
        appendLine("$prefix${key.smartPad()}:")
      else
        appendLine("$prefix${key.smartPad()}: $value")
      updateArrayStatus()
    }

    fun nestArrayElement(code: () -> Unit) {
      prefix = "$prefix- "
      arrayStatus = ArrayStatus.FIRST
      code()
      prefix = prefix.dropLast(2)
      arrayStatus = ArrayStatus.NO_ARRAY
    }

    fun nest(title: String? = null, code: () -> Unit) {
      if (title != null) {
        out(title)
      }
      prefix = "    $prefix"
      code()
      prefix = prefix.substring(4)
    }

    fun DataTypeReference.dump(prefix: String) {
      when (this) {
        is DataClassRef -> out("$prefix ReferenceType", fqName.name)
        is SimpleTypeRef -> out("$prefix SimpleType", dataType.name)
      }
    }

    fun DataProperty.dump() {
      out("PropertyName", name)
      valueType.dump("Property")
    }

    fun FunctionSemantic.dump() {
      when (this) {
        is PlainFunction -> this.returnValue.dump("Function")
        is BlockFunction -> this.accessor.dump("Block")
      }
    }

    fun SchemaMemberFunction.dump() {
      out("FunctionName", simpleName)
      receiver.dump("Receiver")
      nest("Parameters") {
        parameters.sortedBy { it.name }.forEach {
          nestArrayElement {
            out("Name", it.name ?: "N/A")
            it.type.dump("Value")
          }
        }
      }
      semantic.dump()
    }

    fun ClassModel.dump() {
      out("Name", name.name)
      nest("MemberFunctions") { memberFunctions.sortedBy { it.name }.forEach { nestArrayElement { it.dump() } } }
      nest("Properties") {
        properties.sortedBy { it.name }.forEach { nestArrayElement { it.dump() } }
      }
      out("Supertypes", supertypes.joinToString(",") { it.name })
    }

    fun EnumModel.dump() {
      out("Name", name.name)
      nest("EntryNames") { this.entryNames.joinToString (",") }
    }

    fun ClassType.dump(){
      when(this){
        is EnumModel -> dump()
        is ClassModel -> dump()
      }
    }

    fun BuildDeclarativeSchema.dump() {
      getRootReceiver().dump()
      nest("DataClassesMap:") {
        dataClassesByFqName.entries.sortedBy { it.key.name }.forEach {
          nest(it.key.name) { it.value.dump() }
        }
      }
    }

    nest("Projects:") {
      schema?.projects?.sortedBy { it.getRootReceiver().name.name }?.forEach {
        nest("ProjectSchema:") {
          it.dump()
        }
      }
    }

    nest("Settings:") {
      schema?.settings?.sortedBy { it.getRootReceiver().name.name }?.forEach {
        nest("Settings Schema:") {
          it.dump()
        }
      }
    }
  }
}