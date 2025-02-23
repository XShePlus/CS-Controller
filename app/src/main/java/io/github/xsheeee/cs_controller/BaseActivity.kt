package io.github.xsheeee.cs_controller

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.xsheeee.cs_controller.Tools.Logger
import java.util.logging.Logger

open class BaseActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    fun logError(message: String){
        val TAG = javaClass.simpleName
        Logger.writeLog(TAG,"ERROR",message)
    }
}