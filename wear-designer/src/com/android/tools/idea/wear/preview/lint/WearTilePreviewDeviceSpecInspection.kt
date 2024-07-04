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
package com.android.tools.idea.wear.preview.lint

import com.android.tools.idea.preview.util.device.check.DeviceSpecCheck
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.android.tools.idea.wear.preview.isTilePreviewAnnotation
import com.android.tools.preview.config.DEFAULT_WEAROS_DEVICE_ID
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class WearTilePreviewDeviceSpecInspection : WearTilePreviewInspectionBase() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastVisitorAdapter(
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitAnnotation(node: UAnnotation): Boolean {
          if (!node.isTilePreviewAnnotation()) {
            return super.visitAnnotation(node)
          }
          DeviceSpecCheck.checkAnnotation(
              node,
              holder.manager,
              isOnTheFly,
              DEFAULT_WEAROS_DEVICE_ID,
            )
            ?.let { holder.registerProblem(it) }
          return super.visitAnnotation(node)
        }
      },
      true,
    )
  }

  override fun getStaticDescription() = message("inspection.device.spec")
}
