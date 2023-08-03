/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.android.tools.idea.debug.BitmapDrawableRenderer.BITMAP_DRAWABLE_FQCN
import com.android.tools.idea.debug.BitmapPopupEvaluatorV2.ImageResult
import com.android.tools.idea.debug.BitmapPopupEvaluatorV2.ImageResult.Error
import com.android.tools.idea.debug.BitmapPopupEvaluatorV2.ImageResult.Success
import com.android.tools.idea.debug.BitmapRenderer.BITMAP_FQCN
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.tree.render.CustomPopupFullValueEvaluator
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.ImageUtil
import com.sun.jdi.ArrayType
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.intellij.images.editor.impl.ImageEditorManagerImpl
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants.CENTER

/**
 * A [CustomPopupFullValueEvaluator] for Bitmap and BitmapDrawable values.
 *
 * The data is obtained by calling `Bitmap.getPixels()` and creating matching [BufferedImage] from the bitmaps pixels.
 */
internal class BitmapPopupEvaluatorV2(private val evaluationContext: EvaluationContextImpl, private val value: ObjectReference) :
  CustomPopupFullValueEvaluator<ImageResult>("… View Bitmap", evaluationContext) {

  override fun getData(): ImageResult {
    return try {
      val bitmap = value.getBitmap()
      val width = bitmap.getWidth()
      val height = bitmap.getHeight()
      val pixels = bitmap.getPixels(width, height)
      val image = ImageUtil.createImage(width, height, TYPE_INT_ARGB)

      for (x in 0 until width) {
        for (y in 0 until height) {
          val argb = pixels[x + y * width]
          image.setRGB(x, y, argb)
        }
      }
      Success(image)
    } catch (e: Exception) {
      invokeLater {
        // Schedule an uncaught exception to trigger an error notification so user can report a bug.
        throw RuntimeException("Unexpected error while obtaining image", e)
      }
      Error
    }
  }

  override fun createComponent(data: ImageResult): JComponent {
    @Suppress("INACCESSIBLE_TYPE") // Unsure why this warning is reported.
    return when (data) {
      is Success -> ImageEditorManagerImpl.createImageEditorUI(data.image)
      Error -> JLabel("Unexpected error while obtaining image", Messages.getErrorIcon(), CENTER)
    }
  }

  private fun ObjectReference.getBitmap(): ObjectReference {
    val fqcn: String = type().name()

    return when (fqcn) {
      BITMAP_FQCN -> this
      BITMAP_DRAWABLE_FQCN -> getBitmapFromDrawable() ?: throw RuntimeException("Unable to obtain bitmap from drawable")
      else -> throw RuntimeException("Invalid parameter passed into method")
    }
  }

  private fun ObjectReference.getBitmapFromDrawable(): ObjectReference? {
    return try {
      val method = DebuggerUtils.findMethod(referenceType(), "getBitmap", "()Landroid/graphics/Bitmap;") ?: return null
      evaluationContext.debugProcess.invokeMethod(evaluationContext, this, method, listOf()) as ObjectReference
    } catch (ignored: EvaluateException) {
      null
    }
  }

  private fun ObjectReference.getPixels(width: Int, height: Int): List<Int> {
    val method = DebuggerUtils.findMethod(referenceType(), "getPixels", "([IIIIIII)V")
      ?: throw RuntimeException("Method getPixels not found in ${type().name()}")

    val vm = virtualMachine()
    val intArrayType = vm.classesByName("int[]").first() as ArrayType
    val pixels = intArrayType.newInstance(width * height)
    val offset = vm.mirrorOf(0)
    val stride = vm.mirrorOf(width)
    val x = vm.mirrorOf(0)
    val y = vm.mirrorOf(0)
    val args = listOf(pixels, offset, stride, x, y, vm.mirrorOf(width), vm.mirrorOf(height))

    evaluationContext.debugProcess.invokeMethod(evaluationContext, this, method, args)

    return pixels.values.map { (it as IntegerValue).value() }
  }

  private fun ObjectReference.getWidth() = getInt("getWidth")

  private fun ObjectReference.getHeight() = getInt("getHeight")

  private fun ObjectReference.getInt(methodName: String): Int {
    val method = DebuggerUtils.findMethod(referenceType(), methodName, "()I")
      ?: throw RuntimeException("Method $methodName not found in ${type().name()}")
    val value = evaluationContext.debugProcess.invokeMethod(evaluationContext, this, method, emptyList<Value>()) as IntegerValue
    return value.value()
  }

  internal sealed class ImageResult {
    class Success(val image: BufferedImage) : ImageResult()
    object Error : ImageResult()
  }
}
