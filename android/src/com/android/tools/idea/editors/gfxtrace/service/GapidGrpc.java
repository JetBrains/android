package com.android.tools.idea.editors.gfxtrace.service;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;

@javax.annotation.Generated("by gRPC proto compiler")
public class GapidGrpc {

  private GapidGrpc() {}

  public static final String SERVICE_NAME = "service.Gapid";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "Get"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_SET =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "Set"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_FOLLOW =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "Follow"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_SCHEMA =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetSchema"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_AVAILABLE_STRING_TABLES =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetAvailableStringTables"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_STRING_TABLE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetStringTable"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_FEATURES =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetFeatures"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_IMPORT_CAPTURE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "ImportCapture"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_LOAD_CAPTURE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "LoadCapture"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_CAPTURES =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetCaptures"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_DEVICES =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetDevices"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_FRAMEBUFFER_COLOR =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetFramebufferColor"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_FRAMEBUFFER_DEPTH =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetFramebufferDepth"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));
  @io.grpc.ExperimentalApi
  public static final io.grpc.MethodDescriptor<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
      com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> METHOD_GET_TIMING_INFO =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "service.Gapid", "GetTimingInfo"),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response.getDefaultInstance()));

  public static GapidStub newStub(io.grpc.Channel channel) {
    return new GapidStub(channel);
  }

  public static GapidBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new GapidBlockingStub(channel);
  }

  public static GapidFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new GapidFutureStub(channel);
  }

  public static interface Gapid {

    public void get(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void set(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void follow(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getSchema(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getAvailableStringTables(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getStringTable(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getFeatures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void importCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void loadCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getCaptures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getDevices(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getFramebufferColor(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getFramebufferDepth(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);

    public void getTimingInfo(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver);
  }

  public static interface GapidBlockingClient {

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response get(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response set(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response follow(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getSchema(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getAvailableStringTables(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getStringTable(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFeatures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response importCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response loadCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getCaptures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getDevices(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFramebufferColor(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFramebufferDepth(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getTimingInfo(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);
  }

  public static interface GapidFutureClient {

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> get(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> set(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> follow(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getSchema(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getAvailableStringTables(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getStringTable(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFeatures(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> importCapture(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> loadCapture(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getCaptures(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getDevices(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFramebufferColor(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFramebufferDepth(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);

    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getTimingInfo(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request);
  }

  public static class GapidStub extends io.grpc.stub.AbstractStub<GapidStub>
      implements Gapid {
    private GapidStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GapidStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GapidStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GapidStub(channel, callOptions);
    }

    @java.lang.Override
    public void get(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void set(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_SET, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void follow(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_FOLLOW, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getSchema(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_SCHEMA, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getAvailableStringTables(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_AVAILABLE_STRING_TABLES, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getStringTable(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_STRING_TABLE, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getFeatures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_FEATURES, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void importCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_IMPORT_CAPTURE, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void loadCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_LOAD_CAPTURE, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getCaptures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_CAPTURES, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getDevices(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_DEVICES, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getFramebufferColor(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_FRAMEBUFFER_COLOR, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getFramebufferDepth(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_FRAMEBUFFER_DEPTH, getCallOptions()), request, responseObserver);
    }

    @java.lang.Override
    public void getTimingInfo(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request,
        io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_TIMING_INFO, getCallOptions()), request, responseObserver);
    }
  }

  public static class GapidBlockingStub extends io.grpc.stub.AbstractStub<GapidBlockingStub>
      implements GapidBlockingClient {
    private GapidBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GapidBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GapidBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GapidBlockingStub(channel, callOptions);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response get(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response set(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_SET, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response follow(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_FOLLOW, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getSchema(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_SCHEMA, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getAvailableStringTables(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_AVAILABLE_STRING_TABLES, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getStringTable(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_STRING_TABLE, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFeatures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_FEATURES, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response importCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_IMPORT_CAPTURE, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response loadCapture(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_LOAD_CAPTURE, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getCaptures(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_CAPTURES, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getDevices(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_DEVICES, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFramebufferColor(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_FRAMEBUFFER_COLOR, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getFramebufferDepth(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_FRAMEBUFFER_DEPTH, getCallOptions(), request);
    }

    @java.lang.Override
    public com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response getTimingInfo(com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_TIMING_INFO, getCallOptions(), request);
    }
  }

  public static class GapidFutureStub extends io.grpc.stub.AbstractStub<GapidFutureStub>
      implements GapidFutureClient {
    private GapidFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GapidFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GapidFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GapidFutureStub(channel, callOptions);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> get(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> set(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_SET, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> follow(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_FOLLOW, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getSchema(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_SCHEMA, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getAvailableStringTables(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_AVAILABLE_STRING_TABLES, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getStringTable(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_STRING_TABLE, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFeatures(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_FEATURES, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> importCapture(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_IMPORT_CAPTURE, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> loadCapture(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_LOAD_CAPTURE, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getCaptures(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_CAPTURES, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getDevices(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_DEVICES, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFramebufferColor(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_FRAMEBUFFER_COLOR, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getFramebufferDepth(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_FRAMEBUFFER_DEPTH, getCallOptions()), request);
    }

    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response> getTimingInfo(
        com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_TIMING_INFO, getCallOptions()), request);
    }
  }

  private static final int METHODID_GET = 0;
  private static final int METHODID_SET = 1;
  private static final int METHODID_FOLLOW = 2;
  private static final int METHODID_GET_SCHEMA = 3;
  private static final int METHODID_GET_AVAILABLE_STRING_TABLES = 4;
  private static final int METHODID_GET_STRING_TABLE = 5;
  private static final int METHODID_GET_FEATURES = 6;
  private static final int METHODID_IMPORT_CAPTURE = 7;
  private static final int METHODID_LOAD_CAPTURE = 8;
  private static final int METHODID_GET_CAPTURES = 9;
  private static final int METHODID_GET_DEVICES = 10;
  private static final int METHODID_GET_FRAMEBUFFER_COLOR = 11;
  private static final int METHODID_GET_FRAMEBUFFER_DEPTH = 12;
  private static final int METHODID_GET_TIMING_INFO = 13;

  private static class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final Gapid serviceImpl;
    private final int methodId;

    public MethodHandlers(Gapid serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GET:
          serviceImpl.get((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_SET:
          serviceImpl.set((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_FOLLOW:
          serviceImpl.follow((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_SCHEMA:
          serviceImpl.getSchema((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_AVAILABLE_STRING_TABLES:
          serviceImpl.getAvailableStringTables((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_STRING_TABLE:
          serviceImpl.getStringTable((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_FEATURES:
          serviceImpl.getFeatures((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_IMPORT_CAPTURE:
          serviceImpl.importCapture((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_LOAD_CAPTURE:
          serviceImpl.loadCapture((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_CAPTURES:
          serviceImpl.getCaptures((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_DEVICES:
          serviceImpl.getDevices((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_FRAMEBUFFER_COLOR:
          serviceImpl.getFramebufferColor((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_FRAMEBUFFER_DEPTH:
          serviceImpl.getFramebufferDepth((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        case METHODID_GET_TIMING_INFO:
          serviceImpl.getTimingInfo((com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request) request,
              (io.grpc.stub.StreamObserver<com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static io.grpc.ServerServiceDefinition bindService(
      final Gapid serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder(SERVICE_NAME)
        .addMethod(
          METHOD_GET,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET)))
        .addMethod(
          METHOD_SET,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_SET)))
        .addMethod(
          METHOD_FOLLOW,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_FOLLOW)))
        .addMethod(
          METHOD_GET_SCHEMA,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_SCHEMA)))
        .addMethod(
          METHOD_GET_AVAILABLE_STRING_TABLES,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_AVAILABLE_STRING_TABLES)))
        .addMethod(
          METHOD_GET_STRING_TABLE,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_STRING_TABLE)))
        .addMethod(
          METHOD_GET_FEATURES,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_FEATURES)))
        .addMethod(
          METHOD_IMPORT_CAPTURE,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_IMPORT_CAPTURE)))
        .addMethod(
          METHOD_LOAD_CAPTURE,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_LOAD_CAPTURE)))
        .addMethod(
          METHOD_GET_CAPTURES,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_CAPTURES)))
        .addMethod(
          METHOD_GET_DEVICES,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_DEVICES)))
        .addMethod(
          METHOD_GET_FRAMEBUFFER_COLOR,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_FRAMEBUFFER_COLOR)))
        .addMethod(
          METHOD_GET_FRAMEBUFFER_DEPTH,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_FRAMEBUFFER_DEPTH)))
        .addMethod(
          METHOD_GET_TIMING_INFO,
          asyncUnaryCall(
            new MethodHandlers<
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Request,
              com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.Response>(
                serviceImpl, METHODID_GET_TIMING_INFO)))
        .build();
  }
}
