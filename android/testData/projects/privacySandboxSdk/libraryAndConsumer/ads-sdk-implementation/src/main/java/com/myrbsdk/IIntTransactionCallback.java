/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.myrbsdk;
public interface IIntTransactionCallback extends android.os.IInterface
{
  /** Default implementation for IIntTransactionCallback. */
  public static class Default implements IIntTransactionCallback
  {
    @Override public void onCancellable(ICancellationSignal cancellationSignal) throws android.os.RemoteException
    {
    }
    @Override public void onFailure(int errorCode, String errorMessage) throws android.os.RemoteException
    {
    }
    @Override public void onSuccess(int result) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements IIntTransactionCallback
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.myrbsdk.IIntTransactionCallback interface,
     * generating a proxy if needed.
     */
    public static IIntTransactionCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof IIntTransactionCallback))) {
        return ((IIntTransactionCallback)iin);
      }
      return new Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      String descriptor = DESCRIPTOR;
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
        case TRANSACTION_onCancellable:
        {
          ICancellationSignal _arg0;
          _arg0 = ICancellationSignal.Stub.asInterface(data.readStrongBinder());
          this.onCancellable(_arg0);
          break;
        }
        case TRANSACTION_onFailure:
        {
          int _arg0;
          _arg0 = data.readInt();
          String _arg1;
          _arg1 = data.readString();
          this.onFailure(_arg0, _arg1);
          break;
        }
        case TRANSACTION_onSuccess:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.onSuccess(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements IIntTransactionCallback
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
      public String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void onCancellable(ICancellationSignal cancellationSignal) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(cancellationSignal);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onCancellable, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onFailure(int errorCode, String errorMessage) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(errorCode);
          _data.writeString(errorMessage);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onFailure, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      @Override public void onSuccess(int result) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(result);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onSuccess, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onCancellable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onFailure = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onSuccess = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final String DESCRIPTOR = "com$myrbsdk$IIntTransactionCallback".replace('$', '.');
  public void onCancellable(ICancellationSignal cancellationSignal) throws android.os.RemoteException;
  public void onFailure(int errorCode, String errorMessage) throws android.os.RemoteException;
  public void onSuccess(int result) throws android.os.RemoteException;
}
