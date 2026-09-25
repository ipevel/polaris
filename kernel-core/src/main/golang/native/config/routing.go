// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package config

import (
	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"

	"cfa/native/config/routing"
)

// localRoutingEnabled 由 patchLocalRouting 按处理顺序写入、patchProfile 读取；
// process() 串行执行，与 sessionOverride 同样的无锁约定（见 pitfall #18）。
var localRoutingEnabled = false

// patchLocalRouting 读取 App 侧写入的 routing.json，启用时用内置分流表
// （对齐 Karing 预设）整体替换面板下发的 proxy-groups/rules/rule-providers。
// 状态缺失/损坏一律视为关闭，回退到面板配置原样生效。
// 具体逻辑在子包 routing（独立于平台代码，可宿主跑测试），此处仅做适配。
func patchLocalRouting(cfg *config.RawConfig, _ string) error {
	state := routing.ReadState()
	localRoutingEnabled = state.Enabled
	if !state.Enabled {
		return nil
	}
	if err := routing.Build(cfg, state, directDomains); err != nil {
		// 生成本地分流失败时回退面板配置（而不是让整个 profile 加载失败）
		localRoutingEnabled = false
		log.Warnln("Apply local routing: %s", err.Error())
		return nil
	}
	log.Infoln("Local routing applied: %d rules", len(cfg.Rule))
	return nil
}
