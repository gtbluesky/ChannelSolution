## 简介
Android APP 渠道包解决方案：支持V1、V2、V3签名体系

## 使用
1. 编译write项目， `./gradlew :writer:build`
2. 编译app项目，`./gradlew aR`
3. 写入渠道信息，`Java -jar writer.jar -i x.apk -o y.apk -c z`
 - -i: 待写入渠道信息的APK
 - -o: 已写入渠道信息的APK
 - -c: 渠道信息