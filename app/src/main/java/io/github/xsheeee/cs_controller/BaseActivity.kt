package io.github.xsheeee.cs_controller

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.xsheeee.cs_controller.Tools.Logger

open class BaseActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    fun log(message: String,level: String){
        val TAG = javaClass.simpleName
        Logger.writeLog(TAG,level,message)
    }
}