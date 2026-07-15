package com.doujinmenu.android.network

import java.net.URI

object EndpointNormalizer {
    const val DEFAULT_PORT = 47831

    fun normalize(rawHost: String, rawPort: String): String {
        val hostInput = rawHost.trim()
        require(hostInput.isNotEmpty()) { "데스크톱 주소를 입력하세요." }

        val candidate = if ("://" in hostInput) hostInput else "http://$hostInput"
        val uri = runCatching { URI(candidate) }
            .getOrElse { throw IllegalArgumentException("올바른 데스크톱 주소가 아닙니다.") }

        require(uri.scheme == "http" || uri.scheme == "https") {
            "주소는 http 또는 https만 사용할 수 있습니다."
        }
        require(uri.host != null && uri.userInfo == null) { "올바른 호스트를 입력하세요." }
        require(uri.query == null && uri.fragment == null) { "주소에 쿼리나 프래그먼트를 넣을 수 없습니다." }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "주소에는 경로를 넣지 마세요." }

        val port = if (uri.port >= 0) {
            uri.port
        } else {
            rawPort.trim().ifEmpty { DEFAULT_PORT.toString() }.toIntOrNull()
                ?: throw IllegalArgumentException("포트는 숫자로 입력하세요.")
        }
        require(port in 1..65535) { "포트는 1~65535 사이여야 합니다." }

        return URI(uri.scheme, null, uri.host, port, null, null, null).toString()
    }
}
