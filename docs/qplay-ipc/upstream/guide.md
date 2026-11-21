# IPC文档-接入指南
## 1、注册信息并申请Appid
SDK使用前，需要在本站点提交相关信息以申请AppId。

## 2、Demo工程
安装Demo工程，以下接入步骤均可参考实现，且所示的代码片段均出自Demo工程中。

**demo工程地址：** [QPlay IPC SDK demo](https://github.com/tencentmusic/QQMusic_Innovation_QPlay_AIDL_OpenID_Demo)

## 3、添加仓库及依赖
* **版本区别**

1.1.0前：需要下载Demo工程，在Demo工程主目录的libs目录下下载aar文件,然后在您项目的build.gradle中添加aar依赖。

```groovy
dependencies {
    api(name: 'qqmusic-innovation-aidl-api-sdk', ext: 'aar')
}
```
1.1.0及以后：在您项目的build.gradle中添加我们的maven仓库以及sdk依赖。

```groovy
allprojects {
    repositories {
        // SDK仓库
        maven {
            url 'http://ct.y.qq.com/TVapp/nexus/repository/maven-public/'
             credentials {
                username 'tvsdk_client' //直接复制即可，账号密码就是'tvsdk_client'
                password 'tvsdk-client'
            }
        }
    }
}
```
```groovy
dependencies {
    implementation "com.tencent.qqmusic.innovation:aidl-api-sdk:${version}"
}
```

## 4、添加配置信息
使用前SDK要求您必须配置以下两个参数，用于与QQ音乐客户端交互时，识别您的身份信息。

* appid: 在前序步骤中注册申请并得到QQ音乐审批后获取。
* platForm: 您需要交互的QQ音乐平台类型，可选的平台类型如下：

```java
    public static final String AIDL_PLATFORM_TYPE_PHONE = "phone";
    public static final String AIDL_PLATFORM_TYPE_TV = "tv";
    public static final String AIDL_PLATFORM_TYPE_TV_FOR_THIRD = "tvForThird";
    public static final String AIDL_PLATFORM_TYPE_CAR = "car";
    public static final String AIDL_PLATFORM_TYPE_PAD = "pad";
```
如与手机端进行交互则选择参数 `AIDL_PLATFORM_TYPE_PHONE`  。
参考Demo工程配置在全局配置类中即可， 以便于在后续调用sdk中各接口时使用。

## 5、整体流程
SDK的整体流程图如下，各流程的实现方式可参考图中各流程的相关代码在Demo工程的实现。

![](img/process.png)

以下将详细描述各步骤实现方式。

## 6、绑定QQ音乐Service以及初始化
要调用QQ音乐功能或获取相关信息， 必须先绑定QQ音乐服务。不再需要时，取消绑定。

不同的QQ音乐平台的 `QQMusicApiService` 的类路径不同，需注意区分。

* **绑定QQ音乐的三方调用服务**：

```kotlin
    private fun bindQQMusicApiService(platform: String): Boolean {
        val action = when(platform) {
            AIDL_PLATFORM_TYPE_PHONE -> "com.tencent.qqmusic.third.api.QQMusicApiService"
            
            AIDL_PLATFORM_TYPE_CAR -> "com.tencent.qqmusiccar.third.api.QQMusicApiService"
            AIDL_PLATFORM_TYPE_TV -> "com.tencent.qqmusictv.third.api.QQMusicApiService"
            else -> null // error platform 
        }
        if(action != null) {
            var intent = Intent(action)
            intent.`package` = "com.tencent.qqmusic"
            return bindService(intent, this, Context.BIND_AUTO_CREATE)
        }
        return false
    }
```
* **取消绑定：**

```kotlin
context.unbindService(serviceConnection)
```
* **绑定成功**：
  绑定成功的回调中获取`qqmusicApi` 对象，通过该对象即可调用SDK提供的功能接口。

```kotlin
    override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
        qqmusicApi = IQQMusicApi.Stub.asInterface(p1)
        //...
        CommonCmd.init(BIND_PLATFORM)
        sayHiAndInitData()
    }
```
* **绑定失败：**[为什么bindService失败了？](https://developer.y.qq.com/docs/qplayaidl#/FAQ?id=bindservice%e5%a4%b1%e8%b4%a5%e7%9a%84%e9%97%ae%e9%a2%98)
* **后续初始化**
  * 调用`CommonCmd.init(BIND_PLATFORM)`初始化当前绑定的QQ音乐平台
  * （可选）调用`hi` 接口。
    * 作用：仅用来判断和QQ音乐的连接是否正常。
    * 不调用会有什么影响：在首次连接QQ音乐时，可以提前发现如未同意隐私协议/未授权等全局性问题。避免后续调用业务接口时再处理全局性问题。
    * 如何调用：参考下一步[Api服务调用方式](https://developer.y.qq.com/docs/qplayaidl#/guide?id=_7%e3%80%81api%e6%9c%8d%e5%8a%a1%e8%b0%83%e7%94%a8%e6%96%b9%e5%bc%8f)中的说明。
  * 调用`registerEventListener`注册你想要监听的信息，可注册的事件回调参考：[事件定义](https://developer.y.qq.com/docs/qplayaidl#/error_code_and_return?id=%e4%ba%8b%e4%bb%b6%e5%ae%9a%e4%b9%89-events)。

## 7、Api服务调用方式
在上述步骤中，服务绑定成功后获取到qqmusicApi对象， 该对象包含`execute`和`executeAsync`两个方法。区别为前者为同步接口，后者为异步。通过这两个方法即可调用QQ音乐的相关功能。

```Plain Text
Bundle execute(String action, Bundle params);
void executeAsync(String action, Bundle params, IQQMusicApiCallback callback);
```
* **参数解释**
    * **action**: [接口列表](https://developer.y.qq.com/docs/qplayaidl#/interface) 中任一接口的方法名。
    * **params**: 接口的参数列表，`key` 为接口的参数名， `value` 为该参数要传入的值。
    * **callback**: 异步返回结果， 包含错误信息和数据。
      每个接口方法需要使用同步接口还是异步接口取决于该方法的函数声明，接口方法中包含参数`IQQMusicApiCallback` 则必须使用异步接口`executeAsync` 。

* **示例：**
  调用同步方法 `playMusic` :

```kotlin
    fun playMusic(api: IQQMusicApi?): Int {
        val ret = api?.execute("playMusic", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }
```
调用异步方法 `playSonglistAtIndex` ：

```kotlin
    //通过SongId播放歌曲列表
    private fun playSonglistAtIndex(idList:ArrayList<String>, index: Int, callback: IQQMusicApiCallback) {
        val params = Bundle()
        params.putStringArrayList("songIdList", idList)
        params.putInt("index", index)
        printToTextView("播放歌曲列表... $index,${songList.size}")
        qqmusicApi?.executeAsync("playSongIdAtIndex", params, callback)
    }
```
## 8、返回值处理
具体接口的返回值定义查看[接口文档](https://developer.y.qq.com/docs/qplayaidl#/interface)。

异步接口`executeAsync`在回调callback中返回Bundle，bundle中的字段定义参考类`Keys` ,主要包含以下几个参数：

|定义|类型|解释|
| ----- | ----- | ----- |
|API\_RETURN\_KEY\_CODE|Int|返回的错误码|
|API\_RETURN\_KEY\_ERROR|String？|如果有错误，这里将包含错误的描述。|
|API\_RETURN\_KEY\_DATA|T|接口的返回值，不同接口的返回类型也不同，具体参考接口描述。|

* **通用错误码处理**
  所有接口都有可能返回某些特定错误码， 如未同意隐私协议/未登录/未授权。可以实现方法在接收到通用错误码时做相应处理。在Demo工程中可参考`commonOpen` 方法中的处理。

* **错误码分析**
  全部错误码类型参考：[错误码说明](https://developer.y.qq.com/docs/qplayaidl#/error_code_and_return?id=qq%e9%9f%b3%e4%b9%90api%e9%94%99%e8%af%af%e7%a0%81-errorcodes)
  常见错误码场景说明：[常见错误码场景说明](https://developer.y.qq.com/docs/qplayaidl#/FAQ?id=%e9%94%99%e8%af%af%e7%a0%81%e7%9b%b8%e5%85%b3)

## 9、授权相关
SDK内提供两个授权方法，根据以下说明进行调用。

* `CommonCmd.verifyCallerIdentity` :
    * **说明**：该方法的作用是用于合作方身份识别，取得该授权是调用SDK提供的接口列表中任意功能的前提。
    * **调用时机**：服务绑定成功后可立即调用， 或在调用`hi` 或其他接口返回错误码`5` 时调用。
* `Method.requestAuth` :
    * **说明**：获取QQ登录用户的个人资产时，需要调用该方法鉴权。
    * **调用时机**：需要获取QQ登录用户的个人资产时。
    * **接口返回**：该接口会返回`openId` 和`openToken` 在调用个人资产接口时需要传入。

## 10、EncryptString字段说明
部分接口需要传入`encryptString` ,参考Demo工程中的`OpenIDHelper` 生成该字段。




