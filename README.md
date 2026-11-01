# 여기콕

<p align="center">
  <img src="docs/branding/icon.png" alt="손을 흔드는 휴대폰 모양의 여기콕 아이콘 콕콕이" width="200">
</p>

**“어디를 눌러야 할지 모르겠을 때, 여기콕.”**<br>
지금 보고 있는 휴대폰 화면에서 다음에 할 행동 하나를 알려주는 AI 스마트폰 도우미입니다.

## 프로젝트 개요

[Android Remote Control MCP](https://github.com/danielealbano/android-remote-control-mcp)를 **로컬(기기 내부)로 포팅**하고, 디지털 취약 계층을 위해 **접근성에 맞게 툴을 수정**했습니다. 이 툴들을 **GPT 6.1 Terra 모델**이 조작해 현재 화면을 읽고 다음 행동을 한 단계씩 안내합니다.

- 음성 또는 글로 물어보면 화면의 글자·버튼·위치를 읽고 다음 행동 하나를 정합니다.
- 누를 곳은 강조, 입력 칸은 테두리, 스와이프는 화살표로 보여줍니다.
- **콕콕이는 가리키고, 사용자가 직접 누릅니다.**

## 팀원

**팀:** [InThon-Team10](https://github.com/InThon-Team10)

| 이름 | GitHub 프로필 |
| --- | --- |
| IlYoung | [@humdrum00001010](https://github.com/humdrum00001010) |
| Yujin | [@makk06](https://github.com/makk06) |
| ARYAL AADARSHA | [@aryalaadarsha](https://github.com/aryalaadarsha) |
| shmoon-37 | [@shmoon-37](https://github.com/shmoon-37) |

## 실행 방법

Android 13 이상, 인터넷 연결이 필요합니다. 시연용 APK는 심사위원분들께 별도로 제공합니다.

- Google Play Protect를 해제한 후에 설치해야 합니다.
- 안 될 경우 ADB 디버그용으로 서명된 앱이라 ADB로 설치해주셔야 합니다.

1. APK를 설치하고 **여기콕**을 실행합니다.
2. 시스템 설정에서 **접근성 서비스**를 켜고 **마이크 권한**을 허용합니다.
3. 홈에서 **물어보기**를 누르고 원하는 일을 말한 뒤, 강조된 위치를 직접 누릅니다.

## 라이선스

[GPL v3.0 or later](LICENSE.md) · [서드파티 고지](THIRD_PARTY_LICENSES.md) · [기존 MCP README](README.upstream.md)
