# NAJAKS (나작스)

스트리머와 팬을 잇는 커뮤니티 사이트. 콘텐츠 큐레이션, 방송 일정, 팬 커뮤니티, 클립, 리포트, 스트리머 등록까지.

- 도메인: https://najaks.co.kr
- 배포: AWS EC2 (Amazon Linux 2023) + RDS (MySQL) + S3 + nginx + systemd

---

## 기능

- 회원 (이메일/비밀번호 + JWT, 역할 분리: FAN / STREAMER / ADMIN)
- 스트리머 등록 신청 / 승인 (관리자)
- 방송 일정 / LIVE 감지 (치지직 폴링)
- 콘텐츠 (영상/방송 큐레이션, 카테고리·검색·태그)
- 클립스 (인기 클립 모음)
- 커뮤니티 게시판 + 댓글
- 신고/리포트 (AI 분류 — Gemini 연동)
- 주간 리포트 자동 생성 (Notion DB 연동)
- 채팅 분석 / DAU 트래킹
- 관리자 페이지 (대시보드 · 배너 · 회원 · 리포트 처리)

---

## 빠른 시작 (로컬 개발)

### 사전 요구
- Java 17 (Amazon Corretto 권장)
- Node.js 20 이상
- MySQL 8 (DB명 `najacks_dev` — `application-dev.yml` 기준)

### 1. 백엔드

```bash
cd backend
# 환경변수 셋업 (.env 또는 시스템 env)
#   DB_USERNAME, DB_PASSWORD
#   JWT_SECRET (256bit+ 랜덤)
#   AWS_S3_BUCKET, AWS_ACCESS_KEY, AWS_SECRET_KEY
#   GEMINI_API_KEY (선택)
#   NOTION_TOKEN, NOTION_DB_* (선택)
#   CHZZK_CLIENT_ID, CHZZK_CLIENT_SECRET, CHZZK_REDIRECT_URI
#   CRYPTO_MASTER_KEY
#   MAIL_USERNAME, MAIL_PASSWORD (gmail SMTP)

./gradlew bootRun --args='--spring.profiles.active=dev'
# http://localhost:8080
```

> 로컬 개발은 `application-dev.yml` (DB `najacks_dev`, ddl-auto `create-drop`)을 사용합니다. 운영 프로파일은 `application.yml` 단독 + 환경변수.

### 2. 프런트엔드

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173 → /api 호출은 백엔드(:8080)로 프록시 필요
```

---

## 구조

```
najaks/
├── backend/                 Spring Boot 3 + Java 17
│   └── src/main/
│       ├── java/com/najacks/backend/
│       │   ├── ai/          Gemini API 클라이언트 + 분류 파이프라인
│       │   ├── auth/        회원 가입/로그인/JWT/이메일 인증
│       │   ├── config/      Security, CORS, WebClient 설정
│       │   ├── domain/
│       │   │   ├── admin/   관리자 대시보드/배너/통계
│       │   │   ├── broadcast/   방송 일정
│       │   │   ├── chat/    채팅 분석
│       │   │   ├── clip/    클립스
│       │   │   ├── content/ 콘텐츠 큐레이션
│       │   │   ├── notification/
│       │   │   ├── post/    커뮤니티 게시판 + 댓글
│       │   │   ├── report/  신고/리포트 (주간 리포트)
│       │   │   ├── resource/  자료실
│       │   │   ├── search/
│       │   │   ├── stream/  치지직 폴링 + LIVE 감지
│       │   │   └── user/    프로필/마이페이지
│       │   ├── global/      공통 응답/예외/유틸
│       │   ├── infra/       S3, Mail, PDF 등 외부 인프라
│       │   ├── notion/      Notion API 클라이언트 + DB 매핑
│       │   └── tracking/    DAU 트래킹
│       └── resources/
│           ├── application.yml          기본 (env 변수 주입)
│           ├── application-dev.yml      로컬 dev
│           └── templates/               메일/PDF Thymeleaf 템플릿
└── frontend/                Vue 3 + Vite
    └── src/
        ├── api/             axios 함수
        ├── views/
        │   ├── admin/       관리자 페이지
        │   ├── auth/        로그인/회원가입
        │   ├── community/   게시판/클립
        │   ├── content/     콘텐츠
        │   ├── creator/     스트리머 등록/관리
        │   ├── mypage/
        │   ├── search/
        │   ├── streamer/    스트리머 상세
        │   └── static/      이용약관 등
        ├── components/
        ├── composables/
        ├── layouts/
        ├── router/
        └── stores/          Pinia
```

---

## 스택

**Backend**: Spring Boot 3 / Java 17 / Spring Security / Spring Data JPA / Hibernate / JJWT 0.12 / MySQL / AWS SDK S3 / Spring Mail (SMTP) / Thymeleaf + openhtmltopdf (PDF 리포트) / socket.io-client (외부 신호 수신)

**Frontend**: Vue 3.5 (Composition API) / Vite 8 / Pinia / Vue Router 4 / Axios / cropperjs (이미지 크롭)

**외부 통합**: 치지직 OpenAPI / Gemini AI / Notion API / Gmail SMTP

**배포**: AWS EC2 (Amazon Corretto 17) + RDS MySQL + S3 + nginx (SPA fallback + /api 프록시) + systemd

---

## 배포 참고 파일

- `najacks.nginx.conf` — nginx 사이트 설정 (SPA fallback + /api 리버스 프록시)
- `najacks.service` — systemd 유닛 (Java jar 실행, `/etc/najacks.env`에서 환경변수 로드)

운영 환경변수는 `/etc/najacks.env` 같은 시스템 파일에 두고 systemd `EnvironmentFile`로 주입하는 패턴입니다.
