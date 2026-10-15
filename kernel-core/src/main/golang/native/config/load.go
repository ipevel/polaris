// SPDX-FileCopyrightText: The Clash Meta for Android Authors
// SPDX-FileCopyrightText: 2026 Polaris Contributors (modifications)
// SPDX-License-Identifier: GPL-3.0-only
//
// Derived from Clash Meta for Android (https://github.com/MetaCubeX/ClashMetaForAndroid).
// Upstream copyright retained per THIRD-PARTY-NOTICES.md.

package config

import (
	"os"
	P "path"
	"runtime"
	"strings"
	"sync"

	"cfa/native/app"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/log"
)

func logDns(cfg *config.RawConfig) {
	bytes, err := yaml.Marshal(&cfg.DNS)
	if err != nil {
		log.Warnln("Marshal dns: %s", err.Error())

		return
	}

	log.Infoln("dns:")

	for _, line := range strings.Split(string(bytes), "\n") {
		log.Infoln("  %s", line)
	}
}

func UnmarshalAndPatch(profilePath string) (*config.RawConfig, error) {
	configPath := P.Join(profilePath, "config.yaml")

	configData, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}

	rawConfig, err := config.UnmarshalRawConfig(configData)
	if err != nil {
		return nil, err
	}

	if err := process(rawConfig, profilePath); err != nil {
		return nil, err
	}

	return rawConfig, nil
}

func Parse(rawConfig *config.RawConfig) (*config.Config, error) {
	cfg, err := config.ParseRawConfig(rawConfig)
	if err != nil {
		return nil, err
	}

	return cfg, nil
}

// applyConfigMu 串行化**所有**把配置灌进内核的入口（Load / LoadDefault）。
//
// 为什么必须有：hub.ApplyConfig 内部会调 route.ReCreateServer（无锁）与
// executor.ApplyConfig。而 native 层存在两条**互不串行化**的配置装载链：
//  1) Java 侧 ConfigurationModule 的重载 Channel（它只串行化自己那条链）；
//  2) Java 侧 KernelProxySpeed 经 Clash.loadActiveProfile() 走的独立入口
//     （native/config.go 的 load 导出）。
// 两条链重叠时会在同一进程内并发重建路由服务器，表现为「刚选的节点被重置 /
// 策略组错乱 / 内核半加载」。config.Load 是这两条链在 Go 侧的唯一汇聚点，
// 因此把锁放在这里并覆盖 Load 全文——load 导出只是调用方，锁若加在它
// spawn 的 goroutine 之外就等于没加（Load 实际执行在 goroutine 内）。
//
// 重入检查（改动前必须复核）：全仓库 config.Load 只有 native/config.go:51 一个调用点，
// 且 Load / LoadDefault 内部都不会再进入本入口（UnmarshalAndPatch → process →
// routing 补丁不回调 Load），故不存在同 goroutine 重入导致的死锁。与 mihomo
// 内部 executor 的 mux 也不构成锁序反转：没有任何持 mux 的路径会回调 config.Load。
var applyConfigMu sync.Mutex

// applyConfig 是 hub.ApplyConfig 的注入点，只为单测存在：D1 的回归测试要断言
// 「两条装载链共用同一把锁」，而直接调 hub.ApplyConfig 会把内核初始化拖进单测
// （且 config 包测试二进制在本机因 android 标签无法链接）。换掉这一步，测试就能在
// 不初始化内核、不碰 JNI 的前提下观察「谁在什么并发度下进入了应用阶段」。
// 生产路径取值恒为 hub.ApplyConfig 本身，无行为差异。
var applyConfig = hub.ApplyConfig

// Load 装载 profile 并应用为内核配置；全程持有 applyConfigMu（见其上注释）。
func Load(path string) error {
	applyConfigMu.Lock()
	defer applyConfigMu.Unlock()

	rawCfg, err := UnmarshalAndPatch(path)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	logDns(rawCfg)

	cfg, err := Parse(rawCfg)
	if err != nil {
		log.Errorln("Load %s: %s", path, err.Error())

		return err
	}

	// like hub.Parse()
	applyConfig(cfg)

	app.ApplySubtitlePattern(rawCfg.ClashForAndroid.UiSubtitlePattern)

	runtime.GC()

	return nil
}

// LoadDefault 与 Load 共用同一把锁：reset()（native/main.go:47 → Clash.reset，由
// ClashRuntime.kt:38/58 触发）会走这条路径。只锁 Load 的话，LoadDefault 仍可能与一次
// Load 并发进入 hub.ApplyConfig，D1 的竞态就只堵了一半。
func LoadDefault() {
	applyConfigMu.Lock()
	defer applyConfigMu.Unlock()

	cfg, err := config.Parse([]byte{})
	if err != nil {
		panic(err.Error())
	}

	applyConfig(cfg)
}
