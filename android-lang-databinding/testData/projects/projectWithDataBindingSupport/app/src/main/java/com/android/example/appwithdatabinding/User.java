package com.android.example.appwithdatabinding;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

class User extends BaseObservable {
  private String firstName;
  private String lastName;

  @Bindable
  public String getFirstName() {
    return this.firstName;
  }

  @Bindable
  public String getLastName() {
    return this.lastName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
    notifyPropertyChanged(BR.firstName);
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
    notifyPropertyChanged(BR.lastName);
  }
}
