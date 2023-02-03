package androidx.lifecycle;

import _layoutlib_._internal_.androidx.lifecycle.FakeSavedStateRegistry;

public class ViewTreeLifecycleOwner {

  private static boolean myIsReturningNull = true;
  private static FakeSavedStateRegistry savedStateRegistry = new FakeSavedStateRegistry();

  public static LifecycleOwner get(android.view.View view) {
    if (myIsReturningNull) {
      return null;
    }
    return savedStateRegistry;
  }

  /**
   * Used for testing reasons.
   *
   * @param isReturnNull when true get(view) returns null it returns a FakeSavedStateRegistry otherwise.
   */
  public static void setReturnNull(boolean isReturningNull) {
    myIsReturningNull = isReturningNull;
  }
}
