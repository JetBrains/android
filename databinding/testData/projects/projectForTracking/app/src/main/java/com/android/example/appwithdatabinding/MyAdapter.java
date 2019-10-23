package com.android.example.appwithdatabinding;

import androidx.databinding.BindingAdapter;
import androidx.databinding.InverseBindingMethod;
import androidx.databinding.InverseBindingMethods;

@InverseBindingMethods({@InverseBindingMethod(type = android.widget.TextView.class, attribute = "android:text",
  event = "android:textAttrChanged", method = "getText")})
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