# Task4 — CI/CD + Helm + Docker + DNS

## Что сделано

### 1) REST-сервис (Go)
- Реализованы эндпоинты:
  - `GET /ping` — основной health endpoint для проб Kubernetes.
  - `GET /health` — liveness (процесс жив).
  - `GET /ready` — readiness (становится ready через ~0.5 сек после старта).
- Фича-флаг: при `ENABLE_FEATURE_X=true`
  - `GET /ping` возвращает `pong (feature_x=on)`.
  - Появляется эндпоинт `GET /feature`.

Файлы:
- `task4/booking-service/main.go`

### 2) Docker-образ
- Multi-stage Dockerfile:
  - build: `golang:1.21-alpine`
  - runtime: `alpine` + `curl` для `HEALTHCHECK`
- `HEALTHCHECK` по `http://localhost:8080/ping`

Файлы:
- `task4/booking-service/Dockerfile`

### 3) Helm-чарт
- Deployment:
  - `replicaCount`
  - `image.name`, `image.tag`, `image.pullPolicy`
  - `env[]` + `ENABLE_FEATURE_X`
  - `resources` (requests/limits)
  - `livenessProbe` и `readinessProbe` по `/ping`
- Service:
  - ClusterIP
  - порт `80 -> 8080`
- Имя Service/Deployment фиксировано как `booking-service` (совпадает с `check-dns.sh`).

Файлы:
- `task4/helm/booking-service/*`

### 4) values для staging/prod
- `results/values-staging.yaml` — 1 реплика, feature OFF, debug логирование.
- `results/values-prod.yaml` — 3 реплики, feature ON, более высокие ресурсы.

### 5) CI/CD (.gitlab-ci.yml)
Стадии:
- `build`: `docker build`
- `test`: `docker run` + проверка `/ping` + `docker rm`
- `deploy`: `minikube image load` + `helm upgrade --install`
- `tag`: git-тег `deploy-<utc_timestamp>`

Запуск локально:
```bash
cd tasks/task4
# по отдельности
gitlab-ci-local build
gitlab-ci-local test
gitlab-ci-local deploy
gitlab-ci-local tag

# или все стадии
gitlab-ci-local
```

Файлы:
- `task4/.gitlab-ci.yml` (+ копия в `results/.gitlab-ci.yml`)

## Проверка Service Discovery (DNS)
Скрипт:
```bash
cd tasks/task4
./check-dns.sh
```
Ожидается, что внутри кластера будет доступно:
- `http://booking-service/ping`

## Команды для ручной проверки
```bash
# build
cd tasks/task4
docker build -t booking-service:local ./booking-service

# run
docker run --rm -p 8080:8080 -e ENABLE_FEATURE_X=true booking-service:local
curl http://localhost:8080/ping
curl http://localhost:8080/feature

# deploy в minikube
minikube image load booking-service:local
helm upgrade --install booking-service ./helm/booking-service -f ./results/values-staging.yaml --set image.tag=local
kubectl get pods,svc
```

## Артефакты (скриншоты/логи)
В этот репозиторий нужно добавить/положить в `task4/results/`:
- вывод `./check-status.sh`
- вывод `./check-dns.sh`
- `kubectl get pods` и `kubectl get services`
- `docker image ls` и `minikube image list`
- лог успешной сборки
- скриншот успешного `curl /ping`
