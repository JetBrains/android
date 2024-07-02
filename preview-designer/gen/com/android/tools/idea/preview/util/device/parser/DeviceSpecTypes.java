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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.preview.util.device.parser.impl.*;

public interface DeviceSpecTypes {

  IElementType BOOLEAN_T = new DeviceSpecElementType("BOOLEAN_T");
  IElementType CHIN_SIZE_PARAM = new DeviceSpecElementType("CHIN_SIZE_PARAM");
  IElementType DPI_PARAM = new DeviceSpecElementType("DPI_PARAM");
  IElementType HEIGHT_PARAM = new DeviceSpecElementType("HEIGHT_PARAM");
  IElementType ID_PARAM = new DeviceSpecElementType("ID_PARAM");
  IElementType IS_ROUND_PARAM = new DeviceSpecElementType("IS_ROUND_PARAM");
  IElementType NAME_PARAM = new DeviceSpecElementType("NAME_PARAM");
  IElementType ORIENTATION_PARAM = new DeviceSpecElementType("ORIENTATION_PARAM");
  IElementType ORIENTATION_T = new DeviceSpecElementType("ORIENTATION_T");
  IElementType PARAM = new DeviceSpecElementType("PARAM");
  IElementType PARENT_PARAM = new DeviceSpecElementType("PARENT_PARAM");
  IElementType SHAPE_PARAM = new DeviceSpecElementType("SHAPE_PARAM");
  IElementType SIZE_T = new DeviceSpecElementType("SIZE_T");
  IElementType SPEC = new DeviceSpecElementType("SPEC");
  IElementType UNIT = new DeviceSpecElementType("UNIT");
  IElementType UNIT_PARAM = new DeviceSpecElementType("UNIT_PARAM");
  IElementType WIDTH_PARAM = new DeviceSpecElementType("WIDTH_PARAM");

  IElementType CHIN_SIZE_KEYWORD = new DeviceSpecTokenType("chinSize");
  IElementType COLON = new DeviceSpecTokenType(":");
  IElementType COMMA = new DeviceSpecTokenType(",");
  IElementType DP = new DeviceSpecTokenType("dp");
  IElementType DPI_KEYWORD = new DeviceSpecTokenType("dpi");
  IElementType EQUALS = new DeviceSpecTokenType("=");
  IElementType FALSE = new DeviceSpecTokenType("false");
  IElementType HEIGHT_KEYWORD = new DeviceSpecTokenType("height");
  IElementType ID_KEYWORD = new DeviceSpecTokenType("id");
  IElementType IS_ROUND_KEYWORD = new DeviceSpecTokenType("isRound");
  IElementType LANDSCAPE_KEYWORD = new DeviceSpecTokenType("landscape");
  IElementType NAME_KEYWORD = new DeviceSpecTokenType("name");
  IElementType NUMERIC_T = new DeviceSpecTokenType("NUMERIC_T");
  IElementType ORIENTATION_KEYWORD = new DeviceSpecTokenType("orientation");
  IElementType PARENT_KEYWORD = new DeviceSpecTokenType("parent");
  IElementType PORTRAIT_KEYWORD = new DeviceSpecTokenType("portrait");
  IElementType PX = new DeviceSpecTokenType("px");
  IElementType SPEC_KEYWORD = new DeviceSpecTokenType("spec");
  IElementType SQUARE_KEYWORD = new DeviceSpecTokenType("square");
  IElementType STRING_T = new DeviceSpecTokenType("STRING_T");
  IElementType TRUE = new DeviceSpecTokenType("true");
  IElementType UNIT_KEYWORD = new DeviceSpecTokenType("unit");
  IElementType WIDTH_KEYWORD = new DeviceSpecTokenType("width");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == BOOLEAN_T) {
        return new DeviceSpecBooleanTImpl(node);
      }
      else if (type == CHIN_SIZE_PARAM) {
        return new DeviceSpecChinSizeParamImpl(node);
      }
      else if (type == DPI_PARAM) {
        return new DeviceSpecDpiParamImpl(node);
      }
      else if (type == HEIGHT_PARAM) {
        return new DeviceSpecHeightParamImpl(node);
      }
      else if (type == ID_PARAM) {
        return new DeviceSpecIdParamImpl(node);
      }
      else if (type == IS_ROUND_PARAM) {
        return new DeviceSpecIsRoundParamImpl(node);
      }
      else if (type == NAME_PARAM) {
        return new DeviceSpecNameParamImpl(node);
      }
      else if (type == ORIENTATION_PARAM) {
        return new DeviceSpecOrientationParamImpl(node);
      }
      else if (type == ORIENTATION_T) {
        return new DeviceSpecOrientationTImpl(node);
      }
      else if (type == PARENT_PARAM) {
        return new DeviceSpecParentParamImpl(node);
      }
      else if (type == SHAPE_PARAM) {
        return new DeviceSpecShapeParamImpl(node);
      }
      else if (type == SIZE_T) {
        return new DeviceSpecSizeTImpl(node);
      }
      else if (type == SPEC) {
        return new DeviceSpecSpecImpl(node);
      }
      else if (type == UNIT) {
        return new DeviceSpecUnitImpl(node);
      }
      else if (type == UNIT_PARAM) {
        return new DeviceSpecUnitParamImpl(node);
      }
      else if (type == WIDTH_PARAM) {
        return new DeviceSpecWidthParamImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
