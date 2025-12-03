# Cache Stampede 실험

## 개요
- 캐시 스탬피드 재현(main)과 대응 전략(각 feature 브랜치)을 비교 실험하는 프로젝트.
- Spring Boot + Redis Cluster + MySQL + Prometheus/Grafana로 QPS/지연/자원 경합을 관찰.

## 브랜치 맵
- `main` : 순수 Look-aside 캐시, TTL 만료 시 스탬피드 발생.
- `feature/cache-spin-lock` : Redisson `RLock`으로 단일 요청만 DB 조회, 나머지는 spin 재확인.
- `feature/cache-stale-while-revalidation` : Stale-While-Revalidate로 만료 후에도 stale 값을 응답, 백그라운드 갱신.
- `feature/PER` : Probabilistic Early Refresh, 만료 시점 갱신을 확률적으로 분산.

## 실험 환경
- 앱 2대, MySQL 1대, Redis 6노드(3 master/3 replica), Prometheus/Grafana, Redis/MySQL/Node exporter.
- 기본 부하: JMeter ramp-up 10s, duration 120s, 500 threads.
- 캐시 전략: Look-aside, 키 prefix `itemCache:`, TTL 120s.

## 빠른 실행
- 필수: Docker/Compose, MySQL(`cache_practice`, `cache_user`/`1234`), Java 17.
- 전체 스택: `docker compose up --build` (Windows: `./start_server.ps1`)

## API
- `GET /items/{id}` : 캐시 경유 조회.
- `GET /items/{id}/db` : DB 강제 조회.


## 원인 정리 (Cache Stampede)
- 동일 키 만료 시 모든 요청이 cache miss → 동일 SELECT로 인덱스 페이지 latch 경합 → DB 지연이 WAS로 전파, 커넥션 풀 고갈 및 스레드 풀 포화 → 장애.

## 참고
- 클러스터 부트스트랩: `scripts/init-redis-cluster.sh`
- 모니터링 스크레이프: `prometheus/prometheus.yml`
