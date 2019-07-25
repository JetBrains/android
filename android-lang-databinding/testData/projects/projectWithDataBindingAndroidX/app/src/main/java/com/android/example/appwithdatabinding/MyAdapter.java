package com.android.example.appwithdatabinding;
import androidx.databinding.BindingAdapter;
import android.view.View.OnClickListener;

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

  @BindingAdapter("onClick2")
  public void bindOnClick2(android.view.View view, OnClickListener listener) {

  }

  @BindingAdapter("onClick3")
  public void bindOnClick3(android.view.View view, OnClickListener listener) {

  }
}
