package com.example.bilidownloader.data.repository

import com.example.bilidownloader.data.database.UserDao
import com.example.bilidownloader.data.database.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户账号仓库
 * 负责管理用户数据的增删改查
 */
class UserRepository(private val userDao: UserDao) {

    // 获取所有用户流
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    // 获取当前用户
    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()

    // 插入或更新用户
    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)

    // 删除用户
    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)

    // 清除所有登录状态
    suspend fun clearAllLoginStatus() = userDao.clearAllLoginStatus()

    // 设置指定用户为登录状态
    suspend fun setLoginStatus(mid: Long) = userDao.setLoginStatus(mid)
}