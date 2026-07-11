## 本地BUILD备忘

### 1.JDK选21

`Setting->Build,Execution,Deloyment->Build Tools->Gradle->Gradle Projects->Gradel JDK`


### 2.需要生成./keystore.jks

`keytool -genkeypair \
-keystore keystore.jks \
-alias YOUR-KEYALIAS \
-keyalg RSA \
-keysize 2048 \
-storepass YOUR-PASSWORD \
-keypass YOUR-PASSWORD \
-dname "CN=签发人通用名称,OU=组织部门(可选),O=公司/机构全称,L=市,ST=省,C=CN" \
-validity 天数`

### 3.新建./key.properties

`storeFile=keystore.jks
storePassword=YOUR-PASSWORD
keyAlias=YOUR-KEYALIAS
keyPassword=YOUR-PASSWORD`

### 4.命令行build 指定架构包

windows(powershell)

`.\gradlew.bat :tv:assembleRelease "-PtargetAbis=arm64-v8a,x86"`

macOS 需要确保./gradlew可执行

`chmod +x ./gradlew`

`./gradlew :tv:assembleRelease "-PtargetAbis=arm64-v8a,x86"`

