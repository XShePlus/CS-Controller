package io.github.xsheeee.cs_controller.tools

import java.util.Arrays

object Values {
    var isFirst: Boolean = false
    const val CSConfigPath: String = "/storage/emulated/0/Android/MW_CpuSpeedController/config.txt"
    const val CSCPath: String = "/sdcard/Android/CSController/"
    const val bPath: String = "/sdcard/Android/CSController/balance.txt"
    const val poPath: String = "/sdcard/Android/CSController/powersave.txt"
    const val pePath: String = "/sdcard/Android/CSController/performance.txt"
    const val faPath: String = "/sdcard/Android/CSController/fast.txt"
    const val spPath: String = "/sdcard/Android/CSController/super_powersave.txt"
    const val csmodulePath: String = "/data/adb/modules/MW_CpuTurboScheduler/module.prop"
    const val csProcess: String = "/data/adb/modules/MW_CpuTurboScheduler/MW_CpuSpeedController"
    const val csLog: String = "/storage/emulated/0/Android/MW_CpuSpeedController/log.txt"
    const val csSettingsPath: String =
        "/storage/emulated/0/Android/MW_CpuSpeedController/config.ini"
    const val appConfig: String = "/storage/emulated/0/Android/CSController/app_config.json"
    const val CsServicePath: String = "/data/adb/modules/MW_CpuTurboScheduler/service.sh"
    const val balanceName: String = "balance"
    const val powersaveName: String = "powersave"
    const val performanceName: String = "performance"
    const val fastName: String = "fast"
    const val superPowersaveName: String = "super_powersave"
    var powersaveConfigPath: String = "/path/to/powersave/config"
    var balanceConfigPath: String = "/path/to/balance/config"
    var performanceConfigPath: String = "/path/to/performance/config"
    var fastConfigPath: String = "/path/to/fast/config"
    var superPowersaveConfigPath: String = "/path/to/superPowersave/config"

    // 这里定义模式对应的包名列表
    var powersaveList: List<String> = ArrayList()
    var balanceList: List<String> = ArrayList()
    var performanceList: List<String> = ArrayList()
    var fastList: List<String> = ArrayList()
    var superPowersaveList: List<String> = ArrayList()
    var poList: MutableList<String> = ArrayList()
    var baList: MutableList<String> = ArrayList()
    var peList: MutableList<String> = ArrayList()
    var faList: MutableList<String> = ArrayList()
    var spList: MutableList<String> = ArrayList()
    private val filePaths: List<String> = Arrays.asList(
        poPath,
        bPath,
        pePath,
        faPath,
        spPath
    )
    var lists: List<MutableList<String>> = Arrays.asList(
        poList,
        baList,
        peList,
        faList,
        spList
    )

}
