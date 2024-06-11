/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.avd

import com.android.sdklib.devices.DeviceParser
import java.io.ByteArrayInputStream

fun readTestDevices() =
  DeviceParser.parse(ByteArrayInputStream(testDeviceXml.encodeToByteArray())).values().toList()

val testDeviceXml =
  """
<d:devices xmlns:d="http://schemas.android.com/sdk/devices/7">
    <d:device>
        <d:name>Pixel 8</d:name>
        <d:id>pixel_8</d:id>
        <d:manufacturer>Google</d:manufacturer>
        <d:playstore-enabled>true</d:playstore-enabled>
        <d:hardware>
            <d:screen>
                <d:screen-size>normal</d:screen-size>
                <d:diagonal-length>6.17</d:diagonal-length>
                <d:pixel-density>420dpi</d:pixel-density>
                <d:screen-ratio>long</d:screen-ratio>
                <d:dimensions>
                    <d:x-dimension>1080</d:x-dimension>
                    <d:y-dimension>2400</d:y-dimension>
                </d:dimensions>
                <d:xdpi>429</d:xdpi>
                <d:ydpi>427</d:ydpi>
                <d:touch>
                    <d:multitouch>jazz-hands</d:multitouch>
                    <d:mechanism>finger</d:mechanism>
                    <d:screen-type>capacitive</d:screen-type>
                </d:touch>
            </d:screen>
            <d:networking>Wifi Bluetooth NFC</d:networking>
            <d:sensors>Accelerometer Barometer Compass GPS Gyroscope LightSensor ProximitySensor StepCounter StepDetector Fingerprint</d:sensors>
            <d:mic>true</d:mic>
            <d:camera>
                <d:location>back</d:location>
                <d:autofocus>true</d:autofocus>
                <d:flash>true</d:flash>
            </d:camera>
            <d:camera>
                <d:location>front</d:location>
                <d:autofocus>true</d:autofocus>
                <d:flash>false</d:flash>
            </d:camera>
            <d:keyboard>nokeys</d:keyboard>
            <d:nav>nonav</d:nav>
            <d:ram unit="MiB">7562</d:ram>
            <d:buttons>soft</d:buttons>
            <d:internal-storage unit="MiB">8134</d:internal-storage>
            <d:removable-storage unit="MiB">112288</d:removable-storage>
            <d:cpu>Google Tensor G3</d:cpu>
            <d:gpu>ARM, Mali-G715, OpenGL ES 3.2 v1.r44p0-01eac0.d0969c01d66270848df0f1eaaba55820</d:gpu>
            <d:abi>
                arm64-v8a
            </d:abi>
            <d:dock> </d:dock>
            <d:power-type>battery</d:power-type>
            <d:skin>pixel_8</d:skin>
        </d:hardware>
        <d:software>
            <d:api-level>34</d:api-level>
            <d:live-wallpaper-support>true</d:live-wallpaper-support>
            <d:bluetooth-profiles> </d:bluetooth-profiles>
            <d:gl-version>3.2</d:gl-version>
            <d:gl-extensions>GL_EXT_debug_marker GL_ARM_rgba8 GL_ARM_mali_shader_binary GL_OES_depth24 GL_OES_depth_texture GL_OES_depth_texture_cube_map GL_OES_packed_depth_stencil GL_OES_rgb8_rgba8 GL_EXT_read_format_bgra GL_OES_compressed_paletted_texture GL_OES_compressed_ETC1_RGB8_texture GL_OES_standard_derivatives GL_OES_EGL_image GL_OES_EGL_image_external GL_OES_EGL_image_external_essl3 GL_OES_EGL_sync GL_OES_texture_npot GL_OES_vertex_half_float GL_OES_required_internalformat GL_OES_vertex_array_object GL_OES_mapbuffer GL_EXT_texture_format_BGRA8888 GL_EXT_texture_rg GL_EXT_texture_type_2_10_10_10_REV GL_OES_fbo_render_mipmap GL_OES_element_index_uint GL_EXT_shadow_samplers GL_OES_texture_compression_astc GL_KHR_texture_compression_astc_ldr GL_KHR_texture_compression_astc_hdr GL_KHR_texture_compression_astc_sliced_3d GL_EXT_texture_compression_astc_decode_mode GL_EXT_texture_compression_astc_decode_mode_rgb9e5 GL_KHR_debug GL_EXT_occlusion_query_boolean GL_EXT_disjoint_timer_query GL_EXT_blend_minmax GL_EXT_discard_framebuffer GL_OES_get_program_binary GL_OES_texture_3D GL_EXT_texture_storage GL_EXT_multisampled_render_to_texture GL_EXT_multisampled_render_to_texture2 GL_OES_surfaceless_context GL_OES_texture_stencil8 GL_EXT_shader_pixel_local_storage GL_ARM_shader_framebuffer_fetch GL_ARM_shader_framebuffer_fetch_depth_stencil GL_ARM_mali_program_binary GL_EXT_sRGB GL_EXT_sRGB_write_control GL_EXT_texture_sRGB_decode GL_EXT_texture_sRGB_R8 GL_EXT_texture_sRGB_RG8 GL_KHR_blend_equation_advanced GL_KHR_blend_equation_advanced_coherent GL_OES_texture_storage_multisample_2d_array GL_OES_shader_image_atomic GL_EXT_robustness GL_EXT_draw_buffers_indexed GL_OES_draw_buffers_indexed GL_EXT_texture_border_clamp GL_OES_texture_border_clamp GL_EXT_texture_cube_map_array GL_OES_texture_cube_map_array GL_OES_sample_variables GL_OES_sample_shading GL_OES_shader_multisample_interpolation GL_EXT_shader_io_blocks GL_OES_shader_io_blocks GL_EXT_tessellation_shader GL_OES_tessellation_shader GL_EXT_primitive_bounding_box GL_OES_primitive_bounding_box GL_EXT_geometry_shader GL_OES_geometry_shader GL_ANDROID_extension_pack_es31a GL_EXT_gpu_shader5 GL_OES_gpu_shader5 GL_EXT_texture_buffer GL_OES_texture_buffer GL_EXT_copy_image GL_OES_copy_image GL_EXT_shader_non_constant_global_initializers GL_EXT_color_buffer_half_float GL_EXT_unpack_subimage GL_EXT_color_buffer_float GL_EXT_float_blend GL_EXT_YUV_target GL_OVR_multiview GL_OVR_multiview2 GL_OVR_multiview_multisampled_render_to_texture GL_KHR_robustness GL_KHR_robust_buffer_access_behavior GL_EXT_draw_elements_base_vertex GL_OES_draw_elements_base_vertex GL_EXT_protected_textures GL_EXT_buffer_storage GL_EXT_external_buffer GL_EXT_EGL_image_array GL_EXT_clear_texture GL_EXT_texture_filter_anisotropic GL_OES_texture_float_linear GL_ARM_texture_unnormalized_coordinates GL_EXT_texture_storage_compression GL_EXT_EGL_image_storage_compression GL_EXT_shader_framebuffer_fetch GL_EXT_clip_control GL_EXT_fragment_shading_rate GL_EXT_fragment_shading_rate_primitive GL_EXT_fragment_shading_rate_attachment GL_EXT_polygon_offset_clamp </d:gl-extensions>
            <d:status-bar>true</d:status-bar>
        </d:software>
        <d:state name="Portrait" default="true">
            <d:description>The device in portrait view</d:description>
            <d:screen-orientation>port</d:screen-orientation>
            <d:keyboard-state>keyssoft</d:keyboard-state>
            <d:nav-state>nonav</d:nav-state>
        </d:state>
        <d:state name="Landscape">
            <d:description>The device in landscape view</d:description>
            <d:screen-orientation>land</d:screen-orientation>
            <d:keyboard-state>keyssoft</d:keyboard-state>
            <d:nav-state>nonav</d:nav-state>
        </d:state>
    </d:device>
</d:devices>
"""
