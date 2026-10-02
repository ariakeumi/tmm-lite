GO ?= go
APP := media-manager
BIN_DIR := bin

GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo unknown)
BUILD_DATE := $(shell date -u +%Y-%m-%dT%H:%M:%SZ)
VERSION ?= dev

LDFLAGS := -s -w \
	-X media-manager-lite/internal/version.Version=$(VERSION) \
	-X media-manager-lite/internal/version.Commit=$(GIT_COMMIT) \
	-X media-manager-lite/internal/version.BuildDate=$(BUILD_DATE)

.DEFAULT_GOAL := help

.PHONY: help build test lint tidy run docker docker-amd64 docker-arm64 build-linux-arm64 build-linux-amd64

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

build: ## Build the server binary into bin/
	CGO_ENABLED=0 $(GO) build -trimpath -ldflags "$(LDFLAGS)" -o $(BIN_DIR)/$(APP) ./cmd/media-manager

test: ## Run all tests
	$(GO) test ./...

lint: ## go vet + gofmt check
	$(GO) vet ./...
	@files="$$($(GO) run mvdan.cc/gofmt@latest -l . 2>/dev/null || gofmt -l .)"; \
	if [ -n "$$files" ]; then echo "gofmt needed:"; echo "$$files"; exit 1; fi; \
	echo "lint ok"

tidy: ## go mod tidy
	$(GO) mod tidy

run: ## Run the server locally (defaults: :8080, data/media-manager.db)
	$(GO) run ./cmd/media-manager

docker: ## Multi-arch image build (linux/amd64 + linux/arm64)
	docker buildx build \
		--platform linux/amd64,linux/arm64 \
		--build-arg VERSION=$(VERSION) \
		--build-arg COMMIT=$(GIT_COMMIT) \
		--build-arg BUILD_DATE=$(BUILD_DATE) \
		.

docker-arm64: ## Build + load linux/arm64 image locally
	docker buildx build --platform linux/arm64 --load -t media-manager-lite:arm64 \
		--build-arg VERSION=$(VERSION) --build-arg COMMIT=$(GIT_COMMIT) --build-arg BUILD_DATE=$(BUILD_DATE) .

docker-amd64: ## Build linux/amd64 image (cache only, no load)
	docker buildx build --platform linux/amd64 \
		--build-arg VERSION=$(VERSION) --build-arg COMMIT=$(GIT_COMMIT) --build-arg BUILD_DATE=$(BUILD_DATE) .

build-linux-arm64: ## Cross-compile linux/arm64 binary (no Docker needed)
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 $(GO) build -trimpath -ldflags "$(LDFLAGS)" -o $(BIN_DIR)/$(APP)-linux-arm64 ./cmd/media-manager

build-linux-amd64: ## Cross-compile linux/amd64 binary (no Docker needed)
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GO) build -trimpath -ldflags "$(LDFLAGS)" -o $(BIN_DIR)/$(APP)-linux-amd64 ./cmd/media-manager
