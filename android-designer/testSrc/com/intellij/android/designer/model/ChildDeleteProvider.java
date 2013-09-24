package com.intellij.appcode.designer.model;

/**
 * @author Alexander Lobas
 */
public interface ChildDeleteProvider {
  boolean canDelete(RadXmlComponent childComponent);
}
