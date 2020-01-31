/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates

import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException
import java.io.IOException
import java.io.StringWriter
import java.util.Properties

/** Escapes a property value (such that its syntax is valid in a Java properties file  */
class FmEscapePropertyValueMethod : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 1) {
      throw TemplateModelException("Wrong arguments")
    }

    // Slow, stupid implementation, but is 100% compatible with Java's property file implementation
    val properties = Properties()
    val value = args[0].toString()
    properties.setProperty("k", value) // key doesn't matter
    val writer = StringWriter()
    val escaped: String = try {
      properties.store(writer, null)
      val s = writer.toString()
      var end = s.length

      // Writer inserts trailing newline
      val lineSeparator = System.lineSeparator()
      if (s.endsWith(lineSeparator)) {
        end -= lineSeparator.length
      }

      val start = s.indexOf('=')
      assert(start != -1) { s }

      s.substring(start + 1, end)
    }
    catch (e: IOException) {
       value // shouldn't happen; we're not going to disk
    }

    return SimpleScalar(escaped)
  }
}
