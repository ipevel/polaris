//! 代理内核抽象。
//!
//! [`Core`] trait 定义了 UI 需要的全部内核能力。当前实现 [`MockCore`]
//! 仅做内存模拟，用于 UI 联调。
//!
//! # 真实实现路线（Windows）
//! 新建 `WindowsCore: Core`，二选一：
//! - A. sidecar 方案：打包 mihomo/sing-box Windows 二进制为 Tauri sidecar，
//!   通过 HTTP API（/configs、/proxies）与本地 mixed-port 驱动；TUN 经
//!   内核自身的 tun 支持（mihomo 的 `tun.enable: true`，需管理员权限）。
//! - B. 原生方案：Rust 直接集成 sing-box 库 + wintun.dll，自建 TUN。
//! 两种方案都只需实现本 trait，`main.rs` 的 commands 层零改动。

use std::fmt;

/// 内核错误
#[derive(Debug)]
pub struct CoreError(pub String);
impl fmt::Display for CoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result { write!(f, "{}", self.0) }
}

/// UI 需要的内核能力
pub trait Core {
    fn status(&self) -> super::Status;
    fn connect(&mut self) -> Result<(), CoreError>;
    fn disconnect(&mut self) -> Result<(), CoreError>;
    fn nodes(&self) -> Vec<super::Node>;
    fn select_node(&mut self, name: &str) -> Result<String, CoreError>;
    fn speed_test(&mut self) -> Result<(), CoreError>;
    fn set_mode(&mut self, mode: &str) -> Result<String, CoreError>;
}

/// 内存模拟内核（UI 联调 / 浏览器预览用）
pub struct MockCore {
    connected: bool,
    node: String,
    latency: i64,
    mode: String,
    nodes: Vec<super::Node>,
}

impl MockCore {
    pub fn new() -> Self {
        Self {
            connected: true,
            node: "香港 01".into(),
            latency: 45,
            mode: "规则模式".into(),
            nodes: vec![
                super::Node { name: "香港 01".into(), region: "香港".into(), latency: 45, group: "节点选择".into() },
                super::Node { name: "香港 02".into(), region: "香港".into(), latency: 62, group: "节点选择".into() },
                super::Node { name: "新加坡 01".into(), region: "新加坡".into(), latency: 188, group: "节点选择".into() },
                super::Node { name: "日本 01".into(), region: "日本".into(), latency: 92, group: "节点选择".into() },
                super::Node { name: "美国 01".into(), region: "美国".into(), latency: -1, group: "节点选择".into() },
            ],
        }
    }
}

impl Core for MockCore {
    fn status(&self) -> super::Status {
        super::Status {
            connected: self.connected,
            node: self.node.clone(),
            latency: self.latency,
            up_speed: 2.1, down_speed: 12.4,
            up_total: "1.2 GB".into(), down_total: "8.6 GB".into(),
            uptime: "02:34:18".into(),
            mode: self.mode.clone(),
        }
    }
    fn connect(&mut self) -> Result<(), CoreError> {
        // 真实实现：启动 sidecar / 建立 TUN，失败返回 CoreError
        self.connected = true;
        Ok(())
    }
    fn disconnect(&mut self) -> Result<(), CoreError> {
        self.connected = false;
        Ok(())
    }
    fn nodes(&self) -> Vec<super::Node> { self.nodes.clone() }
    fn select_node(&mut self, name: &str) -> Result<String, CoreError> {
        match self.nodes.iter().find(|n| n.name == name) {
            Some(n) => {
                self.node = n.name.clone();
                self.latency = n.latency;
                self.connected = true;
                Ok(n.name.clone())
            }
            None => Err(CoreError("节点不存在".into())),
        }
    }
    fn speed_test(&mut self) -> Result<(), CoreError> {
        // 真实实现：对全部节点并发 url-test，更新延迟
        for n in self.nodes.iter_mut() {
            if n.latency < 0 { n.latency = 120; }
        }
        Ok(())
    }
    fn set_mode(&mut self, mode: &str) -> Result<String, CoreError> {
        // 真实实现：PATCH /configs { "mode": "rule|global|direct" }
        self.mode = mode.into();
        Ok(mode.into())
    }
}
