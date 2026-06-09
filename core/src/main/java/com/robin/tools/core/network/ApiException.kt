package com.robin.tools.core.network

class ApiException(val msg: String, val status: Int) :
    Throwable()