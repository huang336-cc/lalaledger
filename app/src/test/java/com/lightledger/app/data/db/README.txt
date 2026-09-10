这里原本计划放 Room 官方 MigrationTestHelper 回归测试，
但 Gradle 9.3 + AGP 8.7.3 跑 testDebugUnitTest 时会命中 Gradle 自身的
"apkForHostTest / apk-for-local-test.ap_" 状态跟踪兼容问题，无法执行。

替代方案（已落地、可直接跑）：
    python3 tools/check_migrations.py

它做的事比 Robolectric 测试更直接：
1. 解析 Migrations.kt，检查所有 MIGRATION_x_y 是否都注册进 Migrations.ALL（漏注册 = 启动闪退）
2. 检查迁移链是否连续、终点是否等于 AppDatabase.version
3. 用真实 sqlite 按各历史 schema 建库 → 逐条执行迁移 DDL → 与最新 schema 比对
   表结构与索引，并验证旧数据不丢

MigrationTest.kt 保留作为参考，等升级到 Gradle 10 或换用 androidTest
（需真机/模拟器）后可启用。
