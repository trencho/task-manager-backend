FROM mongo:8.0.38@sha256:d97fea17ccb8d004cd137be8dab54d70d0df11be69c9f422aefe4484c8414ab7

LABEL maintainer="Aleksandar Trenchevski <atrenchevski@gmail.com>"

# mongod 8.0.x refuses to start on Linux kernels 6.19 to 7.0.13 (SERVER-121912). GitHub's
# ubuntu-24.04 runners boot a 7.0.0 HWE kernel since 2026-11, and Ubuntu reports 7.0.0-NN, which
# 8.0.34's exemption for >= 7.0.14 does not match. The image sets glibc.pthread.rseq=0 so tcmalloc
# owns rseq; =1 hands rseq to glibc, tcmalloc falls back to per-thread caches, and the startup check
# no longer fires. Cost: slightly lower allocator throughput. Drop this once 8.0.35 (SERVER-131779,
# Ubuntu-aware check) ships. MongoTestContainerConfig sets the same value on the test container.
ENV GLIBC_TUNABLES=glibc.pthread.rseq=1

EXPOSE 27017