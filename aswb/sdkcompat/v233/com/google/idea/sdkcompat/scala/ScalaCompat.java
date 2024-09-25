package com.google.idea.sdkcompat.scala;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil;
import scala.Option;

/** Provides SDK compatibility shims for Scala classes, available to IntelliJ CE & UE. */
public class ScalaCompat {
  private ScalaCompat() {}

  /** #api213: Inline the call. Method location and signature changed in 2021.2 */
  public static Option<PsiMethod> findMainMethod(@NotNull ScObject obj) {
    return ScalaMainMethodUtil.findScala2MainMethod(obj);
  }

  /** #api213: Inline the call. Method location and signature changed in 2021.2 */
  public static boolean hasMainMethod(@NotNull ScObject obj) {
    return ScalaMainMethodUtil.hasScala2MainMethod(obj);
  }
}
