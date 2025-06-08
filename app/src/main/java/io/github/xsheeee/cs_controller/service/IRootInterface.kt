package io.github.xsheeee.cs_controller.service

import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import io.github.xsheeee.cs_controller.tools.Logger

/**
 * IRootInterface接口的Stub和Proxy实现
 * 这是对AIDL生成代码的手动实现版本
 */
interface IRootInterface : IInterface {
    /**
     * 以Root权限读取文件内容
     */
    @Throws(RemoteException::class)
    fun readFileAsRoot(filePath: String): String
    
    /**
     * 以Root权限写入文件
     */
    @Throws(RemoteException::class)
    fun writeFileAsRoot(filePath: String, content: String): Boolean
    
    /**
     * 以Root权限追加内容到文件
     */
    @Throws(RemoteException::class)
    fun appendFileAsRoot(filePath: String, content: String): Boolean
    
    /**
     * 以Root权限检查文件是否存在
     */
    @Throws(RemoteException::class)
    fun fileExistsAsRoot(filePath: String): Boolean
    
    /**
     * 以Root权限创建目录
     */
    @Throws(RemoteException::class)
    fun mkdirsAsRoot(dirPath: String): Boolean
    
    /**
     * Binder接口描述符
     */
    companion object {
        const val DESCRIPTOR = "io.github.xsheeee.cs_controller.service.IRootInterface"
        
        // 方法ID
        const val TRANSACTION_readFileAsRoot = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_writeFileAsRoot = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_appendFileAsRoot = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_fileExistsAsRoot = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_mkdirsAsRoot = IBinder.FIRST_CALL_TRANSACTION + 4
        
        /**
         * 将IBinder转换为IRootInterface接口
         */
        @JvmStatic
        fun asInterface(obj: IBinder): IRootInterface {
            return if (obj is Stub) {
                obj
            } else {
                Proxy(obj)
            }
        }
    }
    
    /**
     * IRootInterface的Stub实现（服务端）
     */
    abstract class Stub : android.os.Binder(), IRootInterface {
        init {
            this.attachInterface(this, DESCRIPTOR)
        }
        
        override fun asBinder(): IBinder {
            return this
        }
        
        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_readFileAsRoot -> {
                    data.enforceInterface(DESCRIPTOR)
                    val filePath = data.readString() ?: ""
                    val result = this.readFileAsRoot(filePath)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                TRANSACTION_writeFileAsRoot -> {
                    data.enforceInterface(DESCRIPTOR)
                    val filePath = data.readString() ?: ""
                    val content = data.readString() ?: ""
                    val result = this.writeFileAsRoot(filePath, content)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                TRANSACTION_appendFileAsRoot -> {
                    data.enforceInterface(DESCRIPTOR)
                    val filePath = data.readString() ?: ""
                    val content = data.readString() ?: ""
                    val result = this.appendFileAsRoot(filePath, content)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                TRANSACTION_fileExistsAsRoot -> {
                    data.enforceInterface(DESCRIPTOR)
                    val filePath = data.readString() ?: ""
                    val result = this.fileExistsAsRoot(filePath)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
                TRANSACTION_mkdirsAsRoot -> {
                    data.enforceInterface(DESCRIPTOR)
                    val dirPath = data.readString() ?: ""
                    val result = this.mkdirsAsRoot(dirPath)
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }
    
    /**
     * IRootInterface的Proxy实现（客户端）
     */
    class Proxy(private val mRemote: IBinder) : IRootInterface {
        override fun asBinder(): IBinder {
            return mRemote
        }
        
        val interfaceDescriptor: String
            get() = DESCRIPTOR
        
        @Throws(RemoteException::class)
        override fun readFileAsRoot(filePath: String): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var result = ""
            
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(filePath)
                mRemote.transact(TRANSACTION_readFileAsRoot, data, reply, 0)
                reply.readException()
                result = reply.readString() ?: ""
            } catch (e: Exception) {
                Logger.e("IRootInterface", "读取文件远程调用失败: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
            
            return result
        }
        
        @Throws(RemoteException::class)
        override fun writeFileAsRoot(filePath: String, content: String): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var result = false
            
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(filePath)
                data.writeString(content)
                mRemote.transact(TRANSACTION_writeFileAsRoot, data, reply, 0)
                reply.readException()
                result = reply.readInt() != 0
            } catch (e: Exception) {
                Logger.e("IRootInterface", "写入文件远程调用失败: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
            
            return result
        }
        
        @Throws(RemoteException::class)
        override fun appendFileAsRoot(filePath: String, content: String): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var result = false
            
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(filePath)
                data.writeString(content)
                mRemote.transact(TRANSACTION_appendFileAsRoot, data, reply, 0)
                reply.readException()
                result = reply.readInt() != 0
            } catch (e: Exception) {
                Logger.e("IRootInterface", "追加文件远程调用失败: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
            
            return result
        }
        
        @Throws(RemoteException::class)
        override fun fileExistsAsRoot(filePath: String): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var result = false
            
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(filePath)
                mRemote.transact(TRANSACTION_fileExistsAsRoot, data, reply, 0)
                reply.readException()
                result = reply.readInt() != 0
            } catch (e: Exception) {
                Logger.e("IRootInterface", "检查文件远程调用失败: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
            
            return result
        }
        
        @Throws(RemoteException::class)
        override fun mkdirsAsRoot(dirPath: String): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            var result = false
            
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(dirPath)
                mRemote.transact(TRANSACTION_mkdirsAsRoot, data, reply, 0)
                reply.readException()
                result = reply.readInt() != 0
            } catch (e: Exception) {
                Logger.e("IRootInterface", "创建目录远程调用失败: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
            
            return result
        }
    }
} 