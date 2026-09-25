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
// 即维持面板下发的 rules/proxy-groups，与 override 的静默降级策略一致；
// App 侧 RoutingState 的缺省值与之保持一致（两侧均默认关闭）。
type State struct {
	Version int `json:"version"`
	Enabled bool `json:"enabled"`
	Groups  map[string]bool `json:"groups"` // 内置分流组名 -> 启用；缺省 = 内置默认
	Custom  []CustomGroup   `json:"custom"`
	// DirectDomains 自家后端域名（App 构建期注入清单），作为生成规则最前
	// 的直连白名单。为空时内核退回编译期占位清单。
	DirectDomains []string `json:"direct_domains"`
}

// StatePath 状态文件路径（Go home 目录，App 侧为 filesDir/clash/）。
func StatePath() string {
	return constant.Path.Resolve("routing.json")
}

// DegradedPath 降级标记文件路径。本地分流因「面板与保留名冲突」等原因
// 无法应用时写入，App 侧读取后给用户可见提示——避免静默退回面板分流
// 让用户以为「分流开关坏了」。成功应用本地分流时删除。
func DegradedPath() string {
	return constant.Path.Resolve("routing-degraded.json")
}

// Degraded 降级标记内容。
type Degraded struct {
	Reason string `json:"reason"`
	Detail string `json:"detail"`
}

// WriteDegraded 写入降级标记（失败仅告警，不影响连接）。
func WriteDegraded(reason, detail string) {
	buf, err := json.Marshal(&Degraded{Reason: reason, Detail: detail})
	if err != nil {
		log.Warnln("Encode routing degraded: %s", err.Error())
		return
	}
	if err := os.WriteFile(DegradedPath(), buf, 0600); err != nil {
		log.Warnln("Write routing degraded: %s", err.Error())
	}
}

// ClearDegraded 删除降级标记；文件不存在视为成功。
func ClearDegraded() {
	if err := os.Remove(DegradedPath()); err != nil && !os.IsNotExist(err) {
		log.Warnln("Clear routing degraded: %s", err.Error())
	}
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
