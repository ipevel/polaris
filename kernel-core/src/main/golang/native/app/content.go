// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package app

import (
	"errors"
	"os"
)

var openContentImpl = func(url string) (int, error) {
	return -1, errors.New("not implement")
}

func OpenContent(url string) (*os.File, error) {
	fd, err := openContentImpl(url)

	if err != nil {
		return nil, err
	}

	_ = setNonblock(fd)

	return os.NewFile(uintptr(fd), "fd"), nil
}

func ApplyContentContext(openContent func(string) (int, error)) {
	openContentImpl = openContent
}
