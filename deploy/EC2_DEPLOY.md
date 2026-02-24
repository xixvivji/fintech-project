# EC2 Single-Instance Deployment (Nginx + Spring Boot + Postgres)

목표 구성:
- `nginx`: React 정적 파일 서빙 + `/api` 프록시
- `backend`: Spring Boot API (`8080`)
- `postgres`: 내부 DB (`5432`, 외부 비공개)

## 1. EC2 준비

- Ubuntu 22.04 LTS 권장
- 인스턴스 타입: 최소 `t3.small` (가능하면 `t3.medium`)
- 보안그룹:
  - `80/tcp` 허용 (0.0.0.0/0)
  - `22/tcp` 허용 (운영자 IP만)
  - `5432/tcp` 비공개
  - `8080/tcp` 비공개

## 2. Docker 설치

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

로그아웃/재로그인 후 `docker --version` 확인.

## 3. 프로젝트 업로드

```bash
git clone <your-repo-url> fintech-project
cd fintech-project
cp .env.deploy.example .env
```

`.env` 수정:
- `POSTGRES_PASSWORD`
- `AUTH_JWT_SECRET`
- 필요 시 `KIS_*`

## 4. 실행

```bash
docker compose -f docker-compose.deploy.yml --env-file .env up -d --build
```

확인:

```bash
docker compose -f docker-compose.deploy.yml ps
docker compose -f docker-compose.deploy.yml logs -f backend
docker compose -f docker-compose.deploy.yml logs -f nginx
```

브라우저:
- `http://<EC2_PUBLIC_IP>/`

## 5. 운영 체크 (3일 테스트 서비스 기준)

- 서버/앱/DB 타임존 `Asia/Seoul` 유지
- `postgres` 포트 외부 미노출 확인
- 디스크 사용량 확인:

```bash
df -h
docker system df
```

- 중지/재시작:

```bash
docker compose -f docker-compose.deploy.yml down
docker compose -f docker-compose.deploy.yml up -d
```

## 6. 가격 데이터 사전 적재 운영 팁

- 우선 `2020-01-01 ~ 2025-12-31`부터 적재하고 서비스 시작
- 여유가 있으면 `2015-01-01 ~ 2019-12-31` 추가 적재
- `daily_price(code, trade_date)` 유니크 키 기반으로 중복 적재 방지
- 모의투자 공용 리그 날짜는 백엔드에서 `2020-01-01` 기본 시작으로 동작

## 7. HTTPS (선택)

3일 테스트면 HTTP로도 가능하지만, 도메인 연결 시 `certbot` 또는 ALB/CloudFront로 TLS 적용 권장.
