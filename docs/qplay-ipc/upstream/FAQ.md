# 常见问题

## 授权相关

- **授权返回结果，ret返回-1**

代表授权失败，有以下几种可能性：

1. `encryptString`结果有误。请注意`encryptString`是用「App私钥」进行签名，「Q音公钥」进行加密。这里分别有两套公私钥，一定不要混用。参照`OpenIDHelper.getEncryptString`里面的逻辑。
2. 调用授权接口`CommandCmd.verifyCallerIdentity()`参数有误。请仔细检查`appid`, `packageName`以及`encryptString`是否写错。
3.  授权时网络连接错误。
4. 其他情况请与Q音开发人员及时沟通联系。

- **授权之后，没有返回callbackUrl对应页面**

请检查`callbackUrl`是否有误，以及代码中是否声明了对应`Activity`的`scheme`.

- **如何判断授权成功和失败**

授权完成时，`intent`中的`data`会返回`xxx://?cmd=verify&ret=x`，其中`ret`返回`0`代表授权成功，`-1`代表失败。


## bindService失败的问题
1. 检查bindService绑定的服务名是否有误。
2. 目前很多高版本的Android手机会禁止后台启动服务，如果绑定失败，请留意bindService的返回值或者系统Log，一般来说遇到系统提示不能后台启动服务时，可以在绑定服务前先调用startQQMusicProcess启动QQ音乐进程，这样的话大部分情况下都可以顺利绑定的。如果不行，则用Demo试一下，如果Demo也不行，请联系QQ音乐。
3. 若手机是华为/魅族系统，打开应用管理选项，然后打开QQ音乐，将「后台管理」这个选项选为「允许后台运行」。

## 错误码相关

* **code: 3**

1. 传入了非法的操作名， 检查`execute` 或 `executeAsync` 中的方法名参数是否传递正确。
2. 当前平台的Q音版本不支持该操作，目前\`QPlay IPC (Android)\`支持TV/平板/车机/手机四个平台，但不同平台支持的接口并不一致。请仔细查看接口说明中的对应的平台版本。如显示car对应的值是 NULL\`，则代表车机平台不支持该接口。
3. 同步/异步接口混用。参数中有`callback`的接口为异步(`executeAsync`)接口，没有则为同步(`execute`)接口。
* **code：5**

1. 应用无权限， 首先确认是否已向QQ音乐申请`appid` ,并检查`appid` 是否正确。
2. 第一次授权或授权已过期， 请参考`demo`中的`commonOpen()`的处理方法，调用`Command.verifyCallerIdentity()`进行授权。
* **code:  6**

1. 传入的midList为空， 检查传入的midList列表是否为空。
2. 检查网络是否连接正常。
* **code:  7**

1. 执行的操作需要先登录QQ音乐， 请参考`demo`中的`commonOpen()`的处理方法，调用`Command.loginQQMusic()`进行登录。
* **code:  10**

1. 传入的mdList为空， 检查传入的idList列表是否为空。
2. 检查网络是否连接正常。
* **code:  11**

1. 播放服务未就绪， 出现此错误可以稍后重试。
* **code:  202**

1. 内部获取数据出错， 检查网络情况是否正常。
* **code:  204**

1. 未授权， 调用`Command.verifyCallerIdentity()`进行授权。
* **调用语音服务接口， 返回code:  300**

1. 参考语音接入文档。

## 登录相关

- **如何判断登录成功和失败**

登录完成时，`intent`中的`data`会返回`xxx://qmlogin=x`，其中`qmlogin`返回`1`代表登录成功，`0`代表登录失败。

## 其他

- **公钥和私钥**

`SDK`中有两套公私钥，请务必要分清楚，不要混用：

1. Q音公钥：申请AppId成功后QQ音乐会把这个公钥给合作方，这个每个合作方都是共用的，在Demo中也有。
2. Q音私钥：Q音解密合作方的加密串
3. 合作方公钥：合作方自己生成的RSA公钥，申请AppId时需要发送给QQ音乐
4. 合作方私钥：合作方自己生成的RSA私钥。**请妥善保管**
注：公私钥的生成方法在Demo的README中有详细介绍。