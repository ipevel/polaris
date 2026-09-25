// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

package config

import (
	"errors"

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
		routing.ClearDegraded()
		return nil
	}
	// 自家后端域名优先用 App 侧注入的真实清单（routing.json）；
	// 内核编译期清单只是占位兜底。
	domains := directDomains
	if len(state.DirectDomains) > 0 {
		domains = state.DirectDomains
	}
	if err := routing.Build(cfg, state, domains); err != nil {
		// 生成本地分流失败时回退面板配置（而不是让整个 profile 加载失败）。
		// 写下降级标记，App 侧据此给出可见提示，避免"开关看起来开了但其实没生效"。
		localRoutingEnabled = false
		reason := "build-failed"
		var collision *routing.NameCollisionError
		if errors.As(err, &collision) {
			reason = "name-collision"
		}
		routing.WriteDegraded(reason, err.Error())
		log.Warnln("Apply local routing: %s", err.Error())
		return nil
	}
	routing.ClearDegraded()
	log.Infoln("Local routing applied: %d rules", len(cfg.Rule))
	return nil
}
