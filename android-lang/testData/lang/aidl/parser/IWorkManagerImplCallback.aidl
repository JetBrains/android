// IWorkManagerImplCallback.aidl
package com.google;

oneway interface IWorkManagerImplCallback {
    void onSuccess();
    void onFailure(String error);
}