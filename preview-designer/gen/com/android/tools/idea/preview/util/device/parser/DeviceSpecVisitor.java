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

// ATTENTION: This file has been automatically generated from
// preview-designer/src/com/android/tools/idea/preview/util/device/parser/device.bnf.
// Do not edit it manually.
package com.android.tools.idea.preview.util.device.parser;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DeviceSpecVisitor extends PsiElementVisitor {

  public void visitBooleanT(@NotNull DeviceSpecBooleanT o) {
    visitPsiElement(o);
  }

  public void visitChinSizeParam(@NotNull DeviceSpecChinSizeParam o) {
    visitParam(o);
  }

  public void visitCutoutParam(@NotNull DeviceSpecCutoutParam o) {
    visitParam(o);
  }

  public void visitCutoutT(@NotNull DeviceSpecCutoutT o) {
    visitPsiElement(o);
  }

  public void visitDpiParam(@NotNull DeviceSpecDpiParam o) {
    visitParam(o);
  }

  public void visitHeightParam(@NotNull DeviceSpecHeightParam o) {
    visitParam(o);
  }

  public void visitIdParam(@NotNull DeviceSpecIdParam o) {
    visitParam(o);
  }

  public void visitIsRoundParam(@NotNull DeviceSpecIsRoundParam o) {
    visitParam(o);
  }

  public void visitNameParam(@NotNull DeviceSpecNameParam o) {
    visitParam(o);
  }

  public void visitNavigationParam(@NotNull DeviceSpecNavigationParam o) {
    visitParam(o);
  }

  public void visitNavigationT(@NotNull DeviceSpecNavigationT o) {
    visitPsiElement(o);
  }

  public void visitOrientationParam(@NotNull DeviceSpecOrientationParam o) {
    visitParam(o);
  }

  public void visitOrientationT(@NotNull DeviceSpecOrientationT o) {
    visitPsiElement(o);
  }

  public void visitParam(@NotNull DeviceSpecParam o) {
    visitPsiElement(o);
  }

  public void visitParentParam(@NotNull DeviceSpecParentParam o) {
    visitParam(o);
  }

  public void visitSizeT(@NotNull DeviceSpecSizeT o) {
    visitPsiElement(o);
  }

  public void visitSpec(@NotNull DeviceSpecSpec o) {
    visitPsiElement(o);
  }

  public void visitUnit(@NotNull DeviceSpecUnit o) {
    visitPsiElement(o);
  }

  public void visitWidthParam(@NotNull DeviceSpecWidthParam o) {
    visitParam(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
