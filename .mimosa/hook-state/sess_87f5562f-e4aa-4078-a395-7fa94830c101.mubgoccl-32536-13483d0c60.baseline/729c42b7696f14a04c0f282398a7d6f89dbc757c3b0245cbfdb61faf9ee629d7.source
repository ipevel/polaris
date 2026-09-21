//go:build !windows

package app

import "syscall"

func setNonblock(fd int) error {
	return syscall.SetNonblock(fd, true)
}
