/* SPDX-FileCopyrightText: The Clash Meta for Android Authors
 * SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
 * Upstream copyright retained per THIRD-PARTY-NOTICES.md.
 */

#include "trace.h"

#if ENABLE_TRACE

void trace_method_exit(const char **name) {
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "TRACE-OUT %s", *name);
}

#endif