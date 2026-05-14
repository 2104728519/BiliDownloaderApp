package com.example.bilidownloader.features.home

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.features.login.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 处理用户登录、账号切换及 Cookie 管理的 ViewModel.
 */
class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    val userList = authRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeUser = authRepository.getCurrentUser()
            if (activeUser != null) {
                CookieManager.saveCookies(getApplication(), listOf(activeUser.sessData))
                _currentUser.value = activeUser
            } else {
                CookieManager.clearCookies(getApplication())
                _currentUser.value = null
            }
        }
    }

    fun syncCookieToUserDB() {
        viewModelScope.launch(Dispatchers.IO) {
            val localCookie = CookieManager.getCookie(getApplication())
            if (!localCookie.isNullOrEmpty()) addOrUpdateAccount(localCookie, isSilent = true)
        }
    }

    fun addOrUpdateAccount(cookieInput: String, isSilent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawCookie =
                    if (cookieInput.contains("=")) cookieInput else "SESSDATA=$cookieInput"
                val cookieMap = CookieManager.parseCookieStringToMap(rawCookie)
                val inputSess = cookieMap["SESSDATA"]
                if (inputSess.isNullOrEmpty()) {
                    if (!isSilent) showToast("无效的 Cookie (缺少 SESSDATA)")
                    return@launch
                }
                CookieManager.saveCookies(getApplication(), listOf(rawCookie))
                val response = NetworkModule.biliService.getSelfInfo().execute()
                val userData = response.body()?.data
                if (userData != null && userData.isLogin) {
                    val inputCsrf = cookieMap["bili_jct"] ?: ""
                    val finalCsrf =
                        if (inputCsrf.isNotEmpty()) inputCsrf else CookieManager.getCookieValue(
                            getApplication(),
                            "bili_jct"
                        ) ?: ""
                    val newUser = UserEntity(
                        mid = userData.mid,
                        name = userData.uname,
                        face = userData.face,
                        sessData = rawCookie,
                        biliJct = finalCsrf,
                        isLogin = true
                    )
                    authRepository.clearAllLoginStatus()
                    authRepository.insertUser(newUser)
                    _currentUser.value = newUser
                    if (!isSilent) showToast("登录成功")
                } else {
                    if (!isSilent) showToast("验证失败：Cookie 可能已过期")
                    restoreSession()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (!isSilent) showToast("登录异常: ${e.message}")
                restoreSession()
            }
        }
    }

    fun switchAccount(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            authRepository.setLoginStatus(user.mid)
            CookieManager.saveCookies(getApplication(), listOf(user.sessData))
            _currentUser.value = user
        }
    }

    fun quitToGuestMode() {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.clearAllLoginStatus()
            CookieManager.clearCookies(getApplication())
            _currentUser.value = null
        }
    }

    fun logoutAndRemove(user: UserEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.deleteUser(user)
            if (currentUser.value?.mid == user.mid) quitToGuestMode()
        }
    }

    private suspend fun showToast(msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}
