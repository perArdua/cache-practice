#!/bin/sh
set -eu

NODES="${REDIS_CLUSTER_NODES:-redis-node-1:6379 redis-node-2:6379 redis-node-3:6379 redis-node-4:6379 redis-node-5:6379 redis-node-6:6379}"
REPLICAS="${REDIS_CLUSTER_REPLICAS:-1}"
PRIMARY_NODE="$(printf '%s\n' "$NODES" | awk '{print $1}')"
PRIMARY_HOST="${PRIMARY_NODE%%:*}"
PRIMARY_PORT="${PRIMARY_NODE##*:}"

wait_for_node() {
  host="${1%%:*}"
  port="${1##*:}"

  printf 'Waiting for %s:%s to accept connections...\n' "$host" "$port"
  until redis-cli -h "$host" -p "$port" ping >/dev/null 2>&1; do
    sleep 1
  done
  printf '%s:%s is up.\n' "$host" "$port"
}

for node in $NODES; do
  wait_for_node "$node"
done

if redis-cli -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then
  echo "Redis cluster already configured. Skipping bootstrap."
  exit 0
fi

echo "Bootstrapping Redis cluster with nodes: $NODES"
redis-cli --cluster create $NODES --cluster-replicas "$REPLICAS" --cluster-yes

echo "Redis cluster configured."
