# doujin-menu-android

`doujin-menu` Electron 앱의 Companion Server와 연동하는 Android 클라이언트입니다.

## 현재 구현 범위

- Companion Server 주소와 포트 입력
- 인증 없는 `GET /v1/status` 연결 확인
- 6자리 코드로 `POST /v1/pair` 페어링
- 장치 토큰과 데스크톱 정보를 Android Keystore 기반 AES-GCM 암호화 저장
- 앱 재실행 후 저장된 데스크톱 선택
- Bearer 토큰을 사용한 `POST /v1/hitomi/search`
- 검색 결과 ID별 `GET /v1/hitomi/gallery/{id}` 상세 조회
- 썸네일·제목·작가·태그·페이지 수 갤러리 카드
- 30개 단위 페이지네이션과 목록 끝 자동 무한 스크롤
- 갤러리 상세 요청 최대 4개 병렬 처리
- 휴대폰 하단 내비게이션 및 넓은 화면 왼쪽 Navigation Rail
- 브라우저·갤러리·다운로드·설정 화면 분리
- 갤러리 카드에서 상세 화면 이동
- `GET /v1/hitomi/gallery/{id}/pages` 기반 전체화면 리더
- 좌우 페이지 이동, 다음 2페이지 프리페치, 페이지 표시
- 두 번 탭 확대와 확대 상태에서 이동·축소

페어링·검색, 갤러리 카드·페이지네이션, 기본 상세 화면과 원격 리더까지 구현되어 있습니다. 작가·태그 클릭 검색, 필터 상속, 북마크, 다운로드 큐와 SMB 라이브러리는 다음 단계에서 추가합니다.

## 개발 환경

- Android Studio Quail 2 이상 권장
- JDK 17
- Android SDK 37
- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Kotlin/Compose Compiler 2.3.21
- Jetpack Compose BOM 2026.06.00
- 최소 Android API 26

Android Studio에서 저장소 루트를 열고 SDK 37을 설치한 뒤 Gradle Sync를 실행합니다.

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 연결 방법

1. 데스크톱 앱의 `설정 → 모바일 연동`에서 Companion Server를 켭니다.
2. Android 장치와 데스크톱을 같은 사설 네트워크에 연결합니다.
3. 앱에 데스크톱 IP와 포트(기본값 `47831`)를 입력하고 상태를 확인합니다.
4. 데스크톱에서 6자리 코드를 생성해 Android 앱에 입력합니다.
5. 페어링된 데스크톱을 선택하고 Hitomi 검색 조건을 입력합니다.

Companion API 1차 버전이 로컬 HTTP를 사용하므로 현재 앱은 cleartext 트래픽을 허용합니다. TLS 또는 요청 서명을 도입할 때 네트워크 보안 설정도 함께 제한해야 합니다.

