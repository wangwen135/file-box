#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p config data/default logs runtime/multipart-tmp

JAR_NAME="$(ls file-box-*.jar 2>/dev/null | head -n 1)"
if [ -z "$JAR_NAME" ]; then
    echo "Error: no file-box-*.jar found in $SCRIPT_DIR"
    exit 1
fi

while true; do
    echo ""
    echo "File Box Management"
    echo "1. Reset admin password"
    echo "2. Show config file path"
    echo "0. Exit"
    read -r -p "Select: " choice

    case "$choice" in
        1)
            java -jar "$JAR_NAME" --filebox.maintenance=reset-admin-password "$@"
            ;;
        2)
            echo "$SCRIPT_DIR/config/filebox.yml"
            ;;
        0)
            exit 0
            ;;
        *)
            echo "Invalid selection."
            ;;
    esac
done
