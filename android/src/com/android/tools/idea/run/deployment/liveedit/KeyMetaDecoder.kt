/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

data class InvalidateGroupEntry (var key : Int, val startOffset : Int, val endOffSet : Int)

/**
 * Used mostly for testing / debugging.
 */
fun main(args : Array<String>) {
  var input = args[0]
  var reader = ClassReader(Files.readAllBytes(Path.of(input)))
  computeGroups(reader)
}

internal fun computeGroups(reader: ClassReader): Pair<String?, List<InvalidateGroupEntry>> {
  var visitor = KeyMetaAnnotationVisitor()
  reader.accept(object : ClassVisitor(Opcodes.ASM5) {
    override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor {
      return visitor
    }
  }, 0)
  var totalGroups = visitor.keys.size

  if (totalGroups != visitor.startOffsets.size) {
    print("Invalid startOffSet size")
  }
  if (totalGroups != visitor.endOffsets.size) {
    print("Invalid endOffSet size")
  }

  var groups = ArrayList<InvalidateGroupEntry>(totalGroups)
  for (i in 0 until totalGroups) {
    groups.add(InvalidateGroupEntry(visitor.keys[i], visitor.startOffsets[i], visitor.endOffsets[i]))
  }

  return Pair(visitor.file, groups)
}

internal class KeyMetaAnnotationVisitor : AnnotationVisitor(Opcodes.ASM5) {
  var file : String? = null
  var keys = ArrayList<Int>()
  var startOffsets = ArrayList<Int>()
  var endOffsets = ArrayList<Int>()

  override fun visit(name: String, value: Any) {
    if ("file" == name) {
      file = value.toString()
    }
    super.visit(name, value)
  }


  override fun visitArray(name: String?): AnnotationVisitor? {
    return GroupKeyAnnotationVisitor(this)
  }
}

internal class GroupKeyAnnotationVisitor(val parent: KeyMetaAnnotationVisitor) : AnnotationVisitor(Opcodes.ASM5) {
  override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
    return this
  }

  override fun visit(name: String, value: Any) {
    if ("key" == name) {
      parent.keys.add(value.toString().toInt())
    } else if ("startOffset" == name) {
      parent.startOffsets.add(value.toString().toInt())
    } else if ("endOffset" == name) {
      parent.endOffsets.add(value.toString().toInt())
    }
  }
}
