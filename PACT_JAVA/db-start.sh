#!/bin/sh
set -eu
cd "$(dirname "$0")"

DB_DIR=${PACT_PGDATA:-data/postgres}
DB_USER=${PACT_DB_USER:-pact}
DB_PASSWORD=${PACT_DB_PASSWORD:-pact-local}

for command_name in initdb pg_ctl psql createdb; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "PostgreSQL command not found: $command_name" >&2
        exit 1
    }
done

if [ ! -s "$DB_DIR/PG_VERSION" ]; then
    mkdir -p "$DB_DIR"
    pass_file=$(mktemp)
    trap 'rm -f "$pass_file"' 0 HUP INT TERM
    (umask 077; printf '%s\n' "$DB_PASSWORD" > "$pass_file")
    initdb -D "$DB_DIR" --username="$DB_USER" --pwfile="$pass_file" \
        --auth-local=trust --auth-host=scram-sha-256
    rm -f "$pass_file"
    trap - 0 HUP INT TERM
fi

if ! pg_ctl -D "$DB_DIR" status >/dev/null 2>&1; then
    pg_ctl -D "$DB_DIR" -l "$DB_DIR/server.log" \
        -o "-h 127.0.0.1 -p 5432" start
fi

DB_EXISTS=$(PGPASSWORD="$DB_PASSWORD" psql -h 127.0.0.1 -U "$DB_USER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname='pact'")
if [ "$DB_EXISTS" != "1" ]; then
    PGPASSWORD="$DB_PASSWORD" createdb -h 127.0.0.1 -U "$DB_USER" pact
fi

echo "PACT PostgreSQL database is ready at localhost:5432/pact"
