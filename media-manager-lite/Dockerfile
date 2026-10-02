# syntax=docker/dockerfile:1
#
# Multi-stage build producing a static, distroless image for linux/amd64 and
# linux/arm64. The Go build stage always runs on the build machine's native
# platform (BUILDPLATFORM) and cross-compiles, so no emulation is involved.

FROM --platform=$BUILDPLATFORM golang:1.27-alpine AS build
ARG TARGETOS
ARG TARGETARCH
ARG VERSION=dev
ARG COMMIT=unknown
ARG BUILD_DATE=unknown
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath \
    -ldflags "-s -w \
      -X media-manager-lite/internal/version.Version=${VERSION} \
      -X media-manager-lite/internal/version.Commit=${COMMIT} \
      -X media-manager-lite/internal/version.BuildDate=${BUILD_DATE}" \
    -o /out/media-manager ./cmd/media-manager

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/media-manager /media-manager

# Defaults for container deployments; /config is expected to be a volume.
ENV MEDIA_MANAGER_HTTP_ADDR=:8080 \
    MEDIA_MANAGER_DB=/config/media-manager.db \
    MEDIA_MANAGER_LOG_LEVEL=info

VOLUME /config
EXPOSE 8080

# Runs as root by default (NAS media trees are frequently root-owned and
# /config volumes vary in ownership). Drop privileges explicitly if you
# prefer: `user: "65532:65532"` in compose or `--user` with docker run —
# the /config volume must then be writable by that UID.
ENTRYPOINT ["/media-manager"]
