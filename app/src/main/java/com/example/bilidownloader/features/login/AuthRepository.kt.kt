package com.example.bilidownloader.features.login

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.database.UserDao
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.network.api.BiliApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 认证与用户仓库.
 *
 * 垂直切片架构：负责所有与 "身份 (Auth)" 相关的逻辑。
 * 包括：本地账号管理 (UserDao) + 远程登录流程 (BiliApiService)。
 */
class AuthRepository(
    private val context: Context,
    private val userDao: UserDao
) {
    private val apiService: BiliApiService = NetworkModule.biliService

    // region Local User Management (本地账号管理)

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()

    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)

    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)

    suspend fun clearAllLoginStatus() = userDao.clearAllLoginStatus()

    suspend fun setLoginStatus(mid: Long) = userDao.setLoginStatus(mid)

    // endregion

    // region Remote Login Logic (远程登录逻辑)

    /**
     * Step 1: 获取人机验证参数 (Geetest)
     */
    suspend fun fetchCaptcha(): Resource<CaptchaResult> = withContext(Dispatchers.IO) {
        try {
            // 预热 Cookie (获取 buvid)
            NetworkModule.biliService.getNavInfo().execute()

            val response = apiService.getCaptcha().execute()
            val body = response.body()
            if (body?.code == 0 && body.data != null) {
                Resource.Success(body.data)
            } else {
                Resource.Error(body?.message ?: "获取验证码失败")
            }
        } catch (e: Exception) {
            Resource.Error("网络错误: ${e.message}")
        }
    }

    /**
     * Step 2: 提交验证结果并发送短信
     */
    suspend fun sendSmsCode(
        tel: Long,
        token: String,
        challenge: String,
        validate: String,
        seccode: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendSmsCode(
                cid = 86,
                tel = tel,
                token = token,
                challenge = challenge,
                validate = validate,
                seccode = seccode
            ).execute()

            val body = response.body()
            if (body?.code == 0 && body.data != null) {
                Resource.Success(body.data.captcha_key)
            } else {
                Resource.Error(body?.message ?: "短信发送失败")
            }
        } catch (e: Exception) {
            Resource.Error("发送异常: ${e.message}")
        }
    }

    /**
     * Step 3: 使用短信验证码登录
     */
    suspend fun loginBySms(
        tel: Long,
        code: Int,
        captchaKey: String
    ): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.loginBySms(
                cid = 86,
                tel = tel,
                code = code,
                captchaKey = captchaKey
            ).execute()

            val body = response.body()
            if (body?.code == 0) {
                // 捕获 Set-Cookie
                val cookies = response.headers().values("Set-Cookie")
                if (cookies.isNotEmpty()) {
                    CookieManager.saveCookies(context, cookies)
                    Resource.Success(true)
                } else {
                    Resource.Error("登录成功但未下发 Cookie")
                }
            } else {
                Resource.Error(body?.message ?: "登录失败")
            }
        } catch (e: Exception) {
            Resource.Error("登录异常: ${e.message}")
        }
    }

    // endregion
}