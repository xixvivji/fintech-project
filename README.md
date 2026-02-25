# Fintech Mock Investing Platform

공용 리그 날짜를 기준으로 과거 시세를 리플레이하며 모의투자를 진행하는 웹 서비스입니다.  
차트 조회, 모의투자 주문(시장가/지정가), 체결내역/미체결 주문, 수익률 랭킹, 리그 운영 상태, 종목정보, 뉴스/공시(Mock) 화면을 제공합니다.

## 프로젝트 소개

이 프로젝트는 단순 차트 조회 앱이 아니라, `공용 기준일`을 중심으로 여러 사용자가 같은 시점에서 경쟁하는 `리그형 모의투자 서비스`를 목표로 개발했습니다.

핵심 컨셉
- 공용 리그 날짜 기반 시뮬레이션
- 과거 일봉 데이터 리플레이
- 모의투자 주문/체결/평가/랭킹 연동
- 홈 대시보드 중심 UX

## 주요 기능

### 1. 홈 대시보드 (`/`)
- 공용 리그 상태 요약 (기준일 / 진행 상태)
- 내 계좌 요약 (총자산, 예수금, 손익, 수익률)
- 상승/하락 TOP 카드
- 관심종목 / 보유종목 요약
- 수익률 랭킹 Top 5
- 빠른 차트
- 위젯 드래그 정렬 / 접기 / `localStorage` 저장
- 블루 / 민트 테마 프리셋

### 2. 차트 페이지 (`/charts`)
- 종목 검색 및 차트 추가
- 다중 차트 카드 표시
- 카드별 기간 선택 (`6M / 1Y / 전체`)
- 비교 종목 선택 (상대수익률 비교)
- 카드 순서 드래그 정렬
- 카드 설정 `localStorage` 저장
- 공용 기준일 배지 표시

### 3. 모의투자 페이지 (`/sim`)
- 리플레이 시작 / 일시정지 / 초기화
- 시장가 / 지정가 주문
- 선택 종목 미니차트 + 현재가 표시
- 미체결 주문 탭
- 체결내역 탭
- 수익률 랭킹 탭
- 보유 종목 테이블 + 빠른 주문 버튼(25% / 50% / 전량)
- 주문 확인 모달
- 랭킹 사용자 포트폴리오 요약 모달

### 4. 종목정보 페이지 (`/market`)
- 종목 선택
- 기준일 기준 차트
- 종목 요약 정보 (일부 Mock)
- 관심종목 추가/해제
- 모의투자 페이지로 바로 이동

### 5. 뉴스·공시 페이지 (`/news`)
- 종목 선택 / 기준일 표시
- 뉴스 / 공시 탭 UI
- 현재는 Mock 데이터 기반 (향후 과거 데이터 적재 예정)

### 6. 리그 운영 페이지 (`/league`)
- 공용 리그 상태/기준일/시작일/틱 정보
- Top 3 랭킹 요약

## 스크린샷 (포트폴리오용)



### 홈 대시보드
![홈 대시보드](docs/screenshots/home-dashboard.png)

### 차트 페이지 (비교 모드 / 카드 정렬)
![차트 페이지](docs/screenshots/charts-page.png)

### 모의투자 페이지 (주문/보유/체결내역/랭킹)
![모의투자 페이지](docs/screenshots/simulation-page.png)

### 리그 운영 페이지
![리그 운영 페이지](docs/screenshots/league-page.png)

### 종목정보 / 뉴스·공시 페이지
![종목정보 페이지](docs/screenshots/market-page.png)
![뉴스 공시 페이지](docs/screenshots/news-page.png)

## 기술 스택

### Frontend
- React
- Axios
- `lightweight-charts`
- CSS (커스텀 스타일링)

### Backend
- Spring Boot `3.4.1`
- Java `17`
- Spring Data JPA
- PostgreSQL

### Infra / Deployment
- Docker / Docker Compose
- Nginx (EC2 배포용 구성 초안)

## 아키텍처 / 핵심 설계

### 1. 공용 리그 날짜 (`sim_league_state`)
모든 사용자가 동일한 날짜 기준으로 평가되도록 `공용 리그 날짜`를 도입했습니다.

포인트
- 랭킹 공정성 확보
- 차트/모의투자/리그 운영 화면 기준일 통일
- 리그 상태 API로 프론트 전역 동기화 가능

### 2. 가격 데이터 DB 캐시 (`daily_price`)
외부 시세 API에 직접 의존하면 응답속도와 안정성이 떨어지므로, 일봉 데이터를 DB에 저장하는 구조를 도입했습니다.

조회 흐름
1. `daily_price` 조회
2. 요청 범위 커버 여부 검사
3. 부족하면 외부 API(KIS) 조회
4. DB 저장 후 반환

효과
- 차트 응답속도 개선
- 모의투자 평가 일관성 향상
- 외부 API 호출량 감소

### 3. 가격 백필(Backfill) 파이프라인
과거 리플레이 운영을 위해 `2015-01-01 ~ 2025-12-31` 일봉 데이터를 종목 단위로 선적재할 수 있도록 구현했습니다.

구성
- 백엔드 API: `POST /api/stock/backfill`
- PowerShell 스크립트: `scripts/backfill-prices.ps1`
- 실패 종목 스킵 / 로그 저장 지원

## 데이터 적재 결과 (예시)

- 백필 대상: 50개 종목
- 적재 범위: `2015-01-01 ~ 2025-12-31`
- 적재 결과: `124,982 rows`

특징
- 장기 상장 종목: 약 `2700` 영업일 데이터
- 최근 상장 종목: 상장일 기준으로 자연스럽게 row 수 감소

## 구현하면서 해결한 기술 이슈

### 1. Read-only 트랜잭션에서 INSERT 실패
- 증상: `cannot execute INSERT in a read-only transaction`
- 원인: 조회 메서드 내부에서 가격 캐시 저장(INSERT) 발생
- 해결: 캐시 저장이 발생 가능한 메서드의 `readOnly=true` 제거

### 2. PostgreSQL 예약어 충돌
- `current_date` 컬럼명이 PostgreSQL 예약어와 충돌
- `league_current_date`로 변경 및 JPA 매핑 수정

### 3. 백필 청크 경계 누락
- 백필 시 `2015-01-01` 시작 구간 일부 누락
- 청크 경계 계산 수정 및 백필 전용 로직 분리로 해결

### 4. partial-hit 캐시 문제
- DB에 일부 데이터만 있어도 성공 처리되어 차트가 중간에서 끊김
- 요청 범위 커버 검사 도입으로 해결

## 로컬 실행 방법

### 1) PostgreSQL 실행 (Docker)
```powershell
docker compose up -d
```

### 2) 백엔드 실행
```powershell
cd backend
.\gradlew.bat bootRun
```

### 3) 프론트 실행
```powershell
cd frontend
npm install
npm start
```

접속 주소
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`

## 백필 실행 예시

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backfill-prices.ps1 `
  -ApiBaseUrl "http://localhost:8080" `
  -StartDate "2015-01-01" `
  -EndDate "2025-12-31" `
  -ChunkMonths 6 `
  -Codes "005930,000660,035420,035720,005380,000270,068270,207940,005490,033780"
```

적재 확인
```powershell
docker exec -it fintech-postgres psql -U fintech -d fintech -P pager=off -c "SELECT COUNT(DISTINCT code) AS code_count, COUNT(*) AS total_rows FROM daily_price;"
```



### 제품 관점
- 공용 리그 날짜 기반의 경쟁형 모의투자라는 서비스 컨셉
- 홈 대시보드 중심 UX
- 차트 → 종목정보 → 모의투자 → 랭킹으로 이어지는 사용자 흐름

### 기술 관점
- 외부 API 의존을 줄이기 위한 DB 캐시 구조 (`daily_price`)
- 백필 자동화 파이프라인
- 랭킹/리그 날짜 설계 (공정성 기준)
- 프론트 대시보드 위젯화 + 상태 저장(`localStorage`)

## 향후 개선 계획

- 기간 랭킹(`오늘 / 최근 7일`) 실제 계산 (`portfolio_snapshot` 도입)
- 수수료 / 세금 반영
- 영업일 캘린더 기반 리그 날짜 진행 (주말/휴장일 스킵)
- 뉴스·공시 실제 과거 데이터 적재 및 연동
- 홈 대시보드 집계 API(상승/하락 TOP) 백엔드화

## 라이선스 / 주의사항

- 본 프로젝트의 뉴스·공시 화면은 현재 Mock 데이터 기반입니다.
- 외부 시세/뉴스/공시 데이터 연동 시, 각 제공자의 이용약관 및 재배포 정책을 반드시 확인해야 합니다.
```
