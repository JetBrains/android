package com.android.tools.idea.fileTypes;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.AndroidIcons;
import com.android.tools.idea.lang.rs.RenderscriptLanguage;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AndroidRenderscriptFileType extends LanguageFileType {
  public static final AndroidRenderscriptFileType INSTANCE = new AndroidRenderscriptFileType();
  @NonNls public static final String CODE_EXTENSION = "rs";
  @NonNls public static final String FS_CODE_EXTENSION = "fs";
  @NonNls private static final String HEADER_EXTENSION = "rsh";

  private AndroidRenderscriptFileType() {
    super(RenderscriptLanguage.INSTANCE);
  }
  
  @NotNull
  @Override
  public String getName() {
    return "Android RenderScript";
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.renderscript.file.type.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return CODE_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.Renderscript;
  }

  public static FileNameMatcher[] fileNameMatchers() {
    return new FileNameMatcher[] {
      new ExtensionFileNameMatcher(CODE_EXTENSION),
      new ExtensionFileNameMatcher(FS_CODE_EXTENSION),
      new ExtensionFileNameMatcher(HEADER_EXTENSION),
    };
  }
}
