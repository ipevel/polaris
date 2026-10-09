北辰 Polaris · Windows 便携版
=============================

解压即用，不需要安装任何东西，也不需要管理员权限（TUN 模式除外）。

怎么用
------
1. 双击 Polaris.exe
2. 填面板地址 + 账号密码，登录
3. 回首页点中间那个圆形电源键
4. 看到「已连接」就可以上网了

关窗口 = 收进右下角托盘，程序还在跑。
真正退出请右键托盘图标 → 退出。退出时会自动还原系统代理设置。

目录说明
--------
  Polaris.exe      主程序
  core/            代理内核（mihomo）+ TUN 驱动（wintun.dll）
  data/            你的所有数据，删掉它就等于恢复出厂设置
    config.yaml      由订阅自动生成的内核配置
    profiles/        订阅原文
    settings.json    设置
    credentials.dat  登录凭据（用 Windows DPAPI 加密，换机器就失效，需重登）
    logs/            运行日志，排查问题用
  resources/       程序资源，不用动

可以直接把整个文件夹拷到 U 盘带走，数据都在里面。

几个说明
--------
* 默认用「系统代理」模式，改的是当前用户的代理设置，不需要管理员权限
* TUN 模式（全局接管，能代理不走系统代理的程序）需要管理员权限，
  在设置里开启时会弹 UAC
* 杀毒软件可能对 mihomo.exe 报警 —— 这是代理内核的常见误报。
  内核来源：https://github.com/MetaCubeX/mihomo（GPL-3.0）
* 数据目录是 exe 同级的 data/。如果放在只读位置（比如只读 U 盘），
  会自动改用 %LOCALAPPDATA%\Polaris

出问题了
--------
先看 data/logs/polaris.log。设置页里也有「导出日志」。