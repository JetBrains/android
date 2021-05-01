package com.android.example.appwithdatabinding;
import android.widget.TextView;
import androidx.databinding.BindingAdapter;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;

@BindingMethods({
  @BindingMethod(type = TextView.class, attribute = "android:customTextSetter", method = "setText")
})
public class MyAdapter {
  @BindingAdapter("foo")
  public void bindFoo(android.view.View view, String foo) {

  }

  @BindingAdapter("my_binding_attribute")
  public void bindTestSetter(android.view.View view, String foo) {

  }

  @BindingAdapter("padding")
  public void bindPadding(android.view.View view, String foo) {

  }
}
