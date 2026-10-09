// Polaris Windows 客户端 —— Tauri 后端。
// 架构：前端（src/，HTML/CSS/JS）只负责渲染与交互，所有能力经 tauri::command
// 暴露。后端分两层：
//   1) commands（本文件）：参数校验、状态管理、托盘/窗口等系统能力；
//   2) core（core.rs）：代理内核抽象。当前为 MockCore（内存模拟），
//      真实实现时替换为对接 mihomo/sing-box sidecar + wintun 的 WindowsCore，
//      commands 层不需要改。
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod core;

use core::{Core, MockCore};
use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use tauri::{tray::TrayIconBuilder, AppHandle, Manager, State};

struct AppState {
    core: Mutex<MockCore>,
    settings: Mutex<Settings>,
    authed: Mutex<bool>,
}

/* ---------------- 数据结构（与前端 api.js 一一对应） ---------------- */

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Status {
    connected: bool,
    node: String,
    latency: i64,
    up_speed: f64,
    down_speed: f64,
    up_total: String,
    down_total: String,
    uptime: String,
    mode: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Node { name: String, region: String, latency: i64, group: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct TrafficInfo {
    range: String, down_today: String, up_today: String,
    total_down: String, total_up: String, peak: String, online_nodes: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Plan { name: String, used: i64, total: i64, expire: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct PlanOffer { id: String, name: String, price: String, unit: String, feats: Vec<String>, hot: bool }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Order { name: String, no: String, date: String, amount: String, status: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Ticket { subject: String, no: String, date: String, status: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Invite { code: String, link: String, invited: i64, earned: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct GiftRecord { code: String, reward: String, date: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Notice { title: String, date: String, unread: bool, body: String }

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Settings {
    theme: String, lang: String, tun: String,
    expire_notify: bool, traffic_notify: bool, autostart: bool,
    version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct UpdateInfo { has_update: bool, version: String, size: String, notes: String }

/* 与前端 api.js 的 mock 形状一致的统一响应（Result 的 Err 只用于真正的异常） */
#[derive(Debug, Clone, Serialize)]
struct NodeResult { ok: bool, node: String }
#[derive(Debug, Clone, Serialize)]
struct RefreshResult { ok: bool, count: i64 }
#[derive(Debug, Clone, Serialize)]
struct OrderResult { ok: bool, order_no: String }
#[derive(Debug, Clone, Serialize)]
struct TicketResult { ok: bool, no: String }
#[derive(Debug, Clone, Serialize)]
struct RedeemResult { ok: bool, reward: String, msg: String }
#[derive(Debug, Clone, Serialize)]
struct ModeResult { ok: bool, mode: String }
#[derive(Debug, Clone, Serialize)]
struct LoginResult { ok: bool, email: String, msg: String }

/* ---------------- 命令 ---------------- */

#[tauri::command]
fn get_status(s: State<AppState>) -> Status { s.core.lock().unwrap().status() }

#[tauri::command]
fn connect(s: State<AppState>) -> Result<(), String> {
    s.core.lock().unwrap().connect().map_err(|e| e.to_string())
}

#[tauri::command]
fn disconnect(s: State<AppState>) -> Result<(), String> {
    s.core.lock().unwrap().disconnect().map_err(|e| e.to_string())
}

#[tauri::command]
fn get_nodes(s: State<AppState>) -> Vec<Node> { s.core.lock().unwrap().nodes() }

#[tauri::command]
fn select_node(s: State<AppState>, name: String) -> Result<NodeResult, String> {
    s.core.lock().unwrap().select_node(&name)
        .map(|n| NodeResult { ok: true, node: n })
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn speed_test(s: State<AppState>) -> Result<(), String> {
    s.core.lock().unwrap().speed_test().map_err(|e| e.to_string())
}

#[tauri::command]
fn refresh_subscription(s: State<AppState>) -> Result<RefreshResult, String> {
    // TODO(真实实现): 从面板拉取订阅并重载内核配置
    let _ = s;
    Ok(RefreshResult { ok: true, count: 32 })
}

#[tauri::command]
fn get_traffic(s: State<AppState>, range: String) -> TrafficInfo {
    let _ = s;
    TrafficInfo {
        range,
        down_today: "8.6 GB".into(), up_today: "1.2 GB".into(),
        total_down: "128.4 GB".into(), total_up: "36.2 GB".into(),
        peak: "24.8 MB/s".into(), online_nodes: 28,
    }
}

#[tauri::command]
fn get_plan(_s: State<AppState>) -> Plan {
    // TODO(真实实现): 调面板 /api/v1/user/info
    Plan { name: "旗舰套餐".into(), used: 86, total: 200, expire: "2026-11-05".into() }
}

#[tauri::command]
fn get_plans(_s: State<AppState>) -> Vec<PlanOffer> {
    // TODO(真实实现): 调面板 /api/v1/plan/fetch
    vec![
        PlanOffer { id: "m".into(), name: "月度套餐".into(), price: "19.9".into(), unit: "月".into(), feats: vec!["100GB 流量".into(), "2 台设备".into()], hot: false },
        PlanOffer { id: "y".into(), name: "年度套餐".into(), price: "169".into(), unit: "年".into(), feats: vec!["500GB 流量".into(), "5 台设备".into(), "优先线路".into()], hot: true },
        PlanOffer { id: "q".into(), name: "季度套餐".into(), price: "49".into(), unit: "季".into(), feats: vec!["200GB 流量".into(), "3 台设备".into()], hot: false },
    ]
}

#[tauri::command]
fn create_order(_s: State<AppState>, plan_id: String) -> Result<OrderResult, String> {
    // TODO(真实实现): 调面板下单接口，返回支付链接/二维码
    let _ = plan_id;
    Ok(OrderResult { ok: true, order_no: "No.202610091205".into() })
}

#[tauri::command]
fn get_orders(_s: State<AppState>) -> Vec<Order> {
    // TODO(真实实现): 调面板 /api/v1/order/fetch
    vec![
        Order { name: "旗舰年付套餐".into(), no: "No.202610091201".into(), date: "2026-10-09".into(), amount: "169".into(), status: "done".into() },
        Order { name: "旗舰年付套餐".into(), no: "No.202610071103".into(), date: "2026-10-07".into(), amount: "169".into(), status: "pending".into() },
        Order { name: "月度套餐".into(), no: "No.20260912088".into(), date: "2026-09-12".into(), amount: "19.9".into(), status: "refunded".into() },
    ]
}

#[tauri::command]
fn pay_order(_s: State<AppState>, order_no: String) -> Result<(), String> {
    // TODO(真实实现): 打开面板收银台（WebView2 内嵌或外部浏览器），轮询订单状态
    let _ = order_no;
    Ok(())
}

#[tauri::command]
fn get_tickets(_s: State<AppState>) -> Vec<Ticket> {
    // TODO(真实实现): 调面板 /api/v1/ticket/fetch
    vec![
        Ticket { subject: "节点连接超时".into(), no: "No.2026100801".into(), date: "2026-10-08".into(), status: "replied".into() },
        Ticket { subject: "支付结算异常".into(), no: "No.2026100703".into(), date: "2026-10-07".into(), status: "pending".into() },
        Ticket { subject: "账户登录失败".into(), no: "No.2026100509".into(), date: "2026-10-05".into(), status: "closed".into() },
    ]
}

#[tauri::command]
fn create_ticket(_s: State<AppState>, subject: String, content: String) -> Result<TicketResult, String> {
    // TODO(真实实现): 调面板 /api/v1/ticket/save
    let _ = (subject, content);
    Ok(TicketResult { ok: true, no: "No.202610091301".into() })
}

#[tauri::command]
fn get_invite(_s: State<AppState>) -> Invite {
    // TODO(真实实现): 调面板 /api/v1/user/invite
    Invite { code: "AB12CD".into(), link: "https://polaris.app/i/AB12CD".into(), invited: 3, earned: "30 GB".into() }
}

#[tauri::command]
fn get_gift_history(_s: State<AppState>) -> Vec<GiftRecord> { vec![] }

#[tauri::command]
fn redeem_gift(_s: State<AppState>, code: String) -> RedeemResult {
    // TODO(真实实现): 调面板礼品卡兑换接口
    let digits: String = code.chars().filter(|c| c.is_ascii_alphanumeric()).collect();
    if digits.len() == 16 {
        RedeemResult { ok: true, reward: "50 GB".into(), msg: "".into() }
    } else {
        RedeemResult { ok: false, reward: "".into(), msg: "卡密格式不正确".into() }
    }
}

#[tauri::command]
fn get_notices(_s: State<AppState>) -> Vec<Notice> {
    // TODO(真实实现): 调面板 /api/v1/notice/fetch
    vec![
        Notice { title: "v1.7.4 版本更新说明".into(), date: "2026-10-09".into(), unread: true, body: "1. 修复后台运行时长清零问题\n2. 速率显示采用非对称 EMA 平滑\n3. 优化节点测速逻辑".into() },
        Notice { title: "香港线路维护通知".into(), date: "2026-10-07".into(), unread: false, body: "香港 03 节点将于 10-10 02:00-04:00 维护，届时自动切换。".into() },
        Notice { title: "国庆活动：年付 8 折".into(), date: "2026-10-01".into(), unread: false, body: "活动期间年度套餐 8 折优惠，自动生效。".into() },
    ]
}

#[tauri::command]
fn set_proxy_mode(s: State<AppState>, mode: String) -> Result<ModeResult, String> {
    s.core.lock().unwrap().set_mode(&mode)
        .map(|m| ModeResult { ok: true, mode: m })
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn get_settings(s: State<AppState>) -> Settings { s.settings.lock().unwrap().clone() }

#[tauri::command]
fn set_setting(s: State<AppState>, key: String, value: serde_json::Value) -> Result<(), String> {
    let mut st = s.settings.lock().unwrap();
    match key.as_str() {
        "expire_notify" => st.expire_notify = value.as_bool().unwrap_or(true),
        "traffic_notify" => st.traffic_notify = value.as_bool().unwrap_or(true),
        "autostart" => {
            st.autostart = value.as_bool().unwrap_or(false);
            // TODO(真实实现): 写 HKCU\...\Run 注册表项
        }
        _ => return Err("未知设置项".into()),
    }
    Ok(())
}

#[tauri::command]
fn check_update(_s: State<AppState>) -> UpdateInfo {
    // TODO(真实实现): 请求版本接口，对比 version
    UpdateInfo {
        has_update: true, version: "1.7.5".into(), size: "34.6 MB".into(),
        notes: "1. 修复后台运行时长清零问题\n2. 速率显示更加平滑\n3. 优化节点测速逻辑".into(),
    }
}

#[tauri::command]
fn login(s: State<AppState>, email: String, password: String, panel: String) -> LoginResult {
    // TODO(真实实现): 调面板 /api/v1/passport/auth/login，保存 token（Windows Credential Manager）
    let _ = panel;
    if email.is_empty() || password.is_empty() {
        return LoginResult { ok: false, email: "".into(), msg: "请输入邮箱和密码".into() };
    }
    *s.authed.lock().unwrap() = true;
    LoginResult { ok: true, email: email.clone(), msg: "".into() }
}

#[tauri::command]
fn logout(s: State<AppState>) -> Result<(), String> {
    *s.authed.lock().unwrap() = false;
    // TODO(真实实现): 清除保存的 token
    Ok(())
}

/* ---------------- 托盘 ---------------- */

fn build_tray(app: &AppHandle) -> tauri::Result<()> {
    use tauri::menu::{MenuBuilder, MenuItemBuilder};
    let toggle = MenuItemBuilder::with_id("toggle", "断开连接").build(app)?;
    let nodes = MenuItemBuilder::with_id("nodes", "节点选择 ›").build(app)?;
    let show = MenuItemBuilder::with_id("show", "打开主界面").build(app)?;
    let settings = MenuItemBuilder::with_id("settings", "设置").build(app)?;
    let update = MenuItemBuilder::with_id("update", "检查更新").build(app)?;
    let quit = MenuItemBuilder::with_id("quit", "退出").build(app)?;
    let menu = MenuBuilder::new(app)
        .items(&[&toggle, &nodes, &show])
        .separator()
        .items(&[&settings, &update])
        .separator()
        .item(&quit)
        .build()?;
    TrayIconBuilder::new()
        .icon(app.default_window_icon().cloned().unwrap_or_else(|| {
            // 打包时若未生成图标则退化为空图标，不 panic
            tauri::image::Image::new_owned(vec![0; 4], 1, 1)
        }))
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "toggle" => { /* TODO: 切换连接 */ let _ = app; }
            "show" => { if let Some(w) = app.get_webview_window("main") { let _ = w.show(); let _ = w.set_focus(); } }
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;
    Ok(())
}

/* ---------------- 入口 ---------------- */

fn main() {
    tauri::Builder::default()
        .manage(AppState {
            core: Mutex::new(MockCore::new()),
            settings: Mutex::new(Settings {
                theme: "system".into(), lang: "zh-CN".into(), tun: "System".into(),
                expire_notify: true, traffic_notify: true, autostart: false,
                version: "1.7.4".into(),
            }),
            authed: Mutex::new(false),
        })
        .setup(|app| { build_tray(app.handle())?; Ok(()) })
        .invoke_handler(tauri::generate_handler![
            get_status, connect, disconnect, get_nodes, select_node, speed_test,
            refresh_subscription, get_traffic, get_plan, get_plans, create_order,
            get_orders, pay_order, get_tickets, create_ticket, get_invite,
            get_gift_history, redeem_gift, get_notices, set_proxy_mode,
            get_settings, set_setting, check_update, login, logout,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Polaris 失败");
}
