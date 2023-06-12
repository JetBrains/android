/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.myrbsdk;
public interface IMySdk extends android.os.IInterface
{
  /** Default implementation for IMySdk. */
  public static class Default implements IMySdk
  {
    @Override public void doMath(int x, int y, IIntTransactionCallback transactionCallback) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements IMySdk
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.myrbsdk.IMySdk interface,
     * generating a proxy if needed.
     */
    public static IMySdk asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof IMySdk))) {
        return ((IMySdk)iin);
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
        case TRANSACTION_doMath:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          IIntTransactionCallback _arg2;
          _arg2 = IIntTransactionCallback.Stub.asInterface(data.readStrongBinder());
          this.doMath(_arg0, _arg1, _arg2);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements IMySdk
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
      @Override public void doMath(int x, int y, IIntTransactionCallback transactionCallback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(x);
          _data.writeInt(y);
          _data.writeStrongInterface(transactionCallback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_doMath, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_doMath = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final String DESCRIPTOR = "com$myrbsdk$IMySdk".replace('$', '.');
  public void doMath(int x, int y, IIntTransactionCallback transactionCallback) throws android.os.RemoteException;
}
