package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"github.com/dlclark/regexp2"

	"cfa/native/common"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/log"
)

var processors = []processor{
	patchExternalController, // must before patchOverride, so we only apply ExternalController in Override settings
	patchOverride,
	patchGeneral,
	patchProfile,
	patchDns,
	patchRules,
	patchTun,
	patchListeners,
	patchProviders,
	validConfig,
}

type processor func(cfg *config.RawConfig, profileDir string) error

func patchOverride(cfg *config.RawConfig, _ string) error {
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotPersist))).Decode(cfg); err != nil {
		log.Warnln("Apply persist override: %s", err.Error())
	}
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotSession))).Decode(cfg); err != nil {
		log.Warnln("Apply session override: %s", err.Error())
	}

	return nil
}

func patchExternalController(cfg *config.RawConfig, _ string) error {
	cfg.ExternalController = ""
	cfg.ExternalControllerTLS = ""
	// 安全:订阅内容不得开启任何 REST 控制面(含 Unix socket / named pipe)或自带 secret
	cfg.ExternalControllerUnix = ""
	cfg.ExternalControllerPipe = ""
	cfg.Secret = ""

	return nil
}

func patchGeneral(cfg *config.RawConfig, _ string) error {
	cfg.Interface = ""
	cfg.RoutingMark = 0

	// 安全:订阅内容不得打开任何入站监听/局域网入口(flow-style、引号键等
	// 可绕过 app 侧行级脱敏,这里做最终兜底)
	cfg.Port = 0
	cfg.SocksPort = 0
	cfg.RedirPort = 0
	cfg.TProxyPort = 0
	cfg.MixedPort = 0
	cfg.ShadowSocksConfig = ""
	cfg.VmessConfig = ""
	cfg.AllowLan = false
	cfg.BindAddress = ""
	cfg.Authentication = nil
	cfg.Tunnels = nil
	cfg.TuicServer = config.RawTuicServer{}
	cfg.Listeners = nil
	cfg.Hosts = nil
	cfg.TLS = config.RawTLS{}
	// 安全:订阅不得启用 iptables 流量劫持(无 tproxy 端口时 executor 会 os.Exit(2) 崩溃)
	cfg.IPTables = config.RawIPTables{}

	return nil
}

func patchProfile(cfg *config.RawConfig, _ string) error {
	cfg.Profile.StoreSelected = false
	cfg.Profile.StoreFakeIP = true

	// 安全:订阅不得携带正则匹配脚本(ReDoS 输入面,app 侧脱敏可被绕过)
	cfg.ClashForAndroid.UiSubtitlePattern = ""

	return nil
}

// 自家后端域名：必须与 app 侧 RemoteConfig.ALLOWED_HOST_SUFFIXES 保持同步，
// 新增白名单域时同步此清单（app 清洗注入 + 内核兜底双防线一致）
var directDomains = []string{"example.com"}

func patchDns(cfg *config.RawConfig, _ string) error {
	// 安全:订阅不得开启本机/局域网 DNS 监听(未认证递归解析器)
	cfg.DNS.Listen = ""

	// 安全:订阅不得提供 DNS 上游/解析模式(fake-ip 范围、redir-host、自定
	// nameserver/fallback/policy 等),一律重置为应用侧默认解析,防止订阅方
	// nameserver 接管全设备解析
	cfg.DNS = config.DefaultRawConfig().DNS
	cfg.DNS.Enable = true
	cfg.DNS.NameServer = defaultNameServers
	cfg.DNS.EnhancedMode = C.DNSFakeIP
	cfg.DNS.FakeIPRange = defaultFakeIPRange
	cfg.DNS.FakeIPFilter = defaultFakeIPFilter

	cfg.ClashForAndroid.AppendSystemDNS = true

	if cfg.ClashForAndroid.AppendSystemDNS {
		cfg.DNS.NameServer = append(cfg.DNS.NameServer, "system://")
	}

	// 自家后端域名不进 fake-ip：否则内核抓订阅时解析到 fake-ip 会回环失败。
	// app 侧清洗已注入完整列表，此处逐项兜底缺失域名（大小写不敏感、幂等）
	for _, domain := range directDomains {
		found := false
		for _, f := range cfg.DNS.FakeIPFilter {
			if strings.Contains(strings.ToLower(f), domain) {
				found = true
				break
			}
		}
		if !found {
			cfg.DNS.FakeIPFilter = append(cfg.DNS.FakeIPFilter, "+."+domain)
		}
	}

	return nil
}

// 自家后端域名直连规则兜底：app 侧清洗已注入列表，此处逐项补齐缺失规则，
// 防止订阅 rules 被绕过时业务 API 走代理（与 app 侧 ALLOWED_HOST_SUFFIXES 一致）。
func patchRules(cfg *config.RawConfig, _ string) error {
	var missing []string
	for _, domain := range directDomains {
		found := false
		for _, r := range cfg.Rule {
			if strings.HasPrefix(r, "DOMAIN-SUFFIX,"+domain+",DIRECT") {
				found = true
				break
			}
		}
		if !found {
			missing = append(missing, "DOMAIN-SUFFIX,"+domain+",DIRECT")
		}
	}
	if len(missing) > 0 {
		// 规则按序首匹配生效：插到头部，避免被订阅末尾的 MATCH 规则吞掉
		cfg.Rule = append(missing, cfg.Rule...)
	}
	return nil
}

func patchTun(cfg *config.RawConfig, _ string) error {
	cfg.Tun.Enable = false
	cfg.Tun.AutoRoute = false
	cfg.Tun.AutoDetectInterface = false
	return nil
}

func patchListeners(cfg *config.RawConfig, _ string) error {
	// 安全:订阅不得绑定任何入站监听(socks/http/mixed/ss/vmess/trojan/
	// hysteria2/tuic 等一律移除;patchGeneral 已清空,此处作纵深防御)
	cfg.Listeners = nil
	return nil
}

func patchProviders(cfg *config.RawConfig, profileDir string) error {
	forEachProviders(cfg, func(index int, total int, key string, provider map[string]any, prefix string) {
		path, _ := provider["path"].(string)
		if len(path) > 0 {
			path = common.ResolveAsRoot(path)
		} else if url, ok := provider["url"].(string); ok {
			path = prefix + "/" + utils.MakeHash([]byte(url)).String() // same as C.GetPathByHash
		} else {
			return // both path and url are empty, maybe inline provider
		}
		provider["path"] = profileDir + "/providers/" + path
	})

	return nil
}

func validConfig(cfg *config.RawConfig, _ string) error {
	if len(cfg.Proxy) == 0 && len(cfg.ProxyProvider) == 0 {
		return errors.New("profile does not contain `proxies` or `proxy-providers`")
	}

	if _, err := regexp2.Compile(cfg.ClashForAndroid.UiSubtitlePattern, 0); err != nil {
		return fmt.Errorf("compile ui-subtitle-pattern: %s", err.Error())
	}

	return nil
}

func process(cfg *config.RawConfig, profileDir string) error {
	for _, p := range processors {
		if err := p(cfg, profileDir); err != nil {
			return err
		}
	}

	return nil
}
