# 本次交付验证

- `bash scripts/test.sh`：通过 30 项独立 Java 解析和筛选检查。
- `bash scripts/build.sh`：Java 编译、Android 资源编译和 DEX 编译成功，生成已签名 APK。
- `apksigner verify --verbose`：签名验证通过，APK Signature Scheme v2 / v3 有效。
- `zipalign -c -p 4`：APK 对齐检查通过。
- `unzip -tq`：安装包完整性检查通过。
- `aapt2 dump badging`：包名 `local.ridewatch`，版本 `0.1.0`，最低 SDK 26，目标 SDK 35，具有可启动 Activity 和无障碍服务；只声明通知权限。
- `adb devices`：未发现连接的手机或模拟器。因此本次未运行 Android 设备演示、真实滴滴 App / 微信小程序兼容测试、实际手势检查或通知投递检查。

APK SHA-256：`e3ee8c04e228be2e027aa77f5dcfb486a184d6fcfd951318c5fca24ce2baa57f`。

安装与手机自测步骤见 README；演示功能本次仅完成代码构建，需在手机上运行验证。
