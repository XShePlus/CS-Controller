package io.github.xsheeee.cs_controller.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.xsheeee.cs_controller.tools.Logger

open class BaseActivity : AppCompatActivity(){
    open fun log(message: String, level: String){
        val TAG = javaClass.simpleName
        Logger.writeLog(TAG,level,message)
    }
}