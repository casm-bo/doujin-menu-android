package com.doujinmenu.android.ui

internal data class AppChangelog(
    val version: String,
    val changes: List<String>,
)

internal val appChangelog = listOf(
    AppChangelog(
        version = "0.6.0",
        changes = listOf(
            "설정의 정보 화면에서 버전별 업데이트 기록을 확인할 수 있습니다.",
            "진단 정보가 포함된 GitHub 문제 등록 화면을 바로 열 수 있습니다.",
            "라이브러리 기본 정렬을 수정일 기준 최신순으로 변경하고 선택한 정렬을 기억합니다.",
            "female:·male: 태그를 데스크톱 앱과 같은 색상 UI로 표시합니다.",
            "새 버전 확인과 APK 다운로드 흐름을 추가했습니다.",
            "가로 화면 리더의 컨트롤과 미리보기 동작을 다듬었습니다.",
        ),
    ),
    AppChangelog(
        version = "0.5.0",
        changes = listOf(
            "GitHub Release에 서명된 APK를 게시하는 자동 릴리스를 추가했습니다.",
            "마지막 페이지까지 읽으면 작품을 읽음 상태로 기록하도록 수정했습니다.",
        ),
    ),
    AppChangelog(
        version = "0.1.0",
        changes = listOf("Android 앱의 첫 공개 버전을 출시했습니다."),
    ),
)
