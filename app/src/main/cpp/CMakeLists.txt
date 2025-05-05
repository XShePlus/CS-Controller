cmake_minimum_required(VERSION 3.10.2)

# 设置库名
project(cs-controller-native)

# 添加本地库
add_library(
        cs-controller-native
        SHARED
        file_utils.cpp
)

# 查找库
find_library(
        log-lib
        log
)

# 链接库
target_link_libraries(
        cs-controller-native
        ${log-lib}
) 