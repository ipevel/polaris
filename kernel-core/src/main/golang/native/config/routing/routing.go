// SPDX-FileCopyrightText: 2026 Polaris Contributors
// SPDX-License-Identifier: GPL-3.0-only

// Package routing 实现 Polaris 本地分流方案（设计移植自 Karing）：
// 状态读取、内置分流表与本地配置生成。本包不依赖平台相关代码，
// 可在任意宿主平台运行单元测试。
package routing

import (
	"encoding/json"
	"io"
	"os"

	"github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

// CustomGroup 用户自定义分流组：一个规则集 URL 对应一个本地分流组。
type CustomGroup struct {
	Name     string `json:"name"`
	URL      string `json:"url"`
	Behavior string `json:"behavior"` // classical | domain | ipcidr
	Interval int    `json:"interval"` // 刷新间隔（秒）
}

// State 本地分流状态，由 App 侧写入 home 目录 routing.json
// （与 override.json 同目录、同读写模式）。缺失或损坏一律回退为关闭，
// 即维持面板下发的 rules/proxy-groups，与 override 的静默降级策略一致。
type State struct {
	Version int           `json:"version"`
	Enabled bool          `json:"enabled"`
	Groups  map[string]bool `json:"groups"` // 内置分流组名 -> 启用；缺省 = 内置默认
	Custom  []CustomGroup `json:"custom"`
}

// StatePath 状态文件路径（Go home 目录，App 侧为 filesDir/clash/）。
func StatePath() string {
	return constant.Path.Resolve("routing.json")
}

// ReadState 读取本地分流状态；任何错误都返回 enabled=false 的零状态。
func ReadState() *State {
	state := &State{Version: 1, Enabled: false}
	file, err := os.OpenFile(StatePath(), os.O_RDONLY, 0600)
	if err != nil {
		return state
	}
	defer file.Close()

	buf, err := io.ReadAll(file)
	if err != nil {
		log.Warnln("Read routing state: %s", err.Error())
		return state
	}
	if len(buf) == 0 {
		return state
	}
	if err := json.Unmarshal(buf, state); err != nil {
		log.Warnln("Decode routing state: %s", err.Error())
		return &State{Version: 1, Enabled: false}
	}
	return state
}

// GroupEnabled 内置组的启用判定：状态缺省时用表内默认值。
func (s *State) GroupEnabled(item RuleGroup) bool {
	if v, ok := s.Groups[item.Name]; ok {
		return v
	}
	return item.DefaultOn
}
