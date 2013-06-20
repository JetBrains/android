#pragma once

// Including SDKDDKVer.h defines the highest available Windows platform.

// If you wish to build your application for a previous Windows platform, include WinSDKVer.h and
// set the _WIN32_WINNT macro to the platform you wish to support before including SDKDDKVer.h.


// Indicate we want at least all Windows Server 2003 (5.2) APIs.
// Note: default is set by system/core/include/arch/windows/AndroidConfig.h to 0x0500
// which is Win2K. However our minimum SDK tools requirement is Win XP (0x0501).
#undef  _WIN32_WINNT
#define _WIN32_WINNT 0x0501
// Indicate we want at least all IE 5 shell APIs
#define _WIN32_IE    0x0500


#include <SDKDDKVer.h>

