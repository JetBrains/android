package com.android.example.appwithdatabinding;
import android.databinding.BindingAdapter;
public class MyAdapter {
  @BindingAdapter("foo")
  public void bindFoo(android.view.View view, String foo) {

  }

  @BindingAdapter("my_binding_attribute")
  public void bindTestSetter(android.view.View view, String foo) {

  }
}
