// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser;

import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecBooleanTImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecChinSizeParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecDpiParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecHeightParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecIdParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecIsRoundParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecNameParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecOrientationParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecOrientationTImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecParentParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecShapeParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecSizeTImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecSpecImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecUnitImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecUnitParamImpl;
import com.android.tools.idea.compose.preview.util.device.parser.impl.DeviceSpecWidthParamImpl;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

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

  final class Factory {
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
