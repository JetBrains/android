/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager

import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import java.util.Observable
import javax.swing.JComboBox
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.text.Document
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

// TODO convert to the Observable framework

/**
 * Classes used to create a binding between a SwingComponent and a property.
 */
abstract class InputParam<T : Any?>(
  var placeholder: String? = null
) : Observable() {

  var paramValue: T? by Delegates.observable<T?>(null) { _, old, new ->
    if (new != old) setChanged(); notifyObservers(new)
  }

  override fun toString(): String = this::class.simpleName + " " + this::class.typeParameters.joinToString()
}

/**
 * Class used to bind ComboBoxes to a collection and a selected value.
 */
open class CollectionParam<T>(values: Collection<T>,
                              placeholder: String? = null,
                              var parser: ((T?) -> String?)? = null
) : InputParam<T>(placeholder) {
  var values: Collection<T> by Delegates.observable(values) { _, old, new -> if (old != new) setChanged(); notifyObservers(new) }
}

/**
 * Class used to bind a text field to integer
 * @param range An optional [IntRange] used to validate the input.
 */
data class IntParam(val range: IntRange? = null) : InputParam<Int?>()

/**
 * Class used to bind a text field to String
 */
data class TextParam(val validator: (String) -> Boolean) : InputParam<String>()

/**
 * Class that run the runnable provided in [update] only if it's not already being invoked.
 */
private class UpdateLock {
  private var isRunning = false

  @Synchronized
  fun update(runnable: () -> Unit) {
    if (!isRunning) {
      isRunning = true
      runnable()
      isRunning = false
    }
  }
}

/**
 * Binds a [JComboBox] to a [CollectionParam], setting [CollectionParam.paramValue] when an item
 * is selected in the comboBox.
 * The [JComboBox] items are automatically updated when [CollectionParam.values] is updated.
 */
fun <T, ComboBox : JComboBox<T>> CollectionParam<T>.bind(comboBox: ComboBox): ComboBox {
  val lock = UpdateLock()
  val comboModel = CollectionComboBoxModel<T>(values.toMutableList(), paramValue)

  comboBox.model = comboModel
  comboBox.addActionListener {
    lock.update {
      @Suppress("UNCHECKED_CAST")
      paramValue = comboBox.selectedItem as? T?
    }
  }

  addObserver { _, _ ->
    lock.update {
      val index = comboBox.selectedIndex
      comboModel.removeAll()
      comboModel.addAll(0, values.toList())
      comboBox.selectedIndex = max(-1, min(comboBox.model.size - 1, index))
    }
  }
  return comboBox
}

/**
 * Binds a [JTextField] to a [TextParam].
 * This creates a two way binding between the [JTextField] document and the [TextParam.paramValue].
 */
fun TextParam.bind(document: Document) {
  val lock = UpdateLock()

  // Initialize the document with the value of the param
  document.remove(0, document.length)
  document.insertString(0, paramValue.toString(), null)

  document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      lock.update {
        val text = e.document.getText(0, e.document.length)
        paramValue = if (validator(text)) text else null
      }
    }
  })

  addObserver { _, text ->
    lock.update {
      document.remove(0, document.length)
      document.insertString(0, text as String, null)
    }
  }
}

/**
 * Binds a [JTextField] to an [IntParam] and parses the content of the field as an int if possible.
 * If the text cannot be parsed as an Int, or a [IntParam.range] is provided and the value is outside the range,
 * [IntParam.paramValue] is set to null.
 * This creates a two way binding between the [JTextField] document and the [IntParam.paramValue].
 */
fun IntParam.bind(document: Document) {
  val lock = UpdateLock()

  // Initialize the document with the value of the param
  document.remove(0, document.length)
  document.insertString(0, paramValue.toString(), null)

  document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      lock.update {
        paramValue = try {
          val parsedInt = Integer.parseInt(e.document.getText(0, e.document.length))
          if (range == null || parsedInt in range) parsedInt else null
        }
        catch (numberFormatException: NumberFormatException) {
          null
        }
      }
    }
  })

  addObserver { _, _ ->
    lock.update {
      document.remove(0, document.length)
      document.insertString(0, paramValue.toString(), null)
    }
  }
}