// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package config

import (
	"sync"
	"sync/atomic"
	"testing"
	"time"

	mihomoconfig "github.com/metacubex/mihomo/config"
)

// swapApplyConfig 把应用阶段换成探针，并在用例结束时还原。
func swapApplyConfig(t *testing.T, probe func(*mihomoconfig.Config)) {
	t.Helper()
	original := applyConfig
	applyConfig = probe
	t.Cleanup(func() { applyConfig = original })
}

// 空配置能否被解析决定了 LoadDefault 会不会 panic（生产路径 panic(err)）。
// 本容器实测 err=nil；这里只作为护栏，避免环境差异把用例变成一条误导性的崩溃。
func skipIfEmptyConfigUnparsable(t *testing.T) {
	t.Helper()
	if _, err := mihomoconfig.Parse([]byte{}); err != nil {
		t.Skipf("本环境无法解析空配置，跳过 LoadDefault 用例: %v", err)
	}
}

// D1 回归：native 层两条配置装载链（Java 侧重载 Channel 与 KernelProxySpeed 的
// loadActiveProfile）在 Go 侧唯一汇聚到 Load / LoadDefault，两者必须共用同一把
// applyConfigMu——只锁其中一条，另一条仍能与它并发进入 hub.ApplyConfig
// （route.ReCreateServer 无锁，表现为策略组错乱 / 半加载）。
//
// 手法：测试线程先持锁，再并发发起两条入口，两条都必须被挡在锁外；放锁后
// Load（空 profile 目录）返回错误且不触碰探针，只有 LoadDefault 走到应用阶段。
func TestLoadAndLoadDefaultShareApplyConfigMutex(t *testing.T) {
	skipIfEmptyConfigUnparsable(t)

	var calls int32
	swapApplyConfig(t, func(*mihomoconfig.Config) { atomic.AddInt32(&calls, 1) })

	applyConfigMu.Lock()

	loadDone := make(chan error, 1)
	go func() { loadDone <- Load(t.TempDir()) }()
	defaultDone := make(chan struct{})
	go func() {
		LoadDefault()
		close(defaultDone)
	}()

	select {
	case err := <-loadDone:
		t.Fatalf("锁被占用时 Load 提前返回: %v", err)
	case <-defaultDone:
		t.Fatal("锁被占用时 LoadDefault 已完成（未取到 applyConfigMu）")
	case <-time.After(200 * time.Millisecond):
	}

	applyConfigMu.Unlock()

	select {
	case err := <-loadDone:
		if err == nil {
			t.Fatal("空 profile 目录应让 Load 返回错误")
		}
	case <-time.After(5 * time.Second):
		t.Fatal("放锁后 Load 未返回")
	}
	select {
	case <-defaultDone:
	case <-time.After(5 * time.Second):
		t.Fatal("放锁后 LoadDefault 未返回")
	}

	if got := atomic.LoadInt32(&calls); got != 1 {
		t.Fatalf("applyConfig 调用次数 = %d, 期望 1（坏路径的 Load 不该走到应用阶段）", got)
	}
}

// D1 回归（互斥强度）：多个 goroutine 同时进 LoadDefault，探针观察到的最大并发
// 进入数必须是 1。去掉 applyConfigMu 后该断言会稳定失败（并发进入数 > 1）。
func TestConcurrentApplyConfigEntriesAreSerialized(t *testing.T) {
	skipIfEmptyConfigUnparsable(t)

	const entries = 8

	var active, maxActive, total int32
	swapApplyConfig(t, func(*mihomoconfig.Config) {
		cur := atomic.AddInt32(&active, 1)
		for {
			prev := atomic.LoadInt32(&maxActive)
			if cur <= prev || atomic.CompareAndSwapInt32(&maxActive, prev, cur) {
				break
			}
		}
		time.Sleep(2 * time.Millisecond)
		atomic.AddInt32(&active, -1)
		atomic.AddInt32(&total, 1)
	})

	var wg sync.WaitGroup
	for i := 0; i < entries; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			LoadDefault()
		}()
	}
	wg.Wait()

	if got := atomic.LoadInt32(&maxActive); got != 1 {
		t.Fatalf("applyConfig 最大并发进入数 = %d, 期望 1（两条装载链未串行化）", got)
	}
	if got := atomic.LoadInt32(&total); got != entries {
		t.Fatalf("applyConfig 调用次数 = %d, 期望 %d", got, entries)
	}
}