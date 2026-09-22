/* SPDX-FileCopyrightText: The Clash Meta for Android Authors
 * SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
 * Upstream copyright retained per THIRD-PARTY-NOTICES.md.
 */

#pragma once

#include "bridge.h"

#include <android/log.h>

#define ENABLE_TRACE 0

#if ENABLE_TRACE

extern void trace_method_exit(const char **name);

#define TRACE_METHOD() __attribute__((cleanup(trace_method_exit))) const char *__method_name = __FUNCTION__; __android_log_print(ANDROID_LOG_VERBOSE, TAG, "TRACE-IN  %s", __method_name)

#else

#define TRACE_METHOD()

#endif