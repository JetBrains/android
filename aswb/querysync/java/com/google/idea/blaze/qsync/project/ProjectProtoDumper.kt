/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project

import com.google.idea.blaze.common.Label
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

interface FormattableModel

/**
 * Outputs project proto to the give stream in a semi-Yaml format.
 */
inline fun <reified T : FormattableModel> T.formatTo(to: OutputStream) {
  PrintStream(BufferedOutputStream(to)).use {
    formatToAs(it, T::class)
  }
}

inline fun <reified T: FormattableModel> T.format(): String {
  val os = ByteArrayOutputStream()
  formatTo(os)
  return os.toByteArray().toString(Charsets.UTF_8)
}

@JvmName("formatTo")
fun ProjectProto.Project.formatTo_javaShim(to: OutputStream) = formatTo(to)

fun FormattableModel.formatToAs(to: PrintStream, klass: KClass<*>) {
  val printer = PrinterImpl(to)
  @Suppress("UNCHECKED_CAST")
  (Dumpers.dumper(klass) as ValueFormatter<FormattableModel>).formatTo(this, printer)
}

private fun interface ValueFormatter<in T> {
  fun formatTo(v: T?, printer: ValuePrinter)
}

private interface ValuePrinter {
  fun value(value: String, nested: (NestedValuePrinter.() -> Unit)? = null)
  fun <T> list(value: Collection<T>, valueFormatter: ValueFormatter<T>)
  fun <T> map(value: Map<Any, T>, valueFormatter: ValueFormatter<T>)
}

private interface NestedValuePrinter {
  fun prop(name: KProperty<*>, v: ValuePrinter.() -> Unit)
}

/**
 * Runs body and captures the value printed via [value] function only.
 */
private fun valueOnlyPrinter(body: NestedValuePrinter.() -> Unit) = buildString {
  (object : NestedValuePrinter {
    override fun prop(name: KProperty<*>, v: ValuePrinter.() -> Unit) {
      (object : ValuePrinter {
        override fun value(value: String, nested: (NestedValuePrinter.() -> Unit)?) {
          append(value)
        }

        override fun <T> list(value: Collection<T>, valueFormatter: ValueFormatter<T>) = Unit
        override fun <T> map(value: Map<Any, T>, valueFormatter: ValueFormatter<T>) = Unit
      }).v()
    }
  }).body()
}

private val KType.isSimple: Boolean
  get() =
    (this.classifier as? KClass<*>)?.typeParameters?.isEmpty() ?: false

private object Dumpers {
  private val registered: MutableMap<KClass<*>, ValueFormatter<*>> = mutableMapOf(
    String::class to SimpleValueFormatter,
    Boolean::class to SimpleValueFormatter,
    Label::class to SimpleValueFormatter,
    ProjectPath::class to ProjectPathValueFormatter,
    ProjectPath.ProjectRelativeProjectPath::class to ProjectPathValueFormatter,
    ProjectPath.WorkspaceRelativeProjectPath::class to ProjectPathValueFormatter,
    ProjectPath.AbsoluteProjectPath::class to ProjectPathValueFormatter,
  )

  @Suppress("UNCHECKED_CAST")
  fun dumper(clazz: KClass<*>): ValueFormatter<*> {
    return registered.getOrPut(clazz) {
      when {
        clazz.isSubclassOf(FormattableModel::class) -> AutoValueFormatter(clazz as KClass<FormattableModel>)
        clazz.isSubclassOf(Enum::class) -> SimpleValueFormatter
        else -> error("unsupported type: $clazz")
      }
    }
  }

  object SimpleValueFormatter : ValueFormatter<Any> {
    override fun formatTo(v: Any?, printer: ValuePrinter) {
      printer.value(value = v?.toString().orEmpty())
    }
  }

  object ProjectPathValueFormatter : ValueFormatter<ProjectPath> {
    override fun formatTo(v: ProjectPath?, printer: ValuePrinter) {
      printer.value(value = v?.toPrintString().orEmpty())
    }
  }
}

private class AutoValueFormatter<T : FormattableModel>(clazz: KClass<T>) : ValueFormatter<T> {

  sealed class Case<in T, in V>(private val property: KProperty<V>, private val getter: (T) -> V?) {
    fun printTo(receiver: T?, printer: NestedValuePrinter) {
      val v = receiver?.let { getter(receiver) } ?: return
      printer.prop(property) {
        printTo(v)
      }
    }

    abstract fun ValuePrinter.printTo(value: V?)
  }

  class ListCase<T, V>(
    property: KProperty<Collection<V>>,
    getter: (T) -> Collection<V>?,
    private val valueFormatter: ValueFormatter<V>,
  ) : Case<T, Collection<V>>(property, { getter(it)?.takeUnless(Collection<*>::isEmpty) }) {
    override fun ValuePrinter.printTo(value: Collection<V>?) {
      list(value.orEmpty(), valueFormatter = valueFormatter)
    }
  }

  class MapCase<T, V>(
    property: KProperty<Map<Any, V>>,
    getter: (T) -> Map<Any, V>?,
    private val valueFormatter: ValueFormatter<V>,
  ) : Case<T, Map<Any, V>>(property, { getter(it)?.takeUnless(Map<*,*>::isEmpty) }) {
    override fun ValuePrinter.printTo(value: Map<Any, V>?) {
      map(value.orEmpty(), valueFormatter = valueFormatter)
    }
  }

  class SimpleCase<T, V>(
    property: KProperty<V>,
    getter: (T) -> V?,
    val valueFormatter: ValueFormatter<V>,
  ) : Case<T, V>(property, { getter(it)?.takeUnless { v -> v == false || v == "" } }) {

    override fun ValuePrinter.printTo(value: V?) {
      valueFormatter.formatTo(value, this)
    }
  }

  private val printFunction: (v: T?, printer: ValuePrinter) -> Unit

  init {
    @Suppress("UNCHECKED_CAST")
    fun dumper(k: KClass<*>): ValueFormatter<Any> = (if (k == clazz) this else Dumpers.dumper(k)) as ValueFormatter<Any>

    val props = clazz
      .primaryConstructor
      ?.parameters
      ?.mapNotNull { (it.name ?: return@mapNotNull null) to it }
      ?.mapNotNull {
        val property = clazz.memberProperties.find { p -> p.name == it.first } ?: return@mapNotNull null
        property
      }
      .orEmpty()
    val cases: List<Case<T, *>> = props.map {
      @Suppress("UNCHECKED_CAST")
      when {
        it.returnType.isSimple
          -> SimpleCase(
          it as KProperty<T>,
          it.getter as (T) -> Any?,
          dumper(it.returnType.classifier as KClass<Any>)
        )

        it.returnType.classifier == List::class || it.returnType.classifier == Set::class
          -> ListCase<T, Any>(
          it as KProperty<List<Any>>,
          it.getter as (T) -> List<Any>?,
          dumper((it.returnType).arguments[0].type?.classifier as KClass<*>)
        )

        it.returnType.classifier == Map::class
          -> MapCase<T, Any>(
          it as KProperty<Map<Any, Any>>,
          it.getter as (T) -> Map<Any, Any>?,
          dumper((it.returnType).arguments[1].type?.classifier as KClass<*>)
        )

        else -> error("Unsupported: $it")
      }
    }
    val valueFunction = cases.firstOrNull()
                          ?.let { it as? SimpleCase<T, *> }
                          ?.let {
                            fun(v: T?): String = valueOnlyPrinter { it.printTo(v, this) }
                          }

    printFunction = fun (v: T?, printer: ValuePrinter) {
      printer.value(valueFunction?.invoke(v).orEmpty()) {
        cases
          .drop(if (valueFunction != null) 1 else 0)
          .forEach { case -> case.printTo(v, this@value) }
      }
    }
  }

  override fun formatTo(v: T?, printer: ValuePrinter) = printFunction(v, printer)
}

private inline fun <reified T : FormattableModel> autoDumper(): ValueFormatter<T> = AutoValueFormatter(T::class)

private class PrinterImpl(private val to: PrintStream) : ValuePrinter, NestedValuePrinter {
  private val indentIncrement = "    "
  private var indent: String = ""
  private var newLine = true
  private var inValue = false

  private fun nest(body: () -> Unit) {
    indent += indentIncrement
    try {
      body()
    }
    finally {
      indent = indent.drop(indentIncrement.length)
    }
  }

  private fun maybeIndent() {
    if (newLine) {
      to.print(indent)
    }
  }

  private fun outln(v: String) {
    maybeIndent()
    to.println(v)
    newLine = true
    inValue = false
  }

  private fun out(v: String) {
    maybeIndent()
    to.print(v)
    newLine = false
  }

  override fun value(value: String, nested: (NestedValuePrinter.() -> Unit)?) {
    fun print() {
      if (!newLine && value.isNotEmpty()) {
        out(" ")
      }
      out(value)
      if (value.isNotEmpty() && nested != null) {
        out(":")
      }
      outln("")
      if (nested != null) {
        nest {
          nested()
        }
      }
    }

    if (inValue && value.isNotEmpty() && nested != null) {
      outln("")
      nest {
        print()
      }
    }
    else {
      print()
    }
  }

  override fun <T> list(value: Collection<T>, valueFormatter: ValueFormatter<T>) {
    outln("")
    nest {
      for (entry in value) {
        out("-")
        valueFormatter.formatTo(entry, this)
      }
    }
  }

  override fun <T> map(value: Map<Any, T>, valueFormatter: ValueFormatter<T>) {
    outln("")
    nest {
      for (entry in value.entries) {
        out("\"${entry.key}\":")
        inValue = true
        valueFormatter.formatTo(entry.value, this)
      }
    }
  }

  override fun prop(name: KProperty<*>, v: ValuePrinter.() -> Unit) {
    out("${name.name}:")
    inValue = true
    v()
  }
}

private fun ProjectPath.toPrintString(): String {
  return when (this) {
    is ProjectPath.ProjectRelativeProjectPath -> "<project>/$relativePath"
    is ProjectPath.WorkspaceRelativeProjectPath -> "<workspace>/$relativePath"
    is ProjectPath.ExternalRepositoryRelativeProjectPath -> "@@$externalRepositoryName/$relativePath"
    is ProjectPath.AbsoluteProjectPath -> absolutePath.toString()
  }
}
