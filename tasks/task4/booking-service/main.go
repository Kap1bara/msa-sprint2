package main

import (
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"sync/atomic"
	"time"
)

func main() {
	enableFeatureX := os.Getenv("ENABLE_FEATURE_X") == "true"

	// Simple readiness signal: become ready shortly after start.
	var ready atomic.Bool
	go func() {
		time.Sleep(500 * time.Millisecond)
		ready.Store(true)
	}()

	http.HandleFunc("/ping", func(w http.ResponseWriter, r *http.Request) {
		if enableFeatureX {
			fmt.Fprintf(w, "pong (feature_x=on)")
			return
		}
		fmt.Fprintf(w, "pong")
	})

	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprint(w, "ok")
	})

	http.HandleFunc("/ready", func(w http.ResponseWriter, r *http.Request) {
		if ready.Load() {
			w.WriteHeader(http.StatusOK)
			fmt.Fprint(w, "ready")
			return
		}
		w.WriteHeader(http.StatusServiceUnavailable)
		fmt.Fprint(w, "not ready")
	})

	// TODO: Feature flag route
	// if ENABLE_FEATURE_X=true, expose /feature
	if enableFeatureX {
		http.HandleFunc("/feature", func(w http.ResponseWriter, r *http.Request) {
			fmt.Fprintf(w, "Feature X is enabled!")
		})
	}

	log.Println("Server running on :8080")

	// Bind explicitly so startup failures are visible.
	ln, err := net.Listen("tcp", ":8080")
	if err != nil {
		log.Fatal(err)
	}
	log.Fatal(http.Serve(ln, nil))
}
