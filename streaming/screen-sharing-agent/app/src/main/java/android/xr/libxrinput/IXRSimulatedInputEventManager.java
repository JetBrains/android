/*
 * Copyright (C) 2025 The Android Open Source Project
 */
// TODO: Remove this file and use IXRSimulatedInputEventManager.aidl instead after figuring out
//       how to make aidl_library rule accept it.
/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package android.xr.libxrinput;
public interface IXRSimulatedInputEventManager extends android.os.IInterface
{
  /** Default implementation for IXRSimulatedInputEventManager. */
  public static class Default implements android.xr.libxrinput.IXRSimulatedInputEventManager
  {
    /**
     * This is Android XR specific non-standard extension which injects XR
     * related input events into a device which accepting simulated inputs.
     *
     * The support for this interface may not last long.
     *
     * Note: There is a similar and more general purpose interface called
     * IInputFlinger::injectCPMMotionEvent.
     */
    @Override public void injectXRSimulatedMotionEvent(android.view.MotionEvent motionEvent) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.xr.libxrinput.IXRSimulatedInputEventManager
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.xr.libxrinput.IXRSimulatedInputEventManager interface,
     * generating a proxy if needed.
     */
    public static android.xr.libxrinput.IXRSimulatedInputEventManager asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.xr.libxrinput.IXRSimulatedInputEventManager))) {
        return ((android.xr.libxrinput.IXRSimulatedInputEventManager)iin);
      }
      return new android.xr.libxrinput.IXRSimulatedInputEventManager.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_injectXRSimulatedMotionEvent:
        {
          android.view.MotionEvent _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.view.MotionEvent.CREATOR);
          this.injectXRSimulatedMotionEvent(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.xr.libxrinput.IXRSimulatedInputEventManager
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
       * This is Android XR specific non-standard extension which injects XR
       * related input events into a device which accepting simulated inputs.
       *
       * The support for this interface may not last long.
       *
       * Note: There is a similar and more general purpose interface called
       * IInputFlinger::injectCPMMotionEvent.
       */
      @Override public void injectXRSimulatedMotionEvent(android.view.MotionEvent motionEvent) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, motionEvent, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_injectXRSimulatedMotionEvent, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_injectXRSimulatedMotionEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final java.lang.String DESCRIPTOR = "android.xr.libxrinput.IXRSimulatedInputEventManager";
  /**
   * This is Android XR specific non-standard extension which injects XR
   * related input events into a device which accepting simulated inputs.
   *
   * The support for this interface may not last long.
   *
   * Note: There is a similar and more general purpose interface called
   * IInputFlinger::injectCPMMotionEvent.
   */
  public void injectXRSimulatedMotionEvent(android.view.MotionEvent motionEvent) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
