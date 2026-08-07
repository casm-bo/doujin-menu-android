# doujin-menu-android

[doujin-menu 데스크톱 앱](https://github.com/casm-bo/doujin-menu-V2)의 Companion Server와 연동하는 Android 클라이언트입니다. Android 앱은 데스크톱 앱과 기능 구성이 다르며, 단독으로 사용하지 않고 데스크톱 앱과 페어링해 사용합니다.

## 주요 기능

- **데스크톱 연결**
  - Companion Server 연결 확인 및 6자리 코드 페어링
  - 여러 데스크톱 저장·선택 및 연결 상태 표시
  - 장치 토큰과 데스크톱 정보를 Android Keystore 기반 AES-GCM으로 암호화 저장
- **Hitomi 검색**
  - 검색어·언어·작가·태그 필터, 자동 완성 및 검색 즐겨찾기
  - 썸네일·제목·작가·태그·페이지 수를 표시하는 갤러리 카드
  - 30개 단위 페이지네이션과 목록 끝 자동 무한 스크롤
  - 갤러리 상세 조회, 작가·태그 클릭 검색 및 다운로드 요청
- **라이브러리 관리 및 동기화**
  - Android 로컬 폴더와 ZIP/CBZ, 데스크톱 라이브러리 통합 조회
  - `info.txt` 메타데이터 파싱, 검색·정렬·필터, 그리드·목록 보기
  - 즐겨찾기·읽음·숨김·진행률과 사용자 지정 제목 동기화
  - 시리즈 생성·편집·정렬·병합 및 여러 항목 일괄 관리
  - Android 로컬 작품을 CBZ로 묶어 데스크톱에 동기화
- **통합 리더**
  - 로컬 및 원격 작품 전체화면 열람, 마지막 페이지 복원과 다음 작품 이동
  - 좌→우·우→좌 방향, 화면·너비 맞춤, 스와이프·탭 페이지 이동
  - 두 번 탭 확대·이동, 썸네일 탐색, 페이지 번호 및 화면 꺼짐 방지
  - 사용자 지정 9분할 탭 영역
- **다운로드 관리**
  - 데스크톱 다운로드 큐의 진행률·속도·오류 확인
  - 일시정지·재개·재시도·취소 및 완료 항목 정리
- **반응형 UI**
  - 휴대폰 하단 내비게이션과 넓은 화면 Navigation Rail
  - 시스템 설정을 따르는 라이트·다크 테마

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

