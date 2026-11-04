# Pretendard

앱에서 사용하는 Regular (400), Medium (500), SemiBold (600), Bold (700)를 정적 TTF로 포함한다. 네트워크 없이 로드하며 Android API 24 이상에서 동일한 리소스를 사용한다.

- 원본: https://github.com/orioncactus/pretendard
- 버전: 1.3.9
- 배포 파일: https://github.com/orioncactus/pretendard/releases/download/v1.3.9/Pretendard-1.3.9.zip
- 원본 경로: `public/static/alternative/Pretendard-{Regular,Medium,SemiBold,Bold}.ttf`
- 앱 리소스: `src/main/res/font/pretendard_*.ttf` (내용 변경 없이 파일명만 Android 리소스 규칙에 맞춤)
- 라이선스: SIL Open Font License 1.1. 배포본의 `LICENSE.txt` 원문을 `src/main/assets/licenses/Pretendard-LICENSE.txt`에 포함한다.

`PretendardFontFamily`를 MoyeotaType 및 Material3 Typography 전체에 적용한다. BasicTextField는 Material 기본 스타일을 상속하지 않으므로 명시적으로 같은 글꼴을 지정한다.
