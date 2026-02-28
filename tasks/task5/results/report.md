добавил в k8s/ chart для rollout v1v2 (сделал в одном файле booking-service-v1v2)
добавил в istio/ правила для canary деплоя

Поправил /ping так, чтобы ответ зависел от версии API (env)

Поправил check-canary, так что бы запросы корректно роутились by istio

Другие тесты хорошо проходили и с port forwarding, например feature-toggle
