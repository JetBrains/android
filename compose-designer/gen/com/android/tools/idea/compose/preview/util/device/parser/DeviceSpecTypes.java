// This is a generated file. Not intended for manual editing.
package com.android.tools.idea.compose.preview.util.device.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.android.tools.idea.compose.preview.util.device.parser.impl.*;

public interface DeviceSpecTypes {

  IElementType CHIN_SIZE_PARAM = new DeviceSpecElementType("CHIN_SIZE_PARAM");
  IElementType DPI_PARAM = new DeviceSpecElementType("DPI_PARAM");
  IElementType HEIGHT_PARAM = new DeviceSpecElementType("HEIGHT_PARAM");
  IElementType IS_ROUND_PARAM = new DeviceSpecElementType("IS_ROUND_PARAM");
  IElementType NAME_PARAM = new DeviceSpecElementType("NAME_PARAM");
  IElementType ORIENTATION_PARAM = new DeviceSpecElementType("ORIENTATION_PARAM");
  IElementType ORIENTATION_T = new DeviceSpecElementType("ORIENTATION_T");
  IElementType PARAM = new DeviceSpecElementType("PARAM");
  IElementType PARENT_PARAM = new DeviceSpecElementType("PARENT_PARAM");
  IElementType SIZE_T = new DeviceSpecElementType("SIZE_T");
  IElementType SPEC = new DeviceSpecElementType("SPEC");
  IElementType UNIT = new DeviceSpecElementType("UNIT");
  IElementType WIDTH_PARAM = new DeviceSpecElementType("WIDTH_PARAM");

  IElementType BOOLEAN = new DeviceSpecTokenType("boolean");
  IElementType CHIN_SIZE_PARAM_KEYWORD = new DeviceSpecTokenType("chinSize");
  IElementType COLON = new DeviceSpecTokenType(":");
  IElementType COMMA = new DeviceSpecTokenType(",");
  IElementType DEVICE_ID_T = new DeviceSpecTokenType("DEVICE_ID_T");
  IElementType DP = new DeviceSpecTokenType("dp");
  IElementType DPI_PARAM_KEYWORD = new DeviceSpecTokenType("dpi");
  IElementType EQUALS = new DeviceSpecTokenType("=");
  IElementType FALSE = new DeviceSpecTokenType("false");
  IElementType HEIGHT_PARAM_KEYWORD = new DeviceSpecTokenType("height");
  IElementType ID_KEYWORD = new DeviceSpecTokenType("id");
  IElementType INT_T = new DeviceSpecTokenType("INT_T");
  IElementType IS_ROUND_PARAM_KEYWORD = new DeviceSpecTokenType("isRound");
  IElementType LANDSCAPE_KEYWORD = new DeviceSpecTokenType("landscape");
  IElementType NAME_PARAM_KEYWORD = new DeviceSpecTokenType("name");
  IElementType ORIENTATION_PARAM_KEYWORD = new DeviceSpecTokenType("orientation");
  IElementType PARENT_PARAM_KEYWORD = new DeviceSpecTokenType("parent");
  IElementType PORTRAIT_KEYWORD = new DeviceSpecTokenType("portrait");
  IElementType PX = new DeviceSpecTokenType("px");
  IElementType SPEC_KEYWORD = new DeviceSpecTokenType("spec");
  IElementType SQUARE_KEYWORD = new DeviceSpecTokenType("square");
  IElementType TRUE = new DeviceSpecTokenType("true");
  IElementType WIDTH_PARAM_KEYWORD = new DeviceSpecTokenType("width");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CHIN_SIZE_PARAM) {
        return new DeviceSpecChinSizeParamImpl(node);
      }
      else if (type == DPI_PARAM) {
        return new DeviceSpecDpiParamImpl(node);
      }
      else if (type == HEIGHT_PARAM) {
        return new DeviceSpecHeightParamImpl(node);
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
      else if (type == PARAM) {
        return new DeviceSpecParamImpl(node);
      }
      else if (type == PARENT_PARAM) {
        return new DeviceSpecParentParamImpl(node);
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
      else if (type == WIDTH_PARAM) {
        return new DeviceSpecWidthParamImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
