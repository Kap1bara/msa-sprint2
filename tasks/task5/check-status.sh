apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: booking-service-feature-flag
  namespace: default
spec:
  # Без workloadSelector: применится ко всем sidecar proxy в namespace default
  # (важно, потому что решение о маршрутизации принимается на стороне клиента).
  configPatches:
    - applyTo: HTTP_ROUTE
      match:
        context: SIDECAR_OUTBOUND
        routeConfiguration:
          vhost:
            # В outbound у Istio vhost обычно имеет вид "<host>:<port>"
            name: "booking-service.default.svc.cluster.local:80"
      patch:
        operation: INSERT_FIRST
        value:
          match:
            headers:
              - name: "x-feature-enabled"
                stringMatch:
                  exact: "true"
          route:
            cluster: "outbound|80|v2|booking-service.default.svc.cluster.local"#!/bin/bash

set -e

echo "▶️ Checking booking-service deployment..."
kubectl get pods -l app=booking-service

echo
echo "▶️ Checking service..."
kubectl get svc booking-service || echo "(No service found)"

echo
echo "▶️ Helm release:"
helm list | grep booking-service || echo "(No release found)"

echo
echo "▶️ Port-forward to test service locally:"
echo "  kubectl port-forward svc/booking-service 8080:80"
echo "  Then in another terminal:"
echo "    curl http://localhost:8080/ping"

echo
echo "▶️ Quick curl (if port-forward already running):"
curl --fail http://localhost:8080/ping && echo "✅ Reachable" || echo "❌ Not responding"
