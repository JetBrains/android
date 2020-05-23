package org.jetbrains.android.refactoring;

public class AndroidRefactoringErrorException extends Exception {
  public AndroidRefactoringErrorException() {
  }

  public AndroidRefactoringErrorException(String message) {
    super(message);
  }
}
